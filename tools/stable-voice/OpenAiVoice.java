package com.softbankrobotics.pepper.pepperGPT;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

/** Direct PCM streaming on API 23. Credentials stay in existing private preferences. */
public final class OpenAiVoice {
    private static final Handler UI = new Handler(Looper.getMainLooper());
    public static final class Session {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private volatile HttpURLConnection request;
        private volatile AudioTrack track;
        public void cancel() {
            if (!cancelled.compareAndSet(false, true)) return;
            AudioTrack audio = track;
            if (audio != null) try { audio.pause(); audio.flush(); } catch (IllegalStateException ignored) { }
            HttpURLConnection http = request;
            if (http != null) new Thread(() -> {
                try { http.disconnect(); } catch (RuntimeException ignored) { }
            }, "OpenAiSpeechCancel").start();
        }
    }
    public static Session start(Context context, String text, boolean story, Runnable started, Runnable done, Runnable error) {
        Session session = new Session();
        new Thread(() -> {
            AudioTrack audio = null;
            boolean success = false;
            long start = SystemClock.elapsedRealtime();
            try {
                String key = context.getSharedPreferences("PepperGPT_Prefs", 0).getString("openai_api_key", "");
                if (key == null || key.trim().isEmpty()) throw new java.io.IOException("OpenAI key unavailable");
                HttpURLConnection request = (HttpURLConnection) new URL("https://api.openai.com/v1/audio/speech").openConnection();
                session.request = request;
                request.setConnectTimeout(10000); request.setReadTimeout(20000);
                request.setRequestMethod("POST"); request.setDoOutput(true);
                request.setRequestProperty("Authorization", "Bearer " + key);
                request.setRequestProperty("Content-Type", "application/json");
                String model = context.getSharedPreferences("PepperGPT_Prefs", 0).getString("speech_model", "gpt-4o-mini-tts");
                String voice = context.getSharedPreferences("PepperGPT_Prefs", 0).getString("openai_voice", "marin");
                boolean italian = LanguageSwitch.italian();
                JSONObject body = new JSONObject().put("model", model).put("voice", voice)
                        .put("input", text).put("response_format", "pcm")
                        .put("instructions", (italian ? "Speak Italian with natural Italian pronunciation. " : "Speak British English with natural English pronunciation. ") +
                                ("coral".equals(voice) ? "Use a youthful, bright, friendly voice. Sound curious and gently playful. " :
                                "Use a warm, gentle voice. ") +
                                (story ? "Narrate calmly, with clear pauses at punctuation and paragraphs." :
                                        "Use a relaxed conversational pace and natural punctuation."));
                byte[] bytes = body.toString().getBytes("UTF-8");
                request.setFixedLengthStreamingMode(bytes.length);
                if (session.cancelled.get()) return;
                try (OutputStream output = request.getOutputStream()) { output.write(bytes); }
                int status = request.getResponseCode();
                if (status != 200) throw new java.io.IOException("OpenAI speech HTTP " + status);
                int minimum = AudioTrack.getMinBufferSize(24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                if (minimum <= 0) throw new java.io.IOException("PCM audio unavailable");
                audio = new AudioTrack(AudioManager.STREAM_MUSIC, 24000, AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, Math.max(minimum, 8192), AudioTrack.MODE_STREAM);
                session.track = audio;
                if (audio.getState() != AudioTrack.STATE_INITIALIZED) throw new java.io.IOException("PCM playback unavailable");
                long written = 0;
                boolean playing = false;
                try (InputStream input = request.getInputStream()) {
                    byte[] buffer = new byte[4096]; int count; int carry = 0;
                    while ((count = input.read(buffer, carry, buffer.length - carry)) != -1) {
                        if (session.cancelled.get()) return;
                        if (count == 0) continue;
                        count += carry;
                        carry = count & 1;
                        byte lastByte = buffer[count - 1];
                        count -= carry;
                        if (count == 0) { buffer[0] = lastByte; continue; }
                        if (!playing) {
                            audio.play(); playing = true;
                            UI.post(() -> { if (!session.cancelled.get()) started.run(); });
                            Log.i("LegacyVoice", "OpenAI first audio after " + (SystemClock.elapsedRealtime() - start) + " ms");
                        }
                        int offset = 0;
                        while (offset < count && !session.cancelled.get()) {
                            int accepted = audio.write(buffer, offset, count - offset);
                            if (accepted <= 0) throw new java.io.IOException("PCM write failed");
                            offset += accepted; written += accepted;
                        }
                        if (carry != 0) buffer[0] = lastByte;
                    }
                    if (carry != 0) throw new java.io.IOException("Incomplete PCM sample");
                }
                if (!playing || written < 2) throw new java.io.IOException("OpenAI audio empty");
                long drainDeadline = SystemClock.elapsedRealtime() + 30000;
                while (!session.cancelled.get() && (audio.getPlaybackHeadPosition() & 0xffffffffL) < written / 2) {
                    if (SystemClock.elapsedRealtime() > drainDeadline) throw new java.io.IOException("Audio drain timed out");
                    Thread.sleep(20);
                }
                success = !session.cancelled.get();
            } catch (Exception failure) {
                if (!session.cancelled.get()) Log.e("LegacyVoice", "OpenAI speech failed: " + failure.getClass().getSimpleName());
            } finally {
                session.track = null;
                if (audio != null) audio.release();
                HttpURLConnection request = session.request;
                if (request != null) try { request.disconnect(); } catch (RuntimeException ignored) { }
                session.request = null;
            }
            if (!session.cancelled.get()) {
                boolean completed = success;
                UI.post(() -> { if (!session.cancelled.get()) { if (completed) done.run(); else error.run(); } });
            }
        }, "OpenAiSpeech").start();
        return session;
    }
}
