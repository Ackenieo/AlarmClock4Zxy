package com.example.alarmclock4jacw;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.io.File;
import java.util.Locale;

/**
 * 美式英语 TTS，语速 0.9x。
 *
 * Android 16 的音频加固会把后台 TTS 引擎的播放静音，所以这里先把词合成到缓存文件，
 * 再由本应用（前台、USAGE_ALARM）用 MediaPlayer 播放。缓存命中后无需再合成。
 */
public class TtsSpeaker {

    private static final String TAG = "TtsSpeaker";

    public interface Listener {
        void onUnavailable();
    }

    private final Context context;
    private final TextToSpeech tts;
    private final Listener listener;
    private final File cacheDir;

    private MediaPlayer player;
    private boolean ready;
    private boolean unavailable;
    private boolean released;
    private String pending;
    private String requestedUtterance;
    private String requestedWord;

    public TtsSpeaker(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.cacheDir = new File(this.context.getCacheDir(), "tts_us");
        this.cacheDir.mkdirs();
        this.tts = new TextToSpeech(this.context, this::onInit);
    }

    private void onInit(int status) {
        if (released || tts == null) return;
        if (status != TextToSpeech.SUCCESS) {
            markUnavailable();
            return;
        }
        int result = tts.setLanguage(Locale.US);
        if (result == TextToSpeech.LANG_MISSING_DATA
                || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            markUnavailable();
            return;
        }
        tts.setSpeechRate(0.9f);
        tts.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
            }

            @Override
            public void onDone(String utteranceId) {
                if (released || !utteranceId.equals(requestedUtterance)) return;
                playFile(new File(utteranceId));
            }

            @Override
            public void onError(String utteranceId) {
                if (released || !utteranceId.equals(requestedUtterance)) return;
                fallbackSpeak(requestedWord);
            }
        });
        ready = true;
        if (pending != null) {
            String word = pending;
            pending = null;
            speak(word);
        }
    }

    private void markUnavailable() {
        unavailable = true;
        if (listener != null) listener.onUnavailable();
    }

    public void speak(String word) {
        if (released || unavailable || word == null || word.isEmpty()) return;
        if (!ready) {
            pending = word;
            return;
        }
        stop();

        File file = fileFor(word);
        if (file.exists() && file.length() > 0) {
            playFile(file);
            return;
        }
        requestedWord = word;
        requestedUtterance = file.getAbsolutePath();
        int result = tts.synthesizeToFile(word, null, file, requestedUtterance);
        if (result != TextToSpeech.SUCCESS) {
            requestedUtterance = null;
            fallbackSpeak(word);
        }
    }

    private File fileFor(String word) {
        String lower = word.toLowerCase(Locale.ROOT);
        String safe = lower.replaceAll("[^a-z0-9]", "_");
        return new File(cacheDir, safe + "_" + Integer.toHexString(lower.hashCode()) + ".wav");
    }

    private void playFile(File file) {
        stopPlayer();
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            player.setDataSource(file.getAbsolutePath());
            player.setOnCompletionListener(mp -> {
                mp.release();
                if (player == mp) player = null;
            });
            player.prepare();
            player.start();
        } catch (Exception e) {
            Log.w(TAG, "play synthesized word failed", e);
            stopPlayer();
        }
    }

    private void fallbackSpeak(String word) {
        if (word == null || tts == null) return;
        tts.speak(word, TextToSpeech.QUEUE_FLUSH, null, "fallback");
    }

    public void stop() {
        requestedUtterance = null;
        requestedWord = null;
        if (tts != null) tts.stop();
        stopPlayer();
    }

    private void stopPlayer() {
        if (player == null) return;
        try {
            if (player.isPlaying()) player.stop();
        } catch (Exception ignored) {
        }
        player.release();
        player = null;
    }

    public void shutdown() {
        released = true;
        stop();
        if (tts != null) tts.shutdown();
    }

    public boolean isUnavailable() {
        return unavailable;
    }
}
