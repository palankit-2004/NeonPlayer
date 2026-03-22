package com.neonplayer.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.neonplayer.ui.activities.MainActivity

/**
 * MusicService — runs in foreground, survives screen off and app background.
 *
 * Uses ExoPlayer + MediaSession for:
 * - Lock screen controls
 * - Notification with play/pause/next/prev
 * - Bluetooth headset controls
 * - Audio focus handling
 * - Background / screen-off playback
 */
class MusicService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession

    companion object {
        const val ACTION_PLAY   = "com.neonplayer.PLAY"
        const val ACTION_PAUSE  = "com.neonplayer.PAUSE"
        const val ACTION_NEXT   = "com.neonplayer.NEXT"
        const val ACTION_PREV   = "com.neonplayer.PREV"
        const val ACTION_STOP   = "com.neonplayer.STOP"

        // Broadcast to wallpaper for visualizer
        const val ACTION_BEAT   = "com.neonplayer.BEAT"
        const val EXTRA_AMPLITUDE = "amplitude"
        const val EXTRA_IS_PLAYING = "is_playing"
    }

    override fun onCreate() {
        super.onCreate()

        // ── Build ExoPlayer with audio focus ──────────────────
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)   // pause on headphone unplug
            .build()

        player.repeatMode = Player.REPEAT_MODE_OFF
        player.shuffleModeEnabled = false

        // ── Build MediaSession ────────────────────────────────
        val activityIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(activityIntent)
            .setCallback(MediaSessionCallback())
            .build()

        // ── Listen for playback state changes → broadcast to wallpaper ──
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                broadcastPlayState(isPlaying)
            }
        })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onDestroy() {
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep playing when app is swiped away
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    // ── Broadcast amplitude to live wallpaper ─────────────────
    private fun broadcastPlayState(isPlaying: Boolean) {
        sendBroadcast(Intent(ACTION_BEAT).apply {
            putExtra(EXTRA_IS_PLAYING, isPlaying)
            putExtra(EXTRA_AMPLITUDE, 0f)
        })
    }

    fun broadcastAmplitude(amplitude: Float) {
        sendBroadcast(Intent(ACTION_BEAT).apply {
            putExtra(EXTRA_IS_PLAYING, player.isPlaying)
            putExtra(EXTRA_AMPLITUDE, amplitude)
        })
    }

    // ── MediaSession Callback ─────────────────────────────────
    private inner class MediaSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                )
                .build()
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val updatedItems = mediaItems.map {
                it.buildUpon().setUri(it.requestMetadata.mediaUri).build()
            }.toMutableList()
            return Futures.immediateFuture(updatedItems)
        }
    }

    // ── Public control methods (called from UI) ───────────────

    fun getPlayer(): ExoPlayer = player

    fun playSongs(songs: List<com.neonplayer.model.Song>, startIndex: Int = 0) {
        val mediaItems = songs.map { song ->
            MediaItem.Builder()
                .setUri(song.uri)
                .setMediaId(song.id.toString())
                .setRequestMetadata(
                    MediaItem.RequestMetadata.Builder()
                        .setMediaUri(song.uri)
                        .build()
                )
                .build()
        }
        player.setMediaItems(mediaItems, startIndex, 0L)
        player.prepare()
        player.play()
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(position: Long) = player.seekTo(position)

    fun skipNext() {
        if (player.hasNextMediaItem()) player.seekToNextMediaItem()
    }

    fun skipPrev() {
        if (player.currentPosition > 3000) {
            player.seekTo(0)
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        }
    }

    fun setRepeat(mode: Int) { player.repeatMode = mode }
    fun setShuffle(enabled: Boolean) { player.shuffleModeEnabled = enabled }
}
