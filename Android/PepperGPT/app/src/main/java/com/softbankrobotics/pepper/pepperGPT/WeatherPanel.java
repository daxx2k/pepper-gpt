package com.softbankrobotics.pepper.pepperGPT;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class WeatherPanel {
    public static String language(Context context) {
        return "it".equals(context.getSharedPreferences("PepperGPT_Prefs", 0).getString("app_language", "en"))
                ? "it" : "en";
    }

    public static String queryUnits(Context context) {
        return "&units=metric&lang=" + language(context);
    }

    private static String extra(Intent intent, String key, String fallback) {
        String value = intent.getStringExtra(key);
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    public static String speech(Activity activity) {
        Intent intent = activity.getIntent();
        boolean it = "it".equals(language(activity));
        String city = extra(intent, "EXTRA_CITY", "");
        String temperature = extra(intent, "EXTRA_TEMP", "");
        String conditions = extra(intent, "EXTRA_DESC", "");
        if (it) {
            String location = city.isEmpty() ? "" : " a " + city;
            if (conditions.isEmpty() || temperature.isEmpty() || "--".equals(temperature))
                return "Il meteo" + location + " non è disponibile.";
            return "Ecco il meteo" + location + ". " + conditions + ". La temperatura è di "
                    + temperature + " gradi Celsius.";
        }
        String location = city.isEmpty() ? "" : " in " + city;
        if (conditions.isEmpty() || temperature.isEmpty() || "--".equals(temperature))
            return "The weather" + location + " is unavailable.";
        return "The weather" + location + " is " + conditions + " with a temperature of "
                + temperature + " degrees Celsius.";
    }

    public static void render(Activity activity) {
        int rootId = activity.getResources().getIdentifier("weather_root", "id", activity.getPackageName());
        View rootView = activity.findViewById(rootId);
        if (!(rootView instanceof FrameLayout)) return;
        FrameLayout root = (FrameLayout) rootView;
        int speechBar = activity.getResources().getIdentifier("speech_bar", "id", activity.getPackageName());
        View bar = activity.findViewById(speechBar);
        if (bar != null) bar.setAlpha(0f);

        Intent intent = activity.getIntent();
        boolean it = "it".equals(language(activity));
        String theme = extra(intent, "EXTRA_THEME", "glass");
        ThemePalette palette = ThemePalette.named(theme);
        int text = palette.ink;
        int muted = palette.muted;
        int accent = palette.accent;
        int line = palette.line;

        root.removeAllViews();
        root.setBackgroundColor(palette.background);
        root.setOnClickListener(null);
        View content = activity.findViewById(android.R.id.content);
        if (content != null) content.setOnClickListener(null);

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(activity, 48), dp(activity, 31), dp(activity, 48), dp(activity, 30));
        card.setBackground(shape(activity, palette.surface, line, 24));
        card.setElevation(dp(activity, 3));
        FrameLayout.LayoutParams cardLayout = new FrameLayout.LayoutParams(-1, -1);
        int margin = dp(activity, 18);
        cardLayout.setMargins(margin, margin, margin, margin);
        root.addView(card, cardLayout);

        Locale locale = it ? Locale.ITALIAN : Locale.UK;
        String date = new SimpleDateFormat("EEEE d MMMM", locale).format(new Date());
        TextView label = label(activity, it ? "METEO" : "WEATHER", accent, 18, true);
        label.setLetterSpacing(.16f);
        card.addView(label);
        TextView city = label(activity, extra(intent, "EXTRA_CITY", it ? "Località sconosciuta" : "Unknown location"), text, 50, true);
        city.setMaxLines(2);
        LinearLayout.LayoutParams title = new LinearLayout.LayoutParams(-1, -2);
        title.setMarginEnd(dp(activity, 64));
        card.addView(city, title);
        card.addView(label(activity, date, muted, 23, false));
        View divider = new View(activity);
        divider.setBackgroundColor(line);
        LinearLayout.LayoutParams rule = new LinearLayout.LayoutParams(-1, dp(activity, 1));
        rule.setMargins(0, dp(activity, 23), 0, dp(activity, 10));
        card.addView(divider, rule);

        LinearLayout reading = new LinearLayout(activity);
        reading.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(reading, new LinearLayout.LayoutParams(-1, 0, 1f));
        String icon = extra(intent, "EXTRA_ICON", "01d");
        WeatherIcon glyph = new WeatherIcon(activity, icon, accent);
        glyph.setBackground(shape(activity, palette.tint, palette.tint, 30));
        reading.addView(glyph, new LinearLayout.LayoutParams(dp(activity, 156), dp(activity, 156)));
        LinearLayout values = new LinearLayout(activity);
        values.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams valueLayout = new LinearLayout.LayoutParams(0, -2, 1);
        valueLayout.setMarginStart(dp(activity, 40));
        reading.addView(values, valueLayout);
        TextView temp = label(activity, extra(intent, "EXTRA_TEMP", "–") + " °C", text, 86, true);
        temp.setIncludeFontPadding(false);
        values.addView(temp);
        TextView conditions = label(activity, extra(intent, "EXTRA_DESC", it ? "Dati non disponibili" : "No data"), muted, 30, false);
        values.addView(conditions);

        TextView close = label(activity, "×", text, 30, false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription(it ? "Chiudi meteo" : "Close weather");
        close.setBackground(new RippleDrawable(ColorStateList.valueOf(line), shape(activity, palette.surface, line, 24), null));
        FrameLayout.LayoutParams closeLayout = new FrameLayout.LayoutParams(dp(activity, 54), dp(activity, 54), Gravity.TOP | Gravity.END);
        closeLayout.setMargins(dp(activity, 34), dp(activity, 34), dp(activity, 34), dp(activity, 34));
        close.setElevation(dp(activity, 4));
        root.addView(close, closeLayout);
        close.setOnClickListener(view -> activity.finish());
    }

    private static TextView label(Activity activity, String value, int color, int size, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private static GradientDrawable shape(Context context, int color, int border, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(context, radius));
        shape.setStroke(dp(context, 1), border);
        return shape;
    }

    private static int dp(Context context, int size) {
        return Math.round(size * context.getResources().getDisplayMetrics().density);
    }

    private static final class WeatherIcon extends View {
        private final String icon;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        WeatherIcon(Context context, String icon, int color) {
            super(context);
            this.icon = icon;
            paint.setColor(color);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(3);
        }

        @Override protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.scale(getWidth() / 88f, getHeight() / 88f);
            if (icon.startsWith("01") && icon.endsWith("n")) {
                canvas.drawArc(24, 22, 66, 64, -30, 255, false, paint);
            } else if (icon.startsWith("01")) {
                canvas.drawCircle(44, 44, 13, paint);
                for (int i = 0; i < 8; i++) {
                    canvas.drawLine(44, 20, 44, 25, paint);
                    canvas.rotate(45, 44, 44);
                }
            } else if (icon.startsWith("50")) {
                canvas.drawLine(24, 35, 60, 35, paint);
                canvas.drawLine(31, 45, 65, 45, paint);
                canvas.drawLine(24, 55, 55, 55, paint);
            } else {
                canvas.drawRoundRect(20, 39, 68, 56, 8, 8, paint);
                canvas.drawCircle(33, 39, 10, paint);
                canvas.drawCircle(48, 34, 13, paint);
                if (icon.startsWith("09") || icon.startsWith("10") || icon.startsWith("11")) {
                    for (int x = 33; x <= 59; x += 13) canvas.drawLine(x, 61, x - 4, 68, paint);
                } else if (icon.startsWith("13")) {
                    for (int x = 31; x <= 57; x += 13) canvas.drawCircle(x, 64, 2, paint);
                }
            }
            canvas.restore();
        }
    }
}
