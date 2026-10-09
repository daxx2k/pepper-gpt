package com.softbankrobotics.pepper.pepperGPT

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.builder.SayBuilder
import com.aldebaran.qi.sdk.design.activity.RobotActivity
import com.squareup.picasso.Picasso
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

class ImmersiveModeActivity : RobotActivity(), RobotLifecycleCallbacks {
    private val TAG = "ImmersiveMode"
    private lateinit var textView: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var imageView: ImageView
    private lateinit var placeholder: ImageView
    private lateinit var placeholderLabel: TextView
    private lateinit var loading: View

    private var wordCount = 0
    private var imageLoaded = false
    private var qiContextCached: QiContext? = null
    private var speechStarted = false
    private var mode = "story"

    private val sceneImagePaths = mutableMapOf<Int, String>()
    private val scenePrompts = mutableListOf<String>()
    private val sceneTexts = mutableListOf<String>()
    private var sceneStartWords = listOf(0)
    private var currentSceneIndex = 0
    private var currentWordIndex = 0

    private val uiHandler = Handler(Looper.getMainLooper())
    private val speechController by lazy { SpeechController(this) }
    private var scrollJob: Runnable? = null
    private var scrollDurationMs = 60_000L
    private var scrollStartMs = 0L
    private var speechRunning = false
    private var userScrollActive = false
    private var storySpeechJob: Job? = null
    private val closeRunnable = Runnable { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_immersive_mode)
        supportActionBar?.hide()
        QiSDK.register(this, this)

        val content = intent.getStringExtra("EXTRA_CONTENT") ?: ""
        val imageUrl = intent.getStringExtra("EXTRA_IMAGE_URL") ?: ""
        val imagePath = intent.getStringExtra("EXTRA_IMAGE_PATH") ?: ""
        mode = intent.getStringExtra("EXTRA_MODE") ?: "story"
        val theme = intent.getStringExtra("EXTRA_THEME") ?: "glass"

        textView = findViewById(R.id.story_text)
        scrollView = findViewById(R.id.story_scroll_view)
        imageView = findViewById(R.id.story_illustration)
        placeholder = findViewById(R.id.story_placeholder)
        placeholderLabel = findViewById(R.id.story_placeholder_label)
        loading = findViewById(R.id.story_loading)
        setupScrollTouchHandling()

        loadSceneConfig(imagePath)
        prepareContent(content)

        placeholderLabel.text = when (mode) {
            "recipe" -> "Plating your dish..."
            "story" -> "Painting scene 1..."
            else -> "Painting your illustration..."
        }

        applyTheme(theme)

        val localFile = resolveImageFile(imagePath, imageUrl)
        when {
            localFile != null -> {
                sceneImagePaths[0] = localFile.absolutePath
                loadIllustration(Picasso.get().load(localFile), startSpeechWhenDone = true)
            }
            mode == "story" && scenePrompts.isNotEmpty() -> {
                generateSceneImage(0, startSpeechWhenDone = true)
            }
            imageUrl.isNotEmpty() -> loadIllustration(Picasso.get().load(imageUrl), startSpeechWhenDone = true)
            else -> {
                loading.visibility = View.GONE
                placeholderLabel.text = when (mode) {
                    "recipe" -> "No illustration — enjoy the recipe!"
                    else -> "No illustration — enjoy the story!"
                }
                imageLoaded = true
                startStorySpeech()
            }
        }

        if (mode == "story" && scenePrompts.size > 1) {
            val missing = scenePrompts.indices.any { !sceneImagePaths.containsKey(it) }
            if (missing) generateRemainingScenesInBackground()
        }

