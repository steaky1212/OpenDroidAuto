package it.smg.hu.projection;

import android.media.AudioTrack;

class AndroidAudioSink implements AudioSink {
    private final AudioTrack audioTrack_;

    AndroidAudioSink(int streamType, int sampleRate, int channelConfig, int audioFormat, int bufferSize) {
        audioTrack_ = new AudioTrack(streamType, sampleRate, channelConfig, audioFormat, bufferSize, AudioTrack.MODE_STREAM);
    }

    @Override
    public void play() {
        audioTrack_.play();
    }

    @Override
    public void write(byte[] data, int offset, int length) {
        audioTrack_.write(data, offset, length);
    }

    @Override
    public void stop() {
        if (audioTrack_.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack_.flush();
            audioTrack_.stop();
        }
    }

    @Override
    public void release() {
        audioTrack_.release();
    }
}
