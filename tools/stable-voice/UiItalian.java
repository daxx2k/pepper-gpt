package com.softbankrobotics.pepper.pepperGPT;

import android.app.Activity;
import android.content.Context;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class UiItalian {
    private static Context application;
    private static final Map<String,String> LABELS = new HashMap<>();
    static {
        LABELS.put("Realtime timed out. Tap Start Listening to try again.", "Realtime non risponde. Tocca Inizia ad ascoltare per riprovare.");
        LABELS.put("Start Listening", "Inizia ad ascoltare");
        LABELS.put("Stop Listening", "Smetti di ascoltare");
        LABELS.put("Settings", "Impostazioni");
        LABELS.put("Help", "Guida");
        LABELS.put("Clear History", "Cancella cronologia");
        LABELS.put("Close", "Chiudi");
        LABELS.put("Cancel", "Annulla");
        LABELS.put("Save", "Salva");
        LABELS.put("Done", "Fatto");
        LABELS.put("Yes", "Sì");
        LABELS.put("No", "No");
        LABELS.put("OK", "OK");
        LABELS.put("Clear", "Cancella");
        LABELS.put("Reset", "Ripristina");
        LABELS.put("Delete", "Elimina");
        LABELS.put("Back", "Indietro");
        LABELS.put("Voice", "Voce");
        LABELS.put("Voice: ", "Voce: ");
        LABELS.put("Pepper voice", "Voce di Pepper");
        LABELS.put("OpenAI Coral — AI voice, online", "OpenAI Coral — voce AI online");
        LABELS.put("OpenAI Marin — AI voice, online", "OpenAI Marin — voce AI online");
        LABELS.put("OpenAI API Key", "Chiave API OpenAI");
        LABELS.put("OpenWeather API Key", "Chiave API OpenWeather");
        LABELS.put("Optional Pepper SSH connection (restart app after changes)", "Connessione SSH facoltativa a Pepper (riavvia dopo le modifiche)");
        LABELS.put("Pepper head address", "Indirizzo della testa di Pepper");
        LABELS.put("SSH username", "Nome utente SSH");
        LABELS.put("SSH password", "Password SSH");
        LABELS.put("Verified SSH known_hosts entry", "Voce verificata di SSH known_hosts");
        LABELS.put("Default City (for weather/time)", "Città predefinita per meteo e ora");
        LABELS.put("e.g. London, New York, Tokyo...", "es. Roma, Londra, Tokyo…");
        LABELS.put("Show keys", "Mostra chiavi");
        LABELS.put("Hide keys", "Nascondi chiavi");
        LABELS.put("Personality Preset", "Personalità predefinita");
        LABELS.put("Edit Pepper’s Personality (System Prompt)", "Modifica la personalità di Pepper (istruzioni di sistema)");
        LABELS.put("Edit Personality", "Modifica personalità");
        LABELS.put("System prompt text...", "Istruzioni di sistema…");
        LABELS.put("System prompt text for Pepper...", "Istruzioni di sistema per Pepper…");
        LABELS.put("Open Fullscreen Editor", "Apri editor a schermo intero");
        LABELS.put("Reset Personality", "Ripristina personalità");
        LABELS.put("Clear API Keys", "Cancella chiavi API");
        LABELS.put("Settings Saved", "Impostazioni salvate");
        LABELS.put("Verifying...", "Verifica in corso…");
        LABELS.put("Invalid OpenAI Key!", "Chiave OpenAI non valida.");
        LABELS.put("Using built-in Default Key", "È in uso la chiave predefinita");
        LABELS.put("Keys cleared (using defaults)", "Chiavi cancellate. Sono in uso le impostazioni predefinite.");
        LABELS.put("Failed to load personas", "Impossibile caricare le personalità");
        LABELS.put("Chat model", "Modello per la chat");
        LABELS.put("Stories and recipes model", "Modello per storie e ricette");
        LABELS.put("Image analysis model", "Modello per analizzare le immagini");
        LABELS.put("Restore defaults", "Ripristina predefiniti");
        LABELS.put("Enter a model ID without spaces", "Inserisci l’identificativo del modello senza spazi");
        LABELS.put("Enter an exact OpenAI model ID compatible with Chat Completions. Image analysis also requires image input support. Availability and cost depend on your API account. Voice and generated images use separate models.", "Inserisci l’identificativo esatto di un modello OpenAI compatibile con Chat Completions. Per analizzare le immagini serve anche il supporto per immagini in ingresso. Disponibilità e costi dipendono dal tuo account API. Voce e generazione di immagini usano modelli separati.");
        LABELS.put("Conversation language", "Lingua di PepperGPT");
        LABELS.put("Conversation language: Italian", "Lingua di PepperGPT: italiano");
        LABELS.put("Conversation language: English", "Lingua di PepperGPT: inglese");
        LABELS.put("Decrease voice volume", "Abbassa il volume della voce");
        LABELS.put("Increase voice volume", "Alza il volume della voce");
        LABELS.put("Voice volume: ", "Volume della voce: ");
        LABELS.put(" percent", " per cento");
        LABELS.put("Clock", "Ora");
        LABELS.put("Clock status", "Stato del servizio orario");
        LABELS.put("Wi-Fi status", "Stato del Wi-Fi");
        LABELS.put("OpenAI status", "Stato di OpenAI");
        LABELS.put("Camera feedback", "Stato della fotocamera");
        LABELS.put("Processing...", "Elaborazione in corso…");
        LABELS.put("Thinking...", "Sto pensando…");
        LABELS.put("Loading...", "Caricamento…");
        LABELS.put("Connecting...", "Connessione in corso…");
        LABELS.put("Almost there...", "Ci sono quasi…");
        LABELS.put("Bear with me...", "Un attimo…");
        LABELS.put("Crunching the numbers...", "Sto facendo i conti…");
        LABELS.put("Let me figure this out...", "Ci sto ragionando…");
        LABELS.put("Let me ponder...", "Fammi pensare…");
        LABELS.put("One moment...", "Un momento…");
        LABELS.put("Working on it...", "Ci sto lavorando…");
        LABELS.put("Theme: Glassmorphism", "Tema: Vetro");
        LABELS.put("Theme: Sleek Dark", "Tema: Scuro");
        LABELS.put("Theme: Soft Friendly", "Tema: Tenue");
        LABELS.put("Generated image", "Immagine generata");
        LABELS.put("Fullscreen image", "Immagine a schermo intero");
        LABELS.put("Storybook placeholder", "Spazio per l’illustrazione");
        LABELS.put("Story illustration", "Illustrazione della storia");
        LABELS.put("Illustration loading...", "Caricamento dell’illustrazione…");
        LABELS.put("Once upon a time...", "C’era una volta…");
        LABELS.put("Illustration unavailable", "Illustrazione non disponibile");
        LABELS.put("Painting scene ", "Sto illustrando la scena ");
        LABELS.put("Painting scene 1...", "Sto illustrando la prima scena…");
        LABELS.put("Painting your illustration...", "Sto preparando l’illustrazione…");
        LABELS.put("Plating your dish...", "Sto impiattando…");
        LABELS.put("No illustration — enjoy the story!", "Illustrazione non disponibile. Buona lettura!");
        LABELS.put("No illustration — enjoy the recipe!", "Illustrazione non disponibile. Buon appetito!");
        LABELS.put("Story Mode", "Modalità storia");
        LABELS.put("Recipe Mode", "Modalità ricetta");
        LABELS.put("Immersive Mode", "Modalità immersiva");
        LABELS.put("Story generation failed", "Impossibile generare la storia");
        LABELS.put("Recipe generation failed", "Impossibile generare la ricetta");
        LABELS.put("Stop Music", "Ferma la musica");
        LABELS.put("Changing Station...", "Cambio di stazione…");
        LABELS.put("Unknown Station", "Stazione sconosciuta");
        LABELS.put("Error: No URL provided.", "Indirizzo della stazione mancante.");
        LABELS.put("Playing", "In riproduzione");
        LABELS.put("Playing ", "In riproduzione: ");
        LABELS.put("Stopping the music.", "Fermo la musica.");
        LABELS.put("🕺 Dance: ON", "🕺 Ballo: SÌ");
        LABELS.put("🕺 Dance: OFF", "🕺 Ballo: NO");
        LABELS.put("Weather Background", "Sfondo del meteo");
        LABELS.put("City Name", "Nome della città");
        LABELS.put("Partly Cloudy", "Parzialmente nuvoloso");
        LABELS.put("Unknown", "Sconosciuto");
        LABELS.put("No data", "Dati non disponibili");
        LABELS.put("The weather in ", "Il meteo a ");
        LABELS.put("User message", "Messaggio dell’utente");
        LABELS.put("Robot message", "Messaggio di Pepper");
        LABELS.put("Wait until Pepper is connected.", "Attendi che Pepper si connetta.");
        LABELS.put("Enter your OpenAI key in Settings.", "Inserisci la chiave OpenAI nelle impostazioni.");
        LABELS.put("Could not open Realtime. Check Settings.", "Impossibile avviare Realtime. Controlla le impostazioni.");
        LABELS.put("Pepper is waiting for the robot connection", "Pepper è in attesa della connessione al robot");
        LABELS.put("Here’s the image I generated for you!", "Ecco l’immagine che ho creato per te!");
        LABELS.put("Here's the image I generated for you!", "Ecco l’immagine che ho creato per te!");
        LABELS.put("Sorry, I couldn't generate that image.", "Non sono riuscita a generare l’immagine.");
        LABELS.put("I encountered an error: ", "Si è verificato un errore: ");
        LABELS.put("Photo Error: ", "Errore della foto: ");
        LABELS.put("Unknown error", "Errore sconosciuto");
        LABELS.put("clear sky", "Sereno");
        LABELS.put("few clouds", "Poco nuvoloso");
        LABELS.put("scattered clouds", "Nubi sparse");
        LABELS.put("broken clouds", "Nuvoloso");
        LABELS.put("overcast clouds", "Coperto");
        LABELS.put("light rain", "Pioggia leggera");
        LABELS.put("moderate rain", "Pioggia moderata");
        LABELS.put("heavy intensity rain", "Pioggia intensa");
        LABELS.put("very heavy rain", "Pioggia molto intensa");
        LABELS.put("freezing rain", "Pioggia gelata");
        LABELS.put("light intensity shower rain", "Rovesci leggeri");
        LABELS.put("shower rain", "Rovesci");
        LABELS.put("heavy intensity shower rain", "Rovesci intensi");
        LABELS.put("thunderstorm", "Temporale");
        LABELS.put("thunderstorm with light rain", "Temporale con pioggia leggera");
        LABELS.put("thunderstorm with rain", "Temporale con pioggia");
        LABELS.put("thunderstorm with heavy rain", "Temporale con pioggia intensa");
        LABELS.put("drizzle", "Pioviggine");
        LABELS.put("light intensity drizzle", "Pioviggine leggera");
        LABELS.put("snow", "Neve");
        LABELS.put("light snow", "Neve leggera");
        LABELS.put("heavy snow", "Nevicata intensa");
        LABELS.put("sleet", "Nevischio");
        LABELS.put("mist", "Foschia");
        LABELS.put("fog", "Nebbia");
        LABELS.put("haze", "Foschia");
        LABELS.put("smoke", "Fumo");
        LABELS.put("dust", "Polvere");
        LABELS.put("sand", "Sabbia");
        LABELS.put("tornado", "Tornado");
        LABELS.put("Could not start the Realtime reply.", "Impossibile avviare la risposta Realtime.");
        LABELS.put("Could not resume listening.", "Impossibile riprendere l’ascolto.");
        LABELS.put("Could not play Realtime audio.", "Impossibile riprodurre l’audio Realtime.");
        LABELS.put("Realtime reply did not complete.", "La risposta Realtime è stata interrotta.");
        LABELS.put("Realtime audio buffer overflow.", "Il buffer audio Realtime è pieno.");
        LABELS.put("Invalid Realtime reply.", "Risposta Realtime non valida.");
        LABELS.put("Could not cancel story narration", "Impossibile interrompere la narrazione");
        LABELS.put("Sorry, recipe generation failed: ", "Non sono riuscita a generare la ricetta: ");
        LABELS.put("Sorry, I couldn't generate a recipe. ", "Non sono riuscita a generare la ricetta. ");
        LABELS.put("How to Use PepperGPT", "Come usare PepperGPT");
        LABELS.put("PepperGPT turns Pepper into a smart, friendly conversational robot powered by AI. Here is everything you can do:", "PepperGPT permette a Pepper di conversare con te e di usare le funzioni descritte qui sotto.");
        LABELS.put("🎙️  Talking to Pepper", "🎙️  Parlare con Pepper");
        LABELS.put("1. Tap the big Start Listening button at the bottom.\n2. Speak naturally — Pepper will listen until you pause.\n3. Pepper transcribes your speech, thinks of a response, and speaks back!\n4. Tap Stop Listening to stop, or Pepper will keep listening for more.", "1. Tocca Inizia ad ascoltare.\n2. Parla normalmente. Pepper capisce quando fai una pausa.\n3. Pepper trascrive quello che dici e ti risponde a voce.\n4. Tocca Smetti di ascoltare per concludere la conversazione.");
        LABELS.put("🌦️  Weather and Time", "🌦️  Meteo e ora");
        LABELS.put("Ask Pepper about the weather or time:\n\n• What is the weather in Paris?\n• How hot is it in Tokyo?\n• What time is it in New York?\n• What is the weather? (uses your default city)\n\nYou can set your default city in Settings.", "Chiedi a Pepper che tempo fa o che ore sono.\n\n• Che tempo fa a Parigi?\n• Quanti gradi ci sono a Tokyo?\n• Che ore sono a New York?\n• Che tempo fa? (usa la città predefinita)\n\nPuoi scegliere la città nelle impostazioni.");
        LABELS.put("🖼️  Image Generation", "🖼️  Generare immagini");
        LABELS.put("Paint me a beautiful landscape\n\nThe image will appear right in the conversation!", "Prova a dire «Disegnami un bel paesaggio». L’immagine comparirà nella conversazione.");
        LABELS.put("📖  Immersive Stories & Recipes", "📖  Storie e ricette illustrate");
        LABELS.put("When Pepper tells a story or gives a recipe:\n\n• Words highlight automatically as Pepper speaks them.\n• The text will scroll on its own to keep up with the story.\n• After it ends, the conversation resumes automatically.\n• You can click on previous activity bubbles to review them!", "Quando Pepper racconta una storia, il testo segue la narrazione e le illustrazioni appaiono accanto. Puoi riaprire storie e ricette toccando i messaggi nella cronologia.");
        LABELS.put("🎵  Radio & Music", "🎵  Radio e musica");
        LABELS.put("Pepper can play music for you!\n\n• 'Play music' or 'Play radio' (plays Synthwave)\n• 'Play pop' (plays Top 40 hits)\n• 'Play synthwave' (plays Nightride FM)\n• 'Stop music' to end playback.\n\nA music note 🎵 appears at the top when playing.", "Chiedi a Pepper di mettere la radio, della musica pop o della synthwave. Per spegnerla, dì «Ferma la musica».");
        LABELS.put("✋  Touch Controls", "✋  Comandi al tocco");
        LABELS.put("• Touch Pepper's head to stop speaking immediately.\n• Touch either hand to interrupt speech.\n• These are useful if Pepper gives a long answer!", "Tocca la testa o il dorso di una mano per interrompere Pepper mentre parla.");
        LABELS.put("🟢  Status Icons", "🟢  Indicatori di stato");
        LABELS.put("At the top of the screen you will see three indicators:\n\n• Wi-Fi — Green means connected to the internet.\n• AI — Green means your OpenAI key is configured.\n• Clock — Green means time services are available.\n\nIf any icon is grey or red, check your network or settings.", "In alto trovi gli indicatori di rete Wi-Fi, OpenAI e ora. Il verde indica che il servizio è disponibile.");
        LABELS.put("⚙️  Settings", "⚙️  Impostazioni");
        LABELS.put("Open Settings from the menu (⋮) to:\n\n• Set your own OpenAI API key\n• Set your own OpenWeather API key\n• Change the default city for weather and time\n• Customize Pepper's personality prompt\n\nThere are no built-in API keys. Enter your own keys to enable AI and weather.", "Apri il menu per inserire le chiavi API, scegliere la città, cambiare la voce e personalizzare Pepper.");
        LABELS.put("💡  Tips", "💡  Consigli");
        LABELS.put("• Speak clearly and at a normal pace.\n• Wait for a brief pause after speaking — Pepper detects silence to know you are done.\n• Pepper remembers your conversation, so you can have follow-up questions!\n• Chat history is saved and restored when you reopen the app.", "Parla a un ritmo normale e fai una breve pausa alla fine della frase. Pepper tiene conto dei messaggi precedenti e conserva la cronologia quando riapri l’app.");
        LABELS.put("Made with ❤️ for Pepper", "Fatto con ❤️ per Pepper");
    }

    public static void attach(Activity activity) {
        application = activity.getApplicationContext();
    }

    public static String text(String original) {
        if (original == null || !italian()) return original;
        String translation = LABELS.get(original);
        if (translation == null) translation = LABELS.get(original.trim());
        if (translation == null) translation = LABELS.get(original.trim().toLowerCase(Locale.ROOT));
        return translation == null ? original : translation;
    }

    private static boolean italian() {
        return application != null && "it".equals(application
                .getSharedPreferences("PepperGPT_Prefs", 0).getString("app_language", "en"));
    }

    public static Locale dateLocale() { return italian() ? Locale.ITALY : Locale.ENGLISH; }

    public static void screen(Activity activity) {
        if (!italian()) return;
        activity.getWindow().getDecorView().post(() -> {
            if (!activity.isDestroyed()) translate(activity.getWindow().getDecorView());
        });
    }

    private static void translate(View view) {
        CharSequence description = view.getContentDescription();
        if (description != null) view.setContentDescription(text(description.toString()));
        if (view instanceof TextView) {
            TextView label = (TextView)view;
            if (!(view instanceof android.widget.EditText)) {
                String before = label.getText().toString(), after = text(before);
                if (!before.equals(after)) label.setText(after);
            }
            CharSequence hint = label.getHint();
            if (hint != null) label.setHint(text(hint.toString()));
        }
        if (view instanceof ViewGroup && !(view.getClass().getName().contains("RecyclerView"))) {
            ViewGroup group = (ViewGroup)view;
            for (int i=0; i<group.getChildCount(); i++) translate(group.getChildAt(i));
        }
    }

    public static void menu(Menu menu) {
        if (!italian()) return;
        for (int i=0; i<menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (item.getTitle() != null) item.setTitle(text(item.getTitle().toString()));
            if (item.hasSubMenu()) menu(item.getSubMenu());
        }
    }
}
