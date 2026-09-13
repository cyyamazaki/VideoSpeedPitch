package com.example.videospeedpitch

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Tela inicial (hub) do app.
 *
 * Fluxo (nessa ordem na tela, priorizando a ação mais rápida/frequente):
 *  1. Digitar diretamente o número de uma música (em qualquer catálogo)
 *     para adicioná-la a uma playlist (fila FIFO) que acumula músicas e as
 *     reproduz em sequência. Esse campo sempre está em foco, com o cursor
 *     no final do que já foi digitado, pronto para receber o próximo
 *     número sem precisar tocar nele.
 *  2. O usuário escolhe QUAL catálogo usar (Karaokê ou Japonês) — os dois
 *     catálogos são sempre mantidos separados; nunca aparecem misturados
 *     na mesma busca. Dentro do catálogo escolhido, busca por
 *     cantor/intérprete, música ou número e toca o vídeo correspondente.
 *  3. Por último, a seleção (única, esporádica) da pasta onde ficam os
 *     arquivos de vídeo (o nome de cada arquivo deve ser o código numérico
 *     da música, ex.: "18483.mp4"). Essa permissão fica salva entre
 *     execuções do app; a cada abertura, verificamos se o sistema ainda
 *     concede a permissão persistente antes de considerá-la válida.
 *
 * Vídeo em segundo plano: sempre que a tela inicial está em primeiro
 * plano e uma pasta de vídeos já foi selecionada, um vídeo aleatório dessa
 * pasta toca embutido nela mesma (sem espera e sem abrir outra Activity),
 * mostrando os dados da música no alto. A área de controles encolhe para
 * dar lugar ao vídeo, que ocupa a maior parte da tela, mas continua
 * acessível (rolável) logo abaixo — nenhuma interação nesta tela
 * interrompe o vídeo. Ele só é escondido ao navegar para uma tela de
 * escolha em lista (catálogo ou playlist) ou ao sair do app sem fechá-lo
 * (ex.: botão Início); ao voltar, retoma o **mesmo** vídeo exatamente de
 * onde parou (posição e se estava tocando/pausado), em vez de sortear um
 * novo. Botões discretos no alto do vídeo permitem trocar para outro
 * aleatório a qualquer momento (sem esperar o atual terminar), buscar a
 * música atual no YouTube (ver [YouTubeSearchHelper]) e adicioná-la à
 * playlist (só enfileira, sem tocar na hora — ela já está tocando aqui).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var tvFolderStatus: TextView
    private lateinit var editSongNumber: EditText
    private lateinit var tvPlaylistStatus: TextView
    private lateinit var idleVideoContainer: View
    private lateinit var idlePlayerView: PlayerView
    private lateinit var tvIdleSongInfo: TextView
    private lateinit var btnShuffleIdleVideo: Button
    private lateinit var btnSearchYoutubeIdle: Button
    private lateinit var btnAddIdleToPlaylist: Button

    private var idlePlayer: ExoPlayer? = null
    private var idleFileIndex: Map<String, Uri> = emptyMap()
    private var currentIdleSong: Song? = null

    // Guardam qual vídeo estava tocando e de onde, para retomar exatamente
    // do mesmo ponto ao voltar a esta tela (troca de tela, ou sair do app
    // sem fechá-lo) em vez de sempre sortear um vídeo novo do zero.
    private var lastIdleCodigo: String? = null
    private var lastIdleUri: Uri? = null
    private var pendingIdleSeekPositionMs = 0L
    private var pendingIdlePlayWhenReady = true

    private val pickFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: SecurityException) {
                    // Ignorado: alguns provedores não suportam permissão persistente.
                }
                getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
                    .edit()
                    .putString(Prefs.KEY_VIDEOS_TREE_URI, uri.toString())
                    .apply()
                // Uma nova seleção de pasta invalida qualquer índice de
                // arquivos em cache (pasta diferente, ou o usuário quer
                // forçar uma nova varredura após adicionar vídeos).
                CatalogRepository.invalidateFileIndexCache(this)
                updateFolderStatus()
                Toast.makeText(this, R.string.folder_selected_ok, Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvFolderStatus = findViewById(R.id.tvFolderStatus)
        editSongNumber = findViewById(R.id.editSongNumber)
        tvPlaylistStatus = findViewById(R.id.tvPlaylistStatus)
        idleVideoContainer = findViewById(R.id.idleVideoContainer)
        idlePlayerView = findViewById(R.id.idlePlayerView)
        tvIdleSongInfo = findViewById(R.id.tvIdleSongInfo)
        btnShuffleIdleVideo = findViewById(R.id.btnShuffleIdleVideo)
        btnShuffleIdleVideo.setOnClickListener { playNextIdleVideo() }
        btnSearchYoutubeIdle = findViewById(R.id.btnSearchYoutubeIdle)
        btnSearchYoutubeIdle.setOnClickListener { searchIdleSongOnYoutube() }
        btnAddIdleToPlaylist = findViewById(R.id.btnAddIdleToPlaylist)
        btnAddIdleToPlaylist.setOnClickListener { addIdleSongToPlaylist() }

        val btnSelectFolder: Button = findViewById(R.id.btnSelectFolder)
        val btnCatalogKaraoke: Button = findViewById(R.id.btnCatalogKaraoke)
        val btnCatalogJapones: Button = findViewById(R.id.btnCatalogJapones)
        val btnAddToPlaylist: Button = findViewById(R.id.btnAddToPlaylist)
        val btnOpenPlaylist: Button = findViewById(R.id.btnOpenPlaylist)
        val btnRefreshCatalogs: Button = findViewById(R.id.btnRefreshCatalogs)
        val btnAbout: Button = findViewById(R.id.btnAbout)

        btnSelectFolder.setOnClickListener {
            pickFolderLauncher.launch(null)
        }

        btnCatalogKaraoke.setOnClickListener {
            openCatalog("catalogo_karaoke.json", getString(R.string.catalog_karaoke_title))
        }

        btnCatalogJapones.setOnClickListener {
            openCatalog("catalogo_japones.json", getString(R.string.catalog_japones_title))
        }

        btnRefreshCatalogs.setOnClickListener { refreshCatalogsAvailability() }

        btnAddToPlaylist.setOnClickListener { addSongToPlaylist() }
        editSongNumber.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addSongToPlaylist()
                true
            } else {
                false
            }
        }
        editSongNumber.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                addSongToPlaylist()
                true
            } else {
                false
            }
        }

        btnOpenPlaylist.setOnClickListener {
            startActivity(Intent(this, PlaylistActivity::class.java))
        }

        btnAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updateFolderStatus()
        updatePlaylistStatus()
        playRandomIdleVideo()
        focusSongNumberAtEnd()
    }

    override fun onPause() {
        super.onPause()
        stopIdlePlayback()
    }

    /** Mantém o foco sempre no final do campo de número, pronto para digitar. */
    private fun focusSongNumberAtEnd() {
        editSongNumber.requestFocus()
        editSongNumber.setSelection(editSongNumber.text?.length ?: 0)
    }

    /**
     * Começa a tocar, embutido nesta tela e sem espera, um vídeo da pasta
     * selecionada. Se havia um vídeo tocando quando a tela saiu de primeiro
     * plano (ver [stopIdlePlayback]), retoma exatamente ele, na mesma
     * posição e estado (tocando/pausado); senão, sorteia um vídeo novo.
     */
    private fun playRandomIdleVideo() {
        val treeUriString = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
            .getString(Prefs.KEY_VIDEOS_TREE_URI, null) ?: return
        val treeUri = Uri.parse(treeUriString)

        idleFileIndex = CatalogRepository.getFileIndex(this, treeUri)
        if (idleFileIndex.isEmpty()) return

        idleVideoContainer.visibility = View.VISIBLE

        val exoPlayer = ExoPlayer.Builder(this).build()
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                // Ao terminar um vídeo, começa outro aleatório da mesma
                // pasta, continuamente, até a tela sair de primeiro plano.
                if (playbackState == Player.STATE_ENDED) {
                    playNextIdleVideo()
                }
            }
        })
        idlePlayerView.player = exoPlayer
        idlePlayer = exoPlayer

        val resumeCodigo = lastIdleCodigo
        val resumeUri = lastIdleUri
        if (resumeCodigo != null && resumeUri != null && idleFileIndex[resumeCodigo] == resumeUri) {
            playIdleVideo(resumeCodigo, resumeUri, pendingIdleSeekPositionMs, pendingIdlePlayWhenReady)
        } else {
            playNextIdleVideo()
        }
        pendingIdleSeekPositionMs = 0L
        pendingIdlePlayWhenReady = true
    }

    /** Sorteia e toca um vídeo novo da pasta, do início — usado ao terminar um vídeo ou pelo botão de trocar. */
    private fun playNextIdleVideo() {
        if (idleFileIndex.isEmpty()) return
        val (codigo, uri) = idleFileIndex.entries.random()
        playIdleVideo(codigo, uri, seekPositionMs = 0L, playWhenReady = true)
    }

    private fun playIdleVideo(codigo: String, uri: Uri, seekPositionMs: Long, playWhenReady: Boolean) {
        val exoPlayer = idlePlayer ?: return
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        if (seekPositionMs > 0L) {
            exoPlayer.seekTo(seekPositionMs)
        }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = playWhenReady
        lastIdleCodigo = codigo
        lastIdleUri = uri
        updateIdleSongInfo(codigo)
    }

    /** Mostra, sobre o vídeo, os dados (cantor, música, início da letra) do vídeo aleatório atual. */
    private fun updateIdleSongInfo(codigo: String) {
        val song = CatalogRepository.findSongByCodigo(this, codigo)
        currentIdleSong = song
        if (song == null) {
            tvIdleSongInfo.visibility = View.GONE
            return
        }
        tvIdleSongInfo.visibility = View.VISIBLE
        tvIdleSongInfo.text = "${getString(R.string.idle_now_playing_prefix)}\n${song.toDisplayLine(this)}"
    }

    /** Busca a música do vídeo aleatório atual no YouTube, ou avisa por toast se não achar. */
    private fun searchIdleSongOnYoutube() {
        val song = currentIdleSong
        if (song == null) {
            Toast.makeText(this, R.string.error_song_info_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        YouTubeSearchHelper.searchAndOpen(this, song)
    }

    /**
     * Adiciona a música do vídeo aleatório atual à playlist — só enfileira,
     * sem tocar na hora (ela já está tocando aqui mesmo), diferente do
     * comportamento da caixa de número.
     */
    private fun addIdleSongToPlaylist() {
        val song = currentIdleSong
        if (song == null) {
            Toast.makeText(this, R.string.error_song_info_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        PlaylistManager.enqueue(song)
        updatePlaylistStatus()
        Toast.makeText(
            this,
            "${getString(R.string.toast_song_added_prefix)}\n${song.toDisplayLine(this)}",
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * Encerra o vídeo em segundo plano (se houver) e devolve a área de
     * controles ao tamanho normal — mas antes salva a posição e se estava
     * tocando/pausado, para retomar exatamente dali na próxima vez que esta
     * tela voltar a ficar em primeiro plano (ver [playRandomIdleVideo]).
     */
    private fun stopIdlePlayback() {
        val exoPlayer = idlePlayer ?: return
        pendingIdleSeekPositionMs = exoPlayer.currentPosition
        pendingIdlePlayWhenReady = exoPlayer.playWhenReady
        exoPlayer.release()
        idlePlayer = null
        idlePlayerView.player = null
        idleVideoContainer.visibility = View.GONE
    }

    private fun openCatalog(assetName: String, title: String) {
        // Lembrado para reabrir o mesmo catálogo quando o vídeo terminar,
        // caso o usuário tenha ativado "escolher a próxima da playlist"
        // (ver PlayerActivity.btnQueueFromCatalog).
        getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
            .edit()
            .putString(Prefs.KEY_LAST_CATALOG_ASSET, assetName)
            .putString(Prefs.KEY_LAST_CATALOG_TITLE, title)
            .apply()

        val intent = Intent(this, CatalogActivity::class.java)
        intent.putExtra(CatalogActivity.EXTRA_ASSET_NAME, assetName)
        intent.putExtra(CatalogActivity.EXTRA_TITLE, title)
        startActivity(intent)
    }

    /**
     * Adiciona a música digitada à playlist. Se a fila estava vazia (esta é
     * a primeira música), toca-a imediatamente em vez de só enfileirar —
     * mesmo comportamento de escolher uma música direto no catálogo.
     */
    private fun addSongToPlaylist() {
        val codigo = editSongNumber.text?.toString()?.trim().orEmpty()
        if (codigo.isEmpty()) return

        val song = CatalogRepository.findSongByCodigo(this, codigo)
        if (song == null) {
            Toast.makeText(
                this,
                getString(R.string.error_song_number_not_found, codigo),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val wasEmpty = PlaylistManager.isEmpty()
        PlaylistManager.enqueue(song)
        editSongNumber.text?.clear()

        if (wasEmpty) {
            playFirstFromPlaylist()
        } else {
            Toast.makeText(
                this,
                "${getString(R.string.toast_song_added_prefix)}\n${song.toDisplayLine(this)}",
                Toast.LENGTH_LONG
            ).show()
            updatePlaylistStatus()
        }
    }

    /**
     * Toca a primeira música da fila imediatamente (usada assim que ela é
     * adicionada com a fila vazia) — mesmo fluxo de "Tocar playlist" na
     * tela de playlist, entrando em modo playlist a partir daí.
     */
    private fun playFirstFromPlaylist() {
        PlaylistPlayer.playNextFromQueue(this)
        updatePlaylistStatus()
    }

    private fun updatePlaylistStatus() {
        tvPlaylistStatus.text = getString(R.string.playlist_status, PlaylistManager.size())
    }

    /**
     * Força uma nova varredura da pasta de vídeos (descartando o índice em
     * cache), atualizando a disponibilidade (✓/✗) que os catálogos mostram
     * sem precisar reabri-los depois de adicionar ou remover arquivos na
     * pasta.
     */
    private fun refreshCatalogsAvailability() {
        val treeUriString = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
            .getString(Prefs.KEY_VIDEOS_TREE_URI, null)
        if (treeUriString == null) {
            Toast.makeText(this, R.string.error_no_folder_selected, Toast.LENGTH_LONG).show()
            return
        }

        val treeUri = Uri.parse(treeUriString)
        CatalogRepository.invalidateFileIndexCache(this)
        val freshIndex = CatalogRepository.getFileIndex(this, treeUri)

        Toast.makeText(
            this,
            getString(R.string.catalogs_refreshed_toast, freshIndex.size),
            Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * Reconfere, a cada abertura da tela, se a permissão persistente da
     * pasta de vídeos ainda é válida junto ao sistema (o usuário pode tê-la
     * revogado fora do app). Se não for mais válida, limpa a preferência
     * salva para não indicar uma pasta que não pode mais ser lida.
     */
    private fun updateFolderStatus() {
        val prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        val treeUriString = prefs.getString(Prefs.KEY_VIDEOS_TREE_URI, null)
        val treeUri = treeUriString?.let { Uri.parse(it) }
        val stillValid = treeUri != null && contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission
        }

        if (treeUriString != null && !stillValid) {
            prefs.edit().remove(Prefs.KEY_VIDEOS_TREE_URI).apply()
        }

        tvFolderStatus.text = if (stillValid) {
            getString(R.string.folder_selected_status, treeUri!!.path)
        } else {
            getString(R.string.folder_not_selected_status)
        }
    }
}
