package com.neonplayer.ui.adapters

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.neonplayer.R
import com.neonplayer.model.Song

class SongAdapter(
    private val onSongClick: (Song) -> Unit,
    private val onMoreClick: ((Song, View) -> Unit)? = null
) : ListAdapter<Song, SongAdapter.SongViewHolder>(SongDiff()) {

    private var currentPlayingId: Long = -1L

    fun setCurrentPlaying(songId: Long) {
        val old = currentPlayingId
        currentPlayingId = songId
        // Refresh old and new items
        currentList.forEachIndexed { i, s ->
            if (s.id == old || s.id == songId) notifyItemChanged(i)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_song, parent, false)
        return SongViewHolder(v)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(getItem(position), getItem(position).id == currentPlayingId)
    }

    inner class SongViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val albumArt: ImageView = view.findViewById(R.id.imgAlbumArt)
        private val tvTitle: TextView   = view.findViewById(R.id.tvSongTitle)
        private val tvArtist: TextView  = view.findViewById(R.id.tvArtistName)
        private val tvDuration: TextView = view.findViewById(R.id.tvDuration)
        private val btnMore: ImageView  = view.findViewById(R.id.btnMore)
        private val nowPlayingBar: View = view.findViewById(R.id.nowPlayingBar)

        fun bind(song: Song, isPlaying: Boolean) {
            tvTitle.text = song.title
            tvArtist.text = song.artist
            tvDuration.text = song.durationFormatted
            nowPlayingBar.visibility = if (isPlaying) View.VISIBLE else View.GONE
            tvTitle.alpha = if (isPlaying) 1f else 0.9f

            // Load album art with Glide
            Glide.with(albumArt)
                .load(song.albumArtUri)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .placeholder(R.drawable.ic_album_placeholder)
                .error(R.drawable.ic_album_placeholder)
                .centerCrop()
                .into(albumArt)

            itemView.setOnClickListener { onSongClick(song) }
            btnMore.setOnClickListener { onMoreClick?.invoke(song, btnMore) }
        }
    }

    class SongDiff : DiffUtil.ItemCallback<Song>() {
        override fun areItemsTheSame(old: Song, new: Song) = old.id == new.id
        override fun areContentsTheSame(old: Song, new: Song) = old == new
    }
}
