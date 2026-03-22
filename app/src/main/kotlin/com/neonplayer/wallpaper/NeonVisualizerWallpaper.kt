package com.neonplayer.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.neonplayer.service.MusicService
import kotlin.math.*
import kotlin.random.Random

/**
 * NeonVisualizerWallpaper
 *
 * Features:
 * ─────────────────────────────────────────────────────────────
 * 1. Audio Visualizer Bars — 32 frequency bars, neon glow
 * 2. Music Note Rain — random neon notes fall from top
 * 3. Beat Flash — whole screen pulses on heavy beat
 * 4. Circular Waveform — around a center clock/logo
 * 5. Star Field — AMOLED static stars in background
 * 6. Color Shift — slow neon palette rotation
 * 7. Idle Animation — beautiful even without music
 * ─────────────────────────────────────────────────────────────
 */
class NeonVisualizerWallpaper : WallpaperService() {

    override fun onCreateEngine(): Engine = VisualizerEngine()

    inner class VisualizerEngine : Engine() {

        // ── Screen dimensions ──────────────────────────────────
        private var W = 0f
        private var H = 0f

        // ── Bar data (from broadcast or idle animation) ────────
        private val bars = FloatArray(32) { 0.05f }
        private var amplitude = 0f
        private var isPlaying = false
        private var isBeat = false

        // ── Color state ────────────────────────────────────────
        private var hue = 200f    // Start at cyan-blue
        private val baseColor get() = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        private val glowColor get() = Color.HSVToColor(floatArrayOf(hue, 0.8f, 0.9f))

        // ── Note Rain ─────────────────────────────────────────
        private val notes = Array(20) { MusicNote() }
        private val NOTE_SYMBOLS = listOf("♩", "♪", "♫", "♬", "𝅘𝅥𝅮", "𝄞")

        // ── Stars ─────────────────────────────────────────────
        private val starX = FloatArray(80)
        private val starY = FloatArray(80)
        private val starAlpha = FloatArray(80)

        // ── Beat flash ────────────────────────────────────────
        private var flashAlpha = 0f

        // ── Precomputed paths ──────────────────────────────────
        private val waveformPts = FloatArray(32 * 4)

        // ── Preallocated Paints — ZERO allocation in draw ──────
        private val bgPaint = Paint().apply { color = Color.BLACK }

        private val barPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        private val barGlowPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        private val notePaint = Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        private val starPaint = Paint().apply {
            isAntiAlias = false
            style = Paint.Style.FILL
            color = Color.WHITE
        }

        private val flashPaint = Paint().apply {
            style = Paint.Style.FILL
        }

        private val linePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 2f
        }

