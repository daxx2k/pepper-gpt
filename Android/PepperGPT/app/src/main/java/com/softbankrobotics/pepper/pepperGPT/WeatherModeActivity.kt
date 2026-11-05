package com.softbankrobotics.pepper.pepperGPT

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import android.os.Bundle
import android.view.View
import android.os.Handler
import android.os.Looper
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.design.activity.RobotActivity
import com.aldebaran.qi.sdk.design.activity.conversationstatus.SpeechBarDisplayStrategy

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

        setSpeechBarDisplayStrategy(SpeechBarDisplayStrategy.OVERLAY)
        WeatherPanel.render(this)

        // Auto-close after 30 seconds
        uiHandler.postDelayed(closeRunnable, 30_000)
    }

    private val touchStop = TouchStopController()

    override fun onRobotFocusGained(qiContext: QiContext) {
        touchStop.bind(qiContext) {
            speechJob?.cancel()
            speechController.stop()
        }
        SpecialScreen.cancel(this)
        uiHandler.removeCallbacks(closeRunnable)
        animationManager.onFocusGained(qiContext)
        animationManager.playShowTablet()

        speechJob = lifecycleScope.launch {
            try {
                speechController.speak(WeatherPanel.speech(this@WeatherModeActivity), qiContext)
            } finally {
                SpecialScreen.closeAfterSpeech(this@WeatherModeActivity)
            }
        }
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
        SpecialScreen.cancel(this)
        speechController.close()
        uiHandler.removeCallbacks(closeRunnable)
        QiSDK.unregister(this, this)
        super.onDestroy()
    }
}
