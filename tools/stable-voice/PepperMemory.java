package com.softbankrobotics.pepper.pepperGPT;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PepperMemory {
    private static final String WAKE = "^(?:pepper[\\s,:.!-]+)?";
    private static final Pattern CHANGE = Pattern.compile(WAKE
            + "(ricordati|ricorda|remember|dimentica|forget)\\b\\s*(?:(?:che|that)\\s+|[:,]\\s*)?(.*)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
    private static final Pattern RECALL = Pattern.compile(WAKE
            + "(?:che cosa ricordi di me|cosa ricordi di me|quali ricordi hai|what do you remember about me|show (?:my|your) memories)[.!?\\s]*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private final SharedPreferences prefs;

    PepperMemory(Context context) {
        prefs = context.getSharedPreferences("PepperGPT_Memory", Context.MODE_PRIVATE);
    }

    String command(String spoken, boolean italian) throws JSONException {
        Matcher change = CHANGE.matcher(spoken.trim());
        if (!change.matches()) {
            if (!RECALL.matcher(spoken.trim()).matches()) return null;
            JSONArray saved = entries();
            if (saved.length() == 0) return italian ? "Non ho ricordi salvati." : "I have no saved memories.";
            StringBuilder reply = new StringBuilder(italian ? "Ho salvato questi ricordi: " : "Here are my saved memories: ");
            for (int i = 0; i < saved.length(); i++) {
                if (i != 0) reply.append(". ");
                reply.append(saved.getString(i));
            }
            return reply.append('.').toString();
        }

        boolean forget = "dimentica".equalsIgnoreCase(change.group(1)) || "forget".equalsIgnoreCase(change.group(1));
        String fact = change.group(2).replaceFirst("[.!?\\s]+$", "").trim();
        if (fact.isEmpty() || fact.length() > 240)
            return italian ? "Dimmi un singolo ricordo breve." : "Tell me one short memory.";
        JSONArray saved = entries();
        int found = -1;
        for (int i = 0; i < saved.length(); i++) {
            if (normalize(fact).equals(normalize(saved.getString(i)))) { found = i; break; }
        }
        if (forget && found < 0)
            return italian ? "Non trovo quel ricordo. Prova a ripeterlo come l’hai salvato."
                    : "I can't find that memory. Try saying it the way you saved it.";
        if (!forget && found >= 0) return italian ? "Lo ricordo già." : "I already remember that.";
        if (!forget && saved.length() >= 24)
            return italian ? "La memoria è piena. Puoi chiedermi di dimenticare un ricordo."
                    : "Memory is full. You can ask me to forget an entry.";
        if (forget) saved.remove(found);
        else saved.put(fact);
        if (!prefs.edit().putString("facts", saved.toString()).commit())
            return italian ? "Non sono riuscita ad aggiornare la memoria." : "I couldn't update memory.";
        if (forget) return italian ? "Ho eliminato quel ricordo." : "I removed that memory.";
        return italian ? "L’ho salvato nella mia memoria." : "I saved that in my memory.";
    }

    String relevant(String spoken) {
        String query = " " + normalize(spoken) + " ";
        JSONArray matches = new JSONArray();
        try {
            JSONArray saved = entries();
            for (int i = 0; i < saved.length() && matches.length() < 5; i++) {
                String fact = saved.getString(i);
                for (String word : normalize(fact).split(" ")) {
                    if (word.length() < 4 || "sono come cosa what that with have this from preferisco".contains(word)) continue;
                    if (query.contains(" " + word + " ")) {
                        matches.put(fact);
                        break;
                    }
                }
            }
        } catch (JSONException ignored) {}
        return matches.length() == 0 ? "" : matches.toString();
    }

    private JSONArray entries() throws JSONException {
        return new JSONArray(prefs.getString("facts", "[]"));
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
}
