package it.smg.hu.projection;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import it.smg.libs.common.Log;

public class AudioCodec implements IAudioCodec, Runnable {
    private static final String TAG = "AudioCodec";

    private final String name_;
    private AudioTrack audioTrack_;
    private final int streamType_;
    private final int sampleRate_;
    private final int channelConfig_;
    private final int sampleSize_;
    protected AtomicBoolean running_;

    private final BlockingQueue<byte[]> queue_;
    private Thread codecThread_;

    public AudioCodec(String name, int streamType, int sampleRate, int channelConfig, int sampleSize){
        name_ = name;
        streamType_ = streamType;
        sampleRate_ = sampleRate;
        channelConfig_ = channelConfig;
        sampleSize_ = sampleSize;
        running_ = new AtomicBoolean(false);

        queue_ = new ArrayBlockingQueue<>(32);
    }

    private static int channels2num(int channels){
        switch (channels){
            case 1:
                return AudioFormat.CHANNEL_OUT_MONO;
            case 2:
                return AudioFormat.CHANNEL_OUT_STEREO;
            case 4:
                return AudioFormat.CHANNEL_OUT_QUAD;
            case 6:
                return AudioFormat.CHANNEL_OUT_5POINT1;
            case 8:
                return AudioFormat.CHANNEL_OUT_7POINT1_SURROUND;
            default:
                return -1;
        }
    }

    public static int sampleSizeFromInt(int sampleSize){
        switch (sampleSize){
            case 16:
                return AudioFormat.ENCODING_PCM_16BIT;
            case 8:
                return AudioFormat.ENCODING_PCM_8BIT;
            default:
                return -1;
        }
    }

    @Override
    public void write(ByteBuffer buffer, long timestamp) {
        if (Log.isVerbose()) Log.v(TAG, "buffer size: " + buffer.limit());
        ByteBuffer source = buffer.slice();
        final int size = source.remaining();
        if (size <= 0) {
            return;
        }
        final byte[] data = new byte[size];
        source.get(data);

        if (!queue_.offer(data)) {
            queue_.poll();
            queue_.offer(data);
        }
    }

    @Override
    public void start() {
        if (Log.isInfo()) Log.i(TAG, "Start");

        if (running_.getAndSet(true)) {
            return;
        }
        codecThread_ = new Thread(this);
        codecThread_.setName(name_);
        codecThread_.start();
    }

    @Override
    public void stop() {
        if (Log.isInfo()) Log.i(TAG, "Stop");
        if (running_.get()) {
            running_.set(false);

            if (codecThread_ != null){
                try {
                    codecThread_.join(1000);
                    if (Log.isDebug()) Log.d(TAG + "_" + codecThread_.getName(), "thread joined");
                } catch (InterruptedException ignored) {}
                codecThread_ = null;
            }

            queue_.clear();
            if (Log.isDebug()) Log.d(TAG, "queue empty");
        }
    }

    @Override
    public void delete() {
    }

    @Override
    public void run() {
        if (audioTrack_ == null) {
            try {
                int bufferSize_ = AudioTrack.getMinBufferSize(sampleRate_, channels2num(channelConfig_), sampleSizeFromInt(sampleSize_));
                if (bufferSize_ <= 0) {
                    Log.e(TAG, "Invalid AudioTrack buffer size " + bufferSize_);
                    running_.set(false);
                    return;
                }
                if (Log.isInfo()) Log.i(TAG, "Buffer size " + bufferSize_ + "*2");
                audioTrack_ = new AudioTrack(streamType_, sampleRate_, channels2num(channelConfig_), sampleSizeFromInt(sampleSize_), bufferSize_ * 3, AudioTrack.MODE_STREAM);
            } catch (Exception e) {
                Log.e(TAG, "error in audiotrack creation", e);
                return;
            }

            if (Log.isInfo()) Log.i(TAG, "initialized");
        }

        if (audioTrack_ != null) {
            if (Log.isInfo()) Log.i(TAG, "starting audiotrack");
            audioTrack_.play();
        }

        if (Log.isVerbose()) Log.v(TAG + "_" + codecThread_.getName(), "running thread");
        while (running_.get()) {
            byte[] data = null;
            try {
                data = queue_.poll(50, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            if (data != null){
                audioTrack_.write(data, 0, data.length);
            }
        }

        if (audioTrack_ != null) {
            if (Log.isInfo()) Log.i(TAG, "stop audiotrack");
            if (audioTrack_.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack_.flush();
                audioTrack_.stop();
            }
            audioTrack_.release();
            audioTrack_ = null;
        }

        if (Log.isInfo()) Log.i(TAG, "audiotrack released");

        if (Log.isVerbose()) Log.v(TAG + "_" + codecThread_.getName(), "thread ended");
    }
}
