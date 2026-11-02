package it.smg.hu.projection;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Records what AudioCodec plays; copies bytes because the codec may reuse its buffers. */
class FakeAudioSink implements AudioSink {
    private final List<byte[]> writes_ = new ArrayList<>();
    final AtomicInteger released = new AtomicInteger();
    final AtomicInteger played = new AtomicInteger();

    @Override
    public void play() {
        played.incrementAndGet();
    }

    @Override
    public synchronized void write(byte[] data, int offset, int length) {
        writes_.add(Arrays.copyOfRange(data, offset, offset + length));
        notifyAll();
    }

    @Override
    public void stop() {
    }

    @Override
    public void release() {
        released.incrementAndGet();
    }

    synchronized int writeCount() {
        return writes_.size();
    }

    /** All bytes played so far, concatenated in order. */
    synchronized byte[] allBytes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] w : writes_) out.write(w, 0, w.length);
        return out.toByteArray();
    }

    /** Waits until at least this many bytes were played; returns whether they were. */
    synchronized boolean awaitBytes(int count, long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (totalBytes() < count) {
            long left = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            if (left <= 0) return false;
            wait(left);
        }
        return true;
    }

    private int totalBytes() {
        int n = 0;
        for (byte[] w : writes_) n += w.length;
        return n;
    }
}
