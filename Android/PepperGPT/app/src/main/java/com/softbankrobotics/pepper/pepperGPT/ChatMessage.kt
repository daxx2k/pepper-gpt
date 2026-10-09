package com.softbankrobotics.pepper.pepperGPT

data class ChatMessage(
    val role: String, // "user", "assistant", or "system"
    val content: String,
    val imageUrl: String? = null, // URL of a DALL-E generated image, if any
    val activityType: String? = null, // "story", "recipe", "weather", "transform"
    val activityData: Map<String, String>? = null // Store needed extras for re-launch
)
