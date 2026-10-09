package com.softbankrobotics.pepper.pepperGPT

object SpeechText {
    /** Remove Pepper-only control tags and split without losing any narration. */
    fun coriChunks(text: String, limit: Int = 3000): List<String> {
        require(limit > 0)
        var remaining = text.replace(Regex("""\\[^\\]*\\"""), " ")
            .replace(Regex("\\s+"), " ").trim()
        val chunks = mutableListOf<String>()
        while (remaining.isNotEmpty()) {
            var end = remaining.length.coerceAtMost(limit)
            if (end < remaining.length) {
                val space = remaining.lastIndexOf(' ', end)
                if (space > 0) end = space
                if (end > 0 && Character.isHighSurrogate(remaining[end - 1])) end--
                if (end == 0) end = 2.coerceAtMost(remaining.length)
            }
            chunks.add(remaining.substring(0, end).trim())
            remaining = remaining.substring(end).trimStart()
        }
        return chunks
    }
}
