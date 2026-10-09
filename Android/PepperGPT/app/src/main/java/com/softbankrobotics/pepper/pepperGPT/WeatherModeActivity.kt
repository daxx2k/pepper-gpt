package com.softbankrobotics.pepper.pepperGPT

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.graphics.Color
import android.widget.TextView
import android.os.Handler
import android.os.Looper
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.builder.SayBuilder
import com.aldebaran.qi.sdk.design.activity.RobotActivity
import com.squareup.picasso.Picasso
import java.text.SimpleDateFormat
import java.util.*

class WeatherModeActivity : RobotActivity(), RobotLifecycleCallbacks {
    private val speechController by lazy { SpeechController(this) }
    private var speechJob: Job? = null

    private val animationManager = AnimationManager()
    private val uiHandler = Handler(Looper.getMainLooper())
    private val closeRunnable = Runnable { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather_mode)
        supportActionBar?.hide()
        QiSDK.register(this, this)

        val city = intent.getStringExtra("EXTRA_CITY") ?: "Unknown"
        val temp = intent.getStringExtra("EXTRA_TEMP") ?: "--"
        val desc = intent.getStringExtra("EXTRA_DESC") ?: "No data"
        val icon = intent.getStringExtra("EXTRA_ICON") ?: "01d"
        val theme = intent.getStringExtra("EXTRA_THEME") ?: "glass"

        findViewById<TextView>(R.id.weather_city).text = city
        findViewById<TextView>(R.id.weather_temp).text = "${temp}°C"
        findViewById<TextView>(R.id.weather_description).text = desc
        
        val dateStr = SimpleDateFormat("EEEE, d MMM yyyy", Locale.getDefault()).format(Date())
        val dateView = findViewById<TextView>(R.id.weather_date)
        if (dateView != null) {
            dateView.text = dateStr
        }

        // Apply theme safely
        val cityView = findViewById<TextView>(R.id.weather_city)
        val tempView = findViewById<TextView>(R.id.weather_temp)
        val descView = findViewById<TextView>(R.id.weather_description)
        val root = findViewById<View>(R.id.weather_root)
        
        if (root != null) {
            when (theme) {
                "glass" -> {
                    root.setBackgroundResource(R.color.glass_bg)
                    cityView?.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.glass_text))
                    tempView?.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.glass_text))
                    descView?.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.glass_text))
                    dateView?.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.glass_text))
                }
                "dark" -> {
                    root.setBackgroundResource(R.color.dark_bg)
                    cityView?.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.dark_text))
                    tempView?.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.dark_text))
                    descView?.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.dark_text))
                    dateView?.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.dark_text))
                }
                "soft" -> {
                    root.setBackgroundResource(R.color.soft_bg)
                    cityView?.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.soft_text))
                    tempView?.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.soft_text))
                    descView?.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.soft_text))
                    dateView?.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.soft_text))
                }
            }
        }

        val iconUrl = "https://openweathermap.org/img/wn/${icon}@4x.png"
        val iconView = findViewById<ImageView>(R.id.weather_icon)
        if (iconView != null) {
            Picasso.get().load(iconUrl).into(iconView)
        }
        
        // Remove LoremFlickr for reliability as requested
        val bgView = findViewById<ImageView>(R.id.weather_background)
        if (bgView != null) {
            bgView.setImageResource(android.R.color.transparent)
            bgView.setBackgroundColor(Color.parseColor("#33000000")) // Subtle overlay
        }

        // Close buttons
        findViewById<View>(R.id.btn_close_weather)?.setOnClickListener { finish() }
        findViewById<View>(android.R.id.content)?.setOnClickListener { finish() }

        // Auto-close after 30 seconds
        uiHandler.postDelayed(closeRunnable, 30_000)
    }

    private val touchStop = TouchStopController()

    override fun onRobotFocusGained(qiContext: QiContext) {
        touchStop.bind(qiContext) {
            speechJob?.cancel()
            speechController.stop()
        }
        val city = intent.getStringExtra("EXTRA_CITY") ?: "Unknown"
        val temp = intent.getStringExtra("EXTRA_TEMP") ?: "--"
        val desc = intent.getStringExtra("EXTRA_DESC") ?: "No data"
        
        animationManager.onFocusGained(qiContext)
        animationManager.playShowTablet()

        val sayText = "The weather in $city is $desc with a temperature of $temp degrees Celsius."
        
        speechJob = lifecycleScope.launch { speechController.speak(sayText, qiContext) }
    }

    override fun onRobotFocusLost() {
        touchStop.clear()
        speechJob?.cancel()
        speechController.stop()
        animationManager.onFocusLost()
    }
    override fun onRobotFocusRefused(reason: String?) {}

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
        uiHandler.removeCallbacks(closeRunnable)
        QiSDK.unregister(this, this)
        super.onDestroy()
    }
}
