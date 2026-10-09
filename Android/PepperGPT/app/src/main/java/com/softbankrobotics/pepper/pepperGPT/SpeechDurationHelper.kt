package com.softbankrobotics.pepper.pepperGPT



object SpeechDurationHelper {

    /** Pepper robot TTS is slower than human narration; \\rspd=N\\ scales linearly. */

    private const val MS_PER_WORD_AT_100 = 450L

    /** Scroll uses a longer timeline than speech so text stays behind Pepper's voice. */

    const val SCROLL_DURATION_FACTOR = 1.45f

    const val STORY_SPEED_PERCENT = 70

    const val DEFAULT_IMMERSIVE_SPEED_PERCENT = 80

    /** \\pau=N\\ after . ! ? within a scene (NAOqi tag, not spoken aloud). */

    const val SENTENCE_PAUSE_MS = 500

    /** Silent gap between scene paragraphs when using sequential Say calls. */

    const val SCENE_BREAK_MS = 650L

    const val STORY_PARAGRAPH_PAUSE_MS = 450



    fun speedForMode(mode: String): Int = when (mode) {

        "story" -> STORY_SPEED_PERCENT

        else -> DEFAULT_IMMERSIVE_SPEED_PERCENT

    }



    fun formatForSpeech(text: String, speedPercent: Int, addStoryPauses: Boolean = false): String {

        val body = if (addStoryPauses) {

            text.split(Regex("\\n\\s*\\n+"))

                .map { it.trim() }

                .filter { it.isNotEmpty() }

                .joinToString("\n\n")

        } else {

            text

        }

        return "\\rspd=$speedPercent\\$body"

    }



    /** One story scene with sentence pauses and speed tag. */

    fun formatScene(scene: String, speedPercent: Int): String {

        val withPauses = scene.trim()

            .replace(Regex("([.!?])\\s+")) { match ->
                "${match.groupValues[1]} \\pau=${SENTENCE_PAUSE_MS}\\ "
            }

        return "\\rspd=$speedPercent\\$withPauses"

    }



    fun formatStoryScenes(scenes: List<String>, speedPercent: Int): String {

        val body = scenes.map { formatScene(it, speedPercent).removePrefix("\\rspd=$speedPercent\\") }

            .joinToString("\n\n")

        return "\\rspd=$speedPercent\\$body"

    }



    fun msPerWord(speedPercent: Int): Long {

        val speed = speedPercent.coerceIn(50, 150) / 100.0

        return (MS_PER_WORD_AT_100 / speed).toLong()

    }



    private fun sentencePauseCount(text: String): Int =

        Regex("[.!?]").findAll(text).count()



    fun estimateSceneMs(scene: String, speedPercent: Int, includePauseAfter: Boolean = false): Long {

        val words = scene.split(Regex("\\s+")).count { it.isNotEmpty() }.coerceAtLeast(1)

        val sentencePauses = sentencePauseCount(scene) * SENTENCE_PAUSE_MS.toLong()

        val pauseExtra = if (includePauseAfter) STORY_PARAGRAPH_PAUSE_MS.toLong() else 0L

        return (words * msPerWord(speedPercent) + 500L + sentencePauses + pauseExtra).coerceAtLeast(800L)

    }



    fun estimateStoryMs(scenes: List<String>, speedPercent: Int = STORY_SPEED_PERCENT): Long {

        if (scenes.isEmpty()) return 3_000L

        var total = 0L

        scenes.forEachIndexed { index, scene ->

            total += estimateSceneMs(scene, speedPercent, includePauseAfter = false)

            if (index < scenes.size - 1) total += SCENE_BREAK_MS

        }

        return total.coerceIn(3_000L, 300_000L)

    }



    fun estimateScrollMs(text: String, mode: String, scenes: List<String> = emptyList()): Long {

        val speed = speedForMode(mode)

        val speechMs = if (mode == "story" && scenes.isNotEmpty()) {

            estimateStoryMs(scenes, speed)

        } else {

            val pauseMs = if (mode == "story") STORY_PARAGRAPH_PAUSE_MS else 0

            estimateMs(text, speed, pauseMs)

        }

        return (speechMs * SCROLL_DURATION_FACTOR).toLong().coerceIn(3_000L, 420_000L)

    }



    fun estimateMs(text: String, speedPercent: Int = DEFAULT_IMMERSIVE_SPEED_PERCENT, paragraphPauseMs: Int = 0): Long {

        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }.size.coerceAtLeast(1)

        val paragraphCount = text.split(Regex("\\n\\s*\\n+")).map { it.trim() }.count { it.isNotEmpty() }

        val pauseExtra = if (paragraphPauseMs > 0 && paragraphCount > 1) {

            (paragraphCount - 1) * paragraphPauseMs.toLong()

        } else {

            0L

        }

        val sentencePauses = sentencePauseCount(text) * SENTENCE_PAUSE_MS.toLong()

        return (words * msPerWord(speedPercent) + 800L + pauseExtra + sentencePauses)

            .coerceIn(3_000L, 300_000L)

    }



    fun scrollProgressForStory(elapsedMs: Long, scenes: List<String>, speedPercent: Int): Float {

        if (scenes.isEmpty()) return 0f



        var sceneStartMs = 0L

        scenes.forEachIndexed { index, scene ->

            val sceneScrollMs = (estimateSceneMs(scene, speedPercent, includePauseAfter = false)

                * SCROLL_DURATION_FACTOR).toLong()

            val sceneEndMs = sceneStartMs + sceneScrollMs

            if (elapsedMs < sceneEndMs) {

                val sceneProgress = ((elapsedMs - sceneStartMs).toFloat() / sceneScrollMs).coerceIn(0f, 1f)

                val scrollStart = index.toFloat() / scenes.size

                val scrollEnd = (index + 1).toFloat() / scenes.size

                return (scrollStart + sceneProgress * (scrollEnd - scrollStart)).coerceIn(0f, 0.92f)

            }

            sceneStartMs = sceneEndMs + (SCENE_BREAK_MS * SCROLL_DURATION_FACTOR).toLong()

        }

        return 0.92f

    }

}


