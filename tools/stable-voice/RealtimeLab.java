package com.softbankrobotics.pepper.pepperGPT;

import android.app.Activity;
import android.app.AlertDialog;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.aldebaran.qi.Future;
import com.aldebaran.qi.sdk.QiContext;
import com.aldebaran.qi.sdk.builder.SayBuilder;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.json.JSONArray;
import org.json.JSONObject;

/** Private opt-in experiment; never replaces classic listening or its feature routing. */
public final class RealtimeLab {
    private static final String TAG = "PepperRealtime";
    private static final int RATE = 24000;
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS).pingInterval(20, TimeUnit.SECONDS).build();
    private static Activity focused;
    private static QiContext context;
    private static RealtimeLab active;

    public static void add(Activity a, Menu menu) {
        menu.add("Realtime voice (experiment)").setOnMenuItemClickListener(item -> {
            open(a);
            return true;
        });
    }
    public static void focus(Activity a, QiContext qi) {
        UI.post(() -> { focused = a; context = qi; });
    }
    public static void lose(Activity a) {
        UI.post(() -> {
            if (focused == a) { focused = null; context = null; }
            stop(a);
        });
    }
    public static void stop(Activity a) {
        if (Looper.myLooper() != Looper.getMainLooper()) { UI.post(() -> stop(a)); return; }
        if (active != null && active.activity == a) active.dialog.dismiss();
    }
    private static void open(Activity a) {
        if (active != null || a.isFinishing()) return;
        String key = a.getSharedPreferences("PepperGPT_Prefs", 0).getString("openai_api_key", "").trim();
        if (key.isEmpty() || focused != a) {
            new AlertDialog.Builder(a).setMessage(key.isEmpty() ? "Enter your OpenAI key in Settings first."
                    : "Wait until Pepper is connected to the robot.").setPositiveButton("OK", null).show();
            return;
        }
        if (classicBusy(a)) {
            new AlertDialog.Builder(a).setMessage("Stop classic listening and wait for the current reply to finish first.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        LegacyVoiceAdapter.beforeLanguageChange(a);
        active = new RealtimeLab(a, LanguageSwitch.italian());
        active.connect(key);
    }

    private static boolean classicBusy(Activity a) {
        try {
            for (String name : new String[]{"isListening","isSpeaking"}) {
                java.lang.reflect.Field field = a.getClass().getDeclaredField(name);
                field.setAccessible(true);
                if (field.getBoolean(a)) return true;
            }
            java.lang.reflect.Field field = a.getClass().getDeclaredField("currentGenerationJob");
            field.setAccessible(true);
            Object job = field.get(a);
            return job != null && (Boolean)Class.forName("kotlinx.coroutines.Job").getMethod("isActive").invoke(job);
        } catch (Exception error) { return true; }
    }

    private final Activity activity;
    private final AlertDialog dialog;
    private final TextView status;
    private final Button hold;
    private final boolean italian;
    private final ArrayBlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(96);
    private static final byte[] END = new byte[0];
    private volatile boolean closed, recording;
    private volatile WebSocket socket;
    private boolean ready, replying, startedAudio, gestureStarted;
    private long releasedAt;
    private int turn;
    private final StringBuilder transcript = new StringBuilder();
    private Future<Void> body;
    private final Runnable timeout = () -> fail("Realtime timed out. Close and reopen the experiment.");

    private RealtimeLab(Activity a, boolean it) {
        activity = a; italian = it;
        LinearLayout box = new LinearLayout(a); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(24, 16, 24, 16);
        status = new TextView(a); status.setText("Connecting to OpenAI…"); box.addView(status);
        hold = new Button(a); hold.setText(it ? "Tieni premuto per parlare" : "Hold to talk");
        hold.setEnabled(false); box.addView(hold);
        hold.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: begin(); return true;
                case MotionEvent.ACTION_UP: release(); v.performClick(); return true;
                case MotionEvent.ACTION_CANCEL: fail("Recording cancelled. Reopen to try again."); return true;
                default: return true;
            }
        });
        dialog = new AlertDialog.Builder(a).setTitle("Realtime voice experiment").setView(box)
                .setNegativeButton("Close", null).create();
        dialog.setOnDismissListener(d -> {
            shutdown();
            if (active == this) active = null;
        });
        dialog.show();
    }
    private static JSONObject obj(Object... values) {
        JSONObject j = new JSONObject();
        try {
            for (int n=0; n<values.length; n+=2) j.put((String)values[n], values[n+1]);
        } catch (Exception e) { throw new IllegalArgumentException(e); }
        return j;
    }
    private void connect(String key) {
        Request request = new Request.Builder().url("https://api.openai.com/v1/realtime?model=gpt-realtime-2.1")
                .header("Authorization", "Bearer " + key).build();
        UI.postDelayed(timeout, 15000);
        socket = CLIENT.newWebSocket(request, new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String raw) {
                UI.post(() -> { if (!closed) event(raw); });
            }
            @Override public void onFailure(WebSocket ws, Throwable failure, Response response) {
                int code = response == null ? 0 : response.code();
                UI.post(() -> fail(code == 0 ? "Realtime connection failed." : "Realtime connection refused ("+code+")."));
            }
            @Override public void onClosed(WebSocket ws, int code, String reason) {
                UI.post(() -> fail("Realtime connection closed."));
            }
        });
    }
    private boolean send(JSONObject event) {
        WebSocket ws = socket;
        return !closed && ws != null && ws.queueSize() < 256000 && ws.send(event.toString());
    }
    private void event(String raw) {
        try {
            JSONObject j = new JSONObject(raw);
            switch (j.optString("type")) {
                case "session.created":
                    JSONObject format = obj("type","audio/pcm","rate",RATE);
                    String chosen = activity.getSharedPreferences("PepperGPT_Prefs",0).getString("openai_voice","marin");
                    JSONObject session = obj("type","realtime","output_modalities",new JSONArray().put("audio"),
                        "instructions", (italian ? "Rispondi solo in italiano. " : "Reply only in English. ")
                            + "You are Pepper, a friendly female robot. Keep conversation natural and brief, at most two sentences. "
                            + "Do not claim to have executed actions or used live data.",
                        "audio",obj("input",obj("format",format,"turn_detection",JSONObject.NULL),
                            "output",obj("format",format,"voice","coral".equals(chosen) ? "coral" : "marin")));
                    if (!send(obj("type","session.update","session",session))) fail("Could not configure Realtime.");
                    break;
                case "session.updated":
                    UI.removeCallbacks(timeout);
                    ready = true; hold.setEnabled(true);
                    status.setText(italian ? "Pronta · italiano" : "Ready · English");
                    Log.i(TAG, "Session ready");
                    break;
                case "response.output_audio_transcript.delta":
                    if (replying && transcript.length() < 300) transcript.append(j.optString("delta"));
                    maybeGesture();
                    break;
                case "response.output_audio.delta":
                    if (replying) {
                        byte[] pcm = Base64.decode(j.getString("delta"), Base64.DEFAULT);
                        if (pcm.length == 0 || pcm.length % 2 != 0 || !queue.offer(pcm)) fail("Realtime audio buffer overflow.");
                    }
                    break;
                case "response.done":
                    if (replying && (!"completed".equals(j.getJSONObject("response").optString("status")) || !queue.offer(END)))
                        fail("Realtime reply did not complete.");
                    break;
                case "error":
                    String code = j.optJSONObject("error") == null ? "" : j.optJSONObject("error").optString("code");
                    if (!code.matches("[a-zA-Z0-9_]{1,50}")) code = "request_failed";
                    Log.w(TAG, "Service error: "+code);
                    fail("Realtime request failed ("+code+").");
                    break;
            }
        } catch (Exception e) { fail("Invalid Realtime reply."); }
    }

    private void begin() {
        if (!ready || closed || recording || replying) return;
        ++turn; recording = true;
        status.setText(italian ? "Ti ascolto…" : "Listening…");
        new Thread(this::capture, "Pepper-Realtime-input").start();
    }
    private void release() {
        if (!recording) return;
        releasedAt = SystemClock.elapsedRealtime();
        replying = true; recording = false; hold.setEnabled(false);
        status.setText(italian ? "Attendo la risposta…" : "Waiting for reply…");
        UI.postDelayed(timeout, 25000);
    }
    private void capture() {
        int frames = 0;
        AudioRecord mic = null;
        try {
            int inputRate = RATE;
            mic = recorder(inputRate);
            if (mic == null) { inputRate = RATE*2; mic = recorder(inputRate); }
            if (mic == null) throw new IllegalStateException();
            if (closed || !recording) {
                UI.post(() -> { if (!closed) resetTurn("Hold a little longer and try again."); });
                return;
            }
            mic.startRecording();
            int ratio = inputRate/RATE;
            short[] samples = new short[480*ratio];
            long began = SystemClock.elapsedRealtime();
            while (recording && !closed) {
                if (SystemClock.elapsedRealtime()-began > 20000) throw new IllegalStateException();
                int n = mic.read(samples,0,samples.length);
                if (n <= 0) throw new IllegalStateException();
                byte[] pcm = new byte[(n/ratio)*2];
                for (int i=0;i<n/ratio;i++) {
                    int value = samples[i*ratio];
                    if (ratio == 2) value = (value + samples[i*ratio+1])/2;
                    pcm[2*i]=(byte)value; pcm[2*i+1]=(byte)(value >> 8);
                }
                if (pcm.length != 0 && !send(obj("type","input_audio_buffer.append",
                        "audio",Base64.encodeToString(pcm,Base64.NO_WRAP)))) throw new IllegalStateException();
                frames += pcm.length/2;
            }
        } catch (Exception e) {
            UI.post(() -> fail("Could not stream microphone audio."));
            return;
        } finally {
            if (mic != null) {
                try { mic.stop(); } catch (Exception ignored) { }
                mic.release();
            }
        }
        if (closed) return;
        if (frames < RATE/5) {
            if (!send(obj("type","input_audio_buffer.clear"))) UI.post(() -> fail("Could not clear audio."));
            else UI.post(() -> resetTurn("Hold a little longer and try again."));
            return;
        }
        if (!send(obj("type","input_audio_buffer.commit")) || !send(obj("type","response.create"))) {
            UI.post(() -> fail("Could not send the voice turn.")); return;
        }
        new Thread(this::play, "Pepper-Realtime-output").start();
    }
    private AudioRecord recorder(int rate) {
        try {
            int n = AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            if (n <= 0) return null;
            AudioRecord mic = new AudioRecord(MediaRecorder.AudioSource.MIC,rate,AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,Math.max(n*2,rate/5));
            if (mic.getState() == AudioRecord.STATE_INITIALIZED) return mic;
            mic.release();
        } catch (RuntimeException ignored) { }
        return null;
    }
    private void play() {
        AudioTrack audio = null;
        int frames = 0;
        try {
            int n = AudioTrack.getMinBufferSize(RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
            if (n <= 0) throw new IllegalStateException();
            audio = new AudioTrack(AudioManager.STREAM_MUSIC,RATE,AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,Math.max(n,RATE/10),AudioTrack.MODE_STREAM);
            if (audio.getState() != AudioTrack.STATE_INITIALIZED) throw new IllegalStateException();
            audio.play();
            while (!closed) {
                byte[] pcm = queue.poll(200,TimeUnit.MILLISECONDS);
                if (pcm == null) continue;
                if (pcm == END) break;
                for (int at=0;at<pcm.length && !closed;) {
                    int written = audio.write(pcm,at,pcm.length-at,AudioTrack.WRITE_NON_BLOCKING);
                    if (written < 0) throw new IllegalStateException();
                    if (written == 0) { Thread.sleep(5); continue; }
                    if (frames == 0) {
                        long first = SystemClock.elapsedRealtime();
                        UI.post(() -> {
                        if (closed) return;
                        startedAudio = true;
                        long delay = first-releasedAt;
                        Log.i(TAG,"Release to first queued audio ms="+delay);
                        status.setText((italian ? "Prima risposta audio: " : "First audio queued: ") + delay + " ms");
                        maybeGesture();
                        });
                    }
                    at += written; frames += written/2;
                }
            }
            while (!closed && audio.getPlaybackHeadPosition() < frames) Thread.sleep(10);
            if (!closed) UI.post(() -> resetTurn(framesMessage()));
        } catch (Exception e) {
            UI.post(() -> fail("Could not play Realtime audio."));
        } finally {
            if (audio != null) {
                try { audio.pause(); audio.flush(); } catch (Exception ignored) {}
                audio.release();
            }
        }
    }
    private String framesMessage() { return startedAudio ? status.getText().toString() : "No audio returned."; }
    private void maybeGesture() {
        if (!startedAudio || gestureStarted || closed || !replying || focused != activity || context == null) return;
        String text = transcript.toString().replaceAll("[^\\p{L}\\p{N} .,!?']", "").trim();
        if (text.length() < 20) return;
        gestureStarted = true;
        QiContext qi = context;
        int gestureTurn = turn;
        SayBuilder.with(qi).withText("\\vol=0\\\\rspd=80\\" + text.substring(0,Math.min(text.length(),120)))
                .buildAsync().thenConsume(built -> UI.post(() -> {
            if (!closed && replying && gestureTurn == turn && gestureStarted && focused == activity && qi == context
                    && !built.hasError() && !built.isCancelled()) {
                try { body = built.get().async().run(); } catch (Exception ignored) {}
            }
        }));
    }
    private void stopBody() {
        if (body != null) { body.requestCancellation(); body = null; }
    }
    private void resetTurn(String message) {
        if (closed) return;
        UI.removeCallbacks(timeout); replying = false; startedAudio = false; gestureStarted = false;
        transcript.setLength(0); stopBody();
        status.setText(message); hold.setEnabled(true);
    }
    private void fail(String message) {
        if (closed) return;
        shutdown();
        hold.setEnabled(false); status.setText(message);
        Log.w(TAG, "Experiment stopped");
    }
    private void shutdown() {
        closed = true; recording = false; ready = false;
        UI.removeCallbacks(timeout); queue.clear(); stopBody();
        WebSocket ws = socket; if (ws != null) ws.cancel();
    }
}
