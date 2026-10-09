package com.softbankrobotics.pepper.pepperGPT

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.util.Random

class MusicVisualizerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = 0xFF00AACD.toInt() // Default Pepper Blue
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val random = Random()
    private val barCount = 12
    private val bars = FloatArray(barCount)
    private val targetBars = FloatArray(barCount)
    private var isPlaying = false
    private val animationSpeed = 0.2f // Interpolation factor

    init {
        // Initialize bars
        for (i in 0 until barCount) {
            bars[i] = 10f
            targetBars[i] = 10f
        }
    }

    fun setColor(color: Int) {
        paint.color = color
        invalidate()
    }

    fun setPlaying(playing: Boolean) {
        this.isPlaying = playing
        if (playing) {
            invalidate()
        } else {
            // Reset to flat line
            for (i in 0 until barCount) {
                targetBars[i] = 10f
            }
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val barWidth = width / (barCount * 1.5f) // Space between bars
        val spacing = barWidth / 2

        var currentX = spacing

        for (i in 0 until barCount) {
            // Update bar height towards target
            if (isPlaying) {
                // Randomly change target occasionally or just jitter
                if (Math.abs(bars[i] - targetBars[i]) < 5f) {
                    targetBars[i] = random.nextFloat() * (height * 0.8f) + (height * 0.1f)
                }
            } else {
                targetBars[i] = 10f // Flat line
            }

            // Interpolate
            bars[i] += (targetBars[i] - bars[i]) * animationSpeed

            // Draw bar (centered vertically)
            val barHeight = bars[i]
            val top = (height - barHeight) / 2
            val bottom = top + barHeight
            
            canvas.drawRoundRect(currentX, top, currentX + barWidth, bottom, 8f, 8f, paint)
            
            currentX += barWidth + spacing
        }

        if (isPlaying) {
            postInvalidateDelayed(30) // ~30fps
        }
    }
}
