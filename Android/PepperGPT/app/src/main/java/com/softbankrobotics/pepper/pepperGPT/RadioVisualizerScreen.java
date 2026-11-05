package com.softbankrobotics.pepper.pepperGPT;

import android.app.Activity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Expands the visualizer in the same window while the radio player keeps running. */
final class RadioVisualizerScreen {
    private static final long IDLE_MS = 10_000;
    private final Activity activity;
    private final FrameLayout content;
    private final View controls;
    private final MusicVisualizerView inline;
    private final RadioManager player;
    private final RadioSpectrum spectrum = new RadioSpectrum(this::levels);
    private final Runnable enter = this::show;
    private MusicVisualizerView visualizer;
    private boolean playing;
    private boolean touching;

    private RadioVisualizerScreen(Activity activity, FrameLayout content, View controls,
            MusicVisualizerView inline, RadioManager player) {
        this.activity = activity;
        this.content = content;
        this.controls = controls;
        this.inline = inline;
        this.player = player;
        content.getViewTreeObserver().addOnWindowFocusChangeListener(focused -> {
            touching = false;
            syncPlayback();
        });
    }

    static void attach(Activity activity, TextView status, MusicVisualizerView inline, RadioManager player) {
        View container = activity.findViewById(android.R.id.content);
        int id = activity.getResources().getIdentifier("radioScrollRoot", "id", activity.getPackageName());
        View controls = activity.findViewById(id);
        if (container instanceof FrameLayout && controls != null) {
            status.setTag(status.getId(), new RadioVisualizerScreen(activity, (FrameLayout) container, controls, inline, player));
        }
    }

    static void playback(TextView status, boolean playing) {
        Object tag = status.getTag(status.getId());
        if (!(tag instanceof RadioVisualizerScreen)) return;
        RadioVisualizerScreen screen = (RadioVisualizerScreen) tag;
        if (screen.playing == playing) return;
        screen.playing = playing;
        screen.syncPlayback();
    }

    private static RadioVisualizerScreen find(Activity activity) {
        int id = activity.getResources().getIdentifier("statusText", "id", activity.getPackageName());
        View status = activity.findViewById(id);
        Object tag = status == null ? null : status.getTag(id);
        return tag instanceof RadioVisualizerScreen ? (RadioVisualizerScreen) tag : null;
    }

    static void stop(Activity activity) {
        RadioVisualizerScreen screen = find(activity);
        if (screen == null) return;
        screen.playing = false;
        screen.syncPlayback();
    }

    static void touch(Activity activity, MotionEvent event) {
        RadioVisualizerScreen screen = find(activity);
        if (screen == null) return;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                screen.touching = true;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                screen.touching = false;
                break;
            default:
                return;
        }
        screen.schedule();
    }

    private void syncPlayback() {
        if (playing && content.hasWindowFocus() && !activity.isFinishing()) {
            spectrum.start(player.audioSessionId());
        } else {
            spectrum.stop();
            inline.setPlaying(false);
            hide();
        }
        schedule();
    }

    private void levels(float[] levels) {
        if (visualizer != null) visualizer.setLevels(levels);
        else inline.setLevels(levels);
    }

    private boolean canShow() {
        return playing && !touching && visualizer == null && content.hasWindowFocus() && !activity.isFinishing();
    }

    private void schedule() {
        content.removeCallbacks(enter);
        if (canShow()) content.postDelayed(enter, IDLE_MS);
    }

    private void show() {
        if (!canShow()) return;
        ThemePalette theme = ThemePalette.named(activity.getSharedPreferences("PepperGPT_Prefs", 0)
                .getString("ui_theme", "glass"));
        visualizer = new MusicVisualizerView(activity);
        visualizer.setBackgroundColor(theme.background);
        visualizer.setColor(RadioStations.visualizerColor(activity));
        visualizer.setPlaying(true);
        visualizer.setContentDescription("it".equals(WeatherPanel.language(activity))
                ? "Visualizzatore musicale. Tocca per mostrare i comandi."
                : "Music visualizer. Tap to show controls.");
        visualizer.setOnClickListener(view -> {
            hide();
            schedule();
        });
        controls.setVisibility(View.INVISIBLE);
        content.addView(visualizer, new FrameLayout.LayoutParams(-1, -1));
    }

    private void hide() {
        if (visualizer == null) return;
        visualizer.setPlaying(false);
        content.removeView(visualizer);
        visualizer = null;
        controls.setVisibility(View.VISIBLE);
    }
}
