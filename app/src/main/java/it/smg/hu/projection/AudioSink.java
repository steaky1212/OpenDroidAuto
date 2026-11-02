package it.smg.hu.projection;

/** Where AudioCodec plays PCM audio to. The real one is an Android AudioTrack. */
interface AudioSink {
    void play();

    /** Blocks until the data has been accepted. */
    void write(byte[] data, int offset, int length);

    void stop();

    void release();

    /** Creates the sink on the playback thread; may throw if the audio settings are rejected. */
    interface Factory {
        AudioSink create() throws Exception;
    }
}
