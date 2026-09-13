package com.example.videospeedpitch

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Tela de UM catálogo (karaokê OU japonês — nunca os dois ao mesmo tempo).
 * O catálogo inteiro (carregado em memória a partir do JSON) já aparece
 * listado, ordenado por cantor/intérprete ou por música à escolha do
 * usuário — não é preciso buscar para navegar por ele. Digitar o nome do
 * cantor, da música ou o número (código) e pressionar Enter/Buscar filtra
 * essa listagem; a lista só é recalculada nessa confirmação (não a cada
 * tecla digitada), evitando lentidão em catálogos grandes. Cada item já
 * indica visualmente se há um vídeo correspondente na pasta selecionada
 * (em vez de só descobrir isso ao tocar no item). Ao escolher um item, se
 * a pasta de vídeos já tiver sido selecionada, o app localiza o arquivo
 * cujo nome é o código da música e abre o player.
 */
class CatalogActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ASSET_NAME = "extra_asset_name"
        const val EXTRA_TITLE = "extra_title"
    }

    private enum class SortField { CANTOR, MUSICA }

    private lateinit var allSongs: List<Song>
    private lateinit var adapter: SongAdapter
    private lateinit var tvEmptyState: TextView
    private lateinit var editSearch: EditText

    private var lastQuery = ""
    private var sortField = SortField.CANTOR

    // Índice (código -> Uri) da pasta de vídeos, em cache em disco via CatalogRepository.
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
        val radioGroupSort: RadioGroup = findViewById(R.id.radioGroupSort)

        adapter = SongAdapter { song -> onSongSelected(song) }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // A lista só é refiltrada quando o usuário confirma a busca
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

        radioGroupSort.setOnCheckedChangeListener { _, checkedId ->
            sortField = if (checkedId == R.id.radioSortMusica) SortField.MUSICA else SortField.CANTOR
            applyFilterAndSort()
        }

        // Mostra o catálogo inteiro (ordenado) desde a abertura da tela,
        // sem exigir uma busca antes.
        applyFilterAndSort()
    }

    override fun onResume() {
        super.onResume()
        // Recarrega a disponibilidade de vídeos a cada retorno à tela (a
        // pasta pode ter sido trocada ou o cache invalidado enquanto o
        // catálogo estava em segundo plano).
        loadAvailability()
    }

    /**
     * Carrega (do cache em disco, quando possível) quais códigos têm vídeo
     * na pasta selecionada, para marcar visualmente cada item da lista.
     * Se nenhuma pasta foi selecionada ainda, não marca nada (adapter
     * recebe `null` e simplesmente não mostra o indicador).
     */
    private fun loadAvailability() {
        val treeUriString = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
            .getString(Prefs.KEY_VIDEOS_TREE_URI, null)

        if (treeUriString == null) {
            fileIndex = null
            adapter.setAvailableCodes(null)
            return
        }

        val treeUri = Uri.parse(treeUriString)
        val index = CatalogRepository.getFileIndex(this, treeUri)
        fileIndex = index
        adapter.setAvailableCodes(index.keys)
    }

    private fun filter(rawQuery: String) {
        lastQuery = rawQuery.trim()
        applyFilterAndSort()
    }

    private fun applyFilterAndSort() {
        val base = if (lastQuery.isEmpty()) {
            allSongs
        } else {
            val query = CatalogRepository.normalize(lastQuery)
            allSongs.filter { song ->
                CatalogRepository.normalize(song.artista).contains(query) ||
                    CatalogRepository.normalize(song.musica).contains(query) ||
                    song.codigo.contains(lastQuery)
            }
        }

        val sorted = when (sortField) {
            SortField.CANTOR -> base.sortedBy { CatalogRepository.normalize(it.artista) }
            SortField.MUSICA -> base.sortedBy { CatalogRepository.normalize(it.musica) }
        }

        adapter.submitList(sorted)
        tvEmptyState.text = if (sorted.isEmpty()) getString(R.string.catalog_no_results) else ""
    }

    private fun onSongSelected(song: Song) {
        val treeUriString = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
            .getString(Prefs.KEY_VIDEOS_TREE_URI, null)

        if (treeUriString == null) {
            Toast.makeText(this, R.string.error_no_folder_selected, Toast.LENGTH_LONG).show()
            return
        }

        val treeUri = Uri.parse(treeUriString)
        val index = fileIndex ?: CatalogRepository.getFileIndex(this, treeUri).also { fileIndex = it }

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
