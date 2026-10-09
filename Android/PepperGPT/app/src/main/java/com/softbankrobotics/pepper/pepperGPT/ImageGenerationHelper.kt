package com.softbankrobotics.pepper.pepperGPT

import android.content.Context
import android.util.Base64
import android.util.Log
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ImageGenerationHelper {
    private const val TAG = "ImageGeneration"
    const val STORY_MODEL = "gpt-image-1"
    const val STORY_QUALITY = "medium"
    /** Fast first scene so the story can start while later scenes render. */
    const val FIRST_SCENE_MODEL = "gpt-image-1-mini"
    const val FIRST_SCENE_QUALITY = "low"
    const val CHAT_QUALITY = "low"
    private val fallbackModels = listOf(STORY_MODEL, "gpt-image-1-mini")

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun readApiKey(context: Context): String {
        val prefs = context.getSharedPreferences("PepperGPT_Prefs", Context.MODE_PRIVATE)
        return prefs.getString("openai_api_key", "")?.trim() ?: ""
    }

    fun scenePrompt(sceneText: String, sceneIndex: Int, totalScenes: Int): String {
        val excerpt = sceneText.take(280).replace("\n", " ").trim()
        return "Children's storybook illustration, scene ${sceneIndex + 1} of $totalScenes: $excerpt. " +
            "Detailed watercolor painting, expressive characters, warm soft lighting, magical atmosphere, no text or words."
    }

    /** Returns (displayRef file://..., absolutePath). */
    suspend fun generate(
        context: Context,
        apiKey: String,
        prompt: String,
        quality: String = CHAT_QUALITY,
        model: String? = null,
        cachePrefix: String = "scene"
    ): Pair<String, String?> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext "" to null
        val modelsToTry = if (model != null) listOf(model) else fallbackModels
        for (tryModel in modelsToTry) {
            val imageReq = JSONObject().apply {
                put("model", tryModel)
                put("prompt", prompt)
                put("size", "1024x1024")
                put("quality", quality)
            }
            try {
                val response = httpClient.newCall(
                    Request.Builder()
                        .url("https://api.openai.com/v1/images/generations")
                        .post(RequestBody.create(MediaType.parse("application/json"), imageReq.toString()))
                        .addHeader("Authorization", "Bearer $apiKey")
                        .addHeader("Content-Type", "application/json")
                        .build()
                ).execute()
                val body = response.body()?.string() ?: "{}"
                Log.d(TAG, "Image response ($tryModel/$quality): HTTP ${response.code()}")
                val json = JSONObject(body)
                if (json.has("error")) {
                    Log.e(TAG, "Image request rejected ($tryModel): HTTP ${response.code()}")
                    continue
                }
                val b64 = json.optJSONArray("data")?.optJSONObject(0)?.optString("b64_json") ?: ""
                if (b64.isEmpty()) continue
                val path = saveB64(context, b64, cachePrefix) ?: continue
                return@withContext "file://$path" to path
            } catch (e: Exception) {
                Log.e(TAG, "Request failed ($tryModel): ${e.message}")
            }
        }
        "" to null
    }

    private fun saveB64(context: Context, b64Json: String, prefix: String): String? {
        return try {
            val bytes = Base64.decode(b64Json, Base64.DEFAULT)
            val file = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.png")
            file.writeBytes(bytes)
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Decode failed", e)
            null
        }
    }
}
