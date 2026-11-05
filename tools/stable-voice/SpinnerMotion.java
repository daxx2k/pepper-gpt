package com.softbankrobotics.pepper.pepperGPT;

import android.app.Activity;
import android.os.Handler;
import android.view.View;
import com.aldebaran.qi.sdk.QiContext;

/** Runs brief, alternating gestures while Pepper waits for a result. */
final class SpinnerMotion {
    private final Activity activity;
    private final Handler ui;
    private final QiContext qi;
    private final ConversationGaze gaze;
    private final boolean ownsGaze;
    private final Runnable tick = this::move;
    private boolean running, gazePaused;
    private int step;

    SpinnerMotion(Activity activity, Handler ui, QiContext qi, ConversationGaze sharedGaze) {
        this.activity = activity;
        this.ui = ui;
        this.qi = qi;
        ownsGaze = sharedGaze == null;
        gaze = ownsGaze ? new ConversationGaze(activity, qi) : sharedGaze;
    }

    void start(long delay) {
        running = true;
        ui.postDelayed(tick, delay);
    }

    boolean owns(Activity a) { return activity == a; }

    private void move() {
        if (!running) return;
        if (activity.isFinishing() || activity.isDestroyed() || !activity.hasWindowFocus()) {
            stop();
            return;
        }
        int cameraId = activity.getResources().getIdentifier("viewfinderOverlay", "id", activity.getPackageName());
        View camera = activity.findViewById(cameraId);
        if (camera != null && camera.isShown()) {
            pauseGaze();
            stopMotion();
            ui.postDelayed(tick, 400);
            return;
        }

        String cue;
        if (step == 0 || step == 3) {
            pauseGaze();
            cue = step == 0 ? "spinner_thinking" : "spinner_thinking_left";
        } else {
            gazePaused = false;
            gaze.resume();
            cue = step % 2 == 0 ? "spinner_idle_right" : "spinner_idle_left";
        }
        if (ContextGesture.play(activity, qi, cue, () -> {
            if (running) ui.postDelayed(tick, 450);
        })) {
            step = (step + 1) % 6;
        } else {
            ui.postDelayed(tick, 650);
        }
    }

    private void pauseGaze() {
        if (gazePaused) return;
        gazePaused = true;
        gaze.pause();
    }

    private void stopMotion() {
        ContextGesture.stop(activity, "spinner_thinking");
        ContextGesture.stop(activity, "spinner_thinking_left");
        ContextGesture.stop(activity, "spinner_idle_left");
        ContextGesture.stop(activity, "spinner_idle_right");
    }

    void stop() {
        running = false;
        ui.removeCallbacks(tick);
        stopMotion();
        if (ownsGaze) gaze.close();
        else if (gazePaused) gaze.resume();
    }
}
