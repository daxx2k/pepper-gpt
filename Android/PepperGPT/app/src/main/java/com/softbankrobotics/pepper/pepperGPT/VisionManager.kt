package com.softbankrobotics.pepper.pepperGPT

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.builder.TakePictureBuilder
import com.aldebaran.qi.sdk.`object`.image.EncodedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

class VisionManager(private val context: android.content.Context, private val apiKey: () -> String) {
    private val TAG = "VisionManager"
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun captureImage(qiContext: QiContext): ByteBuffer? {
        return withContext(Dispatchers.IO) {
            try {
                val takePicture = TakePictureBuilder.with(qiContext).build()
                val timestampedImage = takePicture.run() ?: return@withContext null
                val rawImage = timestampedImage.image
                
                // Extraction strategy
                var dataBuffer: ByteBuffer? = (rawImage as? EncodedImage)?.data
                
                if (dataBuffer == null) {
                    val getValueMethod = rawImage.javaClass.methods.find { it.name == "getValue" }
                    val realImage = getValueMethod?.invoke(rawImage)
                    if (realImage != null) {
                        val getDataMethod = realImage.javaClass.methods.find { it.name == "getData" || it.name == "data" }
                        dataBuffer = getDataMethod?.invoke(realImage) as? ByteBuffer
                    }
                }
                
                if (dataBuffer == null) {
                    val getDataMethod = rawImage.javaClass.methods.find { it.name == "getData" || it.name == "data" }
                    dataBuffer = getDataMethod?.invoke(rawImage) as? ByteBuffer
                }
                
                dataBuffer?.rewind()
                dataBuffer
            } catch (e: Exception) {
                Log.e(TAG, "Capture failed: ${e.message}")
                null
            }
        }
    }

    suspend fun analyzeImage(imageBytes: ByteArray, prompt: String): String {
        return withContext(Dispatchers.IO) {
            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            
            val visionJson = JSONObject().apply {
                put("model", "gpt-5-mini")
                put("messages", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        put(JSONObject().put("type", "text").put("text", "You are Pepper, a physical robot observing this person. Describe the person in this photo briefly for a DALL-E prompt. Focus on age, gender, clothing, and features. Base it on: $prompt"))
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$base64Image"))
                        })
                    })
                }))
                put("max_completion_tokens", 2048)
                put("reasoning_effort", "low")
            }
            ModelSettings.apply(context, visionJson)

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .post(RequestBody.create(MediaType.parse("application/json"), visionJson.toString()))
                .addHeader("Authorization", "Bearer ${apiKey()}")
                .build()

            client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Vision API 400 or failed: ${response.code()}")
            
            val body = response.body()?.string() ?: "{}"
            JSONObject(body).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            }
        }
    }
}
