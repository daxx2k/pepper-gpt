package com.softbankrobotics.pepper.pepperGPT;

import java.util.HashMap;
import java.util.Map;

/** Rotates clips independently for each approved conversational cue. */
final class GestureVariants {
    private static final Map<String, String[]> CLIPS = new HashMap<>();
    static {
        CLIPS.put("hello_wave", library("01-Hello", "Hello_10", "Hello_06", "Hello_01"));
        CLIPS.put("enumeration_01", library("05-Enumeration", "Enumeration_01", "Enumeration_02", "Enumeration_03", "Enumeration_04"));
        CLIPS.put("show_tablet_01", library("03-Tablet", "Show_Tablet_01", "Show_Tablet_02", "Show_Tablet_03",
                "Show_Tablet_04", "Show_Tablet_05", "Show_Tablet_06"));
        CLIPS.put("nice_reaction_02", library("07-Reactions", "NiceReaction_02", "NiceReaction_01"));
        CLIPS.put("sad_reaction_01", library("07-Reactions", "SadReaction_01", "SadReaction_02"));
        CLIPS.put("funny_01", library("06-Solitaries", "Funny_01", "Funny_02"));
        CLIPS.put("show_left_hand", library("02-Body_Parts", "Show_Hand_Left_02", "Show_Hand_Left_01"));
        CLIPS.put("show_right_hand", library("02-Body_Parts", "Show_Hand_Right_02", "Show_Hand_Right_01"));
        CLIPS.put("listen_ack", new String[]{"listen_ack", "head_nod"});
    }
    private final Map<String, Integer> next = new HashMap<>();

    String current(String cue) {
        String[] clips = CLIPS.get(cue);
        return clips == null ? cue : clips[index(cue)];
    }

    void advance(String cue) {
        String[] clips = CLIPS.get(cue);
        if (clips != null) next.put(cue, (index(cue) + 1) % clips.length);
    }

    private int index(String cue) {
        Integer value = next.get(cue);
        return value == null ? 0 : value;
    }

    private static String[] library(String family, String... names) {
        for (int i = 0; i < names.length; i++) {
            names[i] = "pepper_core_anims/" + family + "/" + names[i] + ".qianim";
        }
        return names;
    }
}
