package it.smg.hu.projection;

import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Process;

import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import it.smg.libs.common.Log;

public class AudioCodec implements IAudioCodec, Runnable {
    private static final String TAG = "AudioCodec";
    private static final long IDLE_POLL_MS = 50;
    private static final int PACKET_POOL_SIZE = 64;
    private static final long PREBUFFER_POLL_MS = 5;
    // how a bounded total delay is split: sink buffer + queue cap, with the prebuffer inside the queue part
    private static final int SINK_PERCENT = 60;
    private static final int QUEUE_PERCENT = 40;
    private static final int PREBUFFER_PERCENT = 15;

    /** A reusable audio packet; data may be longer than length. */
    private static final class Packet {
        byte[] data = new byte[0];
        int length;

        void fill(ByteBuffer buffer) {
            length = buffer.remaining();
            if (data.length < length) {
                data = new byte[length];
            }
            buffer.get(data, 0, length);
        }
    }

    private final String name_;
    private final AudioSink.Factory sinkFactory_;
    private AudioSink audioSink_;
    protected AtomicBoolean running_;

    private final int maxQueuedBytes_; // 0 = unbounded
    private final int prebufferBytes_; // 0 = start playing immediately
    private final AtomicInteger queuedBytes_ = new AtomicInteger();

    private final BlockingQueue<Packet> queue_;
    private final BlockingQueue<Packet> pool_;
    private Thread codecThread_;

    /** Legacy latency: small sink buffer, unbounded queue, immediate start. */
    public AudioCodec(String name, int streamType, int sampleRate, int channelConfig, int sampleSize){
        this(name, streamType, sampleRate, channelConfig, sampleSize, 0);
    }

    /**
     * @param totalDelayMs if > 0, sink buffer plus queue cap add up to about this delay and playback
     *                     starts once a small amount is buffered; if 0, today's latency is kept
     */
    public AudioCodec(String name, int streamType, int sampleRate, int channelConfig, int sampleSize, int totalDelayMs){
        this(name, () -> {
            int channels = channels2num(channelConfig);
            int format = sampleSizeFromInt(sampleSize);
            int bufferSize = AudioTrack.getMinBufferSize(sampleRate, channels, format);
            if (totalDelayMs > 0) {
                bufferSize = Math.max(bufferSize, bytesFor(sampleRate, channelConfig, sampleSize, totalDelayMs * SINK_PERCENT / 100));
            } else {
                bufferSize *= 3;
            }
            if (Log.isInfo()) Log.i(TAG, "Buffer size " + bufferSize);
            return new AndroidAudioSink(streamType, sampleRate, channels, format, bufferSize);
        },
        totalDelayMs > 0 ? bytesFor(sampleRate, channelConfig, sampleSize, totalDelayMs * QUEUE_PERCENT / 100) : 0,
        totalDelayMs > 0 ? bytesFor(sampleRate, channelConfig, sampleSize, totalDelayMs * PREBUFFER_PERCENT / 100) : 0);
    }

    AudioCodec(String name, AudioSink.Factory sinkFactory){
        this(name, sinkFactory, 0, 0);
    }

    AudioCodec(String name, AudioSink.Factory sinkFactory, int maxQueuedBytes, int prebufferBytes){
        name_ = name;
        sinkFactory_ = sinkFactory;
        maxQueuedBytes_ = maxQueuedBytes;
        prebufferBytes_ = prebufferBytes;
        running_ = new AtomicBoolean(false);

        queue_ = new LinkedBlockingQueue<>();
        pool_ = new ArrayBlockingQueue<>(PACKET_POOL_SIZE);
    }

    private static int bytesFor(int sampleRate, int channelCount, int sampleSize, int ms){
        return (int) ((long) sampleRate * channelCount * (sampleSize / 8) * ms / 1000);
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
        Packet packet = pool_.poll();
        if (packet == null) {
            packet = new Packet();
        }
        packet.fill(buffer);

        queuedBytes_.addAndGet(packet.length);
        queue_.add(packet);

        if (maxQueuedBytes_ > 0) {
            // drop the oldest audio so the newest is kept and the delay stays bounded
            while (queuedBytes_.get() > maxQueuedBytes_) {
                Packet oldest = queue_.poll();
                if (oldest == null) break;
                queuedBytes_.addAndGet(-oldest.length);
                pool_.offer(oldest);
            }
        }
    }

    @Override
    public void start() {
        if (Log.isInfo()) Log.i(TAG, "Start");

        // must be set before the thread starts, otherwise it can see false and exit immediately
        running_.set(true);

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
                    codecThread_.join();
                    if (Log.isDebug()) Log.d(TAG + "_" + codecThread_.getName(), "thread joined");
                } catch (InterruptedException ignored) {}
                codecThread_ = null;
            }

            queue_.clear();
            queuedBytes_.set(0);
            if (Log.isDebug()) Log.d(TAG, "queue empty");
        }
    }

    @Override
    public void delete() {
    }

    @Override
    public void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);

        if (audioSink_ == null) {
            try {
                audioSink_ = sinkFactory_.create();
            } catch (Exception e) {
                Log.e(TAG, "error in audiotrack creation", e);
                return;
            }

            if (Log.isInfo()) Log.i(TAG, "initialized");
        }

        // avoid an initial underrun: wait until a little audio is buffered before playing
        while (running_.get() && queuedBytes_.get() < prebufferBytes_) {
            try {
                Thread.sleep(PREBUFFER_POLL_MS);
            } catch (InterruptedException e) {
                break;
            }
        }

        if (Log.isInfo()) Log.i(TAG, "starting audiotrack");
        audioSink_.play();

        if (Log.isVerbose()) Log.v(TAG + "_" + codecThread_.getName(), "running thread");
        while (running_.get()) {
            Packet packet;
            try {
                // waits for audio instead of spinning; the timeout lets stop() end the loop promptly
                packet = queue_.poll(IDLE_POLL_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                break;
            }
            if (packet != null){
                queuedBytes_.addAndGet(-packet.length);
                audioSink_.write(packet.data, 0, packet.length);
                pool_.offer(packet);
            }
        }

        if (Log.isInfo()) Log.i(TAG, "stop audiotrack");
        audioSink_.stop();
        audioSink_.release();
        audioSink_ = null;

        if (Log.isInfo()) Log.i(TAG, "audiotrack released");

        if (Log.isVerbose()) Log.v(TAG + "_" + codecThread_.getName(), "thread ended");
    }
}
