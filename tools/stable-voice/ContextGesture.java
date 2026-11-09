package com.softbankrobotics.pepper.pepperGPT;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.aldebaran.qi.Future;
import com.aldebaran.qi.sdk.QiContext;
import com.aldebaran.qi.sdk.builder.AnimateBuilder;
import com.aldebaran.qi.sdk.builder.AnimationBuilder;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

/** Runs one authored cue while its screen owns robot focus. */
final class ContextGesture {
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final GestureVariants VARIANTS = new GestureVariants();
    private static final WeakHashMap<Activity, ContextGesture> ACTIVE = new WeakHashMap<>();
    private static final String MARKER = "(?iu)(?:^|[.!?;,:\\n]\\s*)\\s*";
    private static final Pattern FIRST = Pattern.compile(MARKER
        + "(?:first(?:ly)?[,.:)]|first of all\\b|one[,.:)]|1[.)]|per prim[oa] cosa\\b|in primo luogo\\b|prim[oa][,.:)]|uno[,.:)])");
    private static final Pattern SECOND = Pattern.compile(MARKER
        + "(?:second(?:ly)?[,.:)]|two[,.:)]|2[.)]|in secondo luogo\\b|second[oa][,.:)]|due[,.:)])");
    private final String name;
    private Future<Void> action;
    private ContextGesture(String name) { this.name = name; }
    private boolean cancelled;

    static boolean enumeration(String phrase, CharSequence transcript, boolean directed) {
        if (directed && FIRST.matcher(phrase).lookingAt()) return true;
        java.util.regex.Matcher first = FIRST.matcher(transcript);
        return first.find() && SECOND.matcher(transcript).find(first.end() - 1)
                && (FIRST.matcher(phrase).lookingAt() || SECOND.matcher(phrase).lookingAt());
    }

    static boolean play(Activity activity, QiContext qi, String name, Runnable done) {
        if (qi == null || activity.isFinishing() || activity.isDestroyed()
                || !activity.hasWindowFocus() || ACTIVE.containsKey(activity)) return false;
        String clip = VARIANTS.current(name);
        boolean libraryClip = clip.indexOf('/') >= 0;
        int id = libraryClip ? 0 : activity.getResources().getIdentifier(clip, "raw", activity.getPackageName());
        String asset = libraryClip ? clip : id == 0 ? assetPath(clip) : null;
        String label = libraryClip ? clip.substring(clip.lastIndexOf('/') + 1) : clip;
        if (id == 0 && asset == null) return false;
        ContextGesture cue = new ContextGesture(name);
        ACTIVE.put(activity, cue);
        try {
            AnimationBuilder builder = AnimationBuilder.with(qi);
            if (asset == null) builder.withResources(id);
            else builder.withAssets(asset);
            builder.buildAsync()
                .andThenCompose(animation -> AnimateBuilder.with(qi).withAnimation(animation).buildAsync())
                .thenConsume(ready -> UI.post(() -> {
                    if (cue.cancelled) return;
                    if (ready.hasError() || ready.isCancelled() || !activity.hasWindowFocus()) {
                        cue.finish(activity, label, false, done);
                        return;
                    }
                    try {
                        cue.action = ready.get().async().run();
                        VARIANTS.advance(name);
                        Log.i("PepperCue", "Requested " + label);
                        cue.action.thenConsume(result -> UI.post(() -> {
                            if (cue.cancelled && result.isCancelled()) Log.i("PepperCue", "Cancelled " + label);
                            cue.finish(activity, label, !result.hasError() && !result.isCancelled(), done);
                        }));
                    } catch (Exception error) {
                        cue.finish(activity, label, false, done);
                    }
                }));
            return true;
        } catch (RuntimeException error) {
            ACTIVE.remove(activity);
            Log.w("PepperCue", "Could not prepare " + name);
            return false;
        }
    }

    private static String assetPath(String cue) {
        switch (cue) {
            case "spinner_thinking": return "pepper_idle/spinner_right.qianim";
            case "spinner_thinking_left": return "pepper_idle/spinner_left.qianim";
            case "spinner_idle_left": return "pepper_idle/spinner_left.qianim";
            case "listening_idle_left": return "pepper_idle/listening_idle_left.qianim";
            case "spinner_idle_right": return "pepper_idle/spinner_right.qianim";
            case "listening_idle_right": return "pepper_idle/listening_idle_right.qianim";
            case "illustration_wait": return "pepper_core_anims/10-Loading/PoseRight_03.qianim";
            case "listen_ack": return "pepper_idle/listening_idle_right.qianim";
            case "uncertain": return "pepper_core_anims/10-Loading/PoseRight_03.qianim";
            default: return null;
        }
    }

    private void finish(Activity activity, String name, boolean success, Runnable done) {
        if (cancelled || ACTIVE.get(activity) != this) return;
        ACTIVE.remove(activity);
        if (success) Log.i("PepperCue", "Completed " + name);
        else Log.w("PepperCue", "Could not complete " + name);
        if (done != null) done.run();
    }

    static void stop(Activity activity) { stop(activity, null); }

    static void stop(Activity activity, String name) {
        ContextGesture cue = ACTIVE.get(activity);
        if (cue == null || (name != null && !name.equals(cue.name))) return;
        ACTIVE.remove(activity);
        cue.cancelled = true;
        if (cue.action != null) cue.action.requestCancellation();
    }
}