        findViewById<View>(R.id.btn_close_immersive).setOnClickListener { finish() }
        findViewById<View>(android.R.id.content).setOnClickListener { finish() }
        scheduleAutoClose(estimatedSpeechMs(content))
    }

    private fun prepareContent(text: String) {
        wordCount = text.split(Regex("\\s+")).filter { it.isNotEmpty() }.size.coerceAtLeast(1)
        textView.text = text
    }

    private fun loadSceneConfig(firstImagePath: String) {
        val promptsJson = intent.getStringExtra("EXTRA_SCENE_PROMPTS") ?: ""
        if (promptsJson.isNotEmpty()) {
            try {
                val arr = JSONArray(promptsJson)
                for (i in 0 until arr.length()) scenePrompts.add(arr.getString(i))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse scene prompts", e)
            }
        }
        val startWords = intent.getStringExtra("EXTRA_SCENE_START_WORDS") ?: ""
        if (startWords.isNotEmpty()) {
            sceneStartWords = startWords.split(",").mapNotNull { it.trim().toIntOrNull() }
        }
        if (firstImagePath.isNotEmpty()) {
            sceneImagePaths[0] = firstImagePath
        }
        val pathsJson = intent.getStringExtra("EXTRA_SCENE_PATHS") ?: ""
        if (pathsJson.isNotEmpty()) {
            try {
                val arr = JSONArray(pathsJson)
                for (i in 0 until arr.length()) {
                    val path = arr.optString(i, "")
                    if (path.isNotEmpty()) sceneImagePaths[i] = path
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse scene paths", e)
            }
        }
        val textsJson = intent.getStringExtra("EXTRA_SCENE_TEXTS") ?: ""
        if (textsJson.isNotEmpty()) {
            try {
                val arr = JSONArray(textsJson)
                for (i in 0 until arr.length()) sceneTexts.add(arr.getString(i))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse scene texts", e)
            }
        }
    }

    private fun estimatedSpeechMs(text: String): Long {
        val speed = SpeechDurationHelper.speedForMode(mode)
        if (mode == "story" && sceneTexts.isNotEmpty()) {
            return SpeechDurationHelper.estimateStoryMs(sceneTexts, speed)
        }
        val pauseMs = if (mode == "story") SpeechDurationHelper.STORY_PARAGRAPH_PAUSE_MS else 0
        return SpeechDurationHelper.estimateMs(text, speed, pauseMs)
    }

    private fun scheduleAutoClose(speechDurationMs: Long) {
        uiHandler.removeCallbacks(closeRunnable)
        val closeDelay = (speechDurationMs + 60_000L).coerceIn(120_000L, 420_000L)
        uiHandler.postDelayed(closeRunnable, closeDelay)
    }

    private fun applyTheme(theme: String) {
        when (theme) {
            "glass" -> {
                findViewById<View>(R.id.immersive_root).setBackgroundResource(R.color.glass_bg)
                textView.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.glass_text))
            }
            "dark" -> {
                findViewById<View>(R.id.immersive_root).setBackgroundResource(R.color.dark_bg)
                textView.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.dark_text))
            }
            "soft" -> {
                findViewById<View>(R.id.immersive_root).setBackgroundResource(R.color.soft_bg)
                textView.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.soft_text))
            }
        }
    }

    private fun resolveImageFile(imagePath: String, imageUrl: String): File? {
        val path = when {
            imagePath.isNotEmpty() -> imagePath
            imageUrl.startsWith("file://") -> imageUrl.removePrefix("file://")
            else -> ""
        }
        return path.takeIf { it.isNotEmpty() }?.let { File(it) }?.takeIf { it.exists() }
    }

    private fun generateRemainingScenesInBackground() {
        val apiKey = ImageGenerationHelper.readApiKey(this)
        if (apiKey.isBlank()) return
        for (index in 1 until scenePrompts.size) {
            if (sceneImagePaths.containsKey(index)) continue
            lifecycleScope.launch(Dispatchers.IO) {
                val (_, path) = ImageGenerationHelper.generate(
                    this@ImmersiveModeActivity,
                    apiKey,
                    scenePrompts[index],
                    quality = ImageGenerationHelper.STORY_QUALITY,
                    model = ImageGenerationHelper.STORY_MODEL,
                    cachePrefix = "scene$index"
                )
                if (path != null) {
                    sceneImagePaths[index] = path
                    Log.w(TAG, "Background scene ${index + 1} ready")
                    uiHandler.post {
                        if (index == currentSceneIndex) {
                            showSceneImage(index)
                        }
                    }
                }
            }
        }
    }

    private fun generateSceneImage(index: Int, startSpeechWhenDone: Boolean) {
        val apiKey = ImageGenerationHelper.readApiKey(this)
        if (apiKey.isBlank() || index >= scenePrompts.size) {
            imageLoaded = true
            if (startSpeechWhenDone) startStorySpeech()
            return
        }
        loading.visibility = View.VISIBLE
        placeholderLabel.text = "Painting scene ${index + 1}..."
        lifecycleScope.launch(Dispatchers.IO) {
            val (_, path) = ImageGenerationHelper.generate(
                this@ImmersiveModeActivity,
                apiKey,
                scenePrompts[index],
                quality = ImageGenerationHelper.STORY_QUALITY,
                model = ImageGenerationHelper.STORY_MODEL,
                cachePrefix = "scene$index"
            )
            uiHandler.post {
                if (path != null) {
                    sceneImagePaths[index] = path
                    loadIllustration(Picasso.get().load(File(path)), startSpeechWhenDone)
                } else {
                    loading.visibility = View.GONE
                    imageLoaded = true
                    if (startSpeechWhenDone) startStorySpeech()
                }
            }
        }
    }

    private fun loadIllustration(request: com.squareup.picasso.RequestCreator, startSpeechWhenDone: Boolean) {
        loading.visibility = View.VISIBLE
        request
            .error(R.drawable.ic_book_placeholder)
            .into(imageView, object : com.squareup.picasso.Callback {
                override fun onSuccess() {
                    loading.visibility = View.GONE
                    placeholder.visibility = View.GONE
                    placeholderLabel.visibility = View.GONE
                    imageView.visibility = View.VISIBLE
                    imageLoaded = true
                    if (startSpeechWhenDone) startStorySpeech()
                }

                override fun onError(e: Exception?) {
                    Log.w(TAG, "Illustration load failed: ${e?.message}")
                    loading.visibility = View.GONE
                    imageView.visibility = View.GONE
                    placeholder.visibility = View.VISIBLE
                    placeholderLabel.visibility = View.VISIBLE
                    placeholderLabel.text = "Illustration unavailable"
                    imageLoaded = true
                    if (startSpeechWhenDone) startStorySpeech()
                }
            })
    }

    private fun setupScrollTouchHandling() {
        scrollView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    if (!userScrollActive) {
                        userScrollActive = true
                        stopGentleScrolling()
                        Log.w(TAG, "User took over scrolling")
                    }
                }
            }
            false
        }
    }

    private fun showSceneImage(sceneIndex: Int) {
        val path = sceneImagePaths[sceneIndex]
        if (path == null) {
            currentSceneIndex = sceneIndex
            placeholderLabel.visibility = View.VISIBLE
            placeholderLabel.text = "Painting scene ${sceneIndex + 1}..."
            return
        }
        if (sceneIndex == currentSceneIndex && imageView.visibility == View.VISIBLE && imageView.drawable != null) return
        currentSceneIndex = sceneIndex
        placeholderLabel.text = "Scene ${sceneIndex + 1} of ${scenePrompts.size.coerceAtLeast(1)}"
        imageView.animate().alpha(0f).setDuration(180).withEndAction {
            Picasso.get().load(File(path)).into(imageView, object : com.squareup.picasso.Callback {
                override fun onSuccess() {
                    placeholder.visibility = View.GONE
                    imageView.visibility = View.VISIBLE
                    imageView.animate().alpha(1f).setDuration(280).start()
                }
                override fun onError(e: Exception?) {
                    imageView.animate().alpha(1f).setDuration(100).start()
                }
            })
        }.start()
    }

    private fun maybeAdvanceScene(wordIndex: Int) {
        if (mode != "story" || sceneStartWords.isEmpty()) return
        var target = 0
        for (i in sceneStartWords.indices.reversed()) {
            if (wordIndex >= sceneStartWords[i]) {
                target = i
                break
            }
        }
        if (target != currentSceneIndex && sceneImagePaths.containsKey(target)) {
            showSceneImage(target)
        } else if (target != currentSceneIndex && !sceneImagePaths.containsKey(target)) {
            placeholderLabel.visibility = View.VISIBLE
            placeholderLabel.text = "Painting scene ${target + 1}..."
        }
    }

    private val touchStop = TouchStopController()

    override fun onRobotFocusGained(qiContext: QiContext) {
        touchStop.bind(qiContext) {
            storySpeechJob?.cancel()
            speechController.stop()
            speechRunning = false
            stopGentleScrolling()
        }
        qiContextCached = qiContext
        startStorySpeech()
    }

    private fun startStorySpeech() {
        val qiContext = qiContextCached ?: return
        if (!imageLoaded || speechStarted) return
        speechStarted = true

        val plainText = intent.getStringExtra("EXTRA_CONTENT") ?: ""
        if (plainText.isEmpty()) return

        scheduleAutoClose(estimatedSpeechMs(plainText))

        if (mode == "story" && sceneTexts.isNotEmpty()) {
            textView.post { speakStoryByScenes(qiContext) }
            return
        }

        val speed = SpeechDurationHelper.speedForMode(mode)
        val speechText = SpeechDurationHelper.formatForSpeech(plainText, speed, mode == "story")
        scrollDurationMs = SpeechDurationHelper.estimateScrollMs(plainText, mode, sceneTexts)
        storySpeechJob = lifecycleScope.launch {
            speechRunning = true
            scrollStartMs = SystemClock.elapsedRealtime()
            startGentleScrolling()
            try { speechController.speak(speechText, qiContext) }
            finally {
                speechRunning = false
                if (!userScrollActive) finishGentleScrolling()
            }
        }
    }

    private suspend fun speakSceneText(qiContext: QiContext, text: String): Boolean =
        speechController.speak(text, qiContext)

    private fun speakStoryByScenes(qiContext: QiContext) {
        storySpeechJob?.cancel()
        val speed = SpeechDurationHelper.speedForMode(mode)
        storySpeechJob = lifecycleScope.launch {
            speechRunning = true
            try {
                for (i in sceneTexts.indices) {
                    if (!isActive) break
                    val ctx = qiContextCached ?: break
                    showSceneImage(i)
                    val sceneDuration = SpeechDurationHelper.estimateSceneMs(sceneTexts[i], speed)
                    startSceneScroll(i, (sceneDuration * SpeechDurationHelper.SCROLL_DURATION_FACTOR).toLong())

                    val spoken = speakSceneText(ctx, SpeechDurationHelper.formatScene(sceneTexts[i], speed))
                    stopGentleScrolling()
                    if (!spoken) break
                    if (i < sceneTexts.size - 1) {
                        delay(SpeechDurationHelper.SCENE_BREAK_MS)
                    }
                }
            } finally {
                speechRunning = false
                if (!userScrollActive) finishGentleScrolling()
            }
        }
    }

    private fun scrollYOffsetForScene(sceneIndex: Int): Int {
        if (sceneTexts.isEmpty()) return 0
        val layout = textView.layout ?: return 0
        val fullText = textView.text.toString()
        if (sceneIndex <= 0) return 0
        val textBefore = sceneTexts.take(sceneIndex).joinToString("\n\n")
        val offset = textBefore.length.coerceIn(0, fullText.length)
        val line = layout.getLineForOffset(offset)
        return layout.getLineTop(line)
    }

    private fun maxScrollY(): Int {
        val contentHeight = textView.height
        val viewport = scrollView.height
        return (contentHeight - viewport).coerceAtLeast(0)
    }

    private fun startSceneScroll(sceneIndex: Int, durationMs: Long) {
        if (userScrollActive) return
        stopGentleScrolling()
        textView.post {
            if (textView.layout == null) return@post
            val startY = scrollYOffsetForScene(sceneIndex)
            val endY = if (sceneIndex + 1 < sceneTexts.size) {
                scrollYOffsetForScene(sceneIndex + 1)
            } else {
                maxScrollY()
            }
            if (endY <= startY) {
                scrollView.scrollTo(0, startY)
                return@post
            }
            scrollView.scrollTo(0, startY)
            scrollStartMs = SystemClock.elapsedRealtime()
            val tickMs = 80L
            scrollJob = object : Runnable {
                override fun run() {
                    if (userScrollActive) return
                    val elapsed = SystemClock.elapsedRealtime() - scrollStartMs
                    val progress = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
                    val y = (startY + (endY - startY) * progress).toInt()
                    scrollView.scrollTo(0, y)
                    if (progress < 1f && speechRunning) {
                        uiHandler.postDelayed(this, tickMs)
                    }
                }
            }
            uiHandler.post(scrollJob!!)
        }
    }

    private fun startGentleScrolling() {
        if (userScrollActive) return
        stopGentleScrolling()
        currentWordIndex = 0
        scrollView.post {
            val contentHeight = textView.height
            val viewport = scrollView.height
            val maxScroll = (contentHeight - viewport).coerceAtLeast(0)
            if (maxScroll == 0) return@post

            val tickMs = 80L
            val speed = SpeechDurationHelper.speedForMode(mode)
            scrollJob = object : Runnable {
                override fun run() {
                    if (userScrollActive) return
                    val elapsed = SystemClock.elapsedRealtime() - scrollStartMs
                    val progress = if (speechRunning) {
                        if (mode == "story" && sceneTexts.isNotEmpty()) {
                            SpeechDurationHelper.scrollProgressForStory(elapsed, sceneTexts, speed)
                        } else {
                            (elapsed.toFloat() / scrollDurationMs).coerceIn(0f, 0.90f)
                        }
                    } else {
                        1f
                    }
                    val targetY = (maxScroll * progress).toInt()
                    scrollView.scrollTo(0, targetY)
                    val wordStep = (wordCount * progress).toInt()
                    maybeAdvanceScene(wordStep)
                    currentWordIndex = wordStep
                    if (speechRunning && progress < 0.98f) {
                        uiHandler.postDelayed(this, tickMs)
                    }
                }
            }
            uiHandler.post(scrollJob!!)
        }
    }

    private fun finishGentleScrolling() {
        scrollView.post {
            val contentHeight = textView.height
            val viewport = scrollView.height
            val maxScroll = (contentHeight - viewport).coerceAtLeast(0)
            scrollView.scrollTo(0, maxScroll)
            maybeAdvanceScene(wordCount)
            currentWordIndex = wordCount
        }
        stopGentleScrolling()
    }

    private fun stopGentleScrolling() {
        scrollJob?.let { uiHandler.removeCallbacks(it) }
        scrollJob = null
    }

    override fun onRobotFocusLost() {
        touchStop.clear()
        Log.w(TAG, "Robot focus lost — stopping story speech")
        qiContextCached = null
        storySpeechJob?.cancel()
        speechController.stop()
        speechRunning = false
        stopGentleScrolling()
    }

    override fun onRobotFocusRefused(reason: String?) {
        Log.w(TAG, "Robot focus refused: $reason")
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    override fun onDestroy() {
        speechController.close()
        storySpeechJob?.cancel()
        stopGentleScrolling()
        uiHandler.removeCallbacks(closeRunnable)
        QiSDK.unregister(this, this)
        super.onDestroy()
    }
}
