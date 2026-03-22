package com.neonplayer.ui.activities

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.neonplayer.R
import com.neonplayer.model.Song
import com.neonplayer.service.MusicService
import com.neonplayer.ui.adapters.SongAdapter
import com.neonplayer.ui.viewmodels.PlayerViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var vm: PlayerViewModel
    private lateinit var adapter: SongAdapter

    // Views
    private lateinit var rvSongs: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var tvSongCount: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: View
    private lateinit var miniPlayer: View
    private lateinit var miniAlbumArt: ImageView
    private lateinit var miniTitle: TextView
    private lateinit var miniArtist: TextView
    private lateinit var miniPlayPause: ImageButton
    private lateinit var miniProgress: ProgressBar

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) loadMusic()
        else showPermissionDenied()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        vm = ViewModelProvider(this)[PlayerViewModel::class.java]

        bindViews()
        setupRecyclerView()
        setupSearch()
        setupMiniPlayer()
        observeViewModel()

        // Connect to MusicService
        startService(Intent(this, MusicService::class.java))
        vm.connectToService(ComponentName(this, MusicService::class.java))

        checkPermissionsAndLoad()
    }

    private fun bindViews() {
        rvSongs       = findViewById(R.id.rvSongs)
        etSearch      = findViewById(R.id.etSearch)
        tvSongCount   = findViewById(R.id.tvSongCount)
        progressBar   = findViewById(R.id.progressBar)
        emptyView     = findViewById(R.id.emptyView)
        miniPlayer    = findViewById(R.id.miniPlayer)
        miniAlbumArt  = findViewById(R.id.miniAlbumArt)
        miniTitle     = findViewById(R.id.miniTitle)
        miniArtist    = findViewById(R.id.miniArtist)
        miniPlayPause = findViewById(R.id.miniPlayPause)
        miniProgress  = findViewById(R.id.miniProgress)
        miniTitle.isSelected = true
    }

    private fun setupRecyclerView() {
        adapter = SongAdapter(
            onSongClick = { song ->
                vm.playSong(song, vm.searchResults.value ?: listOf(song))
            },
            onMoreClick = { song, anchor ->
                showSongMenu(song, anchor)
            }
        )
        rvSongs.adapter = adapter
        rvSongs.layoutManager = LinearLayoutManager(this)
        rvSongs.setHasFixedSize(true)
    }

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                vm.search(s?.toString() ?: "")
            }
        })
    }

    private fun setupMiniPlayer() {
        miniPlayer.setOnClickListener {
            startActivity(Intent(this, NowPlayingActivity::class.java))
        }
        miniPlayPause.setOnClickListener { vm.togglePlayPause() }

        // Skip buttons in mini player
        findViewById<ImageButton>(R.id.miniNext).setOnClickListener { vm.skipNext() }
        findViewById<ImageButton>(R.id.miniPrev).setOnClickListener { vm.skipPrev() }
    }

    private fun observeViewModel() {
        vm.searchResults.observe(this) { songs ->
            adapter.submitList(songs)
            tvSongCount.text = "${songs.size} songs"
            emptyView.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
        }

        vm.isLoading.observe(this) { loading ->
            progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        vm.currentSong.observe(this) { song ->
            song?.let {
                miniPlayer.visibility = View.VISIBLE
                miniTitle.text = it.title
                miniArtist.text = it.artist
                adapter.setCurrentPlaying(it.id)

                Glide.with(miniAlbumArt)
                    .load(it.albumArtUri)
                    .placeholder(R.drawable.ic_album_placeholder)
                    .error(R.drawable.ic_album_placeholder)
                    .centerCrop()
                    .into(miniAlbumArt)
            }
        }

        vm.isPlaying.observe(this) { playing ->
            miniPlayPause.setImageResource(
                if (playing) R.drawable.ic_pause else R.drawable.ic_play
            )
        }

        vm.progress.observe(this) { progress ->
            val dur = vm.duration.value ?: 1L
            miniProgress.progress = ((progress.toFloat() / dur) * 1000).toInt()
        }
    }

    private fun showSongMenu(song: Song, anchor: View) {
        val popup = android.widget.PopupMenu(this, anchor)
        popup.menu.add("Play Next")
        popup.menu.add("Add to Playlist")
        popup.menu.add("Song Info")
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Play Next"        -> Toast.makeText(this, "Will play next", Toast.LENGTH_SHORT).show()
                "Song Info"        -> showSongInfo(song)
            }
            true
        }
        popup.show()
    }

    private fun showSongInfo(song: Song) {
        android.app.AlertDialog.Builder(this)
            .setTitle(song.title)
            .setMessage(
                "Artist: ${song.artist}\n" +
                "Album: ${song.album}\n" +
                "Duration: ${song.durationFormatted}\n" +
                "Size: ${song.size / 1024 / 1024} MB"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    // ── Permissions ────────────────────────────────────────────

    private fun checkPermissionsAndLoad() {
        val perms = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasPermission(Manifest.permission.READ_MEDIA_AUDIO))
                perms.add(Manifest.permission.READ_MEDIA_AUDIO)
            if (!hasPermission(Manifest.permission.POST_NOTIFICATIONS))
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            if (!hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE))
                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (perms.isEmpty()) loadMusic()
        else permissionLauncher.launch(perms.toTypedArray())
    }

    private fun hasPermission(perm: String) =
        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

    private fun loadMusic() { vm.loadSongs() }

    private fun showPermissionDenied() {
        emptyView.visibility = View.VISIBLE
        Toast.makeText(this, "Storage permission needed to read music files", Toast.LENGTH_LONG).show()
    }
}
