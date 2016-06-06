package archersmind.myapplication;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

/**
 * Created by alan on 5/22/16.
 */

public class MyCodec {

    private static final String TAG = "MyCodec";

    private MediaCodec mCodec;

    public String mCodecName;
    private String mMIMEType;

    public byte[] mFrameData;

    private File mInputFile;
    private File mOutputFile;

    public int mGenerateIndex;
    public int mNumOfInstance;
    public int mInputBufIndex;

    public int mFrameRate;

    private int mWidth;
    private int mHeight;
    private int mBitrate;
    private int mIframeInterval;

    public int mInputStreamReadStatus;

    public int mProcessStatus;

    public boolean mIsEncoder;
    public boolean mOutputDone;
    public boolean mInputDone;
    public boolean mProcessDone;
    public boolean mReadDone;

    public MediaCodec.BufferInfo mBufInfo;

    public ByteBuffer[] mInputBuffers;
    public ByteBuffer[] mOutputBuffers;

    private FileInputStream mInputStream;
    private FileOutputStream mOutputStream;

    public MyCodec(boolean isEncoder, int instance, String codecName, String mimeType) {

        this.mIsEncoder = isEncoder;
        this.mCodecName = codecName;
        this.mMIMEType = mimeType;
        this.mNumOfInstance = instance;

        this.mProcessDone = false;
        this.mOutputDone = false;
        this.mInputDone = false;
        this.mReadDone = false;

    }

    public void setParameters(int width, int height, int bitrate, int frameRate, int iFrameInterval) {
        this.mWidth = width;
        this.mHeight = height;
        this.mBitrate = bitrate;
        this.mFrameRate = frameRate;
        this.mIframeInterval = iFrameInterval;
    }

    public void setInputStream(String input) {
        mInputFile = new File(input);
        try {
            mInputStream = new FileInputStream(mInputFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return;
    }

    public void setOnputStream(String Output) {
        mOutputFile = new File(Output);
        try {
            mOutputStream = new FileOutputStream(mOutputFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return;
    }

    public FileInputStream getInputStream() {
        return mInputStream;
    }

    public FileOutputStream getOutputStream() {
        return mOutputStream;
    }

    public void StartCodec() {
        // Create a MediaCodec for the desired codec, then configure it as an encoder with
        // our desired properties.
        try {
            MediaFormat format = MediaFormat.createVideoFormat(this.mMIMEType, this.mWidth, this.mHeight);

            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
            format.setInteger(MediaFormat.KEY_BIT_RATE, this.mBitrate);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, this.mFrameRate);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, this.mIframeInterval);

            Log.d(TAG, "format: " + format);
            Log.i(TAG, "Creating codec " + this.mNumOfInstance + " " + this.mCodecName);

            this.mCodec = MediaCodec.createByCodecName(this.mCodecName);
            this.mCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            this.mCodec.start();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public MediaCodec getCodec() {
        return mCodec;
    }

    public void setCodec(MediaCodec codec) {
        this.mCodec = codec;
    }

    public int getFrameSize() {
        return this.mWidth * this.mHeight * 3 / 2;
    }

    public void releaseCodec() {
        this.mCodec.stop();
        this.mCodec.release();
        this.mCodec = null;
    }

    public long getInputFileLen() {
        return this.mInputFile.length();
    }

    public long getOutputFileLen() {
        return this.mOutputFile.length();
    }



}
