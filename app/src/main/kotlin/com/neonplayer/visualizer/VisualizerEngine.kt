package com.neonplayer.visualizer

import android.media.audiofx.Visualizer
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Wraps Android's Visualizer API.
 * Captures FFT data and waveform from the audio output.
 *
 * Provides:
 * - 32 frequency bands for bar visualizer
 * - Peak amplitude for note rain intensity
 * - Beat detection via amplitude threshold
 */
class VisualizerEngine {

    private var visualizer: Visualizer? = null
    private val BAR_COUNT = 32

    // Smoothed bar heights (0f–1f each)
    val bars = FloatArray(BAR_COUNT)
    var peakAmplitude = 0f
        private set
    var isBeat = false
        private set

    private var lastBeatTime = 0L
    private val BEAT_COOL_DOWN = 300L   // min ms between beats
    private val BEAT_THRESHOLD = 0.65f

    var onUpdate: (() -> Unit)? = null

    fun attach(audioSessionId: Int) {
        release()
        try {
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            vis: Visualizer, waveform: ByteArray, samplingRate: Int
                        ) {
                            processWaveform(waveform)
                            onUpdate?.invoke()
                        }

                        override fun onFftDataCapture(
                            vis: Visualizer, fft: ByteArray, samplingRate: Int
                        ) {
                            processFft(fft)
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,  // ~50fps max
                    true,  // waveform
                    true   // FFT
                )
                enabled = true
            }
        } catch (e: Exception) {
            // Visualizer not available (permission or hardware)
            simulateIdle()
        }
    }

    private fun processWaveform(waveform: ByteArray) {
        var sum = 0.0
        for (b in waveform) {
            sum += abs(b.toInt())
        }
        val avg = (sum / waveform.size / 128.0).toFloat().coerceIn(0f, 1f)
        peakAmplitude = avg

        // Beat detection
        val now = System.currentTimeMillis()
        isBeat = avg > BEAT_THRESHOLD && (now - lastBeatTime) > BEAT_COOL_DOWN
        if (isBeat) lastBeatTime = now
    }

    private fun processFft(fft: ByteArray) {
        val n = fft.size
        val bandSize = n / (BAR_COUNT * 2)

        for (i in 0 until BAR_COUNT) {
            var magnitude = 0.0
            val start = i * bandSize
            val end = start + bandSize

            for (j in start until end.coerceAtMost(n / 2)) {
                val re = fft[j * 2].toDouble()
                val im = if (j * 2 + 1 < n) fft[j * 2 + 1].toDouble() else 0.0
                magnitude += sqrt(re * re + im * im)
            }

            val normalized = (magnitude / (bandSize * 128.0)).toFloat().coerceIn(0f, 1f)
            // Smooth: 70% old + 30% new
            bars[i] = bars[i] * 0.7f + normalized * 0.3f
        }
    }

    // When no audio session — idle animation
    private fun simulateIdle() {
        for (i in bars.indices) bars[i] = 0.05f
        peakAmplitude = 0f
        isBeat = false
    }

    fun release() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Exception) {}
        visualizer = null
        simulateIdle()
    }
}
