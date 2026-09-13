package com.example.videospeedpitch

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Mostra a fila FIFO de músicas adicionadas pelo número (em MainActivity) e
 * permite tocá-la em sequência: o primeiro item da fila é removido e
 * enviado ao player, que avança automaticamente para os próximos
 * (ver [PlayerActivity.EXTRA_PLAYLIST_MODE]) até a fila esvaziar.
 */
class PlaylistActivity : AppCompatActivity() {

    private lateinit var adapter: PlaylistAdapter
    private lateinit var tvEmptyState: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist)
        title = getString(R.string.view_playlist)

        tvEmptyState = findViewById(R.id.tvPlaylistEmptyState)
        val recyclerView: RecyclerView = findViewById(R.id.recyclerViewPlaylist)
        val btnPlay: Button = findViewById(R.id.btnPlayPlaylist)
        val btnClear: Button = findViewById(R.id.btnClearPlaylist)

        adapter = PlaylistAdapter { song ->
            PlaylistManager.remove(song)
            refresh()
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        btnPlay.setOnClickListener { playFromQueue() }
        btnClear.setOnClickListener {
            PlaylistManager.clear()
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val items = PlaylistManager.peekAll()
        adapter.submitList(items)
        tvEmptyState.text = if (items.isEmpty()) getString(R.string.playlist_empty) else ""
    }

    private fun playFromQueue() {
        val treeUriString = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
            .getString(Prefs.KEY_VIDEOS_TREE_URI, null)
        if (treeUriString == null) {
            Toast.makeText(this, R.string.error_no_folder_selected, Toast.LENGTH_LONG).show()
            return
        }
        if (PlaylistManager.isEmpty()) {
            Toast.makeText(this, R.string.playlist_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val treeUri = Uri.parse(treeUriString)
        val index = CatalogRepository.getFileIndex(this, treeUri)

        // Descarta, do início da fila, códigos sem arquivo correspondente.
        var firstSong = PlaylistManager.peekAll().firstOrNull()
        while (firstSong != null && !index.containsKey(firstSong.codigo)) {
            PlaylistManager.dequeue()
            Toast.makeText(
                this,
                getString(R.string.error_video_not_found, firstSong.codigo),
                Toast.LENGTH_SHORT
            ).show()
            firstSong = PlaylistManager.peekAll().firstOrNull()
        }
        refresh()

        if (firstSong == null) return
        val videoUri = index[firstSong.codigo] ?: return
        PlaylistManager.dequeue()
        refresh()

        val playerIntent = Intent(this, PlayerActivity::class.java)
        playerIntent.putExtra(PlayerActivity.EXTRA_VIDEO_URI, videoUri)
        playerIntent.putExtra(PlayerActivity.EXTRA_TITLE, "${firstSong.artista} - ${firstSong.musica}")
        playerIntent.putExtra(PlayerActivity.EXTRA_PLAYLIST_MODE, true)
        startActivity(playerIntent)
    }
}
