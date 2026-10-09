package com.softbankrobotics.pepper.pepperGPT

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.aldebaran.qi.AnyObject
import com.aldebaran.qi.Future
import com.aldebaran.qi.Session
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.builder.AnimationBuilder
import com.aldebaran.qi.sdk.builder.AnimateBuilder
import com.aldebaran.qi.sdk.builder.SayBuilder
import kotlinx.coroutines.*

class RadioActivity : AppCompatActivity(), RobotLifecycleCallbacks {

    private lateinit var stationNameText: TextView
    private lateinit var statusText: TextView
    private lateinit var visualizerView: MusicVisualizerView
    private lateinit var stopButton: Button
    private lateinit var stationUrl: String
    
    private val speechController by lazy { SpeechController(this) }
    private var speechJob: Job? = null
    private val radioManager = RadioManager()
    private var qiContext: QiContext? = null
    private var danceJob: Job? = null
    private var currentAnimFuture: Future<Void>? = null
    private val radioScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var danceEnabled = true
    
    // Dance animations from centralised AnimationManager
    private val danceAnims = AnimationManager.DANCE_ANIMS
    
    private var currentUiLevel = 10 // Default start at 100%
    private val MAX_UI_LEVEL = 10
    private val MIN_STREAM_LEVEL = 6 // Empirical minimum for head speakers
    private var originalVolume: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_radio)
        
        QiSDK.register(this, this)
        
        // --- Volume Setup ---
        val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        originalVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        
        // Apply initial volume
        applyVolume(audioManager)
        
        // Apply theme immediately
        applyTheme()
        
        stationNameText = findViewById(R.id.stationName)
        
        // Apply theme immediately
        applyTheme()
        
        statusText = findViewById(R.id.statusText)
        visualizerView = findViewById(R.id.visualizerView)
        stopButton = findViewById(R.id.stopButton)
        
        val stationName = intent.getStringExtra("EXTRA_STATION_NAME") ?: "Unknown Station"
        stationUrl = intent.getStringExtra("EXTRA_URL") ?: ""
        
        stationNameText.text = stationName
        
        // Start playing visualizer immediately? No, wait for status.
        visualizerView.setPlaying(true) 
        
        radioManager.setListener(object : RadioManager.RadioListener {
            override fun onStatusChange(status: String, isError: Boolean) {
                runOnUiThread {
                    statusText.text = status
                    if (isError) {
                        visualizerView.setColor(android.graphics.Color.RED)
                        visualizerView.setPlaying(false)
                        stopDancing()
                        RobotManager.getInstance(this@RadioActivity).stopRadioLedAnimation()
                    } else if (status.contains("Playing")) {
                        visualizerView.setColor(android.graphics.Color.GREEN)
                        visualizerView.setPlaying(true)
                        RobotManager.getInstance(this@RadioActivity).startRadioLedAnimation()
                        if (danceEnabled) startDancing()
                    } else {
                        // Buffering
                        visualizerView.setColor(android.graphics.Color.YELLOW)
                        visualizerView.setPlaying(true)
                        stopDancing()
                    }
                }
            }
        })

        if (stationUrl.isNotEmpty()) {
            radioManager.play(stationUrl)
        } else {
            statusText.text = "Error: No URL provided."
        }
        
        // --- Volume Controls ---
        val volText = findViewById<TextView>(R.id.volLevel)
        updateVolText(volText)

        findViewById<Button>(R.id.volUp).setOnClickListener {
            if (currentUiLevel < MAX_UI_LEVEL) {
                currentUiLevel++
                applyVolume(audioManager)
                updateVolText(volText)
            }
        }
        
        findViewById<Button>(R.id.volDown).setOnClickListener {
            if (currentUiLevel > 0) {
                currentUiLevel--
                applyVolume(audioManager)
                updateVolText(volText)
            }
        }
        
        // --- Station Controls ---
        findViewById<Button>(R.id.btnSynth).setOnClickListener {
            changeStation("Synthwave (SomaFM)", "http://ice1.somafm.com/u80s-128-mp3", "#9C27B0")
        }
        findViewById<Button>(R.id.btnPop).setOnClickListener {
            changeStation("Top 40 Pop", "http://listen.181fm.com/181-power_128k.mp3", "#E91E63")
        }
        findViewById<Button>(R.id.btnLofi).setOnClickListener {
            changeStation("Chill / Lofi", "http://ice1.somafm.com/groovesalad-128-mp3", "#00BCD4")
        }
        findViewById<Button>(R.id.btnRetro).setOnClickListener {
            changeStation("80s Classics", "http://listen.181fm.com/181-80scountry_128k.mp3", "#FF9800")
        }
        findViewById<Button>(R.id.btnAmbient).setOnClickListener {
            changeStation("Chill / Ambient", "http://ice1.somafm.com/dronezone-128-mp3", "#4CAF50")
        }
        findViewById<Button>(R.id.btnJazz).setOnClickListener {
            changeStation("Jazz Now", "http://listen.181fm.com/181-jazz_128k.mp3", "#795548")
        }
        
        stopButton.setOnClickListener {
            stopRadio()
            finish() // Return to chat
        }
        
        // Dance toggle
        val prefs = getSharedPreferences("PepperGPT_Prefs", android.content.Context.MODE_PRIVATE)
        danceEnabled = prefs.getBoolean("dance_enabled", true)
        val danceBtn = findViewById<Button>(R.id.btnDanceToggle)
        updateDanceButton(danceBtn)
        danceBtn.setOnClickListener {
            danceEnabled = !danceEnabled
            prefs.edit().putBoolean("dance_enabled", danceEnabled).apply()
            updateDanceButton(danceBtn)
            if (danceEnabled && statusText.text.toString().contains("Playing")) {
                startDancing()
            } else {
                stopDancing()
            }
        }
    }
    
    private fun updateDanceButton(btn: Button) {
        if (danceEnabled) {
            btn.text = "🕺 Dance: ON"
            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#9C27B0"))
        } else {
            btn.text = "🕺 Dance: OFF"
            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#666666"))
        }
    }
    
    private fun applyVolume(am: android.media.AudioManager) {
        val maxStream = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        // Map 0-10 to MIN_STREAM_LEVEL-MAX
        // Level 0 = Mute (0)
        // Level 1 = MIN_STREAM_LEVEL
        // Level 10 = maxStream
        
        val target = if (currentUiLevel == 0) 0 else {
            val range = maxStream - MIN_STREAM_LEVEL
            val step = range.toFloat() / (MAX_UI_LEVEL - 1) // Distribute over 1-10 (9 steps)
            MIN_STREAM_LEVEL + ((currentUiLevel - 1) * step).toInt()
        }
        
        am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, target, 0)
    }
    
    private fun updateVolText(tv: TextView) {
        tv.text = "${currentUiLevel * 10}%"
    }

    private fun changeStation(name: String, url: String, colorHex: String) {
        stationNameText.text = name
        statusText.text = "Changing Station..."
        visualizerView.setColor(android.graphics.Color.parseColor(colorHex))
        radioManager.play(url)
    }

    private fun stopRadio() {
        radioManager.stop()
        visualizerView.setPlaying(false)
        stopDancing()
        RobotManager.getInstance(this).stopRadioLedAnimation()
    }

    private fun startDancing() {
        val qia = qiContext ?: return
        if (danceJob != null) return
        if (!danceEnabled) return
        
        android.util.Log.d("RadioActivity", "Starting dance animations...")
        danceJob = radioScope.launch(Dispatchers.IO) {
            try {
                while (isActive && danceEnabled) {
                    val animResId = danceAnims.random()
                    try {
                        val animation = AnimationBuilder.with(qia)
                            .withResources(animResId)
                            .build()
                        val animate = AnimateBuilder.with(qia)
                            .withAnimation(animation)
                            .build()
                        currentAnimFuture = animate.async().run()
                        // Wait for animation to finish
                        currentAnimFuture?.get()
                        // Small pause between animations
                        delay(500)
                    } catch (e: Exception) {
                        android.util.Log.w("RadioActivity", "Dance anim failed: ${e.message}")
                        delay(1000) // Retry after a bit
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("RadioActivity", "Dance loop error: ${e.message}")
            }
        }
    }

    private fun stopDancing() {
        danceJob?.cancel()
        danceJob = null
        @Suppress("DEPRECATION") currentAnimFuture?.cancel()
        currentAnimFuture = null
    }

    // LED animations now handled by RobotManager via SSH
    
    
    // Pulse animation removed in favor of MusicVisualizerView
    
    override fun onResume() {
        super.onResume()
        hideSystemUI()
        applyTheme()
    }

    private fun applyTheme() {
        val prefs = getSharedPreferences("PepperGPT_Prefs", android.content.Context.MODE_PRIVATE)
        val theme = prefs.getString("ui_theme", "glass") ?: "glass"
        
        val scrollRoot = findViewById<View>(R.id.radioScrollRoot)
        val stationText = findViewById<TextView>(R.id.stationName)
        val statusTxt = findViewById<TextView>(R.id.statusText)
        val volTxt = findViewById<TextView>(R.id.volLevel)
        val stopBtn = findViewById<Button>(R.id.stopButton)
        val volDown = findViewById<Button>(R.id.volDown)
        val volUp = findViewById<Button>(R.id.volUp)
        
        val bgColor: Int
        val textColor: Int
        val volBtnColor: Int
        
        when (theme) {
            "dark" -> {
                bgColor = androidx.core.content.ContextCompat.getColor(this, R.color.dark_bg)
                textColor = androidx.core.content.ContextCompat.getColor(this, R.color.dark_text)
                volBtnColor = android.graphics.Color.parseColor("#333333")
            }
            "soft" -> {
                bgColor = androidx.core.content.ContextCompat.getColor(this, R.color.soft_bg)
                textColor = androidx.core.content.ContextCompat.getColor(this, R.color.soft_text)
                volBtnColor = android.graphics.Color.parseColor("#D8DEE9")
            }
            else -> { // glass
                bgColor = androidx.core.content.ContextCompat.getColor(this, R.color.glass_bg)
                textColor = androidx.core.content.ContextCompat.getColor(this, R.color.glass_text)
                volBtnColor = android.graphics.Color.parseColor("#555555")
            }
        }
        
        scrollRoot.setBackgroundColor(bgColor)
        stationText.setTextColor(textColor)
        statusTxt.setTextColor(textColor)
        volTxt.setTextColor(textColor)
        volDown.backgroundTintList = android.content.res.ColorStateList.valueOf(volBtnColor)
        volUp.backgroundTintList = android.content.res.ColorStateList.valueOf(volBtnColor)
        volDown.setTextColor(textColor)
        volUp.setTextColor(textColor)
        stopBtn.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FF4444"))
        stopBtn.setTextColor(android.graphics.Color.WHITE)
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
        radioScope.cancel()
        stopRadio()
        QiSDK.unregister(this, this)
        super.onDestroy()

        // Restore volume
        if (originalVolume != -1) {
            val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, originalVolume, 0)
        }
    }

    private val touchStop = TouchStopController()

    override fun onRobotFocusGained(qiContext: QiContext) {
        touchStop.bind(qiContext) {
            speechJob?.cancel()
            speechController.stop()
        }
        this.qiContext = qiContext

        // --- VOLUME BALANCE: Quiet speech, loud music ---
        val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        
        // Lower volume for speech (User says 7 was too loud, trying 5/15)
        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 5, 0)
        
        val station = stationNameText.text.toString()
        val currentStationUrl = this.stationUrl
        
        speechJob = radioScope.launch {
            if (speechController.speak("Playing $station", qiContext) && this@RadioActivity.qiContext === qiContext) {
                applyVolume(audioManager)
                radioManager.play(currentStationUrl)
            }
        }
    }

    override fun onRobotFocusLost() {
        touchStop.clear()
        this.qiContext = null
        speechJob?.cancel()
        speechController.stop()
        stopDancing()
    }

    override fun onRobotFocusRefused(reason: String) {}

    // getSession removed — LED animations now handled by RobotManager
}
