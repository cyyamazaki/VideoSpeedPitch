package com.example.videospeedpitch

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Tela de busca dentro de UM catálogo (karaokê OU japonês — nunca os dois
 * ao mesmo tempo). O usuário digita o nome do cantor/intérprete, da música
 * ou o número (código) e pressiona Enter/Buscar para atualizar a lista —
 * a lista não é recalculada a cada tecla digitada, evitando lentidão em
 * catálogos grandes. Ao escolher um item, se a pasta de vídeos já tiver
 * sido selecionada, o app localiza o arquivo cujo nome é o código da
 * música e abre o player.
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
    private lateinit var editSearch: EditText

    // Índice (código -> Uri) da pasta de vídeos, construído sob demanda.
    private var fileIndex: Map<String, Uri>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_catalog)

        val assetName = intent.getStringExtra(EXTRA_ASSET_NAME) ?: return finish()
        title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.app_name)

        allSongs = CatalogRepository.loadCatalog(this, assetName)

        val recyclerView: RecyclerView = findViewById(R.id.recyclerViewSongs)
        editSearch = findViewById(R.id.editSearch)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        adapter = SongAdapter { song -> onSongSelected(song) }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        updateEmptyState(query = "")

        // A lista só é atualizada quando o usuário confirma a busca
        // (Enter/ação de busca do teclado), não a cada tecla digitada.
        editSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                filter(editSearch.text?.toString().orEmpty())
                true
            } else {
                false
            }
        }
        editSearch.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                filter(editSearch.text?.toString().orEmpty())
                true
            } else {
                false
            }
        }
    }

    private fun filter(rawQuery: String) {
        val trimmed = rawQuery.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) {
            adapter.submitList(emptyList())
            updateEmptyState(trimmed)
            return
        }
        val query = CatalogRepository.normalize(trimmed)
        val filtered = allSongs.filter { song ->
            CatalogRepository.normalize(song.artista).contains(query) ||
                CatalogRepository.normalize(song.musica).contains(query) ||
                song.codigo.contains(trimmed)
        }
        adapter.submitList(filtered)
        updateEmptyState(trimmed, resultCount = filtered.size)
    }

    private fun updateEmptyState(query: String, resultCount: Int = -1) {
        tvEmptyState.text = when {
            query.length < MIN_QUERY_LENGTH ->
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