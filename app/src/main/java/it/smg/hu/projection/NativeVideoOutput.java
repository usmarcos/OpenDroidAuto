package it.smg.hu.projection;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.view.Surface;
import android.view.SurfaceView;

import androidx.annotation.Keep;
import androidx.annotation.RequiresApi;

import java.nio.ByteBuffer;

import it.smg.libs.common.Log;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
public class NativeVideoOutput extends VideoOutput implements Runnable {

    private static final String TAG = "NativeVideoOutput";

    private MediaCodec codec_;
    private volatile boolean configured_;
    private volatile boolean running_;
    private Thread codecThread_;

    public NativeVideoOutput(SurfaceView surfaceView){
        super(surfaceView);
    }

    @Override
    protected String tag() {
        return TAG;
    }

    @Keep
    @Override
    public boolean open() {
        return true;
    }

    @Keep
    @Override
    public boolean init() {
        try {
            Surface surface = surfaceView_.getHolder().getSurface();
            int width = surfaceView_.getWidth();
            int height = surfaceView_.getHeight();

            codecThread_ = new Thread(this);
            codecThread_.setName("NativeVideoOutput-thread");

            if (Log.isInfo()) Log.i(TAG, "create codec type= " + MediaFormat.MIMETYPE_VIDEO_AVC + ", width= " + width + ", height= " + height);
            codec_ = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, fps_);
//            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 655360);
            codec_.configure(format, surface, null, 0);
            configured_ = true;
            running_ = true;
            codec_.start();
            codecThread_.start();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Unable to initialize video decoder", e);
            running_ = false;
            configured_ = false;
            releaseCodec();
            return false;
        }
    }

    @Keep
    @Override
    public void write(long timestamp, ByteBuffer buf) {
        if (configured_ && running_) {
            MediaCodec codec = codec_;
            if (codec == null) {
                return;
            }
            int index = codec.dequeueInputBuffer(20000);
            if (index >= 0) {
                ByteBuffer buffer;
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    buffer = codec.getInputBuffers()[index];
                } else {
                    buffer = codec.getInputBuffer(index);
                }
                if (buffer != null) {
                    int size = buf.remaining();
                    buffer.clear();
                    if (size > buffer.remaining()) {
                        Log.e(TAG, "Video frame is larger than the codec input buffer: " + size);
                        codec.queueInputBuffer(index, 0, 0, timestamp, 0);
                        return;
                    }
                    buffer.put(buf);
                    codec.queueInputBuffer(index, 0, size, timestamp, 0);
                }
            }
        }
    }

    @Keep
    @Override
    public void stop() {
        if (Log.isInfo()) Log.i(TAG, "shutdown");
        running_ = false;
        configured_ = false;
        try {
            if (codecThread_ != null) {
                codecThread_.interrupt();
                codecThread_.join(1000);
                codecThread_ = null;
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        releaseCodec();
    }

    @Override
    public void run() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (running_) {
            MediaCodec codec = codec_;
            if (configured_ && codec != null) {
                int index = codec.dequeueOutputBuffer(info, 10000);
                if (index >= 0) {
                    if (Log.isVerbose()) Log.v(TAG, "outputBufferIndex: " + index);
                    ByteBuffer buffer = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        buffer = codec.getOutputBuffer(index);
                    } else {
                        buffer = codec.getOutputBuffers()[index];
                    }
                    if (Log.isVerbose()) Log.v(TAG, "outputBuffer: " + buffer);

                    // setting true is telling system to render frame onto Surface
                    codec.releaseOutputBuffer(index, true);
                }
            }
        }
        if (Log.isVerbose()) Log.v(TAG, "thread closed");
    }

    private void releaseCodec() {
        MediaCodec codec = codec_;
        codec_ = null;
        if (codec == null) {
            return;
        }
        try {
            codec.flush();
        } catch (IllegalStateException ignored) {}
        try {
            codec.stop();
        } catch (IllegalStateException ignored) {}
        codec.release();
    }
}
