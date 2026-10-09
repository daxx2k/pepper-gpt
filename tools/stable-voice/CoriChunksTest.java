package com.softbankrobotics.pepper.pepperGPT;

import java.util.List;

/** Run with assertions enabled. No tablet dependencies. */
public final class CoriChunksTest {
    private static void check(String input) {
        List<String> chunks = CoriChunks.split(input);
        String expected = input.trim().replaceAll("\\s+", " ");
        assert String.join(" ", chunks).equals(expected) : "Narration was changed";
        if (!chunks.isEmpty()) assert chunks.get(0).length() <= 60 : "First chunk too long";
        for (String chunk : chunks) assert chunk.length() <= 100 : "Chunk too long";
    }
    public static void main(String[] args) {
        check(""); check("   \n\t");
        check("First sentence. Then she said, “Hello!” Finally, they went home.");
        StringBuilder story = new StringBuilder();
        for (int i = 0; i < 350; i++) story.append("word").append(i).append(' ');
        check(story.toString());
        List<String> online = CoriChunks.split(story.toString(), 1600, 1600, false);
        assert String.join(" ", online).equals(story.toString().trim()) : "Online narration changed";
        for (String chunk : online) assert chunk.length() <= 1600 : "Online request too long";
        check("One paragraph with punctuation!\n\nAnother paragraph? Every word must remain.");
        assert CoriChunks.split("She said “Hello!” Then smiled.").size() == 2 : "Quoted punctuation not recognized";
        System.out.println("PASS: long narration, quoted punctuation, paragraphs and empty text; all words retained and chunks bounded.");
    }
}
