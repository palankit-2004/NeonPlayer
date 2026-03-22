package com.neonplayer.model

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,           // milliseconds
    val uri: Uri,
    val albumArtUri: Uri?,
    val path: String,
    val dateAdded: Long,
    val size: Long
) {
    val durationFormatted: String get() {
        val totalSecs = duration / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return "%d:%02d".format(mins, secs)
    }
}

data class Playlist(
    val id: Long,
    val name: String,
    val songs: MutableList<Song> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis()
)

enum class RepeatMode { OFF, ONE, ALL }
enum class ShuffleMode { OFF, ON }

data class PlayerState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val progress: Long = 0L,
    val duration: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleMode: ShuffleMode = ShuffleMode.OFF,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = 0
)
