package com.neonplayer.ui.viewmodels

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.neonplayer.model.Song
import com.neonplayer.service.MusicService
import com.neonplayer.utils.MusicScanner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    // ── Song library ───────────────────────────────────────────
    private val _allSongs = MutableLiveData<List<Song>>(emptyList())
    val allSongs: LiveData<List<Song>> = _allSongs

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // ── Playback state ─────────────────────────────────────────
    private val _currentSong = MutableLiveData<Song?>()
    val currentSong: LiveData<Song?> = _currentSong

    private val _isPlaying = MutableLiveData(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _progress = MutableLiveData(0L)
    val progress: LiveData<Long> = _progress

    private val _duration = MutableLiveData(1L)
    val duration: LiveData<Long> = _duration

    private val _shuffleOn = MutableLiveData(false)
    val shuffleOn: LiveData<Boolean> = _shuffleOn

    private val _repeatMode = MutableLiveData(Player.REPEAT_MODE_OFF)
    val repeatMode: LiveData<Int> = _repeatMode

    // ── Search ─────────────────────────────────────────────────
    private val _searchResults = MutableLiveData<List<Song>>(emptyList())
    val searchResults: LiveData<List<Song>> = _searchResults

    // ── Current queue ──────────────────────────────────────────
    var currentQueue: List<Song> = emptyList()
    var currentIndex: Int = 0

    // ── MediaController (talks to MusicService) ────────────────
    private var controller: MediaController? = null

    fun connectToService(componentName: ComponentName) {
        val sessionToken = SessionToken(getApplication(), componentName)
        val future = MediaController.Builder(getApplication(), sessionToken).buildAsync()
        future.addListener({
            controller = future.get()
            controller?.addListener(playerListener)
            startProgressPolling()
        }, MoreExecutors.directExecutor())
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.postValue(isPlaying)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val idx = controller?.currentMediaItemIndex ?: 0
            if (idx < currentQueue.size) {
                _currentSong.postValue(currentQueue[idx])
                currentIndex = idx
            }
        }
    }

    private fun startProgressPolling() {
        viewModelScope.launch {
            while (true) {
                delay(500)
                controller?.let {
                    _progress.postValue(it.currentPosition)
                    _duration.postValue(it.duration.coerceAtLeast(1L))
                }
            }
        }
    }

    // ── Music library loading ─────────────────────────────────

    fun loadSongs() {
        viewModelScope.launch {
            _isLoading.value = true
            val songs = MusicScanner.scanAllSongs(getApplication())
            _allSongs.value = songs
            _searchResults.value = songs
            _isLoading.value = false
        }
    }

    fun search(query: String) {
        val all = _allSongs.value ?: return
        _searchResults.value = if (query.isBlank()) all
        else MusicScanner.searchSongs(all, query)
    }

    // ── Playback controls ─────────────────────────────────────

    fun playSong(song: Song, queue: List<Song> = _allSongs.value ?: listOf(song)) {
        currentQueue = queue
        currentIndex = queue.indexOf(song).coerceAtLeast(0)

        val mediaItems = queue.map { s ->
            MediaItem.Builder()
                .setUri(s.uri)
                .setMediaId(s.id.toString())
                .setRequestMetadata(
                    MediaItem.RequestMetadata.Builder().setMediaUri(s.uri).build()
                )
                .build()
        }

        controller?.run {
            setMediaItems(mediaItems, currentIndex, 0L)
            prepare()
            play()
        }
        _currentSong.value = song
        _isPlaying.value = true
    }

    fun togglePlayPause() {
        controller?.run {
            if (isPlaying) pause() else play()
        }
    }

    fun skipNext() { controller?.seekToNextMediaItem() }
    fun skipPrev() {
        controller?.run {
            if (currentPosition > 3000) seekTo(0) else seekToPreviousMediaItem()
        }
    }

    fun seekTo(ms: Long) { controller?.seekTo(ms) }

    fun toggleShuffle() {
        val next = !(_shuffleOn.value ?: false)
        _shuffleOn.value = next
        controller?.shuffleModeEnabled = next
    }

    fun cycleRepeat() {
        val next = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = next
        controller?.repeatMode = next
    }

    override fun onCleared() {
        controller?.removeListener(playerListener)
        controller?.release()
        super.onCleared()
    }
}
