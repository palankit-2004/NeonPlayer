package com.neonplayer.utils

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.neonplayer.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MusicScanner {

    private val ALBUM_ART_URI = Uri.parse("content://media/external/audio/albumart")

    suspend fun scanAllSongs(context: Context): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} = 1 AND ${MediaStore.Audio.Media.DURATION} > 30000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val cursor: Cursor? = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )

        cursor?.use { c ->
            val idCol       = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol    = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol   = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol    = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol  = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol     = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val dateCol     = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val sizeCol     = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val albumId = c.getLong(albumIdCol)
                val songUri = Uri.withAppendedPath(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString()
                )
                val albumArtUri = Uri.withAppendedPath(ALBUM_ART_URI, albumId.toString())

                songs.add(
                    Song(
                        id       = id,
                        title    = c.getString(titleCol) ?: "Unknown",
                        artist   = c.getString(artistCol) ?: "Unknown Artist",
                        album    = c.getString(albumCol) ?: "Unknown Album",
                        duration = c.getLong(durationCol),
                        uri      = songUri,
                        albumArtUri = albumArtUri,
                        path     = c.getString(dataCol) ?: "",
                        dateAdded = c.getLong(dateCol),
                        size     = c.getLong(sizeCol)
                    )
                )
            }
        }

        songs
    }

    fun groupByArtist(songs: List<Song>): Map<String, List<Song>> =
        songs.groupBy { it.artist }.toSortedMap()

    fun groupByAlbum(songs: List<Song>): Map<String, List<Song>> =
        songs.groupBy { it.album }.toSortedMap()

    fun searchSongs(songs: List<Song>, query: String): List<Song> {
        val q = query.lowercase().trim()
        return songs.filter {
            it.title.lowercase().contains(q) ||
            it.artist.lowercase().contains(q) ||
            it.album.lowercase().contains(q)
        }
    }
}
