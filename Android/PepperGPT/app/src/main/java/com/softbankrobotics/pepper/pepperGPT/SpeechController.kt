package com.softbankrobotics.pepper.pepperGPT

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.builder.SayBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

/** Per-screen native speech owner with lifecycle and touch cancellation. */
class SpeechController(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val mutex = Mutex()
    private var pending: CancellableContinuation<Boolean>? = null
    private var nativeAction: Future<Void>? = null
    private var closed = false
    @Volatile private var generation = 0

    suspend fun speak(text: String, qiContext: QiContext?): Boolean {
        val expected = generation
        return mutex.withLock {
            withContext(Dispatchers.Main) {
                if (closed || expected != generation || text.isBlank()) return@withContext false

                    val ctx = qiContext ?: return@withContext false
                    suspendCancellableCoroutine<Boolean> { waiter ->
                        pending = waiter
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

    fun stop() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { stop() }; return }
        generation++
        val waiter = pending
        pending = null
        nativeAction?.requestCancellation()
        nativeAction = null
        waiter?.cancel()
    }

    fun close() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { close() }; return }
        closed = true
        stop()
    }

}
