package com.softbankrobotics.pepper.pepperGPT;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import java.util.WeakHashMap;

public final class SpecialScreen {
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<Activity, Runnable> timers = new WeakHashMap<>();

    public static void closeAfterSpeech(Activity activity) {
        activity.runOnUiThread(() -> {
            cancel(activity);
            if (activity.isFinishing() || activity.isDestroyed()) return;
            Runnable close = () -> {
                timers.remove(activity);
                if (!activity.isFinishing() && !activity.isDestroyed()) activity.finish();
            };
            timers.put(activity, close);
            UI.postDelayed(close, 5000);
        });
    }

    public static void cancel(Activity activity) {
        activity.runOnUiThread(() -> {
            Runnable close = timers.remove(activity);
            if (close != null) UI.removeCallbacks(close);
        });
    }
}
