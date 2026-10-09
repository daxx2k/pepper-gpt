package com.softbankrobotics.pepper.pepperGPT

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object OpenAIQuickTester {
    suspend fun testKey(key: String): Boolean = withContext(Dispatchers.IO) {
        if (key.isBlank()) return@withContext false
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
            val req = Request.Builder()
                .url("https://api.openai.com/v1/models")
                .addHeader("Authorization", "Bearer $key")
                .build()
            
            val response = client.newCall(req).execute()
            val isSuccess = response.isSuccessful
            response.close() // Good practice to close
            isSuccess
        } catch (_: Exception) {
            false
        }
    }
}
