package com.softbankrobotics.pepper.pepperGPT;

import java.util.ArrayList;
import java.util.List;

/** Bounded speech requests that preserve every word. */
public final class SpeechChunks {
    private SpeechChunks() { }
    public static List<String> split(String text) {
        return split(text, 60, 100, true);
    }
    public static List<String> split(String text, int firstLimit, int followingLimit, boolean sentenceBoundaries) {
        List<String> result = new ArrayList<>();
        StringBuilder chunk = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (word.isEmpty()) continue;
            int limit = result.isEmpty() ? firstLimit : followingLimit;
            if (chunk.length() > 0 && chunk.length() + 1 + word.length() > limit) {
                result.add(chunk.toString()); chunk.setLength(0);
            }
            if (chunk.length() > 0) chunk.append(' ');
            chunk.append(word);
            if (sentenceBoundaries && word.matches(".*[.!?][\\\"'”’)]*")) {
                result.add(chunk.toString()); chunk.setLength(0);
            }
        }
        if (chunk.length() > 0) result.add(chunk.toString());
        return result;
    }
}
