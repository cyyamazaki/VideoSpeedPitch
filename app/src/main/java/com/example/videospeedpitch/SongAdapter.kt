package com.example.videospeedpitch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class SongAdapter(
    private val onClick: (Song) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    private var items: List<Song> = emptyList()

    // null = nenhuma pasta selecionada ainda (não mostra o indicador de disponibilidade).
    private var availableCodes: Set<String>? = null

    fun submitList(newItems: List<Song>) {
        items = newItems
        notifyDataSetChanged()
    }

    /** Códigos com vídeo disponível na pasta selecionada; `null` oculta o indicador. */
    fun setAvailableCodes(codes: Set<String>?) {
        availableCodes = codes
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(items[position], availableCodes)
    }

    override fun getItemCount(): Int = items.size

    inner class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMusica: TextView = itemView.findViewById(R.id.tvMusica)
        private val tvArtista: TextView = itemView.findViewById(R.id.tvArtista)
        private val tvTrecho: TextView = itemView.findViewById(R.id.tvTrecho)
        private val tvAvailability: TextView = itemView.findViewById(R.id.tvAvailability)

        fun bind(song: Song, availableCodes: Set<String>?) {
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

            if (availableCodes == null) {
                tvAvailability.visibility = View.GONE
            } else {
                val available = song.codigo in availableCodes
                tvAvailability.visibility = View.VISIBLE
                tvAvailability.text = itemView.context.getString(
                    if (available) R.string.song_available else R.string.song_unavailable
                )
                tvAvailability.setTextColor(
                    ContextCompat.getColor(
                        itemView.context,
                        if (available) R.color.song_available else R.color.song_unavailable
                    )
                )
            }

            itemView.setOnClickListener { onClick(song) }
        }
    }
}
