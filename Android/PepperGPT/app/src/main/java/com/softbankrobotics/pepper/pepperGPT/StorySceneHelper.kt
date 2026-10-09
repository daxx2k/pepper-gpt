package com.softbankrobotics.pepper.pepperGPT

object StorySceneHelper {
    private const val SCENE_COUNT = 3

    private val storyStartPattern = Regex(
        "(?i)(once upon a time|long ago|there (?:was|were)|in a (?:far|little|small|tiny)|one (?:day|morning|evening|sunny day))"
    )

    /** Drop chatty GPT preamble before the actual narrative. */
    fun stripPreamble(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty()) return text

        storyStartPattern.find(text)?.let { match ->
            if (match.range.first > 0) return text.substring(match.range.first).trim()
        }

        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val preamblePatterns = listOf(
            Regex("(?i)^(sure|of course|absolutely|okay|ok|alright|great|wonderful)!?"),
            Regex("(?i)^i'?m (doing )?(well|great|good|fine)"),
            Regex("(?i)^(here(?:'s| is)|let me) (?:tell|read|share)"),
            Regex("(?i)^i (?:would love|can|will) (?:to )?tell"),
        )
        val narrative = lines.dropWhile { line ->
            preamblePatterns.any { it.containsMatchIn(line) } && line.length < 120
        }
        return if (narrative.isNotEmpty()) narrative.joinToString(" ") else text
    }

    fun formatForDisplay(scenes: List<String>): String =
        scenes.joinToString("\n\n")

    fun splitIntoScenes(story: String, count: Int = SCENE_COUNT): List<String> {
        require(count > 0)
        val trimmed = story.trim()
        if (trimmed.isEmpty()) return emptyList()

        val paragraphs = trimmed.split(Regex("\\n\\s*\\n+")).map { it.trim() }.filter { it.isNotEmpty() }
        if (paragraphs.size >= count) {
            val perScene = (paragraphs.size + count - 1) / count
            return paragraphs.chunked(perScene).map { it.joinToString("\n\n") }
        }

        val sentences = trimmed.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
        if (sentences.isEmpty()) return listOf(trimmed)

        val perScene = (sentences.size + count - 1) / count
        val scenes = mutableListOf<String>()
        var i = 0
        while (i < sentences.size && scenes.size < count) {
            val end = (i + perScene).coerceAtMost(sentences.size)
            scenes.add(sentences.subList(i, end).joinToString(" "))
            i = end
        }
        return scenes
    }

    /** Word index where each scene begins (scene 0 always starts at word 0). */
    fun sceneStartWordIndices(scenes: List<String>): List<Int> {
        if (scenes.size <= 1) return listOf(0)
        val indices = mutableListOf(0)
        var words = 0
        for (i in 0 until scenes.size - 1) {
            words += scenes[i].split(Regex("\\s+")).filter { it.isNotEmpty() }.size
            indices.add(words)
        }
        return indices
    }
}
