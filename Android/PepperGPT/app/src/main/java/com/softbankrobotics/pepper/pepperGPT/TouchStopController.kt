package com.softbankrobotics.pepper.pepperGPT

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.`object`.touch.TouchSensor
import java.util.concurrent.atomic.AtomicInteger

/** Own only our listeners, and ignore events delivered after focus is lost. */
class TouchStopController {
    private val revision = AtomicInteger()
    private val ui = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val listeners = mutableListOf<Pair<TouchSensor, TouchSensor.OnStateChangedListener>>()

    fun bind(context: QiContext, onTouch: () -> Unit) {
        clear()
        val current = revision.get()
        for (name in listOf("Head/Touch", "LHand/Touch", "RHand/Touch")) {
            context.touch.async().getSensor(name).andThenConsume { sensor ->
                synchronized(lock) {
                    if (current != revision.get()) return@andThenConsume
                    val listener = TouchSensor.OnStateChangedListener { state ->
                        if (state.touched) ui.post {
                            if (current == revision.get()) onTouch()
                        }
                    }
                    listeners.add(sensor to listener)
                    sensor.async().addOnStateChangedListener(listener)
                }
            }.thenConsume { result ->
                if (result.hasError()) Log.w("TouchStop", "Could not register $name")
            }
        }
    }

    fun clear() = synchronized(lock) {
        revision.incrementAndGet()
        for ((sensor, listener) in listeners) sensor.async().removeOnStateChangedListener(listener)
        listeners.clear()
    }
}
