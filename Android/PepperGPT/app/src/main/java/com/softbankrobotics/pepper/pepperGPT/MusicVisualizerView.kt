package com.softbankrobotics.pepper.pepperGPT

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class MusicVisualizerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF00AACD.toInt() }
    private val bars = FloatArray(12)

    fun setColor(color: Int) {
        paint.color = color
        invalidate()
    }

    fun setPlaying(playing: Boolean) {
        if (!playing) {
            bars.fill(0f)
            invalidate()
        }
    }

    fun setLevels(levels: FloatArray) {
        for (index in bars.indices) {
            val change = levels[index] - bars[index]
            bars[index] += change * if (change > 0f) 0.75f else 0.35f
        }
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val barWidth = width / (bars.size * 1.5f + 0.5f)
        val spacing = barWidth / 2f
        val baseline = resources.displayMetrics.density * 3f
        val center = height / 2f
        val radius = minOf(8f, barWidth / 2f)
        for (index in bars.indices) {
            val left = spacing + index * (barWidth + spacing)
            val halfHeight = maxOf(baseline, bars[index] * height * 0.88f) / 2f
            canvas.drawRoundRect(left, center - halfHeight, left + barWidth, center + halfHeight,
                radius, radius, paint)
        }
    }
}
