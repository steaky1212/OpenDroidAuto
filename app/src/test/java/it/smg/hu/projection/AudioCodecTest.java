package it.smg.hu.projection;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Method;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class AudioCodecTest {

    private static ByteBuffer packet(int... bytes) {
        ByteBuffer b = ByteBuffer.allocateDirect(bytes.length);
        for (int v : bytes) b.put((byte) v);
        b.flip();
        return b;
    }

    private static AudioCodec codecPlayingTo(FakeAudioSink sink) {
        return new AudioCodec("test-audio", () -> sink);
    }

    @Test
    public void packetWrittenAfterStartReachesTheSink() throws Exception {
        FakeAudioSink sink = new FakeAudioSink();
        AudioCodec codec = codecPlayingTo(sink);

        codec.start();
        codec.write(packet(1, 2, 3), 0);

        assertTrue("packet was never played", sink.awaitBytes(3, 2000));
        assertArrayEquals(new byte[]{1, 2, 3}, sink.allBytes());
        codec.stop();
    }

    @Test
    public void playbackAlwaysStartsEvenWhenAudioArrivesImmediately() throws Exception {
        for (int i = 0; i < 500; i++) {
            FakeAudioSink sink = new FakeAudioSink();
            AudioCodec codec = codecPlayingTo(sink);

            codec.start();
            codec.write(packet(7), 0);

            assertTrue("playback never started on attempt " + i, sink.awaitBytes(1, 2000));
            codec.stop();
        }
    }

    @Test
    public void idlePlaybackThreadDoesNotBurnCpu() throws Exception {
        FakeAudioSink sink = new FakeAudioSink();
        AudioCodec codec = new AudioCodec("idle-test-audio", () -> sink);
        codec.start();

        Thread.sleep(100); // let the playback thread get going
        // java.lang.management is not on the Android compile classpath, but it is on the JVM running the test
        Object threads = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null);
        Method cpuTime = Class.forName("java.lang.management.ThreadMXBean").getMethod("getThreadCpuTime", long.class);
        long id = -1;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if ("idle-test-audio".equals(t.getName())) id = t.getId();
        }
        assertTrue("playback thread not found", id != -1);

        long before = (Long) cpuTime.invoke(threads, id);
        Thread.sleep(500);
        long burnedMs = ((Long) cpuTime.invoke(threads, id) - before) / 1_000_000;
        codec.stop();

        assertTrue("idle playback thread used " + burnedMs + " ms CPU in 500 ms", burnedMs < 50);
    }

    @Test
    public void audioArrivesIntactAndInOrderAcrossMixedPacketSizes() throws Exception {
        FakeAudioSink sink = new FakeAudioSink();
        AudioCodec codec = codecPlayingTo(sink);
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        codec.start();

        for (int i = 0; i < 2000; i++) {
            int size = new int[]{960, 3, 1920, 17, 480}[i % 5];
            byte[] bytes = new byte[size];
            for (int j = 0; j < size; j++) bytes[j] = (byte) (i * 31 + j);
            expected.write(bytes, 0, size);

            ByteBuffer b = ByteBuffer.allocateDirect(size);
            b.put(bytes).flip();
            codec.write(b, i);
        }

        assertTrue("not all audio was played", sink.awaitBytes(expected.size(), 5000));
        codec.stop();
        assertArrayEquals(expected.toByteArray(), sink.allBytes());
    }

    @Test
    public void stopReturnsPromptlyReleasesTheSinkAndPlaybackCanRestart() throws Exception {
        FakeAudioSink first = new FakeAudioSink();
        FakeAudioSink second = new FakeAudioSink();
        FakeAudioSink[] sinks = {first, second};
        int[] next = {0};
        AudioCodec codec = new AudioCodec("restart-test-audio", () -> sinks[next[0]++]);

        codec.start();
        codec.write(packet(1), 0);
        assertTrue(first.awaitBytes(1, 2000));

        long t0 = System.nanoTime();
        codec.stop();
        long stopMs = (System.nanoTime() - t0) / 1_000_000;
        assertTrue("stop took " + stopMs + " ms", stopMs < 500);
        assertTrue("sink was not released", first.released.get() == 1);

        codec.start();
        codec.write(packet(2), 0);
        assertTrue("playback did not restart", second.awaitBytes(1, 2000));
        assertArrayEquals(new byte[]{2}, second.allBytes());
        codec.stop();
    }
}
