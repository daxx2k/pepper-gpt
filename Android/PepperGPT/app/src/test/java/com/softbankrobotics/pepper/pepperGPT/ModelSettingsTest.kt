package com.softbankrobotics.pepper.pepperGPT

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ModelSettingsTest {
    private fun context(values: Map<String, String> = emptyMap()): Context {
        val preferences = mockk<SharedPreferences>()
        every { preferences.getString(any(), any()) } answers { values[firstArg()] ?: secondArg<String>() }
        val context = mockk<Context>()
        every { context.getSharedPreferences("PepperGPT_Prefs", Context.MODE_PRIVATE) } returns preferences
        return context
    }

    private fun body(model: String, image: Boolean = false): JSONObject {
        val content: Any = if (image) JSONArray().put(JSONObject().put("type", "image_url")
            .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,example"))) else "A follow-up question"
        return JSONObject().put("model", model).put("reasoning_effort", "minimal")
            .put("max_completion_tokens", 1024)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
    }

    @Test fun defaultsPreserveExistingRequests() {
        for (model in listOf("gpt-5-nano", "gpt-5-mini")) {
            val body = body(model)
            val original = body.toString()
            ModelSettings.apply(context(), body)
            assertEquals(original, body.toString())
        }
    }

    @Test fun chatAndCreativeSelectionsAreIndependent() {
        val context = context(mapOf("chat_model" to "custom-chat", "creative_model" to "custom-creative"))
        for ((original, selected) in listOf("gpt-5-nano" to "custom-chat", "gpt-5-mini" to "custom-creative")) {
            val body = body(original)
            val messages = body.getJSONArray("messages").toString()
            ModelSettings.apply(context, body)
            assertEquals(selected, body.getString("model"))
            assertEquals(messages, body.getJSONArray("messages").toString())
            assertEquals(1024, body.getInt("max_completion_tokens"))
            assertFalse(body.has("reasoning_effort"))
        }
    }

    @Test fun visionUsesItsOwnSelection() {
        val body = body("gpt-5-mini", true)
        ModelSettings.apply(context(mapOf("vision_model" to "custom-vision", "creative_model" to "custom-creative")), body)
        assertEquals("custom-vision", body.getString("model"))
        assertEquals("image_url", body.getJSONArray("messages").getJSONObject(0)
            .getJSONArray("content").getJSONObject(0).getString("type"))
    }

    @Test fun emptySettingsUseOriginalDefaults() {
        assertEquals("gpt-5-nano", ModelSettings.model(context(mapOf("chat_model" to "  ")), 0))
        assertEquals("gpt-5-mini", ModelSettings.model(context(), 1))
        assertEquals("gpt-5-mini", ModelSettings.model(context(), 2))
    }

    @Test fun unrelatedModelsAreUntouched() {
        for (model in listOf("gpt-4o-mini-tts", "gpt-image-1", "whisper-1")) {
            val body = body(model)
            val original = body.toString()
            ModelSettings.apply(context(mapOf("chat_model" to "custom-chat")), body)
            assertEquals(original, body.toString())
        }
    }
}
