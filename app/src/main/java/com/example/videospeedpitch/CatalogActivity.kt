package com.example.videospeedpitch

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Tela de busca dentro de UM catálogo (karaokê OU japonês — nunca os dois
 * ao mesmo tempo). O usuário digita o nome do cantor/intérprete ou da
 * música, escolhe um item da lista e, se a pasta de vídeos já tiver sido
 * selecionada, o app localiza o arquivo cujo nome é o código da música e
 * abre o player.
 */
class CatalogActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ASSET_NAME = "extra_asset_name"
        const val EXTRA_TITLE = "extra_title"
        private const val MIN_QUERY_LENGTH = 2
    }

    private lateinit var allSongs: List<Song>
    private lateinit var adapter: SongAdapter
    private lateinit var tvEmptyState: TextView

    // Índice (código -> Uri) da pasta de vídeos, construído sob demanda.
    private var fileIndex: Map<String, Uri>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_catalog)

        val assetName = intent.getStringExtra(EXTRA_ASSET_NAME) ?: return finish()
        title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.app_name)

        allSongs = CatalogRepository.loadCatalog(this, assetName)

        val recyclerView: RecyclerView = findViewById(R.id.recyclerViewSongs)
        val editSearch: EditText = findViewById(R.id.editSearch)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        adapter = SongAdapter { song -> onSongSelected(song) }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        updateEmptyState(query = "")

        editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filter(s?.toString().orEmpty())
            }
        })
    }

    private fun filter(rawQuery: String) {
        if (rawQuery.trim().length < MIN_QUERY_LENGTH) {
            adapter.submitList(emptyList())
            updateEmptyState(rawQuery)
            return
        }
        val query = CatalogRepository.normalize(rawQuery.trim())
        val filtered = allSongs.filter { song ->
            CatalogRepository.normalize(song.artista).contains(query) ||
                CatalogRepository.normalize(song.musica).contains(query)
        }
        adapter.submitList(filtered)
        updateEmptyState(rawQuery, resultCount = filtered.size)
    }

    private fun updateEmptyState(query: String, resultCount: Int = -1) {
        tvEmptyState.text = when {
            query.trim().length < MIN_QUERY_LENGTH ->
                getString(R.string.catalog_hint_min_chars, MIN_QUERY_LENGTH)
            resultCount == 0 -> getString(R.string.catalog_no_results)
            else -> ""
        }
    }

    private fun onSongSelected(song: Song) {
        val treeUriString = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
            .getString(Prefs.KEY_VIDEOS_TREE_URI, null)

        if (treeUriString == null) {
            Toast.makeText(this, R.string.error_no_folder_selected, Toast.LENGTH_LONG).show()
            return
        }

        val treeUri = Uri.parse(treeUriString)
        val index = fileIndex ?: CatalogRepository.buildFileIndex(this, treeUri).also { fileIndex = it }

        val videoUri = index[song.codigo]
        if (videoUri == null) {
            Toast.makeText(
                this,
                getString(R.string.error_video_not_found, song.codigo),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val playerIntent = Intent(this, PlayerActivity::class.java)
        playerIntent.putExtra(PlayerActivity.EXTRA_VIDEO_URI, videoUri)
        playerIntent.putExtra(PlayerActivity.EXTRA_TITLE, "${song.artista} - ${song.musica}")
        startActivity(playerIntent)
    }
}
