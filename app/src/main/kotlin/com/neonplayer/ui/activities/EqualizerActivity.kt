package com.neonplayer.ui.activities

import android.media.audiofx.Equalizer
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.neonplayer.R

class EqualizerActivity : AppCompatActivity() {

    private var equalizer: Equalizer? = null
    private val seekBars = mutableListOf<SeekBar>()
    private val bandLabels = mutableListOf<TextView>()

    private val PRESETS = mapOf(
        "Flat"       to intArrayOf(0, 0, 0, 0, 0),
        "Bass Boost" to intArrayOf(600, 400, 0, 0, 0),
        "Treble"     to intArrayOf(0, 0, 0, 300, 500),
        "Rock"       to intArrayOf(400, 200, -100, 200, 400),
        "Pop"        to intArrayOf(-100, 200, 400, 200, -100),
        "Classical"  to intArrayOf(300, 100, -200, 200, 400),
        "Hip Hop"    to intArrayOf(500, 300, 0, -200, 300),
        "Electronic" to intArrayOf(400, 100, -200, 300, 400),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_equalizer)

        setupEqualizer()
        setupPresets()

        findViewById<ImageButton>(R.id.btnBackEq).setOnClickListener { finish() }
    }

    private fun setupEqualizer() {
        try {
            equalizer = Equalizer(0, 0).also { eq ->
                eq.enabled = true

                val minLevel = eq.bandLevelRange[0]
                val maxLevel = eq.bandLevelRange[1]
                val range = maxLevel - minLevel

                val container = findViewById<LinearLayout>(R.id.eqBandsContainer)

                for (band in 0 until eq.numberOfBands) {
                    val bandHz = eq.getCenterFreq(band.toShort()) / 1000
                    val label = if (bandHz >= 1000) "${bandHz/1000}kHz" else "${bandHz}Hz"

                    // Inflate band view
                    val bandView = layoutInflater.inflate(R.layout.item_eq_band, container, false)
                    val tvLabel = bandView.findViewById<TextView>(R.id.tvBandLabel)
                    val seekBar = bandView.findViewById<SeekBar>(R.id.seekBandLevel)
                    val tvValue = bandView.findViewById<TextView>(R.id.tvBandValue)

                    tvLabel.text = label
                    seekBar.max = range
                    val currentLevel = eq.getBandLevel(band.toShort()) - minLevel
                    seekBar.progress = currentLevel
                    tvValue.text = "${(currentLevel + minLevel) / 100}dB"

                    seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                            val level = (p + minLevel).toShort()
                            eq.setBandLevel(band.toShort(), level)
                            tvValue.text = "${level / 100}dB"
                        }
                        override fun onStartTrackingTouch(sb: SeekBar) {}
                        override fun onStopTrackingTouch(sb: SeekBar) {}
                    })

                    container.addView(bandView)
                    seekBars.add(seekBar)
                    bandLabels.add(tvLabel)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Equalizer not available on this device", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupPresets() {
        val spinner = findViewById<Spinner>(R.id.spinnerPresets)
        val items = PRESETS.keys.toList()
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, pos: Int, id: Long) {
                val preset = PRESETS[items[pos]] ?: return
                val eq = equalizer ?: return
                val minLevel = eq.bandLevelRange[0]
                val maxLevel = eq.bandLevelRange[1]
                val range = maxLevel - minLevel

                preset.forEachIndexed { i, millibels ->
                    if (i < eq.numberOfBands) {
                        eq.setBandLevel(i.toShort(), millibels.toShort())
                        seekBars.getOrNull(i)?.progress = (millibels - minLevel).coerceIn(0, range)
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    override fun onDestroy() {
        equalizer?.release()
        super.onDestroy()
    }
}
