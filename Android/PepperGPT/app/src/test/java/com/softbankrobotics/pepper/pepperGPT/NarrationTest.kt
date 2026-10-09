package com.softbankrobotics.pepper.pepperGPT

import org.junit.Assert.*
import org.junit.Test

class NarrationTest {
    @Test fun scenePausesSurviveRegexReplacementAndTagStripping() {
        val text = "First sentence. Second sentence? Third sentence!"
        val formatted = SpeechDurationHelper.formatScene(text, SpeechDurationHelper.STORY_SPEED_PERCENT)
        assertEquals("\\rspd=70\\First sentence. \\pau=500\\ Second sentence? \\pau=500\\ Third sentence!", formatted)
        assertEquals(text, SpeechText.chunks(formatted).joinToString(" "))
    }
    @Test fun allStoryParagraphsSurviveSceneGrouping() {
        val paragraphs = (1..9).map { "Paragraph $it tells another part of the story." }
        val scenes = StorySceneHelper.splitIntoScenes(paragraphs.joinToString("\n\n"), 3)
        assertTrue(scenes.size <= 3)
        assertEquals(paragraphs.joinToString("\n\n"), scenes.joinToString("\n\n"))
    }
    @Test fun speechChunksStripRobotTagsAndPreserveWords() {
        val text = "\\rspd=80\\Hello Pepper. \\pau=380\\This is a long story with many words."
        val chunks = SpeechText.chunks(text, 12)
        assertEquals("Hello Pepper. This is a long story with many words.", chunks.joinToString(" "))
        assertTrue(chunks.all { it.length <= 12 })
    }
    @Test fun emptyNarrationDoesNotQueueAudio() {
        assertTrue(SpeechText.chunks("   \\pau=380\\ ").isEmpty())
    }
    @Test fun unbrokenLongWordIsNeverDropped() {
        val text = "abcdefghijklmnopqrstuvwxyz"
        val chunks = SpeechText.chunks(text, 8)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.all { it.length <= 8 })
    }
}
