package it.smg.hu.projection;

import it.smg.libs.aasdk.messenger.ChannelId;
import it.smg.libs.common.Log;

public class MediaAudioOutput extends AudioOutput {

    private static final String TAG = "MediaAudioOutput";
    private static final int MEDIA_PLAYBACK_DELAY_MS = 250;

    public MediaAudioOutput(){
        super();

        channelConfig_ = settings_.audio.mediaChannelCount();
        sampleSize_ = settings_.audio.mediaSampleSize();
        sampleRate_ = settings_.audio.mediaSampleRate();
        playbackDelayMs_ = MEDIA_PLAYBACK_DELAY_MS;

        if (settings_.advanced.hondaIntegrationEnabled()){
            audioCodecStreamType_ = hondaConnectManager_.mediaAudioStream(ChannelId.MEDIA_AUDIO);
        }
    }

    @Override
    public void start() {
        if (settings_.advanced.hondaIntegrationEnabled()){
            if (Log.isDebug()) Log.d(TAG, "requestAudioFocus");
            hondaConnectManager_.requestAudioFocus();
        }

        super.start();
    }

    @Override
    public void stop() {
        if (settings_.advanced.hondaIntegrationEnabled()){
            if (Log.isDebug()) Log.d(TAG, "releaseAudioFocus");
            hondaConnectManager_.releaseAudioFocus();
        }

        super.stop();
    }

    @Override
    public void suspend() {
        if (settings_.advanced.hondaIntegrationEnabled()){
            if (Log.isDebug()) Log.d(TAG, "releaseAudioFocus");
            hondaConnectManager_.releaseAudioFocus();
        }

        super.suspend();
    }

    @Override
    public String tag() {
        return TAG;
    }
}
