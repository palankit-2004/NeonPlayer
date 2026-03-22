package com.neonplayer.ui.activities

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.neonplayer.R
import com.neonplayer.service.MusicService
import com.neonplayer.ui.viewmodels.PlayerViewModel
import com.neonplayer.ui.views.WaveformView
import androidx.media3.common.Player

class NowPlayingActivity : AppCompatActivity() {

    private lateinit var vm: PlayerViewModel

    private lateinit var albumArt: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var tvAlbum: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnShuffle: ImageButton
    private lateinit var btnRepeat: ImageButton
    private lateinit var btnEqualizer: ImageButton
    private lateinit var btnSetWallpaper: Button
    private lateinit var waveformView: WaveformView

    private var isUserSeeking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_now_playing)

        vm = ViewModelProvider(this)[PlayerViewModel::class.java]
        vm.connectToService(ComponentName(this, MusicService::class.java))

        bindViews()
        setupControls()
        observeViewModel()
    }

    private fun bindViews() {
        albumArt       = findViewById(R.id.imgAlbumArtLarge)
        tvTitle        = findViewById(R.id.tvNowTitle)
        tvArtist       = findViewById(R.id.tvNowArtist)
        tvAlbum        = findViewById(R.id.tvNowAlbum)
        seekBar        = findViewById(R.id.seekBar)
        tvCurrentTime  = findViewById(R.id.tvCurrentTime)
        tvTotalTime    = findViewById(R.id.tvTotalTime)
        btnPlayPause   = findViewById(R.id.btnNowPlayPause)
        btnNext        = findViewById(R.id.btnNowNext)
        btnPrev        = findViewById(R.id.btnNowPrev)
        btnShuffle     = findViewById(R.id.btnNowShuffle)
        btnRepeat      = findViewById(R.id.btnNowRepeat)
        btnEqualizer   = findViewById(R.id.btnEqualizer)
        btnSetWallpaper = findViewById(R.id.btnSetWallpaper)
        waveformView   = findViewById(R.id.waveformView)

        seekBar.max = 1000
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun setupControls() {
        btnPlayPause.setOnClickListener { vm.togglePlayPause() }
        btnNext.setOnClickListener { vm.skipNext() }
        btnPrev.setOnClickListener { vm.skipPrev() }
        btnShuffle.setOnClickListener { vm.toggleShuffle() }
        btnRepeat.setOnClickListener { vm.cycleRepeat() }

        btnEqualizer.setOnClickListener {
            startActivity(Intent(this, EqualizerActivity::class.java))
        }

        btnSetWallpaper.setOnClickListener {
            val intent = Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(
                    android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    android.content.ComponentName(this@NowPlayingActivity,
                        com.neonplayer.wallpaper.NeonVisualizerWallpaper::class.java)
                )
            }
            startActivity(intent)
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(sb: SeekBar) { isUserSeeking = true }
            override fun onStopTrackingTouch(sb: SeekBar) {
                isUserSeeking = false
                val dur = vm.duration.value ?: 0L
                vm.seekTo((sb.progress.toLong() * dur / 1000L))
            }
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {}
        })
    }

    private fun observeViewModel() {
        vm.currentSong.observe(this) { song ->
            song ?: return@observe
            tvTitle.text  = song.title
            tvArtist.text = song.artist
            tvAlbum.text  = song.album
            tvTotalTime.text = song.durationFormatted

            Glide.with(albumArt)
                .load(song.albumArtUri)
                .placeholder(R.drawable.ic_album_placeholder)
                .error(R.drawable.ic_album_placeholder)
                .centerCrop()
                .into(albumArt)
        }

        vm.isPlaying.observe(this) { playing ->
            btnPlayPause.setImageResource(
                if (playing) R.drawable.ic_pause_large else R.drawable.ic_play_large
            )
            waveformView.setPlaying(playing)
        }

        vm.progress.observe(this) { progress ->
            if (!isUserSeeking) {
                val dur = vm.duration.value ?: 1L
                seekBar.progress = ((progress.toFloat() / dur) * 1000).toInt()
                tvCurrentTime.text = formatTime(progress)
            }
        }

        vm.shuffleOn.observe(this) { on ->
            btnShuffle.alpha = if (on) 1f else 0.4f
        }

        vm.repeatMode.observe(this) { mode ->
            btnRepeat.setImageResource(
                when (mode) {
                    Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
                    Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat_all
                    else -> R.drawable.ic_repeat_off
                }
            )
            btnRepeat.alpha = if (mode == Player.REPEAT_MODE_OFF) 0.4f else 1f
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSecs = ms / 1000
        return "%d:%02d".format(totalSecs / 60, totalSecs % 60)
    }
}
