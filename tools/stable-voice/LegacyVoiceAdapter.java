package com.softbankrobotics.pepper.pepperGPT;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import com.aldebaran.qi.Future;
import com.aldebaran.qi.Promise;
import com.aldebaran.qi.sdk.QiContext;
import com.aldebaran.qi.sdk.builder.SayBuilder;
import com.aldebaran.qi.sdk.object.conversation.Say;
import com.aldebaran.qi.sdk.object.touch.TouchSensor;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Voice option and touch cancellation; original feature and narration flows remain intact. */
public final class LegacyVoiceAdapter {
    private static final String TAG = "LegacyVoice";
    private static final String PREFS = "PepperGPT_Prefs";
    private static final String ENGINE = "com.k2fsa.sherpa.onnx.tts.engine";
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final Map<SayBuilder, Info> BUILDERS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Say, Info> BUILT = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Say.Async, Info> SAY_INFO = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Activity, Future<Void>> ACTIVE = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Activity, Binding> TOUCH = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile WeakReference<Activity> owner = new WeakReference<>(null);
    private static TextToSpeech tts;
    private static boolean ready;
    private static boolean initFailed;
    private static Task current;
    private static final AtomicLong SERIAL = new AtomicLong();

    private static final class Info {
        final WeakReference<Activity> activity;
        final String text;
        Info(Activity a, String t) { activity = new WeakReference<>(a); text = t; }
    }
    private static final class Task {
        final Info info;
        final Promise<Void> promise = new Promise<>();
        final AtomicBoolean finished = new AtomicBoolean();
        final String id = "cori-" + SERIAL.incrementAndGet();
        String lastId;
        List<String> pieces;
        int nextPiece;
        boolean story;
        boolean openai;
        OpenAiVoice.Session streamedAudio;
        Future<Void> bodyAction;
        AudioManager audio;
        int previousVolume = -1;
        int coriVolume = -1;
        Task(Info i) { info = i; }
        void stopBody() {
            Future<Void> action = bodyAction;
            bodyAction = null;
            if (action != null && !action.isDone()) action.requestCancellation();
        }
        void raiseCoriVolume(Activity activity) {
            if (previousVolume >= 0) return;
            audio = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
            if (audio == null) return;
            previousVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            int maximum = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            coriVolume = VoiceVolume.externalLevel(activity, previousVolume, maximum);
            try { audio.setStreamVolume(AudioManager.STREAM_MUSIC, coriVolume, 0); }
            catch (SecurityException e) { previousVolume = -1; Log.w(TAG, "Cori volume adjustment unavailable"); }
        }
        void restoreVolume() {
            if (audio == null || previousVolume < 0) return;
            // Preserve an independent volume adjustment made by the user during speech.
            try {
                if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) == coriVolume)
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, previousVolume, 0);
            } catch (SecurityException e) { Log.w(TAG, "Cori volume restoration unavailable"); }
            previousVolume = -1;
        }
        void cancel() {
            if (!finished.compareAndSet(false, true)) return;
            if (streamedAudio != null) streamedAudio.cancel();
            stopBody();
            if (current == this) { if (tts != null) tts.stop(); current = null; }
            restoreVolume();
            promise.setCancelled();
            Log.i(TAG, "Cori cancelled");
        }
        void fail(String reason) {
            if (!finished.compareAndSet(false, true)) return;
            if (streamedAudio != null) streamedAudio.cancel();
            stopBody();
            if (current == this) { if (tts != null) tts.stop(); current = null; }
            restoreVolume();
            promise.setError(reason);
            Activity a = info.activity.get();
            if (a != null && !a.isFinishing()) Toast.makeText(a, "Selected voice unavailable. Choose Pepper voice in Settings.", Toast.LENGTH_LONG).show();
            Log.e(TAG, reason);
        }
        void complete() {
            if (!finished.compareAndSet(false, true)) return;
            stopBody();
            if (current == this) current = null;
            restoreVolume();
            promise.setValue(null);
            Log.i(TAG, "Cori playback completed");
        }
    }

    public static SayBuilder withText(SayBuilder builder, String text) {
        text = LanguageSwitch.speech(text);
        Activity activity = owner.get();
        text = VoiceVolume.nativeText(activity, text);
        if (LanguageSwitch.italian() && (activity == null || "pepper".equals(activity.getSharedPreferences(PREFS, 0).getString("voice_mode", "pepper"))))
            builder = builder.withLocale(LanguageSwitch.locale());
        SayBuilder result = builder.withText(text);
        BUILDERS.put(result, new Info(owner.get(), text));
        return result;
    }
    public static Say build(SayBuilder builder) {
        Say say = builder.build();
        remember(builder, say);
        return say;
    }
    public static Future<Say> buildAsync(SayBuilder builder) {
        return builder.buildAsync().andThenApply(say -> { remember(builder, say); return say; });
    }
    private static void remember(SayBuilder builder, Say say) {
        Info info = BUILDERS.remove(builder);
        if (info != null) BUILT.put(say, info);
    }
    public static Say.Async async(Say say) {
        Say.Async result = say.async();
        Info info = BUILT.get(say);
        if (info != null) SAY_INFO.put(result, info);
        return result;
    }
    public static Future<Void> runAsync(Say.Async say) {
        Info info = SAY_INFO.get(say);
        Activity a = info == null ? owner.get() : info.activity.get();
        if (a != null && owner.get() != a) return Future.cancelled();
        String mode = a == null ? "pepper" : a.getSharedPreferences(PREFS, 0).getString("voice_mode", "pepper");
        if (a == null || info == null || (!"cori".equals(mode) && !"openai".equals(mode))) {
            Future<Void> future = say.run();
            if (a != null) ACTIVE.put(a, future);
            return future;
        }
        Task task = new Task(info);
        task.openai = "openai".equals(mode);
        task.promise.setOnCancel(ignored -> UI.post(task::cancel));
        ACTIVE.put(a, task.promise.getFuture());
        UI.post(() -> {
            if (task.finished.get()) return;
            if (current != null) current.cancel();
            current = task;
            if (task.openai) play(task);
            else {
                initialize(a);
                if (ready) play(task);
                else if (initFailed) task.fail("Cori engine initialization failed");
            }
        });
        return task.promise.getFuture();
    }
    public static void runSync(Say say) {
        try { runAsync(async(say)).get(); }
        catch (CancellationException e) { throw e; }
        catch (Exception e) { throw new RuntimeException("Speech interrupted or failed", e); }
    }

    private static void initialize(Context context) {
        if (tts != null) return;
        initFailed = false;
        Toast.makeText(context.getApplicationContext(), "Preparing Cori voice…", Toast.LENGTH_LONG).show();
        tts = new TextToSpeech(context.getApplicationContext(), status -> UI.post(() -> {
            ready = status == TextToSpeech.SUCCESS;
            initFailed = !ready;
            if (ready) {
                tts.setLanguage(Locale.UK);
                if (tts.getVoices() != null) for (Voice voice : tts.getVoices()) {
                    if (voice.getName().toLowerCase(Locale.ROOT).contains("cori")) { tts.setVoice(voice); break; }
                }
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    public void onStart(String id) { Log.i(TAG, "Cori synthesis started: " + id); }
                    public void onDone(String id) { UI.post(() -> {
                        Task t = current;
                        if (t == null || t.finished.get() || !id.equals(t.lastId)) return;
                        if (t.nextPiece == t.pieces.size()) t.complete();
                        else {
                            boolean sentenceEnd = t.pieces.get(t.nextPiece - 1).matches(".*[.!?][\\\"'”’)]*");
                            UI.postDelayed(() -> nextChunk(t), sentenceEnd ? (t.story ? 500 : 220) : 0);
                        }
                    }); }
                    public void onError(String id) { UI.post(() -> { Task t = current; if (t != null && id.startsWith(t.id + ":")) t.fail("Cori synthesis failed"); }); }
                });
                Log.i(TAG, "Cori engine ready");
                if (current != null) play(current);
            } else if (current != null) current.fail("Cori engine initialization failed");
        }), ENGINE);
    }
    private static void play(Task task) {
        if (task.finished.get() || current != task) return;
        Activity taskOwner = task.info.activity.get();
        if (taskOwner == null || taskOwner.isFinishing() || owner.get() != taskOwner) { task.cancel(); return; }
        String text = task.info.text.replaceAll("\\\\(?:rspd|pau|vol|vct|pitch|rate|mrk)=[^\\\\]*\\\\", "")
                .replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) { task.complete(); return; }
        Activity a = task.info.activity.get();
        task.story = a != null && "story".equals(a.getIntent().getStringExtra("EXTRA_MODE"));
        if (!task.openai) tts.setSpeechRate(task.story ? .85f : 1f);
        task.pieces = task.openai ? CoriChunks.split(text, 1600, 1600, false) : CoriChunks.split(text);
        if (task.pieces.isEmpty()) { task.complete(); return; }
        task.raiseCoriVolume(taskOwner);
        nextChunk(task);
    }
    private static void nextChunk(Task task) {
        if (task.finished.get() || current != task) return;
        Activity a = task.info.activity.get();
        if (a == null || a.isFinishing() || owner.get() != a) { task.cancel(); return; }
        int index = task.nextPiece++;
        task.lastId = task.id + ":" + index;
        String chunk = task.pieces.get(index);
        if (task.openai) {
            task.streamedAudio = OpenAiVoice.start(a, chunk, task.story, () -> startBody(task, chunk), () -> {
                if (task.finished.get() || current != task) return;
                task.stopBody();
                if (task.nextPiece == task.pieces.size()) task.complete();
                else UI.postDelayed(() -> nextChunk(task), task.story ? 500 : 220);
            }, () -> task.fail("OpenAI voice request failed"));
            return;
        }
        Log.i(TAG, "Cori queued chunk " + (index + 1) + "/" + task.pieces.size() + ": " + chunk.length() + " characters");
        if (tts.speak(chunk, TextToSpeech.QUEUE_FLUSH, new Bundle(), task.lastId) == TextToSpeech.ERROR)
            task.fail("Cori rejected speech request");
    }

    private static void startBody(Task task, String text) {
        Activity a = task.info.activity.get();
        Binding binding = a == null ? null : TOUCH.get(a);
        if (task.finished.get() || current != task || owner.get() != a || binding == null || !binding.live) return;
        task.stopBody();
        // Keep native contextual body language. Silence only this native utterance,
        // leaving the user's global robot volume and native voice settings alone.
        String silentText = "\\vol=0\\\\rspd=80\\" + text.replaceAll("\\\\vol=[^\\\\]*\\\\", "");
        // External audio supplies its own language. Use the previously working native
        // gesture engine independently of whether Italian native TTS is installed.
        SayBuilder.with(binding.qi).withText(silentText).buildAsync().thenConsume(built -> UI.post(() -> {
            if (task.finished.get() || current != task || owner.get() != a || !binding.live) return;
            if (built.hasError() || built.isCancelled()) { Log.w(TAG, "Speech body language unavailable"); return; }
            try { task.bodyAction = built.get().async().run(); }
            catch (Exception failure) { Log.w(TAG, "Speech body language action unavailable"); return; }
            task.bodyAction.thenConsume(result -> {
                if (result.hasError()) Log.w(TAG, "Speech body language action failed");
            });
            Log.i(TAG, "Native speech body language started");
        }));
    }

    private static final class SensorListener {
        final TouchSensor sensor;
        final TouchSensor.OnStateChangedListener listener;
        SensorListener(TouchSensor s, TouchSensor.OnStateChangedListener l) { sensor = s; listener = l; }
    }
    private static final class Binding {
        final WeakReference<Activity> activity;
        final QiContext qi;
        final List<SensorListener> sensors = new ArrayList<>();
        boolean live = true;
        Binding(Activity a, QiContext context) { activity = new WeakReference<>(a); qi = context; }
        synchronized void close() {
            live = false;
            for (SensorListener item : sensors) item.sensor.async().removeOnStateChangedListener(item.listener);
            sensors.clear();
        }
    }
    public static void onFocusGained(Activity a, QiContext qi) {
        owner = new WeakReference<>(a);
        if ("MainActivity".equals(a.getClass().getSimpleName()) &&
                "cori".equals(a.getSharedPreferences(PREFS, 0).getString("voice_mode", "pepper"))) {
            UI.post(() -> { if (owner.get() == a && !a.isFinishing()) initialize(a); });
        }
        Binding previous = TOUCH.remove(a); if (previous != null) previous.close();
        Binding binding = new Binding(a, qi); TOUCH.put(a, binding);
        for (String name : new String[]{"Head/Touch", "LHand/Touch", "RHand/Touch"}) {
            qi.getTouch().async().getSensor(name).andThenConsume(sensor -> {
                synchronized (binding) {
                    if (!binding.live) return;
                    TouchSensor.OnStateChangedListener listener = state -> {
                        if (!state.getTouched()) return;
                        UI.post(() -> {
                            synchronized (binding) { if (!binding.live) return; }
                            Activity activity = binding.activity.get();
                            if (activity != null && !activity.isFinishing()) {
                                Log.i(TAG, "Touch interrupt: " + name);
                                stopSpeech(activity);
                            }
                        });
                    };
                    binding.sensors.add(new SensorListener(sensor, listener));
                    sensor.async().addOnStateChangedListener(listener).thenConsume(result -> {
                        if (result.hasError()) Log.e(TAG, "Touch listener registration failed: " + name);
                        else Log.i(TAG, "Touch listener ready: " + name);
                    });
                }
            }).thenConsume(result -> { if (result.hasError()) Log.e(TAG, "Touch sensor unavailable: " + name); });
        }
    }
    public static void onFocusLost(Activity a) {
        Binding binding = TOUCH.remove(a); if (binding != null) binding.close();
        if (owner.get() == a) owner = new WeakReference<>(null);
        UI.post(() -> {
            if ("MainActivity".equals(a.getClass().getSimpleName())) invoke(a, "stopListening");
            stopSpeech(a);
        });
    }
    public static void beforeLanguageChange(Activity activity) {
        for (String name : new String[]{"wasListeningBeforeSpeaking", "wasListeningBeforeOpenAI"}) {
            try { Field f = activity.getClass().getDeclaredField(name); f.setAccessible(true); f.setBoolean(activity, false); }
            catch (ReflectiveOperationException ignored) { }
        }
        invoke(activity, "stopListening");
        stopSpeech(activity);
    }
    private static Object field(Activity a, String name) {
        try { Field f = a.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(a); }
        catch (ReflectiveOperationException e) { return null; }
    }
    private static void invoke(Activity a, String name) {
        try { Method m = a.getClass().getDeclaredMethod(name); m.setAccessible(true); m.invoke(a); }
        catch (ReflectiveOperationException e) { Log.d(TAG, "No speech hook: " + name); }
    }
    private static void stopSpeech(Activity a) {
        Object job = field(a, "storySpeechJob");
        if (job != null) try {
            Method m = job.getClass().getMethod("cancel", CancellationException.class); m.setAccessible(true); m.invoke(job, new Object[]{null});
        } catch (ReflectiveOperationException e) { Log.e(TAG, "Could not cancel story narration"); }
        Future<Void> active = ACTIVE.remove(a); if (active != null && !active.isDone()) active.requestCancellation();
        Object action = field(a, "sayAction"); if (action instanceof Future && !((Future<?>) action).isDone()) ((Future<?>) action).requestCancellation();
        if (current != null && current.info.activity.get() == a) current.cancel();
        if ("MainActivity".equals(a.getClass().getSimpleName())) invoke(a, "stopSpeaking");
        else {
            invoke(a, "stopGentleScrolling");
            try { Field f = a.getClass().getDeclaredField("speechRunning"); f.setAccessible(true); f.setBoolean(a, false); }
            catch (ReflectiveOperationException ignored) { }
        }
    }

    public static void attachSettings(Activity a) {
        ModelSettings.attach(a);
        View content = a.findViewById(android.R.id.content);
        LinearLayout column = settingsColumn(content);
        if (column == null) { Log.e(TAG, "Voice setting container unavailable"); return; }
        Button button = new Button(a);
        updateVoiceLabel(a, button);
        button.setOnClickListener(view -> {
            String voice = a.getSharedPreferences(PREFS, 0).getString("voice_mode", "pepper");
            String onlineVoice = a.getSharedPreferences(PREFS, 0).getString("openai_voice", "marin");
            new AlertDialog.Builder(a).setTitle("Voice")
                    .setSingleChoiceItems(new String[]{"Pepper voice", "Piper Cori — English, offline", "OpenAI Coral — AI voice, online", "OpenAI Marin — AI voice, online"}, "openai".equals(voice) ? ("coral".equals(onlineVoice) ? 2 : 3) : ("cori".equals(voice) ? 1 : 0), (dialog, selection) -> {
                        if (selection == 1 && LanguageSwitch.italian()) {
                            dialog.dismiss();
                            Toast.makeText(a, "Cori supports English. Select an OpenAI voice for Italian.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        android.content.SharedPreferences.Editor preferences = a.getSharedPreferences(PREFS, 0).edit()
                                .putString("voice_mode", selection >= 2 ? "openai" : (selection == 1 ? "cori" : "pepper"));
                        if (selection >= 2) preferences.putString("openai_voice", selection == 2 ? "coral" : "marin");
                        preferences.apply();
                        updateVoiceLabel(a, button);
                        if (selection == 1) initialize(a);
                        dialog.dismiss();
                    }).setNegativeButton("Cancel", null).show();
        });
        column.addView(button, 0, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
    private static void updateVoiceLabel(Activity a, Button b) {
        String voice = a.getSharedPreferences(PREFS, 0).getString("voice_mode", "pepper");
        String onlineVoice = a.getSharedPreferences(PREFS, 0).getString("openai_voice", "marin");
        b.setText("Voice: " + ("openai".equals(voice) ? ("coral".equals(onlineVoice) ? "OpenAI Coral" : "OpenAI Marin") : ("cori".equals(voice) ? "Piper Cori" : "Pepper")));
    }
    private static LinearLayout settingsColumn(View view) {
        if (view instanceof ScrollView && ((ScrollView) view).getChildCount() > 0 && ((ScrollView) view).getChildAt(0) instanceof LinearLayout)
            return (LinearLayout) ((ScrollView) view).getChildAt(0);
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            LinearLayout found = settingsColumn(((ViewGroup) view).getChildAt(i)); if (found != null) return found;
        }
        return null;
    }
}
