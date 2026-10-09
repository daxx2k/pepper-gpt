package com.softbankrobotics.pepper.pepperGPT

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.builder.SayBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile
import kotlin.coroutines.resume

/** Per-screen speech owner. Cori runs locally on Android 6/ARMv7; no PC or TTS API. */
class SpeechController(private val context: Context, private val voiceOverride: String? = null) {
    private val main = Handler(Looper.getMainLooper())
    private val mutex = Mutex()
    private var engine: TextToSpeech? = null
    private var ready: CompletableDeferred<Boolean>? = null
    private var pending: CancellableContinuation<Boolean>? = null
    private var pendingId: String? = null
    private var nativeAction: Future<Void>? = null
    private var closed = false
    @Volatile private var generation = 0

    fun usesCori(): Boolean = (voiceOverride ?: context.getSharedPreferences("PepperGPT_Prefs", Context.MODE_PRIVATE)
        .getString("voice_mode", "pepper")) == "cori"

    private fun initCori(): CompletableDeferred<Boolean> {
        ready?.let { return it }
        val result = CompletableDeferred<Boolean>()
        ready = result
        try {
            val info = context.packageManager.getApplicationInfo(ENGINE, 0)
            val correctModel = ZipFile(info.sourceDir).use { apk ->
                val entries = apk.entries()
                var found = false
                while (entries.hasMoreElements()) {
                    if (entries.nextElement().name.contains("en_GB-cori-medium")) found = true
                }
                found
            }
            if (!correctModel) { result.complete(false); return result }
            engine = TextToSpeech(context.applicationContext, { status ->
                main.post {
                    val tts = engine
                    if (closed || status != TextToSpeech.SUCCESS || tts == null) {
                        result.complete(false)
                    } else {
                        val voice = tts.voices?.firstOrNull {
                            it.locale.language == "en" && !it.isNetworkConnectionRequired
                        }
                        val selected = if (voice != null) tts.setVoice(voice)
                            else tts.setLanguage(Locale.UK)
                        result.complete(selected >= 0)
                    }
                }
            }, ENGINE)
            engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { finish(id, true) }
                override fun onError(id: String?) { finish(id, false) }
                override fun onStop(id: String?, interrupted: Boolean) { finish(id, false) }
            })
        } catch (_: Exception) {
            result.complete(false)
        }
        return result
    }

    private fun finish(id: String?, success: Boolean) {
        main.post {
            if (id != pendingId) return@post
            val waiter = pending
            pending = null
            pendingId = null
            if (waiter?.isActive == true) waiter.resume(success)
        }
    }

    suspend fun speak(text: String, qiContext: QiContext?): Boolean {
        val expected = generation
        return mutex.withLock {
            withContext(Dispatchers.Main) {
                if (closed || expected != generation || text.isBlank()) return@withContext false
                if (usesCori()) {
                    val initialized = withTimeoutOrNull(120_000L) { initCori().await() } == true
                    if (!initialized || closed || expected != generation) return@withContext false
                    val speed = Regex("""\\rspd=(\d+)\\""").find(text)
                        ?.groupValues?.get(1)?.toFloatOrNull() ?: 100f
                    engine?.setSpeechRate((speed / 100f).coerceIn(0.5f, 1.5f))
                    for (chunk in SpeechText.coriChunks(text)) {
                        if (expected != generation || closed) return@withContext false
                        val success = withTimeoutOrNull(180_000L) {
                            suspendCancellableCoroutine<Boolean> { waiter ->
                                val id = UUID.randomUUID().toString()
                                pending = waiter
                                pendingId = id
                                waiter.invokeOnCancellation {
                                    main.post { if (pendingId == id) stop() }
                                }
                                if (engine?.speak(chunk, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
                                    != TextToSpeech.SUCCESS) finish(id, false)
                            }
                        } == true
                        if (!success) return@withContext false
                    }
                    true
                } else {
                    val ctx = qiContext ?: return@withContext false
                    suspendCancellableCoroutine<Boolean> { waiter ->
                        pending = waiter
                        pendingId = null
                        waiter.invokeOnCancellation { main.post { if (pending === waiter) stop() } }
                        SayBuilder.with(ctx).withText(text).buildAsync().thenConsume { built ->
                            main.post {
                                if (!waiter.isActive || expected != generation || closed) return@post
                                if (built.hasError() || built.isCancelled) {
                                    pending = null
                                    waiter.resume(false)
                                } else {
                                    nativeAction = built.get().async().run()
                                    nativeAction?.thenConsume { completed ->
                                        main.post {
                                            if (pending === waiter) {
                                                pending = null
                                                nativeAction = null
                                            }
                                            if (waiter.isActive) waiter.resume(
                                                !completed.hasError() && !completed.isCancelled)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { stop() }; return }
        generation++
        val waiter = pending
        pending = null
        pendingId = null
        nativeAction?.requestCancellation()
        nativeAction = null
        engine?.stop()
        waiter?.cancel()
    }

    fun close() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { close() }; return }
        closed = true
        stop()
        engine?.shutdown()
        engine = null
    }

    companion object { const val ENGINE = "com.k2fsa.sherpa.onnx.tts.engine" }
}
