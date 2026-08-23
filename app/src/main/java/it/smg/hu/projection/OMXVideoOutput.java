package it.smg.hu.projection;

import android.view.SurfaceView;
import android.os.SystemClock;

import androidx.annotation.Keep;

import java.nio.ByteBuffer;

import it.smg.libs.common.Log;
import it.smg.libs.omxvideocodec.OMXVideoCodec;

public class OMXVideoOutput extends VideoOutput /*implements Runnable*/ {

    private static final String TAG = "OMXVideoOutput";
    private OMXVideoCodec videoCodec_;

    private int frameSize_;
    private volatile boolean configured_;
    private volatile boolean running_;

    public OMXVideoOutput(SurfaceView surfaceView){
        super(surfaceView);
        frameSize_ = getFrameSizeFromResolution();
    }

    private int getFrameSizeFromResolution(){
        switch (resolution_){
            case 1: //480p
                return 800*480;
            case 2:
                return 1280*720;
            case 3:
                return 1920*1080;
            case 4:
                return 2560*1440;
            default:
                return -1;
        }
    }

    @Keep
    @Override
    public boolean open() {
        return true;
    }

    @Keep
    @Override
    public synchronized boolean init() {
        int width = surfaceView_.getWidth();
        int height = surfaceView_.getHeight();
        for (int attempt = 0; attempt < 20 && (width <= 0 || height <= 0); attempt++) {
            SystemClock.sleep(50L);
            width = surfaceView_.getWidth();
            height = surfaceView_.getHeight();
        }
        if (width <= 0 || height <= 0 || !surfaceView_.getHolder().getSurface().isValid()) {
            Log.e(TAG, "Video surface is not ready: " + width + "x" + height);
            return false;
        }

        try {
            OMXVideoCodec codec = new OMXVideoCodec(fps_);
            codec.setSurface(surfaceView_.getHolder().getSurface(), width, height);
            if (!codec.init()) {
                codec.shutdown();
                Log.e(TAG, "Legacy OMX decoder initialization failed");
                return false;
            }
            videoCodec_ = codec;
            configured_ = true;
            running_ = true;
            return true;
        } catch (Throwable error) {
            Log.e(TAG, "Unable to initialize legacy OMX decoder", error);
            configured_ = false;
            running_ = false;
            videoCodec_ = null;
            return false;
        }
    }

    @Keep
    @Override
    public synchronized void write(long timestamp, ByteBuffer data) {
        OMXVideoCodec codec = videoCodec_;
        if (configured_ && running_ && codec != null && data != null && data.remaining() > 0) {
            if (Log.isVerbose()) Log.v(TAG, "video message size: " + data.limit());
            try {
                codec.mediaDecode(timestamp, data, data.remaining());
            } catch (Throwable error) {
                Log.e(TAG, "Legacy OMX decode error", error);
            }
        }
    }

    @Keep
    @Override
    public synchronized void stop() {
        if(Log.isInfo()) Log.i(TAG, "stop");
        OMXVideoCodec codec = videoCodec_;
        videoCodec_ = null;
        running_ = false;
        configured_ = false;
        if (codec != null) {
            try {
                codec.shutdown();
            } catch (Throwable error) {
                Log.e(TAG, "Unable to stop legacy OMX decoder", error);
            }
            if(Log.isInfo()) Log.i(TAG, "deleted");
        }
    }

    @Override
    protected String tag() {
        return TAG;
    }
}
