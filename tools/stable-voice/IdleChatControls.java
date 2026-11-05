package com.softbankrobotics.pepper.pepperGPT;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import androidx.recyclerview.widget.RecyclerView;

/** Hides chat controls during hands-free use while preserving the existing touch targets. */
final class IdleChatControls {
    private static final long IDLE_MS = 10000;
    private static final long FADE_MS = 180;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final View header, footer;
    private final StatusIcon icon;
    private final Runnable timeout = this::compactIfIdle;
    private final Runnable reveal = this::revealAndRestart;
    private final ViewTreeObserver.OnWindowFocusChangeListener focusChange = this::windowFocusChanged;
    private long lastTouch;
    private boolean compact, touching, scrolling;

    IdleChatControls(Activity activity, View header, View footer, LinearLayout parent, RecyclerView chat) {
        this.header = header;
        this.footer = footer;
        icon = new StatusIcon(activity);
        icon.setVisibility(View.GONE);
        icon.setElevation(dp(2));
        icon.setFocusable(true);
        icon.setOnClickListener(view -> revealAndRestart());
        LinearLayout.LayoutParams placement = new LinearLayout.LayoutParams(dp(54), dp(54));
        placement.gravity = Gravity.END;
        placement.setMargins(dp(6), 0, dp(6), dp(5));
        parent.addView(icon, parent.indexOfChild(footer) + 1, placement);
        if (chat != null) chat.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrollStateChanged(RecyclerView view, int state) {
                if (state == RecyclerView.SCROLL_STATE_DRAGGING) {
                    scrolling = true;
                    ui.removeCallbacks(timeout);
                } else if (state == RecyclerView.SCROLL_STATE_IDLE && scrolling) {
                    scrolling = false;
                    schedule();
                }
            }
        });
        header.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { observeWindow(); }
            @Override public void onViewDetachedFromWindow(View view) {
                ViewTreeObserver observer = header.getViewTreeObserver();
                if (observer.isAlive()) observer.removeOnWindowFocusChangeListener(focusChange);
                ui.removeCallbacks(timeout);
                ui.removeCallbacks(reveal);
            }
        });
        if (header.isAttachedToWindow()) observeWindow();
    }

    void theme(ThemePalette palette) {
        GradientDrawable badge = new GradientDrawable();
        badge.setShape(GradientDrawable.OVAL);
        badge.setColor(palette.surface);
        badge.setStroke(dp(1), palette.line);
        icon.setBackground(badge);
    }

    void phase(String label, int color, MainControlBar.Phase phase) {
        icon.color = color;
        icon.phase = phase;
        String description = label + (LanguageSwitch.italian() ? ". Mostra i comandi" : ". Show controls");
        if (!description.contentEquals(icon.getContentDescription() == null ? "" : icon.getContentDescription()))
            icon.setContentDescription(description);
        icon.invalidate();
    }

    void touch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touching = true;
                ui.removeCallbacks(timeout);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                touching = false;
                // Finish the original click or scroll before changing the layout.
                ui.removeCallbacks(reveal);
                ui.post(reveal);
                break;
            default:
                break;
        }
    }

    private void observeWindow() {
        header.getViewTreeObserver().addOnWindowFocusChangeListener(focusChange);
        if (header.hasWindowFocus()) revealAndRestart();
    }

    private void windowFocusChanged(boolean focused) {
        if (focused) revealAndRestart();
        else {
            touching = false;
            ui.removeCallbacks(timeout);
        }
    }

    private void revealAndRestart() {
        lastTouch = SystemClock.uptimeMillis();
        if (compact) {
            compact = false;
            icon.animate().cancel();
            icon.setVisibility(View.GONE);
            show(header);
            show(footer);
        }
        schedule();
    }

    private void schedule() {
        ui.removeCallbacks(timeout);
        if (!compact && !touching && !scrolling && header.hasWindowFocus())
            ui.postAtTime(timeout, Math.max(SystemClock.uptimeMillis(), lastTouch + IDLE_MS));
    }

    private void compactIfIdle() {
        if (compact || touching || scrolling || !header.hasWindowFocus()) return;
        compact = true;
        fade(header);
        footer.animate().alpha(0f).setDuration(FADE_MS).withEndAction(() -> {
            if (!compact) return;
            footer.setVisibility(View.GONE);
            show(icon);
        }).start();
    }

    private void fade(View view) {
        view.animate().alpha(0f).setDuration(FADE_MS).withEndAction(() -> {
            if (compact) view.setVisibility(View.GONE);
        }).start();
    }

    private void show(View view) {
        view.animate().cancel();
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);
        view.animate().alpha(1f).setDuration(FADE_MS).start();
    }

    private int dp(int value) {
        return Math.round(value * icon.getResources().getDisplayMetrics().density);
    }

    private final class StatusIcon extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private MainControlBar.Phase phase = MainControlBar.Phase.READY;
        private int color;

        StatusIcon(Activity activity) { super(activity); }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.save();
            canvas.scale(getWidth() / 54f, getHeight() / 54f);
            paint.setColor(color);
            paint.setStrokeWidth(2.2f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            if (phase == MainControlBar.Phase.LISTENING) {
                canvas.drawRoundRect(23, 16, 31, 30, 4, 4, paint);
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawArc(19, 19, 35, 34, 0, 180, false, paint);
                canvas.drawLine(27, 34, 27, 38, paint);
                canvas.drawLine(23, 38, 31, 38, paint);
                paint.setStyle(Paint.Style.FILL);
            } else if (phase == MainControlBar.Phase.REPLYING) {
                for (int i = 0; i < 5; i++) {
                    float x = 17 + i * 5;
                    float half = 3 + (2 - Math.abs(2 - i)) * 3;
                    canvas.drawLine(x, 27 - half, x, 27 + half, paint);
                }
            } else {
                boolean thinking = phase == MainControlBar.Phase.THINKING;
                int beat = (int)((SystemClock.uptimeMillis() / 240) % 3);
                for (int i = 0; i < 3; i++) {
                    paint.setAlpha(thinking && i != beat ? 105 : 255);
                    canvas.drawCircle(18 + i * 9, 27, 2.6f, paint);
                }
                if (thinking && isShown()) postInvalidateDelayed(120);
            }
            canvas.restore();
        }
    }
}
