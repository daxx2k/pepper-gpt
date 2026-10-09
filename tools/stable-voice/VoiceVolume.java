package com.softbankrobotics.pepper.pepperGPT;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.AudioManager;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared voice level; native speech uses an utterance tag and external voices use MUSIC. */
public final class VoiceVolume {
    private static final String PREFS = "PepperGPT_Prefs";
    private static final String KEY = "voice_volume_percent";
    private VoiceVolume() { }
    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
    public static boolean chosen(Context context) { return preferences(context).contains(KEY); }
    private static int clamp(int value) { return Math.max(0, Math.min(100, value)); }
    private static boolean nativeVoice(Context context) {
        return "pepper".equals(preferences(context).getString("voice_mode", "pepper"));
    }
    public static int percent(Context context) {
        SharedPreferences prefs = preferences(context);
        if (prefs.contains(KEY)) return clamp(prefs.getInt(KEY, 80));
        if (nativeVoice(context)) return 100;
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) return 80;
        int maximum = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return Math.max(80, Math.round(100f * audio.getStreamVolume(AudioManager.STREAM_MUSIC) / Math.max(1, maximum)));
    }
    public static int externalLevel(Context context, int previous, int maximum) {
        return chosen(context) ? Math.round(maximum * percent(context) / 100f)
                : Math.max(previous, Math.round(maximum * .8f));
    }
    public static String nativeText(Context context, String text) {
        if (context == null || !nativeVoice(context) || !chosen(context)) return text;
        return "\\vol=" + percent(context) + "\\ " + text.replaceAll("\\\\vol=[^\\\\]*\\\\", "");
    }
    private static void change(Context context, int direction) {
        int value = clamp(percent(context) + direction * 10);
        preferences(context).edit().putInt(KEY, value).apply();
        if (!nativeVoice(context)) {
            AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audio != null) audio.setStreamVolume(AudioManager.STREAM_MUSIC,
                    Math.round(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * value / 100f), 0);
        }
    }
    private static GradientDrawable background(float density, int color) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(6 * density);
        return shape;
    }
    private static Button button(Context context, String label, String description, int color, float density) {
        Button button = new Button(context); button.setText(label); button.setTextSize(20); button.setTextColor(color);
        button.setAllCaps(false); button.setMinWidth(0); button.setMinimumWidth(0); button.setMinHeight(0); button.setMinimumHeight(0);
        button.setPadding(0, 0, 0, 0); button.setStateListAnimator(null); button.setElevation(0);
        button.setContentDescription(description);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, background(density, 0x55ffffff));
        states.addState(new int[]{}, background(density, 0x22ffffff)); button.setBackground(states);
        return button;
    }
    public static void attach(LinearLayout titleRow, int textColor) {
        Context context = titleRow.getContext(); float density = context.getResources().getDisplayMetrics().density;
        LinearLayout controls = new LinearLayout(context); controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(Math.round(8 * density), 0, 0, 0);
        TextView value = new TextView(context); value.setTextSize(14); value.setTextColor(textColor); value.setGravity(Gravity.CENTER);
        Runnable refresh = () -> { int percent = percent(context); value.setText(percent + "%"); value.setContentDescription("Voice volume: " + percent + " percent"); };
        refresh.run();
        Button down = button(context, "−", "Decrease voice volume", textColor, density);
        Button up = button(context, "+", "Increase voice volume", textColor, density);
        down.setOnClickListener(ignored -> { change(context, -1); refresh.run(); });
        up.setOnClickListener(ignored -> { change(context, 1); refresh.run(); });
        int side = Math.round(32 * density);
        controls.addView(down, new LinearLayout.LayoutParams(side, side));
        controls.addView(value, new LinearLayout.LayoutParams(Math.round(44 * density), LinearLayout.LayoutParams.WRAP_CONTENT));
        controls.addView(up, new LinearLayout.LayoutParams(side, side));
        SharedPreferences prefs = preferences(context);
        SharedPreferences.OnSharedPreferenceChangeListener listener = (p, key) -> {
            if (KEY.equals(key) || "voice_mode".equals(key)) controls.post(refresh);
        };
        controls.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View view) { prefs.registerOnSharedPreferenceChangeListener(listener); refresh.run(); }
            public void onViewDetachedFromWindow(View view) { prefs.unregisterOnSharedPreferenceChangeListener(listener); }
        });
        titleRow.addView(controls);
    }
}
