package archersmind.myapplication;

import android.annotation.TargetApi;
import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;

import static junit.framework.Assert.assertTrue;

public class MainActivity extends Activity implements SurfaceHolder.Callback{

    private static final String TAG = "MyApplication";


    private static final String INPUT_YUV_FILE_FOR_ENC_1 =
            Environment.getExternalStorageDirectory() + "/video_1.nv12";
    private static final String INPUT_YUV_FILE_FOR_ENC_2 =
            Environment.getExternalStorageDirectory() + "/video_2.nv12";
    private static final String OUTPUT_ENCODED_FILE_1 =
            Environment.getExternalStorageDirectory() + "/video_1.264";
    private static final String OUTPUT_ENCODED_FILE_2 =
            Environment.getExternalStorageDirectory() + "/video_2.264";
    private static final String VideoPath =
            Environment.getExternalStorageDirectory() + "/TestVideo.mp4";

    int TIMEOUT_USEC = 10000;

    PlayingThread playingThread = null;
    EncodingThread_1 encodingThread_1 = null;
    EncodingThread_2 encodingThread_2 = null;

    private Surface mSurface = null;

    private boolean mDecodingDone = false;

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "OnCreate...");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SurfaceView view = new SurfaceView(this);
        view.getHolder().addCallback(this);
        setContentView(view);

    }

    protected void onPause() {
        Log.i(TAG, "onPause...");
        mDecodingDone = true;
        super.onPause();
    }

    protected void onStop() {
        Log.i(TAG, "onStop...");
        mDecodingDone = true;
        super.onStop();
    }

    protected void onDestroy() {
        Log.i(TAG, "onDestory...");
        super.onDestroy();
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        return super.onOptionsItemSelected(item);
    }
    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        Log.i(TAG, "surfaceCreated..");
        playingThread = new PlayingThread( "Playing-Thead");

        encodingThread_1 = new EncodingThread_1( "Encoding-Thead-1");
        encodingThread_2 = new EncodingThread_2( "Encoding-Thead-2");
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int i, int i1, int i2) {

        Log.i(TAG, "surface Changed...");

        if (playingThread != null) {
            Log.i(TAG, "playing thread starting...");
            playingThread.start();
        }

        if (encodingThread_1 != null) {
            Log.i(TAG, "Encoding thread 1 starting...");
            encodingThread_1.start();
        }

        if (encodingThread_2 != null) {
            Log.i(TAG, "Encoding thread 2 starting...");
            encodingThread_2.start();
        }

        mSurface = holder.getSurface();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

    }

    private void processVideoFromBuffer(MyCodec myCodec) throws IOException {

        Log.i(TAG, "process with  " + myCodec.mCodecName + "[" + myCodec.mNumOfInstance + "]");

        // Create a desire MediaCodec and start it
        myCodec.StartCodec();
        doProcessWithEncoder(myCodec);

        return;

    }

    private void doProcessWithEncoder(MyCodec myCodec) {


        long ptsUsec = 0;

        Log.i(TAG, "doProcess with " + myCodec.mCodecName + "[" + myCodec.mNumOfInstance + "]");

        myCodec.mInputBuffers = myCodec.getCodec().getInputBuffers();
        myCodec.mOutputBuffers = myCodec.getCodec().getOutputBuffers();

        myCodec.mBufInfo = new MediaCodec.BufferInfo();

        myCodec.mGenerateIndex = 0;
        myCodec.mFrameData = new byte[myCodec.getFrameSize()];

        while (!myCodec.mOutputDone) {
            Log.i(TAG, "Looping...for " + myCodec.mNumOfInstance + " myCodec = " + myCodec.mCodecName);

            // If we're not done submitting frames, generate a new one and submit it.  By
            // doing this on every loop we're working to ensure that the encoder always has
            // work to do.
            //
            // We don't really want a timeout here, but sometimes there's a delay opening
            // the encoder device, so a short timeout can keep us from spinning hard.
            if (!myCodec.mInputDone) {
                myCodec.mInputBufIndex = myCodec.getCodec().dequeueInputBuffer(TIMEOUT_USEC);

                Log.d(TAG, "[" + myCodec.mNumOfInstance + "]mInputBufIndex = " + myCodec.mInputBufIndex);

                if (myCodec.mInputBufIndex >= 0) {
                    ptsUsec = computePTS(myCodec.mGenerateIndex, myCodec.mFrameRate);
                    // Store input data into frameData
                    try {
                        generateFrame(myCodec);
                        Log.e(TAG, "generateFrame and mInputStreamReadStatus = " + myCodec.mInputStreamReadStatus);
                        if (myCodec.mInputStreamReadStatus == -1) {
                            Log.i (TAG, "No more frame data ...");
                            myCodec.mInputDone = true;
                        }
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }

                    ByteBuffer inputBuf = myCodec.mInputBuffers[myCodec.mInputBufIndex];
                    // Buf capacity check
                    Log.i(TAG, "inputBuf.capacity() = " + inputBuf.capacity()
                            + "frameData.length = " + myCodec.mFrameData.length);
                    assertTrue(inputBuf.capacity() >= myCodec.mFrameData.length);
                    inputBuf.clear();
                    inputBuf.put(myCodec.mFrameData);

                    if (myCodec.mReadDone) {
                        myCodec.getCodec().queueInputBuffer(myCodec.mInputBufIndex, 0,
                                myCodec.mFrameData.length, ptsUsec, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    } else {
                        myCodec.getCodec().queueInputBuffer(myCodec.mInputBufIndex, 0,
                                myCodec.mFrameData.length, ptsUsec, 0);
                    }

                    Log.i(TAG, "Codec[" + myCodec.mNumOfInstance + "]Buffer " + myCodec.mInputBufIndex + " submitted to encode");
                    myCodec.mGenerateIndex++;
                } else {
                    Log.w(TAG, "Input buffer not available for codec[" + myCodec.mNumOfInstance +"]");
                }

            }

            // Check for output from the encoder.  If there's no output yet, we either need to
            // provide more input, or we need to wait for the encoder to work its magic.  We
            // can't actually tell which is the case, so if we can't get an output buffer right
            // away we loop around and see if it wants more input.
            //
            // Once we get EOS from the encoder, we don't need to do this anymore.
            if (!myCodec.mProcessDone) {
                Log.d(TAG, "Codec[" + myCodec.mNumOfInstance + "]dequeuing Output buffers...");
                myCodec.mProcessStatus = myCodec.getCodec().dequeueOutputBuffer(myCodec.mBufInfo, TIMEOUT_USEC);
                // Line below is for test purpose
                //myCodec.mProcessStatus = MediaCodec.INFO_TRY_AGAIN_LATER;
                if (myCodec.mProcessStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.i(TAG, "No output from encoder available, try again later.");
                } else if (myCodec.mProcessStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    Log.i(TAG, "INFO_OUTPUT_BUFFERS_CHANGED... NOT EXPECTED for and encoder");
                } else if (myCodec.mProcessStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    Log.i(TAG, "INFO_OUTPUT_FORMAT_CHANGED... NOT EXPECTED for and encoder");
                } else if (myCodec.mProcessStatus < 0) {
                    Log.e(TAG, "Unexpected result!!!");
                } else { // it's the index of an output buffer that has been successfully decoded

                    Log.e(TAG, "Encode[" + myCodec.mNumOfInstance + "] success, encoderStatus = " + myCodec.mProcessStatus);
                    ByteBuffer encodedData = myCodec.mOutputBuffers[myCodec.mProcessStatus];
                    if (encodedData == null) {
                        Log.w(TAG, "Codec[" + myCodec.mNumOfInstance + "] encoderOuputBuffer " + myCodec.mProcessStatus + " was null");
                    }

                    encodedData.position(myCodec.mBufInfo.offset);
                    encodedData.limit(myCodec.mBufInfo.offset + myCodec.mBufInfo.size);

                    if (myCodec.getOutputStream() != null) {
                        byte[] data = new byte[myCodec.mBufInfo.size];
                        encodedData.get(data); //Store input buffer into data
                        encodedData.position(myCodec.mBufInfo.offset);
                        try {
                            if (data.length != 0) {
                                Log.w(TAG, "Codec[" + myCodec.mNumOfInstance +
                                        "]writing out data.len = " + data.length);
                                myCodec.getOutputStream().write(data);
                                Log.w(TAG, "Codec[" + myCodec.mNumOfInstance +
                                        "]writing out file.len = " + myCodec.getOutputFileLen());
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    myCodec.getCodec().releaseOutputBuffer(myCodec.mProcessStatus, false);

                    if (myCodec.mBufInfo.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {

                        Log.i (TAG, "Got BUFFER_FLAG_END_OF_STREAM flag on Codec[" + myCodec.mNumOfInstance + "]");

                        if (myCodec.getCodec() != null) {
                            Log.i (TAG, "releasing codec[" + myCodec.mNumOfInstance + "]...");
                            myCodec.releaseCodec();
                            try {
                                myCodec.getOutputStream().close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        myCodec.mOutputDone = true;
                        myCodec.mInputDone = true;
                    }

                }
            }
        }

    }

    private void doActualWork(MyCodec myDec) {
        Log.i(TAG, "Doing actual Work...");

        MediaFormat format = null;
        String mimeType = null;

        MediaExtractor mExtractor = new MediaExtractor();
        try {
            mExtractor.setDataSource(VideoPath);
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (int i = 0; i < mExtractor.getTrackCount(); i++) {
            format = mExtractor.getTrackFormat(i);
            mimeType = format.getString(MediaFormat.KEY_MIME);
            Log.i(TAG, "Media format for Decoder " + myDec.mCodecName + " " + format);

            // Handle Video only
            if (mimeType.startsWith("video/")) {
                mExtractor.selectTrack(i);
                try {
                    myDec.setCodec(MediaCodec.createByCodecName(myDec.mCodecName));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                myDec.getCodec().configure(format, mSurface, null, 0);
                break;
            }
        }

        Log.i(TAG, "Codec " + myDec.getCodec().getName() + " Starting...");
        myDec.getCodec().start();

        // Below methods are deprecated
        ByteBuffer[] inputBuffers = myDec.getCodec().getInputBuffers();
        ByteBuffer[] outputBuffers = myDec.getCodec().getOutputBuffers();

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean isEOS = false;
        long startMs = System.currentTimeMillis();

        while (!mDecodingDone) {
            if (!isEOS) {
                int inputBufIndex = myDec.getCodec().dequeueInputBuffer(TIMEOUT_USEC);

                if (inputBufIndex >= 0) {
                    //Log.i(TAG, "Dequeue an Input Buffer of index " + inputBufIndex);
                    ByteBuffer inputBuffer = inputBuffers[inputBufIndex];

                    int bufferSize = mExtractor.readSampleData(inputBuffer, 0);
                    //Log.i(TAG, "Sample Size = " + bufferSize);

                    if (bufferSize >= 0) {
                        myDec.getCodec().queueInputBuffer(inputBufIndex, 0,
                                bufferSize, mExtractor.getSampleTime(), 0);
                        mExtractor.advance();
                    } else {

                        myDec.getCodec().queueInputBuffer(inputBufIndex, 0,
                                0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        isEOS = true;
                    }
                }
            }

            int outputBufIndex = myDec.getCodec().dequeueOutputBuffer(info, TIMEOUT_USEC);

            switch (outputBufIndex) {
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    Log.i(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                    outputBuffers = myDec.getCodec().getOutputBuffers();
                    break;
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    Log.i(TAG, "New format " + myDec.getCodec().getOutputFormat());
                    break;
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    Log.i(TAG, "dequeueOutputBuffer timed out!");
                    break;
                default:
                    ByteBuffer buffer = outputBuffers[outputBufIndex];
                    Log.v(TAG, "We can't use this buffer but render it due to the API limit, " + buffer);

                    // We use a very simple clock to keep the video FPS, or the video
                    // playback will be too fast
                    while (info.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    myDec.getCodec().releaseOutputBuffer(outputBufIndex, true);
                    break;
            }

            // Got EOS
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.i(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
                mDecodingDone = true;
            }
        }

        Log.i(TAG, "releasing codec...");
        myDec.getCodec().stop();
        myDec.getCodec().release();
        mExtractor.release();
    }

    private int generateFrame(MyCodec myCodec)
            throws FileNotFoundException {

        Log.i (TAG, "generating frame[" + myCodec.mGenerateIndex + "] for instance " + myCodec.mNumOfInstance +
        " frameData len = " + myCodec.mFrameData.length);

        //convert file into array of bytes
        try {
            myCodec.mInputStreamReadStatus =
                    myCodec.getInputStream().read(myCodec.mFrameData, 0, myCodec.getFrameSize());
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (myCodec.mInputStreamReadStatus == -1) {

            Log.i(TAG, "[" + myCodec.mNumOfInstance + "]" + "No more data in " + myCodec.getInputStream());
            myCodec.mReadDone = true;

            try {
                Log.i(TAG, "Closing inputStream for Codec[" + myCodec.mNumOfInstance + "]");
                myCodec.getInputStream().close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return myCodec.mInputStreamReadStatus;
    }

    private long computePTS(int generateIndex, int frameRate) {
        return 132 + generateIndex * 1000000 / frameRate;
    }

    private class EncodingThread_1 implements Runnable {
        private Thread t;
        private String threadName;

        private String codecName = "OMX.qcom.video.encoder.avc";
        private String mimeType = "video/avc";

        EncodingThread_1( String name){
            threadName = name;
            Log.d (TAG, "Creating " +  threadName );
        }
        public void run() {
            try {
                MyCodec myEnc = new MyCodec(true, 1, codecName, mimeType);
                myEnc.setParameters(1920, 1080, 6000000, 30, 1);
                myEnc.setInputStream(INPUT_YUV_FILE_FOR_ENC_1);
                myEnc.setOnputStream(OUTPUT_ENCODED_FILE_1);
                processVideoFromBuffer(myEnc);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void start ()
        {
            Log.d (TAG, "Starting " +  threadName );
            if (t == null)
            {
                t = new Thread (this, threadName);
                t.start ();
            }
        }

    }

    private class EncodingThread_2 implements Runnable {
        private Thread t;
        private String threadName;

        private String codecName = "OMX.qcom.video.encoder.avc";
        private String mimeType = "video/avc";

        EncodingThread_2( String name){
            threadName = name;
            Log.d (TAG, "Creating " +  threadName );
        }
        public void run() {
            try {
                MyCodec myEnc = new MyCodec(true, 2, codecName, mimeType);
                myEnc.setParameters(1280, 720, 6000000, 30, 1);
                myEnc.setInputStream(INPUT_YUV_FILE_FOR_ENC_2);
                myEnc.setOnputStream(OUTPUT_ENCODED_FILE_2);
                processVideoFromBuffer(myEnc);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void start ()
        {
            Log.d (TAG, "Starting " +  threadName );
            if (t == null)
            {
                t = new Thread (this, threadName);
                t.start ();
            }
        }

    }

    private class PlayingThread implements Runnable {
        private Thread t;
        private String threadName;

        private String codecName = "OMX.qcom.video.decoder.avc";
        private String mimeType = "video/avc";

        PlayingThread(String name){
            threadName = name;
            Log.d (TAG, "Creating " +  threadName );
        }
        public void run() {
            MyCodec myDec = new MyCodec(false, 3, codecName, mimeType);
            doActualWork(myDec);
        }

        public void start ()
        {
            Log.d (TAG, "Starting " +  threadName );
            if (t == null)
            {
                t = new Thread (this, threadName);
                t.start ();
            }
        }

    }

}
