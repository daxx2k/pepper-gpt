package com.softbankrobotics.pepper.pepperGPT

import android.Manifest
import android.content.Intent
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.graphics.Bitmap
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.os.Handler
import android.os.Looper
import android.net.ConnectivityManager
import androidx.lifecycle.lifecycleScope
import android.net.Uri
import androidx.core.content.FileProvider
import com.squareup.picasso.Picasso

import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.builder.SayBuilder
import com.aldebaran.qi.sdk.design.activity.RobotActivity
import com.aldebaran.qi.sdk.design.activity.conversationstatus.SpeechBarDisplayStrategy
import com.aldebaran.qi.sdk.`object`.touch.TouchSensor
import com.aldebaran.qi.sdk.builder.AnimationBuilder
import com.aldebaran.qi.sdk.builder.AnimateBuilder
import com.aldebaran.qi.sdk.`object`.actuation.Animate
import com.aldebaran.qi.sdk.`object`.conversation.BodyLanguageOption
import com.aldebaran.qi.sdk.`object`.camera.TakePicture
import com.aldebaran.qi.sdk.`object`.image.EncodedImage
import com.aldebaran.qi.sdk.builder.TakePictureBuilder
import com.aldebaran.qi.sdk.builder.LookAtBuilder
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.io.IOException
import java.net.URLEncoder
import java.util.Date
import java.io.File
import java.io.FileOutputStream
import android.media.ToneGenerator
import android.media.AudioManager
import java.nio.ByteBuffer
import java.nio.ShortBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import android.widget.ImageView
import com.softbankrobotics.pepper.pepperGPT.databinding.ActivityMainBinding
import okhttp3.*
import okhttp3.ConnectionPool
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : RobotActivity(), RobotLifecycleCallbacks {

    private lateinit var binding: ActivityMainBinding
    private var qiContext: QiContext? = null
    private lateinit var robotManager: RobotManager
    private lateinit var visionManager: VisionManager
    private val animationManager = AnimationManager()
    
    @Volatile private var isListening = false
    private var isSpeaking = false
    private var wasManuallyInterrupted = false
    private var wasListeningBeforeSpeaking = true
    private val speechController by lazy { SpeechController(this) }
    private var speechJob: kotlinx.coroutines.Job? = null
    private var speechEpoch = 0
    private var headTouchSensor: TouchSensor? = null
    private var leftHandTouchSensor: TouchSensor? = null
    private var rightHandTouchSensor: TouchSensor? = null
    private val TAG = "PepperApp"
    private val sharedHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(4, 2, TimeUnit.MINUTES))
            .build()
    }
    private suspend fun executeWithRetry(request: Request, maxRetries: Int = 1): okhttp3.Response {
        var lastException: IOException? = null
        for (attempt in 0..maxRetries) {
            try {
                val response = sharedHttpClient.newCall(request).execute()
                if (response.code() in 500..599 && attempt < maxRetries) {
                    response.close()
                    Log.w(TAG, "⚠️ Server error ${response.code()}, retrying (${attempt + 1}/$maxRetries)...")
                    kotlinx.coroutines.delay(1000L * (attempt + 1))
                    continue
                }
                return response
            } catch (e: IOException) {
                lastException = e
                if (attempt < maxRetries) {
                    Log.w(TAG, "⚠️ Network error, retrying (${attempt + 1}/$maxRetries): ${e.message}")
                    kotlinx.coroutines.delay(1000L * (attempt + 1))
                }
            }
        }
        throw lastException ?: IOException("Request failed after ${maxRetries + 1} attempts")
    }

    private var shouldListenOnFocusGained = false
    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()

    // === Status icons + refresh timer ===
    private val CHAT_HISTORY_FILE = "chat_history.json"
    
    // === Status icons + refresh timer ===
    private val statusHandler = Handler(Looper.getMainLooper())
    private val statusRunnable = object : Runnable {
        override fun run() {
            updateConnectivityIndicators()
            statusHandler.postDelayed(this, 20_000) 
        }
    }

    private var currentTheme = "glass" 
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
    private val radioManager = RadioManager()
    private val PREFS = "PepperGPT_Prefs"
    private var lastKnownBatteryLevel = -1
    private val KEY_API = "openai_api_key"
    private val KEY_PROMPT = "system_personality"
    private val KEY_WEATHER = "weather_api_key"

    // Credentials are provided by the user on the device.
    private fun readApiKey(): String =
        getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_API, "")?.trim() ?: ""

    private fun readWeatherKey(): String =
        getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_WEATHER, "")?.trim() ?: ""

    private fun readSystemPrompt(): String {
        val p = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_PROMPT, null)
        return p ?: SettingsActivity.Defaults.defaultPersona(this)
    }

    private fun readDefaultCity(): String {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getString("default_city", "London") ?: "London"
    }

    private val REQUEST_IMMERSIVE = 2001

    override fun onCreate(savedInstanceState: Bundle?) {
        checkAndRequestPermissions()
        android.util.Log.d(TAG, "🚀 App has started!")
        super.onCreate(savedInstanceState)
        
        setTheme(R.style.AppTheme)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        robotManager = RobotManager.getInstance(this)
        visionManager = VisionManager(applicationContext) { readApiKey() }
        
        loadTheme()
        applyTheme()
        
        setSpeechBarDisplayStrategy(SpeechBarDisplayStrategy.OVERLAY)
        QiSDK.register(this, this)
        // ToneGenerator is ready to use immediately, no load needed

        // Initialize RecyclerView
        chatAdapter = ChatAdapter(chatMessages, currentTheme, { url ->
            showFullscreenImage(url)
        }, { msg ->
            relaunchActivity(msg)
        })
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        binding.conversationRecyclerView.layoutManager = layoutManager
        binding.conversationRecyclerView.adapter = chatAdapter

        setSupportActionBar(binding.toolbar)
        // Show version in title
        try {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            supportActionBar?.title = "PepperGPT v$versionName"
        } catch (_: Exception) {}

        binding.listenToggleButton.setOnClickListener { toggleListening() }
        binding.stopButton.setOnClickListener { cancelGeneration() }

        // Bind status icons + initial/periodic updates
        updateConnectivityIndicators()
        statusHandler.post(statusRunnable)
        
        loadChatHistory()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMMERSIVE) {
            Log.d(TAG, "📋 Immersive activity closed. wasListeningBeforeSpeaking=$wasListeningBeforeSpeaking")
            clearStoryImagesFromChat()
            // QiContext is lost during immersive activities. Set flag so that
            // onRobotFocusGained restarts listening when focus returns.
            if (wasListeningBeforeSpeaking) {
                shouldListenOnFocusGained = true
                // Also try directly in case focus is already back
                lifecycleScope.launch {
                    delay(1500)
                    if (qiContext != null && shouldListenOnFocusGained) {
                        shouldListenOnFocusGained = false
                        startListening()
                    }
                }
            }
        }
    }
    
    private fun saveChatHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val array = JSONArray()
                val start = (chatMessages.size - 30).coerceAtLeast(0)
                for (i in start until chatMessages.size) {
                    val msg = chatMessages[i]
                    val obj = JSONObject()
                        .put("role", msg.role)
                        .put("content", msg.content)
                    msg.imageUrl?.let { obj.put("imageUrl", it) }
                    msg.activityType?.let { obj.put("activityType", it) }
                    msg.activityData?.let { data ->
                        val dataObj = JSONObject()
                        for ((key, value) in data) dataObj.put(key, value)
                        obj.put("activityData", dataObj)
                    }
                    array.put(obj)
                }

                val file = File(filesDir, CHAT_HISTORY_FILE)
                file.writeText(array.toString())
                Log.d(TAG, "💾 Chat history saved (${array.length()} msgs)")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to save history", e)
            }
        }
    }

    private fun loadChatHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file = File(filesDir, CHAT_HISTORY_FILE)
                if (!file.exists()) return@launch

                val jsonStr = file.readText()
                val array = JSONArray(jsonStr)

                conversationHistory.clear()
                chatMessages.clear()

                for (i in 0 until array.length()) {
                    val msg = array.getJSONObject(i)
                    val role = msg.optString("role")
                    val content = msg.optString("content")
                    val imageUrl = msg.optString("imageUrl").takeIf { it.isNotEmpty() }
                    val activityType = msg.optString("activityType").takeIf { it.isNotEmpty() }
                    val activityData = msg.optJSONObject("activityData")?.let { dataObj ->
                        val map = mutableMapOf<String, String>()
                        val keys = dataObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            map[key] = dataObj.optString(key)
                        }
                        map
                    }

                    if (role == "user" || role == "assistant") {
                        conversationHistory.add(
                            JSONObject().put("role", role).put("content", content)
                        )
                        chatMessages.add(
                            ChatMessage(role, content, imageUrl, activityType, activityData)
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    chatAdapter.notifyDataSetChanged()
                    if (chatMessages.isNotEmpty()) {
                        binding.conversationRecyclerView.scrollToPosition(chatMessages.size - 1)
                    }
                }
                Log.d(TAG, "📂 Chat history loaded")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to load history", e)
            }
        }
    }

    private fun clearHistory() {
        conversationHistory.clear()
        chatMessages.clear()
        chatAdapter.notifyDataSetChanged()
        val file = File(filesDir, CHAT_HISTORY_FILE)
        if (file.exists()) file.delete()
        Log.d(TAG, "🗑️ Chat history cleared.")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_help -> {
                startActivity(Intent(this, HelpActivity::class.java))
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_clear_history -> {
                clearHistory()
                true
            }
            R.id.action_close -> {
                finish()
                true
            }
            R.id.theme_glass -> { updateTheme("glass"); true }
            R.id.theme_dark -> { updateTheme("dark"); true }
            R.id.theme_soft -> { updateTheme("soft"); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun updateTheme(theme: String) {
        currentTheme = theme
        saveTheme()
        applyTheme()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        applyTheme()
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    private fun saveTheme() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        prefs.edit().putString("ui_theme", currentTheme).apply()
    }

    private fun loadTheme() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        currentTheme = prefs.getString("ui_theme", "glass") ?: "glass"
    }

    private fun applyTheme() {
        runOnUiThread {
            when (currentTheme) {
                "glass" -> {
                    binding.root.setBackgroundColor(ContextCompat.getColor(this, R.color.glass_bg))
                }
                "dark" -> {
                    binding.root.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_bg))
                }
                "soft" -> {
                    binding.root.setBackgroundColor(ContextCompat.getColor(this, R.color.soft_bg))
                }
            }
            updateListeningButtonAppearance()
            // Notify adapter to refresh bubbles if needed
            if (::chatAdapter.isInitialized) chatAdapter.updateTheme(currentTheme)
        }
    }

    override fun onDestroy() {
        stopListening()
        stopSpeaking()
        speechController.close()
        super.onDestroy()
        statusHandler.removeCallbacksAndMessages(null)
        toneGenerator.release()
        radioManager.stop()
        QiSDK.unregister(this, this)
    }

    private val thinkingPhrases = listOf(
        "Thinking...", "Let me ponder...", "Processing...",
        "Hmm, let me think...", "One moment...", "Working on it...",
        "Crunching the numbers...", "Almost there...", "Bear with me...",
        "Let me figure this out..."
    )

    private fun showSpinner() {
        runOnUiThread {
            binding.thinkingLabel.text = thinkingPhrases.random()
            binding.spinnerContainer.visibility = View.VISIBLE
        }
        robotManager.startThinkingLedAnimation()
    }

    private fun hideSpinner() {
        runOnUiThread { binding.spinnerContainer.visibility = View.GONE }
        robotManager.stopThinkingLedAnimation()
    }

    private fun showThinkingIndicator() {
        showSpinner()
        // Removed body animation (triggerThinkingAnimation) — ear LEDs now pulse instead
    }

    private fun toggleListening() {
        if (!isListening) {
            startListening()
        } else {
            stopListening()
        }
    }

    private fun startListening() {
        if (isListening || isFinishing || isDestroyed) return
        isListening = true

        runOnUiThread {
            binding.listenToggleButton.text = "Stop Listening"
            updateListeningButtonAppearance()
            
            // Trigger feedback animation
            animationManager.stopAll() 
        }

        listenAndRespond()
        Log.d(TAG, "🎙️ Listening started.")
    }

    private fun stopListening() {
        isListening = false

        runOnUiThread {
            binding.listenToggleButton.text = "Start Listening"
            updateListeningButtonAppearance()
            hideSpinner() // Ensure spinner is hidden if we stop
        }

        Log.d(TAG, "🛑 Stopping listening...")

        try {
            if (recorder != null) {
                recorder?.stop()
                recorder?.release()
                recorder = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping recorder: ${e.localizedMessage}")
        }
    }


    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_NETWORK_STATE
        )

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 1001)
        }
    }

    private var recorder: AudioRecord? = null

    private val audioFilePath: String by lazy {
        getExternalFilesDir(null)?.absolutePath + "/pepper_audio.wav"
    }

    private fun recordAudio() {
        if (!isListening || isFinishing || isDestroyed) return
        if (isSpeaking) {
            Log.d(TAG, "⚠️ Skipped audio recording because Pepper is currently speaking.")
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "❌ Permission denied: RECORD_AUDIO")
            return
        }

        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        val audioData = ByteArray(bufferSize)
        recorder!!.startRecording()
        Log.d(TAG, "🎙️ Recording started... (Adaptive VAD)")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val outputStream = FileOutputStream(audioFilePath)
                writeWavHeader(outputStream, sampleRate, channelConfig, audioFormat)

                // VAD Parameters
                var ambientNoise = 500
                val speechMargin = 1000
                var silenceThreshold = ambientNoise + speechMargin
                val silenceDurationLimit = 1000L // 1s of silence to stop
                val maxDuration = 15000L        // 15 seconds max recording
                val calibrationDuration = 300L  // 0.3s to measure noise
                
                var startTime = System.currentTimeMillis()
                var lastSpeechTime = 0L
                var hasSpeechStarted = false
                val totalData = java.io.ByteArrayOutputStream()

                Log.d(TAG, "🔇 Calibrating background noise...")

                while (isActive && isListening && recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val read = recorder!!.read(audioData, 0, bufferSize)
                    if (read > 0) {
                        totalData.write(audioData, 0, read)
                        val now = System.currentTimeMillis()

                        // Calculate Amplitude (16-bit PCM)
                        val shortBuffer = ByteBuffer.wrap(audioData, 0, read)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .asShortBuffer()
                        
                        var maxAmplitude = 0
                        while (shortBuffer.hasRemaining()) {
                            val sample = Math.abs(shortBuffer.get().toInt())
                            if (sample > maxAmplitude) maxAmplitude = sample
                        }
                        
                        // Calibration Phase
                        if (now - startTime < calibrationDuration) {
                            if (maxAmplitude > ambientNoise) ambientNoise = maxAmplitude
                            silenceThreshold = ambientNoise + speechMargin
                            continue
                        } else if (now - startTime < calibrationDuration + 100) {
                             Log.d(TAG, "✅ Calibration done. Noise: $ambientNoise, Threshold: $silenceThreshold")
                        }

                        // Speech Detection
                        if (maxAmplitude > silenceThreshold) {
                            if (!hasSpeechStarted) Log.d(TAG, "🗣️ Speech detected!")
                            hasSpeechStarted = true
                            lastSpeechTime = now
                        }

                        // Timeout Logic
                        if (hasSpeechStarted) {
                            // User has spoken, wait for silence gap
                            if (now - lastSpeechTime > silenceDurationLimit) {
                                Log.d(TAG, "🛑 Silence detected ($silenceDurationLimit ms). Stopping.")
                                break
                            }
                        } else {
                            // User hasn't spoken yet. Wait longer but not forever.
                            if (now - startTime > 10000) { // 10s wait for initial speech
                                Log.d(TAG, "🛑 No speech detected for 10s. Aborting.")
                                break
                            }
                        }
                        
                        // Hard limit
                        if (now - startTime > maxDuration) {
                             Log.d(TAG, "🛑 Max duration reached. Stopping.")
                             break
                        }
                    }
                }

                stopRecordingAndCleanUp()
                
                // UX Fix: Show thinking indicator immediately after recording stops
                runOnUiThread { if (isListening) showThinkingIndicator() }
                
                // Only process if we actually heard something or have data
                if (totalData.size() > 16000 * 2 / 2) { // at least 0.5s audio
                    val finalData = totalData.toByteArray()
                    outputStream.write(finalData)
                    outputStream.close()
                    java.io.RandomAccessFile(audioFilePath, "rw").use { wav ->
                        val sizes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                        wav.seek(4)
                        sizes.putInt(36 + finalData.size)
                        wav.write(sizes.array())
                        sizes.clear()
                        sizes.putInt(finalData.size)
                        wav.seek(40)
                        wav.write(sizes.array())
                    }
                    if (isListening) sendAudioToWhisper(audioFilePath)
                } else {
                     Log.d(TAG, "🗑️ Audio too short/empty. Discarding.")
                     outputStream.close()
                     // Maybe restart listening?
                     withContext(Dispatchers.Main) {
                         if (isListening) {
                             binding.listenToggleButton.text = "Start Listening"
                             isListening = false
                         }
                         when (currentTheme) {
                            "glass" -> {
                                binding.root.setBackgroundResource(R.color.glass_bg)
                                binding.stopButton.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF4444"))
                            }
                            "dark" -> {
                                binding.root.setBackgroundResource(R.color.dark_bg)
                                binding.stopButton.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#CC0000"))
                            }
                            "soft" -> {
                                binding.root.setBackgroundResource(R.color.soft_bg)
                                binding.stopButton.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF8888"))
                            }
                        }
                         updateListeningButtonAppearance()
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error during recording: ${e.localizedMessage}", e)
                stopRecordingAndCleanUp()
            }
        }
    }

    private fun stopRecordingAndCleanUp() {
        try {
            recorder?.stop()
            recorder?.release()
            recorder = null
        } catch(e: Exception) {
             // ignore
        }
    }

    private fun writeWavHeader(outputStream: FileOutputStream, sampleRate: Int, channelConfig: Int, audioFormat: Int) {
        val channels = if (channelConfig == AudioFormat.CHANNEL_IN_MONO) 1 else 2
        val bitsPerSample = if (audioFormat == AudioFormat.ENCODING_PCM_16BIT) 16 else 8
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val buffer = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray())
        buffer.putInt(36)
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort((channels * bitsPerSample / 8).toShort())
        buffer.putShort(bitsPerSample.toShort())
        buffer.put("data".toByteArray())
        buffer.putInt(0)
        outputStream.write(buffer.array())
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            // handle permissions if needed
        }
    }

    private fun sendAudioToWhisper(audioFilePath: String) = lifecycleScope.launch(Dispatchers.IO) {
        val apiKey = readApiKey()
        if (apiKey.isBlank()) {
             withContext(Dispatchers.Main) { 
                 Toast.makeText(this@MainActivity, "Missing OpenAI API Key! Check Settings.", Toast.LENGTH_LONG).show()
                 stopListening()
             }
             return@launch
        }

        try {
            val url = "https://api.openai.com/v1/audio/transcriptions"
            val audioFile = File(audioFilePath)

            if (audioFile.length() < 1000) {
                Log.d(TAG, "🛑 Audio file too small, likely silent. Ignoring.")
                resumeAfterIgnoredAudio()
                return@launch
            }

            val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("model", "whisper-1")
                .addFormDataPart("file", audioFile.name, RequestBody.create(MediaType.parse("audio/wav"), audioFile))
                .addFormDataPart("language", "en")
                .addFormDataPart("response_format", "json")
                .build()

            val request = Request.Builder().url(url).post(requestBody).addHeader("Authorization", "Bearer $apiKey").build()
            val response = executeWithRetry(request)
            val responseBody = response.body()?.string()

            Log.w(TAG, "🔍 Whisper STT Response: $responseBody")

            val whisperJson = JSONObject(responseBody ?: "{}")
            if (whisperJson.has("error")) {
                val errMsg = whisperJson.optJSONObject("error")?.optString("message") ?: "Unknown API error"
                val errCode = whisperJson.optJSONObject("error")?.optString("code") ?: ""
                Log.e(TAG, "Whisper request failed: HTTP ${response.code()}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "API Error: $errCode", Toast.LENGTH_LONG).show()
                    speakAndWait(when {
                        errCode.contains("quota") -> "Sorry, my API quota is exhausted. Please check my billing settings."
                        errCode.contains("auth") || errCode.contains("key") -> "Sorry, my API key is invalid. Please update it in settings."
                        else -> "Sorry, I got an API error: $errCode"
                    })
                    if (wasListeningBeforeOpenAI || isListening) startListening()
                }
                return@launch
            }

            var recognizedText = whisperJson.optString("text", "").trim()
            val ignoredPhrases = listOf(
                "thank you for watching", "thanks for watching", "thank you so much for watching",
                "thanks you for watching", "thank you for watching!", "thank you for watching.",
                "thank you very much", "please subscribe", "i'll see you in the next video",
                "thanks for the video", "thanks for checking out the video",
                "uh-huh", "hmm", "ah", "oh", "huh", "yeah", "what", "ok", "okay", "um", "uh"
            )

            if (!isListening) {
                Log.d(TAG, "🤫 Ignored transcription because listening was stopped.")
                return@launch
            }

            val outputText = recognizedText.toLowerCase(Locale.getDefault())
                .replace(Regex("[^a-z ]"), "") // Normalize: remove punctuation
                .trim()

            // Advanced Filtering
            val isHallucination = ignoredPhrases.any { phrase -> 
                (phrase.length >= 12 && outputText.contains(phrase)) || outputText == phrase
            }

            if (recognizedText.length < 4 || isHallucination) {
                Log.w(TAG, "🤫 Ignored false detection: '$recognizedText'")
                resumeAfterIgnoredAudio()
                return@launch
            }

            Log.w(TAG, "✅ Final Transcription: '$recognizedText'")
            withContext(Dispatchers.Main) {
                addUserMessageToChat(recognizedText)
                sendToOpenAI(recognizedText)
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error sending to Whisper STT: ${e.localizedMessage}", e)
            withContext(Dispatchers.Main) {
                if (wasListeningBeforeOpenAI || isListening) startListening()
            }
        }
    }

    private fun updateListeningButtonAppearance() {
        val color = if (isListening) "#B3261E" else "#107C5A"
        binding.listenToggleButton.backgroundTintList =
            android.content.res.ColorStateList.valueOf(Color.parseColor(color))
        binding.listenToggleButton.setTextColor(Color.WHITE)
    }

    private fun resumeAfterIgnoredAudio() {
        runOnUiThread {
            if (isListening && !isSpeaking && !isFinishing && !isDestroyed) {
                hideSpinner()
                listenAndRespond()
            }
        }
    }

    private fun listenAndRespond() {
        if (!isListening) {
            Log.d(TAG, "🔇 Listening aborted.")
            return
        }

        Log.d(TAG, "🎙️ Waiting before starting to listen to avoid self-feedback...")

        Handler(Looper.getMainLooper()).postDelayed({
            Log.d(TAG, "🎙️ Starting recording after delay...")
            recordAudio()
        }, 300)
    }

    private val conversationHistory = mutableListOf<JSONObject>()

    private fun isStoryRequest(message: String): Boolean {
        val lower = message.toLowerCase(Locale.getDefault())
        val keywords = listOf(
            "tell me a story", "tell a story", "read me a story", "read a story",
            "once upon a time", "narrate a story", "read me a book", "story about", "story please"
        )
        return keywords.any { lower.contains(it) }
    }

    private fun isRecipeRequest(message: String): Boolean {
        val lower = message.toLowerCase(Locale.getDefault())
        val keywords = listOf("recipe for", "how to make", "how to cook", "ingredients for", "how to prepare")
        return keywords.any { lower.contains(it) }
    }

    private fun isTransformRequest(message: String): Boolean {
        val lower = message.toLowerCase(Locale.getDefault())
        val keywords = listOf("take a picture", "take a photo", "imagine me as", "transform me into", "what would i look like")
        return keywords.any { lower.contains(it) }
    }

    private fun isRadioRequest(message: String): Boolean {
        val lower = message.toLowerCase(Locale.getDefault())
        // Direct station/genre matches
        if (lower.contains("synthwave") || lower.contains("nightride") || lower.contains("181.fm")) return true
        
        // Command matches
        if (lower.contains("stop") && (lower.contains("music") || lower.contains("radio"))) return true
        if (lower.contains("play") && (lower.contains("music") || lower.contains("radio") || lower.contains("pop"))) return true
        
        return false
    }

    private fun isImageRequest(message: String): Boolean {
        val lower = message.toLowerCase(Locale.getDefault())
        val imageKeywords = listOf(
            "generate an image", "generate image", "create an image", "create image",
            "make an image", "make image", "draw me", "draw a", "draw an",
            "show me a picture", "show me an image", "make a picture",
            "generate a picture", "create a picture", "make me a picture",
            "make me an image", "paint me", "paint a", "paint an",
            "can you draw", "can you generate", "can you create an image",
            "can you make an image", "picture of", "image of",
            "show me what", "visualize", "illustrate"
        )
        return imageKeywords.any { lower.contains(it) }
    }

    private var currentGenerationJob: kotlinx.coroutines.Job? = null

    private fun cancelGeneration() {
        currentGenerationJob?.cancel()
        currentGenerationJob = null
        wasListeningBeforeOpenAI = false
        stopListening()
        stopSpeaking()
        animationManager.stopAll()
        hideSpinner()
        robotManager.setLedStatus("idle")
    }

    private var wasListeningBeforeOpenAI = false

    private fun sendToOpenAI(message: String) {
        // Track state so we can resume after AI speaks
        wasListeningBeforeOpenAI = isListening
        // Stop listening so user knows we are processing and mic is off
        stopListening() 
        
        currentGenerationJob = lifecycleScope.launch(Dispatchers.IO) {
            val client = sharedHttpClient
    
            val OPENAI_API_KEY = readApiKey()
            if (OPENAI_API_KEY.isBlank()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Missing OpenAI API Key! Check Settings.", Toast.LENGTH_LONG).show()
                    speak("I need an API key to work. Please set one in settings.")
                }
                return@launch
            }
    
            if (isStoryRequest(message)) {
                generateStory(message, client, OPENAI_API_KEY)
                return@launch
            }
    
            if (isRecipeRequest(message)) {
                generateRecipe(message, client, OPENAI_API_KEY)
                return@launch
            }

            if (isTransformRequest(message)) {
                takePhotoAndTransform(message, client, OPENAI_API_KEY)
                return@launch
            }

            if (isRadioRequest(message)) {
                handleRadioRequest(message)
                return@launch
            }

            if (isImageRequest(message)) {
                generateImage(message, client, OPENAI_API_KEY)
                return@launch
            }
        
            var systemContent = readSystemPrompt()
            val defaultCity = readDefaultCity()
        
            val currentDateTime = SimpleDateFormat("EEEE, MMMM d, yyyy HH:mm", Locale.getDefault()).format(Date())
            systemContent += " You are located in $defaultCity. The current local time is $currentDateTime."
 
            val (city, requestType) = extractCityAndRequestType(message)
 
            if (requestType != "none") {
                val resolvedCity = if (city.isNotEmpty()) city else defaultCity
                if (requestType == "weather" || requestType == "temperature") {
                    val weatherJson = getWeatherSuspending(resolvedCity)
                    if (weatherJson != null) {
                        val desc = weatherJson.getJSONArray("weather").getJSONObject(0).getString("description")
                        val temp = weatherJson.getJSONObject("main").getDouble("temp")
                        val icon = weatherJson.getJSONArray("weather").getJSONObject(0).getString("icon")
                        
                        // Launch immersive weather mode on UI thread
                        withContext(Dispatchers.Main) {
                            try {
                                Log.d(TAG, "🌤️ Launching WeatherModeActivity for $resolvedCity")
                                val intent = Intent(this@MainActivity, WeatherModeActivity::class.java).apply {
                                    putExtra("EXTRA_CITY", resolvedCity)
                                    putExtra("EXTRA_TEMP", temp.toString())
                                    putExtra("EXTRA_DESC", desc.substring(0, 1).toUpperCase(Locale.getDefault()) + desc.substring(1))
                                    putExtra("EXTRA_ICON", icon)
                                    putExtra("EXTRA_THEME", currentTheme)
                                }
                                // Removed FLAG_ACTIVITY_NEW_TASK as it can break startActivityForResult on some Android 6 versions
                                startActivityForResult(intent, REQUEST_IMMERSIVE)
                                hideSpinner()
                                
                                updateConversationUI(message, "Checking the weather for $resolvedCity...", null, "weather", mapOf(
                                    "EXTRA_CITY" to resolvedCity,
                                    "EXTRA_TEMP" to temp.toString(),
                                    "EXTRA_DESC" to desc.substring(0, 1).toUpperCase(Locale.getDefault()) + desc.substring(1),
                                    "EXTRA_ICON" to icon,
                                    "EXTRA_THEME" to currentTheme
                                ))
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Failed to start WeatherModeActivity: ${e.message}")
                                speak("Sorry, I had trouble showing the weather screen.")
                            }
                        }
                        return@launch // EXIT EARLY for weather mode
                    } else {
                        systemContent += " I couldn't retrieve the weather for $resolvedCity at the moment. "
                    }
                } else if (requestType == "time") {
                    val info = getTimeForCitySuspending(resolvedCity)
                    systemContent += " $info "
                }
            }
        
            sendMessageToOpenAI(message, systemContent, OPENAI_API_KEY)
        }
    }

    private suspend fun sendMessageToOpenAI(message: String, systemMessage: String, apiKey: String) {
        withContext(Dispatchers.Main) { showSpinner() }
        
        try {
             if (conversationHistory.size > 40) {
                repeat(2) { conversationHistory.removeAt(0) }
            }
            conversationHistory.add(JSONObject().put("role", "user").put("content", message))
            val messagesArray = JSONArray().put(JSONObject().put("role", "system").put("content", systemMessage))
            for (msg in conversationHistory) messagesArray.put(msg)
            val jsonObject = JSONObject().apply {
                put("model", "gpt-5-nano")
                put("messages", messagesArray)
                put("max_completion_tokens", 1024)
                put("reasoning_effort", "minimal")
            }
            ModelSettings.apply(this@MainActivity, jsonObject)
             val requestBody = RequestBody.create(MediaType.parse("application/json"), jsonObject.toString())
            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .post(requestBody)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()
            
            val response = executeWithRetry(request)
            val responseBody = response.body()?.string()
            Log.d(TAG, "Chat response: HTTP ${response.code()}")

            val responseJson = JSONObject(responseBody ?: "{}")

            if (responseJson.has("error")) {
                val errMsg = responseJson.optJSONObject("error")?.optString("message") ?: "Unknown API error"
                val errCode = responseJson.optJSONObject("error")?.optString("code") ?: ""
                Log.e(TAG, "Chat request failed: HTTP ${response.code()}")
                withContext(Dispatchers.Main) {
                    hideSpinner()
                    val spokenError = when {
                        errCode.contains("quota") -> "Sorry, my API quota is exhausted. Please check my billing settings."
                        errCode.contains("auth") || errCode.contains("key") -> "Sorry, my API key is invalid."
                        errCode.contains("model") -> "Sorry, the AI model is not available. $errMsg"
                        else -> "Sorry, I got an API error: $errMsg"
                    }
                    updateConversationUI(message, spokenError)
                    speak(spokenError)
                }
                return
            }

            var aiResponse = responseJson
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content") ?: ""

            if (aiResponse.isBlank()) {
                Log.e(TAG, "❌ OpenAI returned empty response")
                withContext(Dispatchers.Main) {
                    hideSpinner()
                    val fallback = "Sorry, I didn't get a response from the AI."
                    updateConversationUI(message, fallback)
                    speak(fallback)
                }
                return
            }

            conversationHistory.add(JSONObject().put("role", "assistant").put("content", aiResponse))
            
            withContext(Dispatchers.Main) {
                updateConversationUI(message, aiResponse)
                speak(aiResponse)
                hideSpinner()
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ OpenAI request failed: ${e.localizedMessage}", e)
            withContext(Dispatchers.Main) {
                hideSpinner()
                Toast.makeText(this@MainActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                if (wasListeningBeforeOpenAI) startListening()
                wasListeningBeforeOpenAI = false
            }
        }
    }

    private fun clearStoryImagesFromChat() {
        var changed = false
        for (i in chatMessages.indices) {
            val msg = chatMessages[i]
            if (msg.activityType == "story" && !msg.imageUrl.isNullOrEmpty()) {
                chatMessages[i] = msg.copy(imageUrl = null)
                changed = true
            }
        }
        if (changed) {
            chatAdapter.notifyDataSetChanged()
            saveChatHistory()
        }
    }

    private fun addUserMessageToChat(userMessage: String) {
        val trimmed = userMessage.trim()
        if (trimmed.isEmpty() || trimmed == "...") return
        val lastUser = chatMessages.lastOrNull { it.role == "user" }
        if (lastUser?.content == trimmed) return

        chatMessages.add(ChatMessage("user", trimmed))
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        binding.conversationRecyclerView.smoothScrollToPosition(chatMessages.size - 1)
    }

    private fun updateConversationUI(userMessage: String?, aiResponse: String, imageUrl: String? = null, activityType: String? = null, activityData: Map<String, String>? = null) {
        userMessage?.let { addUserMessageToChat(it) }

        if (aiResponse.isNotEmpty() || !imageUrl.isNullOrEmpty()) {
            chatMessages.add(ChatMessage("assistant", aiResponse, imageUrl, activityType, activityData))
            chatAdapter.notifyItemInserted(chatMessages.size - 1)
        }

        if (chatMessages.isNotEmpty()) {
            binding.conversationRecyclerView.smoothScrollToPosition(chatMessages.size - 1)
        }

        saveChatHistory()
    }

    private suspend fun generateImage(prompt: String, client: OkHttpClient, apiKey: String) {
        withContext(Dispatchers.Main) { showSpinner() }
        
        try {
            robotManager.setLedStatus("drawing")
            // Show user message in chat first
            withContext(Dispatchers.Main) {
                updateConversationUI(prompt, "")
            }

            val (imageRef, imagePath) = generateOpenAiImage(apiKey, prompt, quality = "low", cachePrefix = "chat")

            if (imagePath != null) {
                val aiText = "Here's the image I generated for you!"
                conversationHistory.add(JSONObject().put("role", "user").put("content", prompt))
                conversationHistory.add(JSONObject().put("role", "assistant").put("content", "[Image generated: $prompt]"))

                withContext(Dispatchers.Main) {
                    updateConversationUI(null, aiText, imageRef)
                    speak(aiText)
                    hideSpinner()
                    showFullscreenImage(imageRef)
                }
            } else {
                val errorText = "Sorry, I couldn't generate that image."
                withContext(Dispatchers.Main) {
                    updateConversationUI(null, errorText)
                    speak(errorText)
                    hideSpinner()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error generating image: ${e.localizedMessage}", e)
            withContext(Dispatchers.Main) {
                updateConversationUI(null, "Sorry, something went wrong generating the image.")
                hideSpinner()
            }
        } finally {
            robotManager.setLedStatus("idle")
        }
    }

    private fun speak(text: String) {
        runOnUiThread {
            val ctx = qiContext
            if (ctx == null && !speechController.usesCori()) {
                Toast.makeText(this, "Pepper voice is waiting for the robot connection. You can choose Cori in Settings.", Toast.LENGTH_LONG).show()
                return@runOnUiThread
            }
            if (text.isBlank()) return@runOnUiThread
            val resumeListening = wasListeningBeforeOpenAI || isListening
            stopSpeaking()
            val epoch = speechEpoch
            stopListening()
            isSpeaking = true
            robotManager.startSpeakingLedAnimation()
            val cleanText = text.replace(Regex("\\*+"), "")
                .replace(Regex("`+"), "").replace(Regex("#+\\s*"), "")
            speechJob = lifecycleScope.launch {
                try {
                    val completed = speechController.speak(cleanText, ctx)
                    if (!completed && speechController.usesCori()) {
                        Toast.makeText(this@MainActivity,
                            "Cori unavailable. Check the voice engine or choose Pepper native voice in Settings.",
                            Toast.LENGTH_LONG).show()
                    }
                    if (epoch == speechEpoch) isSpeaking = false
                    if (completed && epoch == speechEpoch && resumeListening && qiContext === ctx) startListening()
                } finally {
                    if (epoch == speechEpoch) {
                        isSpeaking = false
                        robotManager.stopSpeakingLedAnimation()
                        wasListeningBeforeOpenAI = false
                    }
                }
            }
        }
    }

    private suspend fun speakAndWait(text: String) {
        val ctx = qiContext ?: return
        isSpeaking = true
        try { speechController.speak(text, ctx) }
        finally { isSpeaking = false }
    }

    private fun containsGreetingOrGoodbye(text: String): Boolean {
        // ... (Keep existing logic)
        val lowerText = text.toLowerCase(Locale.getDefault()).trim()
        val greetings = listOf("hi", "hello", "ciao", "good morning", "good afternoon", "good evening")
        val goodbyes = listOf("bye", "goodbye", "see you", "farewell", "talk to you later")
        val sentences = lowerText.split(Regex("[.!?]"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val firstSentence = sentences.firstOrNull() ?: ""
        val lastSentence = sentences.lastOrNull() ?: ""
        val containsGreeting = greetings.any { firstSentence.startsWith(it) || lastSentence.startsWith(it) }
        val containsGoodbye = goodbyes.any { firstSentence.startsWith(it) || lastSentence.startsWith(it) }
        return containsGreeting || containsGoodbye
    }

    private fun triggerHelloAnimation() {
        animationManager.playHello()
    }

    private var thinkingAnimate: Animate? = null
    private var thinkingFuture: Future<Void>? = null

    private fun triggerThinkingAnimation() {
        animationManager.playThinking()
    }

    private fun triggerSuccessAnimation() {
        animationManager.playSuccess()
    }

    private suspend fun getTimeForCitySuspending(city: String): String = withContext(Dispatchers.IO) {
        val cityToTimeZone = mapOf(
            "Las Vegas" to "America/Los_Angeles",
            "New York" to "America/New_York",
            "London" to "Europe/London",
            "Paris" to "Europe/Paris",
            "Tokyo" to "Asia/Tokyo",
            "Sydney" to "Australia/Sydney",
            "Dubai" to "Asia/Dubai",
            "Berlin" to "Europe/Berlin",
            "Rome" to "Europe/Rome"
        )
        val timeZone = cityToTimeZone[city] ?: "Etc/UTC"
        val url = "https://timeapi.io/api/Time/current/zone?timeZone=$timeZone"
        val request = Request.Builder().url(url).build()
        try {
            val response = sharedHttpClient.newCall(request).execute()
            val body = response.body()?.string()
            val json = JSONObject(body ?: "{}")
            if (json.has("dateTime")) {
                val dateTime = json.getString("dateTime")
                val formattedTime = dateTime.substring(11, 16)
                "The current time in $city is $formattedTime."
            } else {
                 "I couldn't find the time for $city."
            }
        } catch (e: Exception) {
            "I couldn't retrieve the time for $city."
        }
    }
    

    private fun extractCityAndRequestType(message: String): Pair<String, String> {
        val lowerMessage = message.toLowerCase(Locale.getDefault()).replace(Regex("[^a-z ]"), "")
        val weatherKeywords = listOf("weather in", "forecast for", "how is the weather in")
        val tempKeywords = listOf("temperature in", "how hot is it in", "how cold is it in", "current temperature in")
        val timeKeywords = listOf("time in", "what time is it in")
        for (keyword in weatherKeywords) {
            if (lowerMessage.contains(keyword)) {
                val index = lowerMessage.indexOf(keyword) + keyword.length
                val words = lowerMessage.substring(index).trim().split(" ")
                return Pair(words.joinToString(" ") { it.capitalize() }, "weather")
            }
        }
        for (keyword in tempKeywords) {
            if (lowerMessage.contains(keyword)) {
                val index = lowerMessage.indexOf(keyword) + keyword.length
                val words = lowerMessage.substring(index).trim().split(" ")
                return Pair(words.joinToString(" ") { it.capitalize() }, "temperature")
            }
        }
        for (keyword in timeKeywords) {
             if (lowerMessage.contains(keyword)) {
                val index = lowerMessage.indexOf(keyword) + keyword.length
                val words = lowerMessage.substring(index).trim().split(" ")
                return Pair(words.joinToString(" ") { it.capitalize() }, "time")
            }
        }
        // Broader detection: "what's the weather" or "how's the weather" without a city
        val broadWeather = listOf("whats the weather", "hows the weather", "weather today", "weather right now", "current weather")
        if (broadWeather.any { lowerMessage.contains(it) }) {
            return Pair("", "weather") // empty city = will use defaultCity
        }
        // Broader time: "what time is it" without a city
        val broadTime = listOf("what time is it", "whats the time", "current time")
        if (broadTime.any { lowerMessage.contains(it) }) {
            return Pair("", "time")
        }
        return Pair("", "none")
    }

    private suspend fun getWeatherSuspending(city: String): JSONObject? = withContext(Dispatchers.IO) {
        val apiKey = readWeatherKey()
        val formattedCity = URLEncoder.encode(city, "UTF-8")
        val url = "https://api.openweathermap.org/data/2.5/weather?q=$formattedCity&appid=$apiKey&units=metric"
        
        val request = Request.Builder().url(url).build()
        try {
            val response = sharedHttpClient.newCall(request).execute()
            val body = response.body()?.string() ?: "{}"
            val json = JSONObject(body)
            if (json.has("cod") && json.getInt("cod") == 200) {
                json
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun updateConnectivityIndicators() {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetworkInfo
        @Suppress("DEPRECATION") val isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting
        
        val hasOpenAiKey = readApiKey().isNotBlank()
        lifecycleScope.launch(Dispatchers.IO) {
        val batteryPct = robotManager.getBatteryLevel()
        
        runOnUiThread {
             // Wi-Fi status
             if (isConnected) {
                 binding.wifiStatusIcon.setImageResource(R.drawable.circle_green)
             } else {
                 binding.wifiStatusIcon.setImageResource(R.drawable.circle_grey)
             }
             
             // OpenAI key status
             if (hasOpenAiKey) {
                 binding.openaiStatusIcon.setImageResource(R.drawable.circle_green)
             } else {
                 binding.openaiStatusIcon.setImageResource(R.drawable.circle_red)
             }
             
             // Clock (always green if connected, since time API needs internet)
             if (isConnected) {
                 binding.clockStatusIcon.setImageResource(R.drawable.circle_green)
             } else {
                 binding.clockStatusIcon.setImageResource(R.drawable.circle_grey)
             }
             
             // Battery level
             if (batteryPct >= 0) {
                 lastKnownBatteryLevel = batteryPct // Update cache
                 val icon = when {
                     batteryPct > 60 -> "🔋"
                     batteryPct > 20 -> "🪫"
                     else -> "⚠️"
                 }
                 binding.batteryLevelText.text = "$icon ${batteryPct}%"
             } else if (lastKnownBatteryLevel >= 0) {
                 // Use cached value if current read failed (e.g. lost focus temporarily)
                 binding.batteryLevelText.text = "🔋 ${lastKnownBatteryLevel}% (cached)"
                 binding.batteryLevelText.setTextColor(Color.GRAY)
             } else {
                 binding.batteryLevelText.text = "🔋 --%"
             }
        }
        }
    }
    
    private fun stopSpeaking() {
        speechEpoch++
        speechJob?.cancel()
        speechJob = null
        speechController.stop()
        if (::robotManager.isInitialized) robotManager.stopSpeakingLedAnimation()
        isSpeaking = false
    }

    private val touchStop = TouchStopController()

    override fun onRobotFocusGained(qiContext: QiContext) {
        this.qiContext = qiContext
        touchStop.bind(qiContext) { stopSpeaking() }
        animationManager.onFocusGained(qiContext)
        robotManager.onFocusGained(qiContext)
        robotManager.ensureInteractiveState()
        if (shouldListenOnFocusGained) {
            shouldListenOnFocusGained = false
            Log.d(TAG, "🤖 Robot focus gained — auto-restarting listening.")
            runOnUiThread { startListening() }
        } else {
            Log.d(TAG, "🤖 Robot focus gained, not listening by default.")
        }
    }
    
    override fun onRobotFocusLost() {
        touchStop.clear()
        this.qiContext = null
        runOnUiThread {
            wasListeningBeforeOpenAI = false
            stopListening()
            stopSpeaking()
            currentGenerationJob?.cancel()
        }
        animationManager.onFocusLost()
        robotManager.onFocusLost()
        this.headTouchSensor?.removeAllOnStateChangedListeners()
        this.leftHandTouchSensor?.removeAllOnStateChangedListeners()
        this.rightHandTouchSensor?.removeAllOnStateChangedListeners()
    }
    
    override fun onRobotFocusRefused(reason: String?) {
        // Handle refusal
    }

    private fun splitContentAndIllustrationPrompt(aiResponse: String): Pair<String, String> {
        val regex = Regex("(?i)\\bPROMPT:\\s*")
        val match = regex.find(aiResponse)
        return if (match != null) {
            aiResponse.substring(0, match.range.first).trim() to
                aiResponse.substring(match.range.last).trim()
        } else {
            aiResponse.trim() to ""
        }
    }

    private fun illustrationPromptFromContent(content: String, mode: String): String {
        val excerpt = content.take(200).replace("\n", " ").trim()
        return when (mode) {
            "recipe" -> "Appetizing food photography of: $excerpt. Warm lighting, storybook illustration style."
            else -> "Whimsical children's storybook illustration of: $excerpt. Painted style, soft warm colors."
        }
    }

    private suspend fun requestIllustration(
        apiKey: String,
        promptPart: String,
        fallbackContent: String,
        mode: String
    ): Pair<String, String?> {
        val prompt = promptPart.ifBlank { illustrationPromptFromContent(fallbackContent, mode) }
        return ImageGenerationHelper.generate(this, apiKey, prompt, quality = "low", cachePrefix = "immersive")
    }

    private suspend fun generateOpenAiImage(
        apiKey: String,
        prompt: String,
        quality: String = "low",
        cachePrefix: String = "gen"
    ): Pair<String, String?> = ImageGenerationHelper.generate(this, apiKey, prompt, quality, cachePrefix)

    private fun launchImmersiveMode(
        prompt: String,
        content: String,
        imageUrl: String,
        imagePath: String?,
        mode: String,
        scenePrompts: List<String>? = null,
        sceneStartWords: List<Int>? = null,
        scenePaths: List<String?>? = null,
        sceneTexts: List<String>? = null
    ) {
        val activityData = mutableMapOf(
            "EXTRA_CONTENT" to content,
            "EXTRA_IMAGE_URL" to imageUrl,
            "EXTRA_MODE" to mode,
            "EXTRA_THEME" to currentTheme
        )
        imagePath?.let { activityData["EXTRA_IMAGE_PATH"] = it }
        if (!scenePrompts.isNullOrEmpty()) {
            activityData["EXTRA_SCENE_PROMPTS"] = JSONArray(scenePrompts).toString()
        }
        if (!sceneStartWords.isNullOrEmpty()) {
            activityData["EXTRA_SCENE_START_WORDS"] = sceneStartWords.joinToString(",")
        }
        if (!scenePaths.isNullOrEmpty()) {
            val pathsArray = JSONArray()
            for (path in scenePaths) pathsArray.put(path ?: "")
            activityData["EXTRA_SCENE_PATHS"] = pathsArray.toString()
        }
        if (!sceneTexts.isNullOrEmpty()) {
            val textsArray = JSONArray()
            for (text in sceneTexts) textsArray.put(text)
            activityData["EXTRA_SCENE_TEXTS"] = textsArray.toString()
        }

        val intent = Intent(this, ImmersiveModeActivity::class.java)
        for ((key, value) in activityData) intent.putExtra(key, value)
        startActivityForResult(intent, REQUEST_IMMERSIVE)
        val modeLabel = when (mode) {
            "story" -> "Story Mode"
            "recipe" -> "Recipe Mode"
            else -> "Immersive Mode"
        }
        val chatImage = when {
            mode == "story" -> null
            !imagePath.isNullOrEmpty() -> "file://$imagePath"
            imageUrl.isNotEmpty() -> imageUrl
            else -> null
        }
        val statusText = when {
            mode == "story" -> "[$modeLabel — tap to reopen]"
            chatImage != null -> "[$modeLabel — tap to reopen]"
            else -> "[$modeLabel — no illustration generated]"
        }
        updateConversationUI(prompt, statusText, chatImage, mode, activityData)
    }

    private suspend fun generateStory(prompt: String, client: OkHttpClient, apiKey: String) {
        withContext(Dispatchers.Main) { showSpinner() }
        try {
            val systemMsg = "You are Pepper, a storytelling robot. Write ONLY the story — no greetings, no status updates, no preamble (never say things like 'I'm doing well'). Start directly with the first narrative sentence. Output exactly 3 short paragraphs separated by blank lines (opening, adventure, ending). Total under 200 words. No titles, no illustration prompts, no commentary."
            val messagesArray = JSONArray()
                .put(JSONObject().put("role", "system").put("content", systemMsg))
                .put(JSONObject().put("role", "user").put("content", prompt))

            val jsonObject = JSONObject().apply {
                put("model", "gpt-5-mini")
                put("messages", messagesArray)
                put("max_completion_tokens", 4096)
                put("reasoning_effort", "low")
            }
            ModelSettings.apply(this@MainActivity, jsonObject)
            val requestBody = RequestBody.create(MediaType.parse("application/json"), jsonObject.toString())
            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .post(requestBody)
                .addHeader("Authorization", "Bearer $apiKey")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body()?.string() ?: ""
            Log.w(TAG, "📖 Story chat response: $body")
            val storyJson = JSONObject(body)

            if (storyJson.has("error")) {
                val errMsg = storyJson.optJSONObject("error")?.optString("message") ?: "Unknown error"
                Log.e(TAG, "Story request failed: HTTP ${response.code()}")
                withContext(Dispatchers.Main) {
                    hideSpinner()
                    updateConversationUI(prompt, "Sorry, story generation failed: $errMsg")
                    speak("Sorry, I couldn't generate a story. $errMsg")
                }
                return
            }

            val aiResponse = storyJson.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content") ?: ""
            if (aiResponse.isBlank()) {
                withContext(Dispatchers.Main) {
                    hideSpinner()
                    speak("Sorry, I got an empty response for the story.")
                }
                return
            }

            val cleaned = StorySceneHelper.stripPreamble(aiResponse.substringBefore("PROMPT:").trim())
            val scenes = StorySceneHelper.splitIntoScenes(cleaned)
            val storyPart = StorySceneHelper.formatForDisplay(scenes)
            val scenePrompts = scenes.mapIndexed { index, scene ->
                ImageGenerationHelper.scenePrompt(scene, index, scenes.size)
            }
            val sceneStartWords = StorySceneHelper.sceneStartWordIndices(scenes)

            val firstSceneResult = if (scenePrompts.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    ImageGenerationHelper.generate(
                        this@MainActivity,
                        apiKey,
                        scenePrompts[0],
                        quality = ImageGenerationHelper.FIRST_SCENE_QUALITY,
                        model = ImageGenerationHelper.FIRST_SCENE_MODEL,
                        cachePrefix = "scene0"
                    )
                }
            } else {
                "" to null
            }
            val scenePaths = MutableList<String?>(scenes.size) { null }
            firstSceneResult.second?.let { scenePaths[0] = it }
            val imgUrl = firstSceneResult.first
            val imgPath = firstSceneResult.second
            Log.w(TAG, "Story scene 1 ready; scenes 2-${scenes.size} will load during narration")

            conversationHistory.add(JSONObject().put("role", "user").put("content", prompt))
            conversationHistory.add(
                JSONObject().put("role", "assistant").put("content", "[Story: ${storyPart.take(120)}]")
            )

            withContext(Dispatchers.Main) {
                stopSpeaking()
                launchImmersiveMode(
                    prompt, storyPart, imgUrl, imgPath, "story", scenePrompts, sceneStartWords, scenePaths, scenes
                )
                hideSpinner()
                triggerSuccessAnimation()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Story generation failed", e)
            withContext(Dispatchers.Main) { hideSpinner() }
        } finally {
            robotManager.setLedStatus("idle")
        }
    }


    private suspend fun generateRecipe(prompt: String, client: OkHttpClient, apiKey: String) {
        withContext(Dispatchers.Main) { showSpinner() }
        try {
            val systemMsg = "You are Pepper, a physical humanoid robot. You have a memory of past interactions and should refer to them if relevant. Provide a recipe based on the user request. Keep it concise with ingredients and steps. End with a line 'PROMPT:' followed by a vivid DALL-E prompt to illustrate the finished dish."
            val messagesArray = JSONArray().put(JSONObject().put("role", "system").put("content", systemMsg))
            
            // Add last 5 history items for context
            val historySubset = conversationHistory.takeLast(5)
            for (msg in historySubset) messagesArray.put(msg)

            messagesArray.put(JSONObject().put("role", "user").put("content", prompt))

            val jsonObject = JSONObject().apply {
                put("model", "gpt-5-mini")
                put("messages", messagesArray)
                put("max_completion_tokens", 4096)
                put("reasoning_effort", "low")
            }
            ModelSettings.apply(this@MainActivity, jsonObject)
            val requestBody = RequestBody.create(MediaType.parse("application/json"), jsonObject.toString())
            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .post(requestBody)
                .addHeader("Authorization", "Bearer $apiKey")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body()?.string() ?: "{}"
            Log.w(TAG, "🍳 Recipe chat response: $body")
            val recipeJson = JSONObject(body)

            if (recipeJson.has("error")) {
                val errMsg = recipeJson.optJSONObject("error")?.optString("message") ?: "Unknown error"
                Log.e(TAG, "Recipe request failed: HTTP ${response.code()}")
                withContext(Dispatchers.Main) {
                    hideSpinner()
                    updateConversationUI(prompt, "Sorry, recipe generation failed: $errMsg")
                    speak("Sorry, I couldn't generate a recipe. $errMsg")
                }
                return
            }

            val aiResponse = recipeJson.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content") ?: ""
            if (aiResponse.isBlank()) {
                withContext(Dispatchers.Main) {
                    hideSpinner()
                    speak("Sorry, I got an empty response for the recipe.")
                }
                return
            }

            val (recipePart, promptPart) = splitContentAndIllustrationPrompt(aiResponse)
            val (imgUrl, imgPath) = requestIllustration(apiKey, promptPart, recipePart, "recipe")
            Log.d(TAG, "🍳 Recipe illustration: url=${imgUrl.isNotEmpty()} cached=${imgPath != null}")

            conversationHistory.add(JSONObject().put("role", "user").put("content", prompt))
            conversationHistory.add(
                JSONObject().put("role", "assistant").put("content", "[Recipe: ${recipePart.take(120)}]")
            )

            withContext(Dispatchers.Main) {
                launchImmersiveMode(prompt, recipePart, imgUrl, imgPath, "recipe")
                hideSpinner()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Recipe generation failed", e)
            withContext(Dispatchers.Main) {
                hideSpinner()
                speak("Sorry, recipe generation failed.")
            }
        }
    }

    // Updated takePhotoAndTransform with Timeout and Better Feedback
    private suspend fun takePhotoAndTransform(rawMessage: String, client: OkHttpClient, apiKey: String) {
        // Fix: Extract the target description from the user's message
        // e.g. "imagine me as a pirate" -> "a pirate"
        val lower = rawMessage.toLowerCase(Locale.getDefault())
        // Expanded regex to catch "make me", "turn me into", "generate", etc.
        val regex = Regex("(?:imagine|transform|picture|image|make|turn|create|draw|generate)(?:\\s+me)?(?:\\s+into)?\\s+as\\s+(.+)", RegexOption.IGNORE_CASE)
        val match = regex.find(lower)
        
        // Fallback: if no specific structure, use the whole message but remove trigger words if possible
        var prompt = match?.groupValues?.get(1)?.trim()
        
        if (prompt == null) {
            // Try another common pattern: "make me a [X]" or "turn me into a [X]"
            val altRegex = Regex("(?:make|turn|create)(?:\\s+me)?\\s+(?:into\\s+)?a\\s+(.+)", RegexOption.IGNORE_CASE)
            prompt = altRegex.find(lower)?.groupValues?.get(1)?.trim()
        }

        if (prompt == null) {
             // Ultimate fallback: Just use the raw message
             prompt = rawMessage
        }

        val ctx = qiContext ?: run {
            Log.e(TAG, "❌ QiContext is null, cannot take photo.")
            return
        }

        // Ensure listening is fully stopped during photo sequence
        withContext(Dispatchers.Main) {
            wasListeningBeforeSpeaking = isListening
            stopListening()
            showSpinner()
            binding.viewfinderOverlay.setImageResource(android.R.drawable.ic_menu_camera)
            binding.viewfinderOverlay.setBackgroundColor(Color.BLACK)
            binding.viewfinderOverlay.visibility = View.VISIBLE
            binding.statusOverlayText.text = "Preparing camera..."
            binding.statusOverlayText.visibility = View.VISIBLE
        }
        
        try {
            // Safety Timeout: 60 seconds max
            withTimeout(60_000L) {
                Log.d(TAG, "📸 Photo sequence started — inferred prompt: $prompt")
                speakAndWait("Ready? Let's take a photo! Look at me...")
                
                // Stop any current animation or speech before starting photo sequence
                try {
                    withContext(Dispatchers.Main) { stopSpeaking() }
                    // If we had a running animation, we'd cancel it here
                } catch (e: Exception) {
                    Log.w(TAG, "Cancel failed: ${e.message}")
                }

                // Try to look at the user and KEEP LOOKING
                try {
                    val human = ctx.humanAwareness.engagedHuman
                    if (human != null) {
                        Log.d(TAG, "📸 Looking at human...")
                        val lookAt = LookAtBuilder.with(ctx)
                            .withFrame(human.headFrame)
                            .build()
                        lookAt.async().run()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "LookAt failed (ignoring): ${e.message}")
                }
                // Verbal Countdown
                speakAndWait("3...")
                speakAndWait("2...")
                speakAndWait("1...")
                
                // Play a shutter beep
                Log.d(TAG, "📸 Playing shutter beep...")
                withContext(Dispatchers.Main) {
                    try {
                        toneGenerator.startTone(ToneGenerator.TONE_DTMF_0, 300)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Beep failed: ${e.message}")
                    }
                }
                delay(300) 
                
                // Caputre using VisionManager
            val dataBuffer = visionManager.captureImage(ctx) ?: throw Exception("Failed to capture image")
            
            dataBuffer.rewind()
            val pixels = ByteArray(dataBuffer.remaining())
            dataBuffer.get(pixels)
            
            // Show captured photo as fullscreen feedback
            val bitmap = BitmapFactory.decodeByteArray(pixels, 0, pixels.size)
            withContext(Dispatchers.Main) {
                binding.viewfinderOverlay.setImageBitmap(bitmap)
                binding.thinkingLabel.text = "Analyzing your photo..."
                binding.statusOverlayText.text = "Analyzing your photo..."
            }
            
            robotManager.setLedStatus("thinking")
            // speakAndWait("Can I take a picture of you?") -- Removed as per user request

            // Step 1: Ask GPT-4o Vision
            Log.d(TAG, "📸 Sending to GPT-4o Vision...")
            val description = visionManager.analyzeImage(pixels, prompt)
            Log.d(TAG, "📸 Vision description: $description")

            // Step 2: Generate image
            robotManager.setLedStatus("drawing")
            Log.d(TAG, "Sending to GPT Image...")
            withContext(Dispatchers.Main) {
                binding.thinkingLabel.text = "Creating your transformation..."
                binding.statusOverlayText.text = "Creating transformation..."
            }
                val imagePrompt = "A creative transformation of this person: $description. User wants to be imagined as: $prompt. Style: Cinematic, high quality."
                val (imageRef, imagePath) = generateOpenAiImage(apiKey, imagePrompt, quality = "low", cachePrefix = "transform")
                if (imagePath == null) throw Exception("Image generation failed")

                withContext(Dispatchers.Main) {
                    showFullscreenImage(imageRef)

                    val activityData = mutableMapOf(
                        "EXTRA_CONTENT" to "I've imagined you as $prompt! What do you think?",
                        "EXTRA_IMAGE_URL" to imageRef,
                        "EXTRA_MODE" to "transform"
                    )
                    activityData["EXTRA_IMAGE_PATH"] = imagePath
                    updateConversationUI(prompt, "[Photo Transformation started]", imageRef, "transform", activityData)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Transformation failed: ${e.stackTraceToString()}")
            val errorMsg = "Photo Error: ${e.message}"
            withContext(Dispatchers.Main) {
                binding.statusOverlayText.text = errorMsg
                binding.statusOverlayText.setBackgroundColor(Color.RED)
                binding.statusOverlayText.visibility = View.VISIBLE
            }
            speakAndWait("I encountered an error: ${e.message}")
            delay(5000) // Allow user to read error
        } finally {
            // ALWAYS cleanup UI
            withContext(Dispatchers.Main) {
                binding.viewfinderOverlay.visibility = View.GONE
                binding.statusOverlayText.visibility = View.GONE
                binding.statusOverlayText.setBackgroundColor(Color.parseColor("#80000000")) // Reset background
                hideSpinner()
                robotManager.ensureInteractiveState()
                if (wasListeningBeforeSpeaking) startListening()
            }
        }
    }

    
    private suspend fun handleRadioRequest(message: String) {
        val lower = message.toLowerCase(Locale.getDefault())
        if (lower.contains("stop")) {
            radioManager.stop()
            speakAndWait("Stopping the music.")
            withContext(Dispatchers.Main) { startListening() }
        } else {
            val url: String
            val stationName: String
            
            if (lower.contains("pop")) {
                url = "http://listen.181fm.com/181-power_128k.mp3" // This URL is already HTTP
                stationName = "Top 40 Pop"
            } else if (lower.contains("synthwave") || lower.contains("nightride")) {
                // Use SomaFM Underground 80s (HTTP) for better compatibility than Nightride HTTPS
                url = "http://ice1.somafm.com/u80s-128-mp3"
                stationName = "Synthwave (SomaFM)"
            } else {
                // Default: SomaFM
                url = "http://ice1.somafm.com/u80s-128-mp3"
                stationName = "Synthwave (SomaFM)"
            }

            // Launch Fullscreen Radio Activity
            runOnUiThread {
                val intent = Intent(this@MainActivity, RadioActivity::class.java).apply {
                    putExtra("EXTRA_STATION_NAME", stationName)
                    putExtra("EXTRA_URL", url)
                }
                startActivity(intent)

            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun showFullscreenImage(url: String, description: String? = null) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_fullscreen_image)
        val imageView = dialog.findViewById<ImageView>(R.id.fullscreen_image_view)
        val closeButton = dialog.findViewById<View>(R.id.btn_close_fullscreen)

        val request = when {
            url.startsWith("file://") -> Picasso.get().load(File(url.removePrefix("file://")))
            url.startsWith("/") -> Picasso.get().load(File(url))
            else -> Picasso.get().load(url)
        }
        request.into(imageView)
        
        closeButton.setOnClickListener { dialog.dismiss() }
        imageView.setOnClickListener { dialog.dismiss() }
        
        dialog.show()
    }

    private fun relaunchActivity(msg: ChatMessage) {
        val type = msg.activityType ?: return
        val data = msg.activityData ?: return
        
        val intent = when (type) {
            "weather" -> Intent(this, WeatherModeActivity::class.java)
            "story", "recipe", "transform" -> Intent(this, ImmersiveModeActivity::class.java)
            else -> return
        }
        
        
        for ((key, value) in data) {
            intent.putExtra(key, value)
        }
        
        wasListeningBeforeSpeaking = isListening
        stopListening()
        hideSpinner()
        startActivityForResult(intent, REQUEST_IMMERSIVE)
    }
}
