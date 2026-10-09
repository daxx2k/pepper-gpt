package com.softbankrobotics.pepper.pepperGPT

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.*
import java.io.File
import java.util.Locale

/** Audio-only check: no QiSDK registration, robot motion or API credentials. */
class VoicePreviewActivity : Activity() {
    private var engine: TextToSpeech? = null
    private val speech by lazy { SpeechController(this, "cori") }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var demoJob: Job? = null
    private val main = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private val sample = "Hello, I am Pepper. This is my Cori voice, running locally on Android six."
    private var closed = false
    private var speaking = false
    private var playAfterCheck = false
    private var deadline: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32,32,32,32) }
        status = TextView(this).apply { text = "Starting offline Cori voice..."; textSize = 22f }
        val play = Button(this).apply { text = "Play Cori demo"; isEnabled = false }
        val stop = Button(this).apply { text = "Stop"; setOnClickListener { demoJob?.cancel(); speech.stop(); engine?.stop(); status.text = "Stopped" } }
        layout.addView(status); layout.addView(play); layout.addView(stop)
        setContentView(layout)
        // Only debug ADB checks may request automatic playback; UI requires pressing Play.
        playAfterCheck = BuildConfig.DEBUG && intent.getBooleanExtra("play", false)
        deadline = Runnable { status.text = "Cori timed out"; Log.w("CoriCheck", "FAIL: timeout"); engine?.stop() }
        main.postDelayed(deadline!!, 120_000L)
        try {
            engine = TextToSpeech(applicationContext, { result ->
                main.post {
                    if (closed) return@post
                    if (result != TextToSpeech.SUCCESS) {
                        status.text = "Cori engine unavailable"; Log.w("CoriCheck", "FAIL: engine initialization")
                        return@post
                    }
                    engine?.setLanguage(Locale.UK)
                    engine?.setSpeechRate(1f)
                    play.isEnabled = true
                    val file = File(filesDir, "cori-self-test.wav")
                    engine?.synthesizeToFile(sample, Bundle(), file, "synthesis-check")
                }
            }, SpeechController.ENGINE)
            engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onError(id: String?) {
                    main.post { status.text = "Cori synthesis failed"; Log.w("CoriCheck", "FAIL: utterance") }
                }
                override fun onDone(id: String?) {
                    main.post {
                        if (closed) return@post
                        if (id == "synthesis-check") {
                            val file = File(filesDir, "cori-self-test.wav")
                            val bytes = file.readBytes()
                            val valid = bytes.size > 44 && String(bytes, 0, 4) == "RIFF"
                            status.text = if (valid) "Cori ready: offline WAV generated. Press Play to listen." else "Cori generated an invalid WAV"
                            Log.w("CoriCheck", "${if(valid) "PASS" else "FAIL"}: offline WAV bytes=${bytes.size}")
                            if (valid && playAfterCheck) play.performClick()
                            else deadline?.let { main.removeCallbacks(it) }
                        } else if (id == "demo") {
                            speaking = false
                            status.text = "Cori demo completed"
                            Log.w("CoriCheck", "PASS: playback completed")
                            deadline?.let { main.removeCallbacks(it) }
                        }
                    }
                }
            })
            play.setOnClickListener {
                speaking = true
                status.text = "Speaking with Cori..."
                demoJob?.cancel()
                demoJob = scope.launch {
                    try {
                        val completed = speech.speak(sample, null)
                        status.text = if (completed) "Cori demo completed" else "Cori playback failed"
                        Log.w("CoriCheck", "${if (completed) "PASS" else "FAIL"}: app speech controller completed")
                    } finally {
                        speaking = false
                        deadline?.let { main.removeCallbacks(it) }
                    }
                }
                val stopAfter = if (BuildConfig.DEBUG) intent.getLongExtra("stop_after_ms", 0) else 0
                if (stopAfter > 0) main.postDelayed({
                    demoJob?.cancel(); speech.stop()
                    status.text = "Stopped"
                    Log.w("CoriCheck", "PASS: app speech stop requested")
                }, stopAfter)
            }
        } catch (_: Exception) { status.text = "Install the Cori ARMv7 engine first" }
    }

    override fun onPause() {
        demoJob?.cancel()
        speech.stop()
        engine?.stop()
        super.onPause()
    }
    override fun onDestroy() {
        closed = true
        scope.cancel()
        speech.close()
        main.removeCallbacksAndMessages(null)
        engine?.stop(); engine?.shutdown()
        super.onDestroy()
    }
}
