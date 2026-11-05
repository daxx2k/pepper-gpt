package com.softbankrobotics.pepper.pepperGPT;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;
import java.util.regex.Pattern;
import org.json.JSONArray;

/** The same station catalogue as the published Neon FM player. */
public final class RadioStations {
    public static final class Station {
        public final String id, name, url, genre;

        private Station(String id, String name, String url, String genre) {
            this.id = id;
            this.name = name;
            this.url = url;
            this.genre = genre;
        }
    }

    public interface Selection {
        void select(Station station);
    }

    private static final Station[] STATIONS = {
        night("nightride", "Nightride FM", "Synthwave / Outrun"),
        night("chillsynth", "Chillsynth", "Dreamwave / Ambient"),
        night("datawave", "Datawave", "Vaporwave / Future Funk"),
        night("spacesynth", "Spacesynth", "Space Disco / Sci-Fi"),
        night("darksynth", "Darksynth", "Cyberpunk / Darksynth"),
        night("ebsm", "EBSM", "Industrial / Cyberpunk"),
        night("horrorsynth", "Horrorsynth", "Horror Synth / Dark Wave"),
        night("rektory", "Rektory Radio", "Electro Swing / Vintage 80s"),
        new Station("record-synth", "Record Synthwave", "http://hls-01-radiorecord.hostingradio.ru/record-synth/playlist.m3u8", "Electronic / Retrowave"),
        new Station("simulator", "Simulator Radio", "http://simulatorradio.stream/stream.mp3", "80s Mix / Highway Vibes"),
        new Station("staddenhaag", "Radio Stad Den Haag", "https://stream.radiostaddenhaag.com/live", "Italo Disco / 80s Euro"),
        new Station("plazaone", "Nightwave Plaza", "http://radio.plaza.one/mp3", "Vaporwave / Nostalgia")
    };

    private static Station night(String id, String name, String genre) {
        return new Station(id, name, "http://stream.nightride.fm/" + id + ".mp3", genre);
    }

    static JSONArray ids() {
        JSONArray ids = new JSONArray().put("default");
        for (Station station : STATIONS) ids.put(station.id);
        return ids;
    }

    static String choices() {
        StringBuilder description = new StringBuilder();
        for (Station station : STATIONS) {
            if (description.length() > 0) description.append("; ");
            description.append(station.id).append(" = ").append(station.name).append(" (").append(station.genre).append(')');
        }
        return description.toString();
    }

    static boolean known(String id) {
        if ("default".equals(id)) return true;
        for (Station station : STATIONS) if (station.id.equals(id)) return true;
        return false;
    }

    static boolean mentionsStation(String text) { return namedIn(text) != null; }

    public static Station resolve(String text) {
        Station match = namedIn(text);
        return match == null ? STATIONS[0] : match;
    }

    private static Station namedIn(String text) {
        if (text == null) return null;
        String lower = text.toLowerCase(Locale.ROOT);
        for (Station station : STATIONS) {
            if (word(lower, station.id) || word(lower, station.name.toLowerCase(Locale.ROOT))) return station;
        }
        return null;
    }

    private static boolean word(String text, String phrase) {
        return Pattern.compile("\\b" + Pattern.quote(phrase) + "\\b").matcher(text).find();
    }

    public static int visualizerColor(Context context) {
        return palette(context).accent;
    }

    public static void showStatus(TextView view, String state) {
        boolean it = "it".equals(WeatherPanel.language(view.getContext()));
        if (state.startsWith("Playing")) state = it ? "In riproduzione" : "Playing";
        else if (state.startsWith("Buffering")) state = it ? "Caricamento…" : "Buffering…";
        else if (state.startsWith("Connecting")) state = it ? "Connessione…" : "Connecting…";
        else if (it && state.startsWith("Error:")) state = "Errore radio" + state.substring(5);
        view.setText(state);
        RadioVisualizerScreen.playback(view, playingLabel(state));
    }

    public static boolean playingLabel(CharSequence state) {
        String text = state.toString();
        return text.startsWith("Playing") || text.startsWith("In riproduzione");
    }

    private static ThemePalette palette(Context context) {
        return ThemePalette.named(context.getSharedPreferences("PepperGPT_Prefs", 0).getString("ui_theme", "glass"));
    }

    public static void install(Activity activity, Selection selection) {
        ThemePalette theme = palette(activity);
        LinearLayout stations = buildPanel(activity, theme);
        if (stations == null) return;
        Button[] buttons = new Button[STATIONS.length];
        String currentUrl = activity.getIntent().getStringExtra("EXTRA_URL");
        for (int start = 0; start < STATIONS.length; start += 2) {
            LinearLayout row = new LinearLayout(activity);
            row.setPadding(0, dp(activity, 4), 0, dp(activity, 3));
            stations.addView(row, new LinearLayout.LayoutParams(-1, -2));
            for (int i = start; i < start + 2 && i < STATIONS.length; i++) {
                Station station = STATIONS[i];
                Button button = new Button(activity);
                button.setTag(station);
                button.setAllCaps(false);
                button.setText(station.name);
                button.setTextSize(15);
                button.setMaxLines(2);
                button.setMinWidth(0);
                button.setMinimumWidth(0);
                button.setStateListAnimator(null);
                button.setContentDescription(station.name + ", " + station.genre);
                buttons[i] = button;
                styleButton(button, theme, station.url.equals(currentUrl));
                button.setOnClickListener(ignored -> {
                    for (Button other : buttons) styleButton(other, theme, other == button);
                    selection.select(station);
                });
                LinearLayout.LayoutParams item = new LinearLayout.LayoutParams(0, dp(activity, 45), 1f);
                item.setMargins(dp(activity, 4), 0, dp(activity, 4), 0);
                row.addView(button, item);
            }
        }
    }

