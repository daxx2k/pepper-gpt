package com.softbankrobotics.pepper.pepperGPT;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.aldebaran.qi.Future;
import com.aldebaran.qi.sdk.QiContext;
import com.aldebaran.qi.sdk.builder.LookAtBuilder;
import com.aldebaran.qi.sdk.object.actuation.LookAtMovementPolicy;
import com.aldebaran.qi.sdk.object.human.Human;
import java.util.List;

/** Follows the engaged human, or the only person currently detected, with QiSDK look-at. */
final class ConversationGaze {
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final String TAG = "PepperGaze";
    private final Activity activity;
    private final QiContext qi;
    private final LocalFaceGaze fallback;
    private final Runnable retry = this::resume;
    private int generation;
    private boolean paused, closed, acquiring;
    private Future<Void> action;

    ConversationGaze(Activity activity, QiContext qi) {
        this.activity = activity;
        this.qi = qi;
        this.fallback = new LocalFaceGaze(activity, qi);
    }

    void pause() {
        paused = true;
        fallback.pause();
        acquiring = false;
        generation++;
        UI.removeCallbacks(retry);
        Future<Void> old = action;
        action = null;
        if (old != null) old.requestCancellation();
    }

    void close() {
        closed = true;
        pause();
    }

    void resume() {
        paused = false;
        if (closed || acquiring || action != null || !activity.hasWindowFocus()) return;
        acquiring = true;
        int request = ++generation;
        try {
            qi.getHumanAwareness().async().getEngagedHuman().thenConsume(result -> UI.post(() -> {
                if (!valid(request)) return;
                Human engaged = value(result);
                if (engaged != null) follow(engaged, request);
                else findOnlyPerson(request);
            }));
        } catch (RuntimeException failure) { tryAgain(request); }
    }

    private void findOnlyPerson(int request) {
        try {
            qi.getHumanAwareness().async().getHumansAround().thenConsume(result -> UI.post(() -> {
                if (!valid(request)) return;
                List<Human> humans = value(result);
                if (humans != null && humans.size() == 1) follow(humans.get(0), request);
                else {
                    fallback.resume();
                    tryAgain(request);
                }
            }));
        } catch (RuntimeException failure) { tryAgain(request); }
    }

    private void follow(Human human, int request) {
        fallback.pause();
        try {
            human.async().getHeadFrame()
                .andThenCompose(frame -> LookAtBuilder.with(qi).withFrame(frame).buildAsync())
                .andThenCompose(look -> look.async().setPolicy(LookAtMovementPolicy.HEAD_AND_BASE)
                        .andThenApply(ignored -> look))
                .thenConsume(result -> UI.post(() -> {
                    if (!valid(request)) return;
                    try {
                        com.aldebaran.qi.sdk.object.actuation.LookAt look = value(result);
                        if (look == null) { tryAgain(request); return; }
                        Future<Void> running = look.async().run();
                        action = running;
                        acquiring = false;
                        Log.i(TAG, "Native look-at requested");
                        running.thenConsume(done -> UI.post(() -> {
                            if (action != running) return;
                            action = null;
                            if (done.hasError()) Log.w(TAG, "Head tracking action ended with an error");
                            tryAgain(request);
                        }));
                    } catch (RuntimeException failure) { tryAgain(request); }
                }));
        } catch (RuntimeException failure) { tryAgain(request); }
    }

    private boolean valid(int request) {
        return !closed && !paused && generation == request && activity.hasWindowFocus();
    }

    private void tryAgain(int request) {
        if (generation != request) return;
        acquiring = false;
        if (!closed && !paused) {
            UI.removeCallbacks(retry);
            UI.postDelayed(retry, 1200);
        }
    }

    private static <T> T value(Future<T> future) {
        try { return future.get(); }
        catch (Exception failure) { return null; }
    }
}