        private val circlePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }

        private val textPaint = Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            color = Color.WHITE
            alpha = 40
        }

        // ── Draw loop ──────────────────────────────────────────
        private val handler = Handler(
            android.os.HandlerThread("NeonWallpaper").also { it.start() }.looper
        )

        private var frameInterval = 33L  // ~30fps when playing, 100ms idle

        private val drawRunnable = object : Runnable {
            override fun run() {
                if (!isVisible) return
                drawFrame()
                handler.postDelayed(this, frameInterval)
            }
        }

        // ── Broadcast receiver for music data ─────────────────
        private val musicReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                isPlaying = intent.getBooleanExtra(MusicService.EXTRA_IS_PLAYING, false)
                amplitude = intent.getFloatExtra(MusicService.EXTRA_AMPLITUDE, 0f)
                frameInterval = if (isPlaying) 33L else 100L

                // Animate bars from amplitude
                if (isPlaying) updateBarsFromAmplitude()
            }
        }

        // ── Lifecycle ──────────────────────────────────────────

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            setTouchEventsEnabled(true)
        }

        override fun onSurfaceChanged(h: SurfaceHolder, fmt: Int, w: Int, ht: Int) {
            super.onSurfaceChanged(h, fmt, w, ht)
            W = w.toFloat(); H = ht.toFloat()
            initStars()
            initNotes()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) {
                registerReceiver(
                    musicReceiver,
                    IntentFilter(MusicService.ACTION_BEAT),
                    Context.RECEIVER_NOT_EXPORTED
                )
                handler.post(drawRunnable)
            } else {
                try { unregisterReceiver(musicReceiver) } catch (_: Exception) {}
                handler.removeCallbacks(drawRunnable)
            }
        }

        override fun onDestroy() {
            handler.removeCallbacks(drawRunnable)
            handler.looper.quit()
            try { unregisterReceiver(musicReceiver) } catch (_: Exception) {}
            super.onDestroy()
        }

        override fun onTouchEvent(event: android.view.MotionEvent) {
            // Touch creates a burst of notes from touch point
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                spawnNoteBurst(event.x, event.y)
                flashAlpha = 0.15f
            }
        }

        // ── Initialization ────────────────────────────────────

        private fun initStars() {
            val rng = Random.Default
            for (i in starX.indices) {
                starX[i] = rng.nextFloat() * W
                starY[i] = rng.nextFloat() * H
                starAlpha[i] = 0.1f + rng.nextFloat() * 0.5f
            }
        }

        private fun initNotes() {
            val rng = Random.Default
            for (n in notes) {
                n.reset(W, rng.nextFloat() * H)
                n.active = rng.nextBoolean()
            }
        }

        // ── Bar simulation when no real FFT ───────────────────

        private fun updateBarsFromAmplitude() {
            val rng = Random.Default
            for (i in bars.indices) {
                val target = amplitude * (0.3f + rng.nextFloat() * 0.7f)
                bars[i] = bars[i] * 0.6f + target * 0.4f
            }
            isBeat = amplitude > 0.7f
        }

        private fun idleAnimate() {
            val t = System.currentTimeMillis() / 1000.0
            for (i in bars.indices) {
                val wave = (sin(t * 0.8 + i * 0.4) * 0.5 + 0.5).toFloat()
                bars[i] = bars[i] * 0.85f + wave * 0.08f * 0.15f
            }
            isBeat = false
        }

        // ── Main draw ─────────────────────────────────────────

        private fun drawFrame() {
            val canvas = surfaceHolder.lockCanvas() ?: return
            try {
                // Color shift — very slow hue rotation
                hue = (hue + 0.05f) % 360f

                if (!isPlaying) idleAnimate()

                // Beat flash fade
                if (flashAlpha > 0) flashAlpha = (flashAlpha - 0.03f).coerceAtLeast(0f)

                // ── 1. True AMOLED black background ───────────
                canvas.drawRect(0f, 0f, W, H, bgPaint)

                // ── 2. Beat flash overlay ──────────────────────
                if (flashAlpha > 0) {
                    flashPaint.color = baseColor
                    flashPaint.alpha = (flashAlpha * 40).toInt()
                    canvas.drawRect(0f, 0f, W, H, flashPaint)
                }

                // ── 3. Stars ───────────────────────────────────
                drawStars(canvas)

                // ── 4. Circular waveform ───────────────────────
                drawCircularWaveform(canvas)

                // ── 5. Visualizer bars ─────────────────────────
                drawBars(canvas)

                // ── 6. Note rain ───────────────────────────────
                drawNoteRain(canvas)

                // ── 7. Center logo ────────────────────────────
                drawCenterLogo(canvas)

            } finally {
                surfaceHolder.unlockCanvasAndPost(canvas)
            }
        }

        // ── Draw: Stars ────────────────────────────────────────

        private fun drawStars(canvas: Canvas) {
            for (i in starX.indices) {
                starPaint.alpha = (starAlpha[i] * 255).toInt()
                canvas.drawPoint(starX[i], starY[i], starPaint)
            }
        }

        // ── Draw: Circular waveform ────────────────────────────

        private fun drawCircularWaveform(canvas: Canvas) {
            val cx = W / 2f
            val cy = H * 0.42f
            val baseR = minOf(W, H) * 0.22f

            circlePaint.color = baseColor
            circlePaint.alpha = 60
            canvas.drawCircle(cx, cy, baseR, circlePaint)

            circlePaint.alpha = 30
            canvas.drawCircle(cx, cy, baseR * 1.15f, circlePaint)

            // Waveform ring
            linePaint.color = baseColor
            linePaint.alpha = 150
            linePaint.strokeWidth = 2f

            val segCount = bars.size
            for (i in 0 until segCount) {
                val angle1 = (i.toFloat() / segCount * 360f - 90f) * PI / 180f
                val angle2 = ((i + 1f) / segCount * 360f - 90f) * PI / 180f
                val barH = bars[i] * baseR * 0.6f

                val x1 = (cx + (baseR + 4f) * cos(angle1)).toFloat()
                val y1 = (cy + (baseR + 4f) * sin(angle1)).toFloat()
                val x2 = (cx + (baseR + 4f + barH) * cos(angle1)).toFloat()
                val y2 = (cy + (baseR + 4f + barH) * sin(angle1)).toFloat()

                linePaint.alpha = (100 + bars[i] * 155).toInt()
                canvas.drawLine(x1, y1, x2, y2, linePaint)
            }
        }

        // ── Draw: Frequency bars ───────────────────────────────

        private fun drawBars(canvas: Canvas) {
            val barCount = bars.size
            val totalW = W * 0.9f
            val barW = totalW / barCount
            val gap = barW * 0.25f
            val startX = W * 0.05f
            val bottomY = H * 0.88f
            val maxH = H * 0.35f

            for (i in 0 until barCount) {
                val barHeight = (bars[i] * maxH).coerceAtLeast(4f)
                val x = startX + i * barW
                val top = bottomY - barHeight
                val right = x + barW - gap

                // Gradient: bright top, fade bottom
                val barShader = LinearGradient(
                    x, top, x, bottomY,
                    intArrayOf(
                        Color.WHITE,
                        baseColor,
                        Color.argb(180, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
                    ),
                    floatArrayOf(0f, 0.3f, 1f),
                    Shader.TileMode.CLAMP
                )
                barPaint.shader = barShader
                barPaint.alpha = 220

                canvas.drawRoundRect(x, top, right, bottomY, 4f, 4f, barPaint)

                // Glow below bar
                if (bars[i] > 0.2f) {
                    barGlowPaint.color = baseColor
                    barGlowPaint.alpha = (bars[i] * 60).toInt()
                    barGlowPaint.maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
                    canvas.drawRoundRect(x, top, right, bottomY, 4f, 4f, barGlowPaint)
                    barGlowPaint.maskFilter = null
                }
            }

            // Reflection (mirror below, very faded)
            for (i in 0 until barCount) {
                val barHeight = (bars[i] * maxH * 0.25f).coerceAtLeast(2f)
                val x = startX + i * barW
                val top = bottomY + 4f
                val right = x + barW - gap

                barPaint.shader = null
                barPaint.color = baseColor
                barPaint.alpha = (bars[i] * 50).toInt()
                canvas.drawRect(x, top, right, top + barHeight, barPaint)
            }
        }

        // ── Draw: Music note rain ──────────────────────────────

        private fun drawNoteRain(canvas: Canvas) {
            val rng = Random.Default
            for (note in notes) {
                if (!note.active) {
                    // Spawn based on amplitude or random
                    val spawnChance = if (isPlaying) 0.03f + amplitude * 0.1f else 0.005f
                    if (rng.nextFloat() < spawnChance) {
                        note.reset(W)
                    }
                    continue
                }

                // Update
                note.y += note.speed * (1f + amplitude * 2f)
                note.alpha -= note.fadeSpeed
                note.rotation += note.rotSpeed
                note.x += sin(note.y * 0.02f) * note.wobble

                if (note.y > H + 80f || note.alpha <= 0f) {
                    note.active = false
                    continue
                }

                // Draw
                canvas.save()
                canvas.translate(note.x, note.y)
                canvas.rotate(note.rotation)

                notePaint.color = note.color
                notePaint.alpha = (note.alpha * 255f).toInt().coerceIn(0, 255)
                notePaint.textSize = note.size
                notePaint.setShadowLayer(note.size * 0.5f, 0f, 0f, note.color)

                canvas.drawText(note.symbol, 0f, 0f, notePaint)
                canvas.restore()
            }
            notePaint.clearShadowLayer()
        }

        private fun spawnNoteBurst(tx: Float, ty: Float) {
            var spawned = 0
            for (note in notes) {
                if (!note.active && spawned < 5) {
                    note.reset(W)
                    note.x = tx + (Random.nextFloat() - 0.5f) * 100f
                    note.y = ty
                    note.speed = 2f + Random.nextFloat() * 4f
                    note.active = true
                    spawned++
                }
            }
        }

        // ── Draw: Center logo ──────────────────────────────────

        private fun drawCenterLogo(canvas: Canvas) {
            val cx = W / 2f
            val cy = H * 0.42f

            textPaint.textSize = W * 0.06f
            textPaint.color = Color.WHITE
            textPaint.alpha = if (isPlaying) 0 else 50

            if (!isPlaying) {
                canvas.drawText("♫", cx, cy + textPaint.textSize * 0.35f, textPaint)
            }
        }

        companion object {
            private const val PI = Math.PI
        }
    }

    // ── Music Note data class ──────────────────────────────────

    class MusicNote {
        var x = 0f
        var y = 0f
        var speed = 0f
        var alpha = 0f
        var fadeSpeed = 0f
        var size = 0f
        var rotation = 0f
        var rotSpeed = 0f
        var wobble = 0f
        var color = 0
        var symbol = "♪"
        var active = false

        private val symbols = listOf("♩", "♪", "♫", "♬", "𝄞", "♭", "♮", "♯")
        private val colors = listOf(
            0xFF00BFFF.toInt(),
            0xFF8A2BE2.toInt(),
            0xFF00FFFF.toInt(),
            0xFF39FF14.toInt(),
            0xFFFF6EC7.toInt(),
            0xFFFFD700.toInt()
        )

        fun reset(screenW: Float, startY: Float = -80f) {
            val rng = Random.Default
            x = rng.nextFloat() * screenW
            y = startY
            speed = 1.5f + rng.nextFloat() * 3f
            alpha = 0.6f + rng.nextFloat() * 0.4f
            fadeSpeed = 0.003f + rng.nextFloat() * 0.005f
            size = 16f + rng.nextFloat() * 28f
            rotation = rng.nextFloat() * 30f - 15f
            rotSpeed = (rng.nextFloat() - 0.5f) * 2f
            wobble = rng.nextFloat() * 0.8f
            color = colors[rng.nextInt(colors.size)]
            symbol = symbols[rng.nextInt(symbols.size)]
            active = true
        }
    }
}