    private static LinearLayout buildPanel(Activity activity, ThemePalette theme) {
        View root = view(activity, "radioRoot");
        View now = view(activity, "stationName");
        View status = view(activity, "statusText");
        View visualizer = view(activity, "visualizerView");
        View level = view(activity, "volLevel");
        View stop = view(activity, "stopButton");
        View dance = view(activity, "btnDanceToggle");
        if (!(root instanceof LinearLayout) || !(now instanceof TextView) || !(status instanceof TextView)
                || visualizer == null || level == null || stop == null || dance == null) return null;
        LinearLayout container = (LinearLayout)root;
        if ("neon-radio-panel".equals(container.getTag())) return null;
        if (!(level.getParent() instanceof LinearLayout) || !(stop.getParent() instanceof LinearLayout)) return null;
        LinearLayout volume = (LinearLayout)level.getParent();
        LinearLayout actions = (LinearLayout)stop.getParent();
        for (View item : new View[]{now, status, visualizer, volume, actions}) detach(item);
        container.removeAllViews();
        container.setTag("neon-radio-panel");
        container.setGravity(Gravity.CENTER_VERTICAL);
        container.setPadding(dp(activity, 22), dp(activity, 18), dp(activity, 22), dp(activity, 18));

        boolean it = "it".equals(WeatherPanel.language(activity));
        container.addView(heading(activity, "Neon FM", 30, theme.ink));
        TextView subtitle = heading(activity, it ? "RADIO IN DIRETTA" : "LIVE RADIO", 13, theme.accent);
        subtitle.setLetterSpacing(.15f);
        container.addView(subtitle);
        LinearLayout panels = new LinearLayout(activity);
        panels.setBaselineAligned(false);
        LinearLayout.LayoutParams panelRow = new LinearLayout.LayoutParams(-1, -2);
        panelRow.topMargin = dp(activity, 13);
        container.addView(panels, panelRow);
        LinearLayout player = panel(activity, theme);
        LinearLayout.LayoutParams playerSize = new LinearLayout.LayoutParams(0, -1, .85f);
        playerSize.setMarginEnd(dp(activity, 14));
        panels.addView(player, playerSize);
        LinearLayout stations = panel(activity, theme);
        panels.addView(stations, new LinearLayout.LayoutParams(0, -2, 1.35f));
        stations.addView(heading(activity, it ? "Stazioni" : "Stations", 21, theme.ink));

        player.addView(heading(activity, it ? "IN ASCOLTO" : "NOW PLAYING", 13, theme.accent));
        TextView stationName = (TextView)now;
        stationName.setTextSize(30);
        stationName.setGravity(Gravity.START);
        stationName.setMaxLines(2);
        player.addView(now, new LinearLayout.LayoutParams(-1, -2));
        TextView stationStatus = (TextView)status;
        stationStatus.setTextSize(16);
        stationStatus.setGravity(Gravity.START);
        player.addView(status, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams visualizerSize = new LinearLayout.LayoutParams(-1, dp(activity, 100));
        visualizerSize.setMargins(0, dp(activity, 10), 0, dp(activity, 10));
        player.addView(visualizer, visualizerSize);
        volume.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        player.addView(volume, new LinearLayout.LayoutParams(-1, -2));
        actions.setGravity(Gravity.CENTER_VERTICAL);
        if (stop instanceof Button) {
            ((Button)stop).setAllCaps(false);
            ((Button)stop).setText("Stop radio");
        }
        for (View action : new View[]{dance, stop}) {
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, dp(activity, 48), 1f);
            size.setMargins(dp(activity, 3), 0, dp(activity, 3), 0);
            action.setLayoutParams(size);
        }
        LinearLayout.LayoutParams actionRow = new LinearLayout.LayoutParams(-1, -2);
        actionRow.topMargin = dp(activity, 14);
        player.addView(actions, actionRow);
        return stations;
    }

    private static void detach(View view) {
        if (view.getParent() instanceof ViewGroup) ((ViewGroup)view.getParent()).removeView(view);
    }

    private static TextView heading(Activity activity, String title, int size, int color) {
        TextView text = new TextView(activity);
        text.setText(title);
        text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        text.setTextSize(size);
        text.setTextColor(color);
        if (size == 13) text.setTag("radio-accent");
        return text;
    }

    private static LinearLayout panel(Activity activity, ThemePalette theme) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 16), dp(activity, 13), dp(activity, 16), dp(activity, 14));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(theme.surface);
        shape.setStroke(dp(activity, 1), theme.line);
        shape.setCornerRadius(dp(activity, 19));
        panel.setBackground(shape);
        return panel;
    }

    private static void styleButton(Button button, ThemePalette theme, boolean selected) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(selected ? theme.tint : theme.surface);
        shape.setStroke(dp(button.getContext(), 1), selected ? theme.accent : theme.line);
        shape.setCornerRadius(dp(button.getContext(), 12));
        button.setTextColor(selected ? theme.accent : theme.ink);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x447f9db1), shape, null));
        button.setSelected(selected);
    }

    private static View view(Activity activity, String id) {
        return activity.findViewById(activity.getResources().getIdentifier(id, "id", activity.getPackageName()));
    }

    private static int dp(Context context, int value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }
}
