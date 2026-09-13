package com.example.videospeedpitch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PlaylistAdapter(
    private val onRemove: (Song) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder>() {

    private var items: List<Song> = emptyList()

    fun submitList(newItems: List<Song>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_playlist, parent, false)
        return PlaylistViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    inner class PlaylistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvPosition: TextView = itemView.findViewById(R.id.tvPlaylistPosition)
        private val tvMusica: TextView = itemView.findViewById(R.id.tvPlaylistMusica)
        private val tvArtista: TextView = itemView.findViewById(R.id.tvPlaylistArtista)
        private val tvTrecho: TextView = itemView.findViewById(R.id.tvPlaylistTrecho)
        private val btnRemove: Button = itemView.findViewById(R.id.btnRemoveFromPlaylist)

        fun bind(song: Song, position: Int) {
            tvPosition.text = (position + 1).toString()
            tvMusica.text = song.musica
            tvArtista.text = itemView.context.getString(
                R.string.song_subtitle_format,
                song.artista,
                song.codigo
            )
            if (song.trecho.isNotBlank()) {
                tvTrecho.visibility = View.VISIBLE
                tvTrecho.text = song.trecho
            } else {
                tvTrecho.visibility = View.GONE
            }
            btnRemove.setOnClickListener { onRemove(song) }
        }
    }
}
