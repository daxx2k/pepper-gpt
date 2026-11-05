package com.softbankrobotics.pepper.pepperGPT;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;
import androidx.recyclerview.widget.RecyclerView;

/** One compact status and action row, using the original app's Listen and Cancel actions. */
public final class MainControlBar {
    private static final int GRAY = 0xff778394;
    private static final int CANCEL = 0xffa73b39;
    enum Phase {
        READY(GRAY, GRAY),
        LISTENING(0xff13765b, 0xff75d9b9),
        THINKING(0xff7445b3, 0xffc5a1ff),
        REPLYING(0xff256ab0, 0xff86bff4);

        final int light, dark;
        Phase(int light, int dark) {
            this.light = light;
            this.dark = dark;
        }
    }
    private final Activity activity;
    private final Button action;
    private final TextView status;
    private final Indicator dots;
    private final GradientDrawable surface;
    private Phase phase = Phase.READY;
    private IdleChatControls idleControls;
    private ThemePalette palette;
    private boolean busy, updating, active;
    private ColorStateList previousTint;

    public static void attach(Activity activity) {
        if (find(activity) != null) return;
        View view = activity.findViewById(id(activity, "listenToggleButton"));
        if (!(view instanceof Button) || !(view.getParent() instanceof LinearLayout)) return;
        MainControlBar bar = new MainControlBar(activity, (Button)view, (LinearLayout)view.getParent());
        view.setTag(view.getId(), bar);
        theme(activity, activity.getSharedPreferences("PepperGPT_Prefs", 0).getString("ui_theme", "glass"));
    }

    private MainControlBar(Activity activity, Button action, LinearLayout parent) {
        this.activity = activity;
        this.action = action;
        int index = parent.indexOfChild(action);
        parent.removeView(action);

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(22), dp(5), dp(8), dp(5));
        surface = new GradientDrawable();
        surface.setColor(0xfff9fbfd);
        surface.setCornerRadius(dp(34));
        surface.setStroke(dp(1), 0xffd6e0ea);
        row.setBackground(surface);
        row.setElevation(dp(2));
        int width = Math.min(dp(780), activity.getResources().getDisplayMetrics().widthPixels - dp(36));
        LinearLayout.LayoutParams shell = new LinearLayout.LayoutParams(width, dp(72));
        shell.gravity = Gravity.CENTER_HORIZONTAL;
        shell.setMargins(dp(12), dp(4), dp(12), dp(10));
        parent.addView(row, index, shell);

        dots = new Indicator();
        row.addView(dots, new LinearLayout.LayoutParams(dp(38), dp(28)));
        status = new TextView(activity);
        status.setTextColor(0xff1a3042);
        status.setTextSize(22);
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams label = new LinearLayout.LayoutParams(0, -2, 1f);
        label.setMarginStart(dp(9));
        label.setMarginEnd(dp(8));
        row.addView(status, label);

