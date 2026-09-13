package com.example.videospeedpitch

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt

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
 *
 * Uma barra de rolagem rápida na borda direita permite arrastar o dedo
 * para pular a lista proporcionalmente (útil em catálogos com milhares de
 * músicas), mostrando uma bolha com a letra inicial (do cantor ou da
 * música, conforme a ordenação escolhida) da posição atual.
 *
 * Quando aberta com [EXTRA_QUEUE_FOR_PLAYLIST] verdadeiro (pelo botão
 * "escolher a próxima da playlist" do player, ao terminar um vídeo),
 * escolher uma música não a toca direto: ela entra no fim da fila da
 * playlist e a tela fecha, tocando a próxima música pendente da fila (ver
 * [PlaylistPlayer]) em vez de simplesmente abrir o player para essa música.
 *
 * Quando a busca não encontra nada neste catálogo, aparece um botão para
 * buscar um karaokê dessa música (pelo texto digitado, presumindo cantor e
 * nome da música) no YouTube (ver [YouTubeSearchHelper]).
 */
class CatalogActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ASSET_NAME = "extra_asset_name"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_QUEUE_FOR_PLAYLIST = "extra_queue_for_playlist"
    }

    private enum class SortField { CANTOR, MUSICA }

    private lateinit var allSongs: List<Song>
    private lateinit var adapter: SongAdapter
    private lateinit var tvEmptyState: TextView
    private lateinit var editSearch: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var fastScrollThumb: View
    private lateinit var tvFastScrollLetter: TextView
    private lateinit var btnSearchYoutubeCatalog: Button

    private var lastQuery = ""
    private var sortField = SortField.CANTOR
    private var queueForPlaylist = false

    // Índice (código -> Uri) da pasta de vídeos, em cache em disco via CatalogRepository.
    private var fileIndex: Map<String, Uri>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_catalog)

        val assetName = intent.getStringExtra(EXTRA_ASSET_NAME) ?: return finish()
        title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.app_name)
        queueForPlaylist = intent.getBooleanExtra(EXTRA_QUEUE_FOR_PLAYLIST, false)

        allSongs = CatalogRepository.loadCatalog(this, assetName)

        recyclerView = findViewById(R.id.recyclerViewSongs)
        editSearch = findViewById(R.id.editSearch)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        val radioGroupSort: RadioGroup = findViewById(R.id.radioGroupSort)
        val fastScrollTrack: View = findViewById(R.id.fastScrollTrack)
        fastScrollThumb = findViewById(R.id.fastScrollThumb)
        tvFastScrollLetter = findViewById(R.id.tvFastScrollLetter)
        val tvQueueModeBanner: TextView = findViewById(R.id.tvQueueModeBanner)
        tvQueueModeBanner.visibility = if (queueForPlaylist) View.VISIBLE else View.GONE
        btnSearchYoutubeCatalog = findViewById(R.id.btnSearchYoutubeCatalog)
        btnSearchYoutubeCatalog.setOnClickListener {
            if (lastQuery.isNotEmpty()) {
                YouTubeSearchHelper.searchKaraokeAndOpen(this, lastQuery)
            }
        }

        adapter = SongAdapter { song -> onSongSelected(song) }
        layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter

        setupFastScroll(fastScrollTrack)

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

    /**
     * Arrastar em qualquer ponto do trilho invisível na borda direita pula a
     * lista para a posição proporcional a essa altura, mostrando uma bolha
     * com a letra inicial da música/cantor daquela posição enquanto arrasta.
     */
    private fun setupFastScroll(fastScrollTrack: View) {
        fastScrollTrack.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    updateFastScroll(event.y, view.height)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    tvFastScrollLetter.visibility = View.GONE
                    true
                }
                else -> false
            }
        }
    }

    private fun updateFastScroll(touchY: Float, trackHeight: Int) {
        val itemCount = adapter.itemCount
        if (itemCount == 0 || trackHeight <= 0) return

        val fraction = (touchY / trackHeight).coerceIn(0f, 1f)
        val targetIndex = (fraction * (itemCount - 1)).roundToInt()
        layoutManager.scrollToPositionWithOffset(targetIndex, 0)

        val thumbRange = (trackHeight - fastScrollThumb.height).coerceAtLeast(0)
        fastScrollThumb.translationY = fraction * thumbRange

        val song = adapter.songAt(targetIndex)
        val letterSource = if (sortField == SortField.MUSICA) song.musica else song.artista
        tvFastScrollLetter.text = CatalogRepository.normalize(letterSource)
            .firstOrNull { it.isLetterOrDigit() }
            ?.uppercaseChar()
            ?.toString()
            ?: "#"

        val bubbleRange = (trackHeight - tvFastScrollLetter.height).coerceAtLeast(0)
        tvFastScrollLetter.translationY = fraction * bubbleRange
        tvFastScrollLetter.visibility = View.VISIBLE
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

        // O botão de busca no YouTube só faz sentido quando o motivo de não
        // haver resultados foi uma busca sem correspondência (não quando o
        // catálogo inteiro está vazio).
        val showYoutubeFallback = sorted.isEmpty() && lastQuery.isNotEmpty()
        if (showYoutubeFallback) {
            btnSearchYoutubeCatalog.text = getString(R.string.search_youtube_for_query, lastQuery)
            btnSearchYoutubeCatalog.visibility = View.VISIBLE
        } else {
            btnSearchYoutubeCatalog.visibility = View.GONE
        }
    }

    private fun onSongSelected(song: Song) {
        if (queueForPlaylist) {
            onSongChosenForPlaylistQueue(song)
            return
        }

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
                getString(R.string.error_video_not_found, song.musica, song.artista, song.codigo),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        Toast.makeText(
            this,
            "${getString(R.string.toast_song_now_playing_prefix)}\n${song.toDisplayLine(this)}",
            Toast.LENGTH_SHORT
        ).show()

        val playerIntent = Intent(this, PlayerActivity::class.java)
        playerIntent.putExtra(PlayerActivity.EXTRA_VIDEO_URI, videoUri)
        PlayerActivity.putSongExtras(playerIntent, song)
        startActivity(playerIntent)
    }

    /**
     * Modo "escolher a próxima da playlist": em vez de tocar a música
     * direto, ela entra no fim da fila e a tela fecha, tocando a próxima
     * música pendente da fila (normalmente a que acabou de ser escolhida).
     */
    private fun onSongChosenForPlaylistQueue(song: Song) {
        val treeUriString = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
            .getString(Prefs.KEY_VIDEOS_TREE_URI, null)

        if (treeUriString == null) {
            Toast.makeText(this, R.string.error_no_folder_selected, Toast.LENGTH_LONG).show()
            return
        }

        val treeUri = Uri.parse(treeUriString)
        val index = fileIndex ?: CatalogRepository.getFileIndex(this, treeUri).also { fileIndex = it }

        if (!index.containsKey(song.codigo)) {
            Toast.makeText(
                this,
                getString(R.string.error_video_not_found, song.musica, song.artista, song.codigo),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        PlaylistManager.enqueue(song)
        Toast.makeText(
            this,
            "${getString(R.string.toast_song_added_prefix)}\n${song.toDisplayLine(this)}",
            Toast.LENGTH_SHORT
        ).show()

        PlaylistPlayer.playNextFromQueue(this)
        finish()
    }
}
