package com.example.videospeedpitch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SongAdapter(
    private val onClick: (Song) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    private var items: List<Song> = emptyList()

    fun submitList(newItems: List<Song>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMusica: TextView = itemView.findViewById(R.id.tvMusica)
        private val tvArtista: TextView = itemView.findViewById(R.id.tvArtista)

        fun bind(song: Song) {
            tvMusica.text = song.musica
            tvArtista.text = itemView.context.getString(
                R.string.song_subtitle_format,
                song.artista,
                song.codigo
            )
            itemView.setOnClickListener { onClick(song) }
        }
    }
}