        action.setAllCaps(false);
        action.setTextSize(20);
        action.setMinimumWidth(0);
        action.setMinWidth(0);
        action.setMinimumHeight(0);
        action.setMinHeight(0);
        action.setPadding(dp(12), 0, dp(12), 0);
        action.setSingleLine(true);
        row.addView(action, new LinearLayout.LayoutParams(dp(158), dp(55)));
        active = isStop(action.getText());
        setAction();
        idle();
        View header = activity.findViewById(id(activity, "toolbar"));
        View history = activity.findViewById(id(activity, "conversation_recycler_view"));
        if (header != null && header.getParent() == parent) {
            idleControls = new IdleChatControls(activity, header, row, parent,
                    history instanceof RecyclerView ? (RecyclerView)history : null);
        }
        action.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (updating) return;
                active = isStop(s);
            }
            @Override public void afterTextChanged(Editable text) {
                if (!updating) action.post(() -> setAction());
            }
        });
    }

    public static void theme(Activity activity, String name) {
        MainControlBar bar = find(activity);
        if (bar == null) return;
        bar.palette = ThemePalette.named(name);
        bar.surface.setColor(bar.palette.surface);
        bar.surface.setStroke(bar.dp(1), bar.palette.line);
        bar.status.setTextColor(bar.palette.ink);
        if (bar.idleControls != null) bar.idleControls.theme(bar.palette);
        bar.updateIndicatorColor();
    }

    private void updateIndicatorColor() {
        int color = palette != null && palette.darkSurface
                ? (phase == Phase.READY ? palette.muted : phase.dark) : phase.light;
        dots.color = color;
        dots.invalidate();
        if (idleControls != null) idleControls.phase(status.getText().toString(), color, phase);
    }

    public static void touch(Activity activity, MotionEvent event) {
        MainControlBar bar = find(activity);
        if (bar != null && bar.idleControls != null) bar.idleControls.touch(event);
    }

    private static boolean isStop(CharSequence text) {
        String value = text.toString().toLowerCase(Locale.ROOT);
        return value.contains("stop") || value.contains("smetti") || value.contains("interrompi");
    }

    private static int id(Activity activity, String name) {
        return activity.getResources().getIdentifier(name, "id", activity.getPackageName());
    }

    private static MainControlBar find(Activity activity) {
        int id = id(activity, "listenToggleButton");
        View view = activity.findViewById(id);
        Object tag = view == null ? null : view.getTag(id);
        return tag instanceof MainControlBar ? (MainControlBar)tag : null;
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private void setAction() {
        boolean it = LanguageSwitch.italian();
        String text = busy ? (it ? "Annulla" : "Cancel") : active ? "Stop" : (it ? "Ascolta" : "Listen");
        if (TextUtils.equals(action.getText(), text)) return;
        updating = true;
        action.setText(text);
        updating = false;
    }

    private void idle() {
        status(LanguageSwitch.italian() ? "Pronta" : "Ready", Phase.READY, false);
    }

    private void status(String text, Phase next, boolean moving) {
        status.setText(text);
        phase = next;
        dots.moving = moving;
        updateIndicatorColor();
    }

    public static boolean phase(Activity a, String it, String en) {
        MainControlBar bar = find(a);
        if (bar == null) return false;
        if (bar.busy) return true;
        String phase = en.toLowerCase(Locale.ROOT);
        Phase next = phase.contains("listen") || phase.contains("hear") ? Phase.LISTENING
                : phase.contains("reply") ? Phase.REPLYING : Phase.THINKING;
        bar.status(LanguageSwitch.italian() ? it : en, next, true);
        return true;
    }

    public static void reset(Activity a) {
        MainControlBar bar = find(a);
        if (bar != null && !bar.busy) {
            bar.idle();
            bar.setAction();
        }
    }

    public static void busy(Activity a, String text) {
        MainControlBar bar = find(a);
        if (bar == null) return;
        if (!bar.busy) bar.previousTint = bar.action.getBackgroundTintList();
        bar.busy = true;
        bar.action.setBackgroundTintList(ColorStateList.valueOf(CANCEL));
        bar.setAction();
        bar.status(text, Phase.THINKING, true);
        View legacy = a.findViewById(id(a, "spinnerContainer"));
        if (legacy != null) legacy.setVisibility(View.GONE);
    }

    public static void ready(Activity a) {
        MainControlBar bar = find(a);
        if (bar == null || !bar.busy) return;
        bar.busy = false;
        ColorStateList tint = bar.action.getBackgroundTintList();
        if (tint != null && tint.getDefaultColor() == CANCEL) bar.action.setBackgroundTintList(bar.previousTint);
        reset(a);
    }

    public static boolean cancelIfBusy(Activity a) {
        MainControlBar bar = find(a);
        if (bar == null || !bar.busy) return false;
        View cancel = a.findViewById(id(a, "stopButton"));
        if (cancel == null) return false;
        return cancel.performClick();
    }

    private final class Indicator extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int color = GRAY;
        boolean moving;

        Indicator() { super(activity); }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            paint.setColor(color);
            int beat = (int)((SystemClock.uptimeMillis() / 160) % 3);
            float unit = getWidth() / 4f;
            for (int i = 0; i < 3; i++) {
                paint.setAlpha(moving && i == beat ? 255 : moving ? 105 : 148);
                canvas.drawCircle(unit * (i + 1), getHeight() / 2f, unit * .29f, paint);
            }
            if (moving && isShown()) postInvalidateDelayed(100);
        }
    }
}
