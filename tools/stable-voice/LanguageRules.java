package com.softbankrobotics.pepper.pepperGPT;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Translate command cues only; leave the original user message and feature content intact. */
public final class LanguageRules {
    private LanguageRules() { }
    public static String route(String text) {
        String value = text.toLowerCase(Locale.ROOT).replace('’', '\'').replaceAll("[,!?]+", " ").replaceAll("\\s+", " ").trim();
        // Recognize the request verb and story noun with optional descriptive words between them.
        // The original prompt still reaches the story generator unchanged.
        Matcher storyRequest = Pattern.compile("\\b(?:(?:mi|ci)\\s+(?:racconti|racconteresti|racconterai|leggi|leggeresti)|raccontami|raccontaci|racconta|raccontate|raccontare|raccontarmi|raccontarci|leggimi|leggici|leggi|narrami|narra|scrivimi|scrivi)\\b[^.;:\\n]{0,120}?\\b(?:storia|favola|fiaba|racconto)\\b").matcher(value);
        if (storyRequest.find()) {
            return value.substring(0, storyRequest.start()) + "tell me a story" + value.substring(storyRequest.end());
        }
        Matcher storyWish = Pattern.compile("\\b(?:vorrei|voglio|mi piacerebbe ascoltare|mi piacerebbe sentire)\\s+(?:una?|qualche)\\s+(?:[\\p{L}]+\\s+){0,4}(?:storia|favola|fiaba|racconto)\\b").matcher(value);
        if (storyWish.find()) {
            return value.substring(0, storyWish.start()) + "tell me a story" + value.substring(storyWish.end());
        }
        // Feature cues accept intervening articles/adjectives; generators keep the raw Italian prompt.
        String translated = request(value, "(?:spegni|spegnere|ferma|fermare|interrompi|interrompere|stoppa|disattiva|disattivare|chiudi|chiudere)", "(?:radio|musica)", "stop radio");
        if (translated != null) return translated;
        translated = request(value, "(?:metti|mettimi|mettere|accendi|accendere|avvia|avviare|riproduci|riprodurre|(?:fammi|fai|farmi) (?:ascoltare|sentire)|fai partire|(?:vorrei|voglio) (?:ascoltare|sentire)|ascoltiamo)", "(?:radio|musica|pop|synthwave|nightride)", "play radio", true);
        if (translated != null) return translated;
        translated = request(value, "(?:(?:mi|ci) (?:dai|mostri|suggerisci|consigli)|dammi|consigliami|suggeriscimi|mostrami|vorrei|voglio|cerco|trovami|preparami|scrivimi)", "ricetta", "recipe for");
        if (translated != null) return translated.replaceFirst("recipe for\\s+(?:per|di)\\s+", "recipe for ");
        Matcher cooking = Pattern.compile("\\bcome\\s+(?:si\\s+)?(?:fa|fanno|prepara|preparano|cucina|cucinano|faccio|preparo|cucino|fare|preparare|cucinare)\\b").matcher(value);
        if (cooking.find()) return value.substring(0, cooking.start()) + "how to cook" + value.substring(cooking.end());
        Matcher ingredients = Pattern.compile("\\bingredienti\\s+(?:per|di|della?|delle?)\\b").matcher(value);
        if (ingredients.find()) return value.substring(0, ingredients.start()) + "ingredients for" + value.substring(ingredients.end());
        translated = request(value, "(?:scatta|scattami|scattarmi|fai|fammi|farmi|fare|scattare)", "(?:foto|fotografia)", "take a photo");
        if (translated != null) return translated;
        if (value.matches(".*\\b(?:fotografami|fotografarmi)\\b.*")) return "take a photo " + value;
        translated = request(value, "(?:crea|creami|creare|genera|generami|generare|fammi|fai|mostrami|mostrare|vorrei|voglio)", "(?:immagine|disegno|illustrazione|ritratto|foto)", "generate an image");
        if (translated != null) return translated;
        Matcher drawing = Pattern.compile("\\b(?:disegnami|disegna|disegnare|dipingimi|dipingi|dipingere|visualizza|illustra)\\b").matcher(value);
        if (drawing.find()) return value.substring(0, drawing.start()) + "draw me" + value.substring(drawing.end());
        Matcher transform = Pattern.compile("\\b(?:trasformami|trasformarmi|trasformaci)\\s+in\\b").matcher(value);
        if (transform.find()) return value.substring(0, transform.start()) + "transform me into" + value.substring(transform.end());
        Matcher imagine = Pattern.compile("\\b(?:immaginami|immaginarmi|immaginaci)\\s+(?:come|nei panni di)\\b").matcher(value);
        if (imagine.find()) return value.substring(0, imagine.start()) + "imagine me as" + value.substring(imagine.end());
        if (value.matches(".*\\b(?:come sarei|come mi vedresti|che aspetto avrei)\\b.*")) return "what would i look like " + value;
        String weather = weatherRoute(value);
        if (weather != null) return weather;
        // Explicit requests referring back to a story already mentioned in conversation.
        if (value.matches(".*\\b(?:raccontamela|raccontacela|raccontamene una|raccontacene una)\\b.*")) {
            return value.replaceFirst("\\b(?:raccontamela|raccontacela|raccontamene una|raccontacene una)\\b", "tell me a story");
        }
        String[][] cues = {
            {"raccontami una storia", "tell me a story"}, {"racconta una storia", "tell me a story"},
            {"raccontami una favola", "tell me a story"}, {"leggi una storia", "read me a story"},
            {"mi racconti una storia", "tell me a story"}, {"raccontarmi una storia", "tell me a story"},
            {"raccontami un racconto", "tell me a story"},
            {"una storia per favore", "story please"}, {"una favola per favore", "story please"},
            {"una storia", "tell me a story"}, {"una favola", "tell me a story"},
            {"una storia su", "story about"}, {"dammi una ricetta per", "recipe for"},
            {"mi dai una ricetta per", "recipe for"}, {"vorrei una ricetta per", "recipe for"},
            {"una ricetta per", "recipe for"}, {"una ricetta", "recipe for"},
            {"dammi una ricetta", "recipe for"}, {"ricetta per", "recipe for"}, {"ricetta di", "recipe for"},
            {"che tempo fa a", "weather in"}, {"che tempo fa in", "weather in"},
            {"com'è il tempo a", "weather in"}, {"meteo a", "weather in"}, {"meteo di", "weather in"},
            {"meteo per", "weather in"}, {"metti radio", "play radio"},
            {"che tempo fa oggi", "weather today"}, {"che tempo fa", "whats the weather"},
            {"spegni la radio", "stop radio"}, {"ferma la radio", "stop radio"},
            {"spegni la musica", "stop music"}, {"ferma la musica", "stop music"},
            {"accendi la radio", "play radio"}, {"metti la radio", "play radio"}, {"ascolta la radio", "play radio"},
            {"genera un'immagine", "generate an image"}, {"genera un’immagine", "generate an image"},
            {"crea un'immagine", "create an image"}, {"crea un’immagine", "create an image"},
            {"disegnami", "draw me"}, {"disegna una", "draw a"}, {"disegna un", "draw a"},
            {"scatta una foto", "take a photo"}, {"fammi una foto", "take a photo"},
            {"immaginami come", "imagine me as"}, {"trasformami in", "transform me into"}
        };
        for (String[] cue : cues) {
            int start = value.indexOf(cue[0]);
            if (start >= 0 && (cue[0].equals("una storia") || cue[0].equals("una favola") || cue[0].equals("una ricetta"))) {
                String prefix = value.substring(0, start).trim();
                if (!prefix.matches("(?:(?:ehi |ciao )?pepper\\s*)?(?:vorrei|voglio|puoi raccontarmi|raccontami|per favore|mi racconti|dammi|puoi darmi|mi dai|fammi|scrivimi|leggi|leggimi)?\\s*")) continue;
            }
            if (start >= 0) return value.substring(0, start) + cue[1] + value.substring(start + cue[0].length())
                    .replaceAll("\\blondra\\b", "london");
        }
        return text;
    }
    private static String request(String value, String verb, String noun, String english) {
        return request(value, verb, noun, english, false);
    }
    private static String request(String value, String verb, String noun, String english, boolean retainNoun) {
        Matcher match = Pattern.compile("\\b" + verb + "\\b[^.;:\\n]{0,80}?\\b(" + noun + ")\\b").matcher(value);
        if (!match.find()) return null;
        boolean retain = retainNoun && !match.group(1).equals("radio") && !match.group(1).equals("musica");
        return value.substring(0, match.start()) + english + (retain ? " " + match.group(1) : "") + value.substring(match.end());
    }
    private static String weatherRoute(String value) {
        Matcher marker = Pattern.compile("\\b(?:che tempo (?:fa|farà|c'è)|com'è il tempo|come è il tempo|(?:qual è|com'è|come è|quanti gradi è) la temperatura|quanti gradi (?:ci sono|fa)|che temperatura (?:c'è|fa)|che ore sono|che ora è|previsioni(?: del tempo| meteo)?|meteo)\\b").matcher(value);
        if (!marker.find()) return null;
        String cue = marker.group();
        // Bare mentions such as 'ho visto le previsioni' remain conversation.
        String prefix = value.substring(0, marker.start()).trim();
        if ((cue.startsWith("previsioni") || cue.equals("meteo")) && !prefix.matches("(?:(?:ehi |ciao )?pepper\\s*)?(?:(?:mi|ci) (?:dici|mostri|dai)|mostrami|dimmi|dammi|vorrei|voglio|puoi dirmi|puoi mostrarmi)?\\s*(?:il|le|delle)?\\s*")) return null;
        String tail = value.substring(marker.end()).replaceAll("\\b(?:per favore|per piacere|grazie)\\b", "").trim();
        Matcher location = Pattern.compile("\\b(?:a|in|di|per)\\s+(.+)").matcher(tail);
        String city = location.find() ? location.group(1).trim() : "";
        if (city.isEmpty() && (cue.equals("meteo") || cue.startsWith("previsioni")))
            city = tail.replaceAll("\\b(?:oggi|domani|adesso|ora)\\b", "").trim();
        city = city.replaceAll("(?:\\s+(?:oggi|domani|adesso|ora|per favore|grazie|per piacere))+$", "").trim();
        city = city.replaceAll("[.]+$", "").trim();
        if (city.equals("londra")) city = "london";
        if (cue.startsWith("che ore") || cue.equals("che ora è")) return city.isEmpty() ? "what time is it" : "time in " + city;
        if (cue.contains("temperatura") || cue.contains("gradi")) return "temperature in " + city;
        return city.isEmpty() ? "weather today" : "weather in " + city;
    }
    public static boolean ignoreTranscription(String text) {
        String value = text.toLowerCase(Locale.ROOT).trim();
        return value.startsWith("sottotitoli") && (value.contains("amara.org") || value.contains("comunit"));
    }
    public static String speech(String text) {
        if (text.startsWith("Playing ")) return "Sto riproducendo " + text.substring(8);
        if (text.equals("Stopping the music.")) return "Fermo la musica.";
        if (!text.startsWith("The weather in ")) return text;
        return text.replace("The weather in ", "Il tempo a ").replace(" with a temperature of ", " con una temperatura di ")
                .replace(" degrees Celsius.", " gradi Celsius.").replace(" is ", " è ")
                .replace("clear sky", "sereno").replace("few clouds", "poche nuvole")
                .replace("scattered clouds", "nuvole sparse").replace("broken clouds", "nuvoloso")
                .replace("overcast clouds", "cielo coperto").replace("light rain", "pioggia leggera")
                .replace("moderate rain", "pioggia moderata").replace("heavy intensity rain", "pioggia intensa")
                .replace("thunderstorm", "temporale").replace("drizzle", "pioggerella")
                .replace("snow", "neve").replace("mist", "foschia").replace("fog", "nebbia");
    }
}
