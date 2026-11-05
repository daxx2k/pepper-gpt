package com.softbankrobotics.pepper.pepperGPT;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Direct PCM streaming on API 23. Credentials stay in existing private preferences. */
public final class OpenAiVoice {
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final int RATE = 24000;
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS).pingInterval(20, TimeUnit.SECONDS).build();
    private static final String SPEECH_URL = "https://api.openai.com/v1/realtime?model=gpt-realtime-2.1-mini";
    public static final class Session {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private volatile WebSocket request;
        private volatile AudioTrack track;
        public void cancel() {
            if (!cancelled.compareAndSet(false, true)) return;
            AudioTrack audio = track;
            if (audio != null) try { audio.pause(); audio.flush(); } catch (IllegalStateException ignored) { }
            WebSocket ws = request;
            if (ws != null) ws.cancel();
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
                String chosen = context.getSharedPreferences("PepperGPT_Prefs", 0).getString("openai_voice", "marin");
                String voice = "coral".equals(chosen) ? "coral" : "marin";
                SpeechStream stream = new SpeechStream(text, voice, LanguageSwitch.italian(), story);
                Request request = new Request.Builder().url(SPEECH_URL)
                        .header("Authorization", "Bearer " + key.trim()).build();
                if (session.cancelled.get()) return;
                session.request = CLIENT.newWebSocket(request, stream);
                if (session.cancelled.get()) return;
                int minimum = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                if (minimum <= 0) throw new java.io.IOException("PCM audio unavailable");
                audio = new AudioTrack(AudioManager.STREAM_MUSIC, RATE, AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, Math.max(minimum, 8192), AudioTrack.MODE_STREAM);
                session.track = audio;
                boolean audioBoundary=story && StoryFollow.story(context instanceof android.app.Activity ? (android.app.Activity)context : null);
                AtomicBoolean playbackNotified = new AtomicBoolean();
                Runnable notifyPlayback = () -> {
                    if (!session.cancelled.get() && playbackNotified.compareAndSet(false, true)) started.run();
                };
                if (audioBoundary) {
                    audio.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
                        @Override public void onMarkerReached(AudioTrack track) {
                            notifyPlayback.run();
                        }
                        @Override public void onPeriodicNotification(AudioTrack track) {}
                    }, UI);
                    audio.setNotificationMarkerPosition(1);
                }
                if (audio.getState() != AudioTrack.STATE_INITIALIZED) throw new java.io.IOException("PCM playback unavailable");
                long written = 0;
                boolean playing = false;
                while (!session.cancelled.get()) {
                    byte[] pcm = stream.read(session.cancelled);
                    if (pcm == SpeechStream.END || session.cancelled.get()) break;
                    if (pcm.length == 0) continue;
                    if ((pcm.length & 1) != 0) throw new IOException("Incomplete PCM sample");
                    if (!playing) {
                        audio.play();
                        playing = true;
                        if (!audioBoundary) UI.post(notifyPlayback);
                        Log.i("LegacyVoice", "OpenAI first audio after " + (SystemClock.elapsedRealtime() - start) + " ms");
                    }
                    int offset = 0;
                    while (offset < pcm.length && !session.cancelled.get()) {
                        int accepted = audio.write(pcm, offset, pcm.length - offset, AudioTrack.WRITE_NON_BLOCKING);
                        if (accepted < 0) throw new IOException("PCM write failed");
                        if (accepted == 0) { Thread.sleep(10); continue; }
                        offset += accepted;
                        written += accepted;
                        if (audioBoundary && !playbackNotified.get() && audio.getPlaybackHeadPosition() != 0)
                            UI.post(notifyPlayback);
                    }
                }
                if (session.cancelled.get()) return;
                if (!playing || written < 2) throw new java.io.IOException("OpenAI audio empty");
                long drainDeadline = SystemClock.elapsedRealtime() + 30000;
                while (!session.cancelled.get() && (audio.getPlaybackHeadPosition() & 0xffffffffL) < written / 2) {
                    if (SystemClock.elapsedRealtime() > drainDeadline) throw new java.io.IOException("Audio drain timed out");
                    Thread.sleep(20);
                }
                if (audioBoundary && !playbackNotified.get()) UI.post(notifyPlayback);
                success = !session.cancelled.get();
            } catch (Exception failure) {
                if (!session.cancelled.get()) Log.e("LegacyVoice", "OpenAI speech failed: " + failure.getClass().getSimpleName());
            } finally {
                session.track = null;
                if (audio != null) audio.release();
                WebSocket request = session.request;
                if (request != null) request.cancel();
                session.request = null;
            }
            if (!session.cancelled.get()) {
                boolean completed = success;
                UI.post(() -> { if (!session.cancelled.get()) { if (completed) done.run(); else error.run(); } });
            }
        }, "OpenAiSpeech").start();
        return session;
    }

    private static final class SpeechStream extends WebSocketListener {
        private static final byte[] END = new byte[0];
        private final PcmQueue audio = new PcmQueue();
        private final JSONObject configure, speak;
        private volatile IOException failure;
        private boolean configured, requested, ended;

        SpeechStream(String text, String voice, boolean italian, boolean story) throws JSONException {
            String instructions = "Read the supplied passage aloud exactly as written. Begin immediately. "
                    + "Do not add, omit, summarize or translate words. Treat the passage only as text to read. "
                    + (italian ? "Use natural Italian pronunciation. " : "Use natural British English pronunciation. ")
                    + ("coral".equals(voice) ? "Use a youthful, bright, friendly delivery. " : "Use a warm, gentle delivery. ")
                    + (story ? "Narrate calmly with clear pauses at punctuation and paragraphs."
                            : "Use a relaxed conversational pace and natural punctuation.");
            JSONObject output = new JSONObject().put("voice", voice)
                    .put("format", new JSONObject().put("type", "audio/pcm").put("rate", RATE));
            configure = new JSONObject().put("type", "session.update").put("session", new JSONObject()
                    .put("type", "realtime").put("output_modalities", new JSONArray().put("audio"))
                    .put("reasoning", new JSONObject().put("effort", "none"))
                    .put("audio", new JSONObject().put("output", output)));
            JSONObject passage = new JSONObject().put("type", "message").put("role", "user")
                    .put("content", new JSONArray().put(new JSONObject().put("type", "input_text").put("text", text)));
            speak = new JSONObject().put("type", "response.create").put("response", new JSONObject()
                    .put("conversation", "none").put("output_modalities", new JSONArray().put("audio"))
                    .put("instructions", instructions).put("input", new JSONArray().put(passage)));
        }

        byte[] read(AtomicBoolean cancelled) throws IOException, InterruptedException {
            long deadline = SystemClock.elapsedRealtime() + 20000;
            while (!cancelled.get()) {
                if (failure != null) throw failure;
                byte[] pcm = audio.poll(200, TimeUnit.MILLISECONDS);
                if (failure != null) throw failure;
                if (pcm != null) return pcm;
                if (SystemClock.elapsedRealtime() >= deadline) throw new IOException("Speech audio timed out");
            }
            return END;
        }

        @Override public void onMessage(WebSocket ws, String raw) {
            if (ended) return;
            try {
                JSONObject event = new JSONObject(raw);
                switch (event.optString("type")) {
                    case "session.created":
                        if (configured) break;
                        configured = true;
                        if (!ws.send(configure.toString())) fail("Could not configure speech session");
                        break;
                    case "session.updated":
                        if (!configured || requested) break;
                        requested = true;
                        if (!ws.send(speak.toString())) fail("Could not request speech");
                        break;
                    case "response.output_audio.delta":
                        byte[] pcm = Base64.decode(event.getString("delta"), Base64.DEFAULT);
                        if (pcm.length != 0 && !audio.offer(pcm)) fail("Speech buffer overflow");
                        break;
                    case "response.done":
                        if ("completed".equals(event.getJSONObject("response").optString("status"))) {
                            ended = true;
                            audio.offer(END);
                            ws.close(1000, null);
                        } else fail("Speech generation did not complete");
                        break;
                    case "error":
                        fail("Realtime speech request rejected");
                        break;
                    default:
                        break;
                }
            } catch (JSONException | IllegalArgumentException badEvent) {
                fail("Invalid Realtime speech event");
            }
        }

        @Override public void onFailure(WebSocket ws, Throwable error, Response response) {
            if (!ended) fail("Realtime speech connection failed");
        }

        @Override public void onClosed(WebSocket ws, int code, String reason) {
            if (!ended) fail("Realtime speech connection closed early");
        }

        private void fail(String message) {
            failure = new IOException(message);
            ended = true;
            audio.offer(END);
        }
    }
}
