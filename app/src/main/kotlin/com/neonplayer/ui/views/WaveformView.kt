package com.neonplayer.ui.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

class WaveformView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var isPlaying = false
    private var phase = 0f
    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.rgb(0, 191, 255)
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isPlaying) {
                phase += 0.08f
                invalidate()
                postDelayed(this, 32)
            }
        }
    }

    fun setPlaying(playing: Boolean) {
        isPlaying = playing
        if (playing) {
            removeCallbacks(updateRunnable)
            post(updateRunnable)
        } else {
            removeCallbacks(updateRunnable)
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        val cx = width / 2f
        val cy = height / 2f
        val amp = if (isPlaying) cy * 0.6f else cy * 0.15f

        path.reset()
        val step = 4f
        var first = true
        var x = 0f
        while (x <= width) {
            val y = cy + sin((x / width * Math.PI * 4 + phase).toFloat()) * amp *
                    sin((x / width * Math.PI).toFloat())
            if (first) { path.moveTo(x, y); first = false }
            else path.lineTo(x, y)
            x += step
        }

        val alpha = if (isPlaying) 200 else 80
        paint.alpha = alpha
        canvas.drawPath(path, paint)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(updateRunnable)
        super.onDetachedFromWindow()
    }
}
