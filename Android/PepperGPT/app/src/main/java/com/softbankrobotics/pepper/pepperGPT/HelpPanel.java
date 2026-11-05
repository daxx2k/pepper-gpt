package com.softbankrobotics.pepper.pepperGPT;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class HelpPanel {
    public static void render(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences("PepperGPT_Prefs", 0);
        boolean it = "it".equals(WeatherPanel.language(activity));
        ThemePalette theme = ThemePalette.named(prefs.getString("ui_theme", "glass"));
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(theme.background);

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(activity, 26), dp(activity, 12), dp(activity, 26), dp(activity, 8));
        page.addView(header, new LinearLayout.LayoutParams(-1, -2));
        TextView heading = label(activity, it ? "Guida a PepperGPT" : "PepperGPT guide", theme.ink, 28, true);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));
        Button close = new Button(activity);
        close.setText(it ? "Torna alla chat" : "Back to chat");
        close.setAllCaps(false);
        close.setTextColor(theme.ink);
        close.setTextSize(16);
        close.setBackground(card(activity, theme));
        close.setPadding(dp(activity, 16), 0, dp(activity, 16), 0);
        close.setOnClickListener(view -> activity.finish());
        header.addView(close, new LinearLayout.LayoutParams(-2, dp(activity, 48)));

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 26), dp(activity, 8), dp(activity, 26), dp(activity, 22));
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, -2));

        String[][] sections = it ? new String[][] {
            {"Parla con Pepper", "Premi Ascolta, parla normalmente e fai una pausa quando hai finito. La barra ti mostra quando Pepper ascolta, pensa e risponde. Premi Stop per chiudere l’ascolto. Per interrompere una risposta, tocca la testa o il dorso di una mano."},
            {"Storie, ricette e meteo", "Prova «Raccontami una storia su una volpe», «Come si fa la pasta al pomodoro?» o «Che tempo fa a Londra?». I pannelli si chiudono cinque secondi dopo che Pepper ha finito di parlare. Puoi chiuderli prima con la X o riaprirli dal messaggio nella chat."},
            {"Vista e immagini", "Tieni un oggetto davanti a Pepper e chiedi «Che cosa ho in mano?». Quando serve per rispondere, Pepper scatta una foto e la mostra nella chat. Puoi anche chiederle di disegnare un’immagine o trasformare una foto."},
            {"Memoria locale", "Prova «Ricorda che mi piace il jazz» oppure «Dimentica che mi piace il jazz». Pepper conserva questi ricordi sul tablet."},
            {"Radio e ora", "Prova «Metti un po’ di musica» o «Che ore sono a Tokyo?». Per la radio puoi scegliere una stazione e regolare il volume dal pannello."},
            {"Stato e cronologia", "In alto trovi gli indicatori Wi-Fi, IA, ora e batteria. La chat viene salvata sul tablet e ricompare alla riapertura."},
            {"Lingua, voce e impostazioni", "La bandiera cambia lingua della conversazione e dell’app. I pulsanti − e + accanto alla bandiera regolano il volume della voce. Dal menu puoi cambiare tema e aprire le impostazioni per chiavi API, città predefinita, personalità, voce e modelli. Marin e Coral sono voci generate dall’IA."}
        } : new String[][] {
            {"Talk to Pepper", "Tap Listen, speak naturally, then pause when you’re done. The bar shows when Pepper is listening, thinking or replying. Tap Stop to end listening. To interrupt a reply, touch her head or the back of one hand."},
            {"Stories, recipes and weather", "Try “Tell me a story about a fox”, “How do I make tomato pasta?” or “What’s the weather in London?”. The panels close five seconds after Pepper finishes speaking. Use the X to close sooner, or tap the message in chat to reopen a panel."},
            {"Vision and images", "Hold an object in front of Pepper and ask “What am I holding?”. When a photo helps answer your question, Pepper takes one and shows it in chat. You can also ask her to draw an image or transform a photo."},
            {"Local memory", "Try “Remember that I like jazz” or “Forget that I like jazz”. Pepper stores these memories on her tablet."},
            {"Radio and time", "Try “Put on some music” or “What time is it in Tokyo?”. Choose a station and adjust radio volume from the radio panel."},
            {"Status and history", "Wi-Fi, AI, time and battery indicators are at the top. Chat history is saved on the tablet and restored when you reopen the app."},
            {"Language, voice and settings", "Use the flag to switch the conversation and app language. The − and + buttons next to it control voice volume. Open the menu to change themes or find settings for API keys, default city, personality, voice and models. Marin and Coral are AI-generated voices."}
        };
        for (String[] section : sections) {
            LinearLayout block = new LinearLayout(activity);
            block.setOrientation(LinearLayout.VERTICAL);
            block.setBackground(card(activity, theme));
            block.setPadding(dp(activity, 22), dp(activity, 17), dp(activity, 22), dp(activity, 17));
            LinearLayout.LayoutParams blockLayout = new LinearLayout.LayoutParams(-1, -2);
            blockLayout.bottomMargin = dp(activity, 12);
            content.addView(block, blockLayout);
            block.addView(label(activity, section[0], theme.accent, 20, true));
            TextView copy = label(activity, section[1], theme.ink, 17, false);
            copy.setPadding(0, dp(activity, 5), 0, 0);
            copy.setLineSpacing(dp(activity, 3), 1f);
            block.addView(copy);
        }
        activity.setContentView(page);
    }

    private static GradientDrawable card(Activity activity, ThemePalette theme) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(theme.surface);
        shape.setStroke(dp(activity, 1), theme.line);
        shape.setCornerRadius(dp(activity, 16));
        return shape;
    }

    private static TextView label(Activity activity, String text, int color, int size, boolean bold) {
        TextView label = new TextView(activity);
        label.setText(text);
        label.setTextColor(color);
        label.setTextSize(size);
        if (bold) label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return label;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
