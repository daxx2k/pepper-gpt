package com.softbankrobotics.pepper.pepperGPT;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.widget.TextView;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Draws the robot battery on Android versions without the newer battery glyph. */
public final class BatteryIndicator extends Drawable {
    private static final Pattern CHARGE = Pattern.compile("(\\d{1,3}|--)%");
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int level;

    private BatteryIndicator(int color, int level) {
        paint.setColor(color);
        this.level = level;
    }

    public static void render(TextView view) {
        String label = view.getText().toString();
        Matcher charge = CHARGE.matcher(label);
        if (!charge.find()) return;
        String value = charge.group(1);
        int level = "--".equals(value) ? -1 : Math.min(100, Integer.parseInt(value));
        view.setText(label.substring(charge.start()));
        BatteryIndicator icon = new BatteryIndicator(view.getCurrentTextColor(), level);
        float dp = view.getResources().getDisplayMetrics().density;
        icon.setBounds(0, 0, Math.round(17 * dp), Math.round(10 * dp));
        view.setCompoundDrawablePadding(Math.round(5 * dp));
        view.setCompoundDrawables(icon, null, null, null);
    }

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        canvas.save();
        canvas.translate(bounds.left, bounds.top);
        canvas.scale(bounds.width() / 17f, bounds.height() / 10f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.15f);
        canvas.drawRoundRect(0.7f, 1.1f, 14.2f, 8.9f, 1.3f, 1.3f, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(15.2f, 3.35f, 16.65f, 6.65f, 0.5f, 0.5f, paint);
        if (level > 0) canvas.drawRect(2.1f, 2.55f, 2.1f + 10.7f * level / 100f, 7.45f, paint);
        canvas.restore();
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
