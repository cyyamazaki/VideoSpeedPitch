package com.example.videospeedpitch

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Reproduz um vídeo (recebido via Intent) permitindo variar a velocidade e o
 * tom (pitch) do áudio de forma independente, usando o ExoPlayer (Media3),
 * que internamente usa um algoritmo de time-stretch (Sonic) capaz de
 * separar velocidade de tom.
 *
 * O vídeo pode ter chegado de quatro origens: escolha manual de arquivo,
 * catálogo karaokê, catálogo japonês ou a playlist (fila FIFO) — todas
 * passam a Uri por [EXTRA_VIDEO_URI]. Quando [EXTRA_PLAYLIST_MODE] é
 * verdadeiro, ao terminar cada vídeo o player avança automaticamente para
 * a próxima música da fila em [PlaylistManager], até esvaziá-la; nesse
 * modo, também avisa com um toast 5 segundos antes do fim de cada vídeo
 * qual será a próxima música. Independente do modo, é possível digitar o
 * número de uma música aqui mesmo para adicioná-la à fila sem sair do
 * vídeo atual.
 *
 * O painel de controles (velocidade, pitch e número da música) fica oculto
 * por padrão, para o vídeo ocupar o máximo de espaço possível, e só
 * aparece ao tocar a tela, mover o mouse sobre o vídeo ou digitar um
 * número — reaproveitando o próprio mecanismo de exibição/ocultação de
 * controles do ExoPlayer. Sempre que aparece, o foco vai automaticamente
 * para o final do campo "Número da música".
 */
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_PLAYLIST_MODE = "extra_playlist_mode"
        private const val NEXT_SONG_WARNING_MS = 5000L
        private const val POLL_INTERVAL_MS = 500L
    }

    private var player: ExoPlayer? = null

    private lateinit var playerView: PlayerView
    private lateinit var controlsPanel: View
    private lateinit var radioGroupSpeed: RadioGroup
    private lateinit var radioGroupPitch: RadioGroup
    private lateinit var editSongNumber: EditText

    // Valores atuais (1.00x = normal)
    private var currentSpeed = 1.0f
    private var currentPitch = 1.0f

    private var currentUri: Uri? = null
    private var playlistMode = false

    // Índice (código -> Uri) da pasta de vídeos, usado apenas em modo playlist.
    private var fileIndex: Map<String, Uri>? = null

    private val handler = Handler(Looper.getMainLooper())
    private var warnedForCurrentVideo = false
    private val nextSongWarningPoller = object : Runnable {
        override fun run() {
            checkNextSongWarning()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.playerView)
        controlsPanel = findViewById(R.id.controlsPanel)
        enterImmersiveFullscreen()
        radioGroupSpeed = findViewById(R.id.radioGroupSpeed)
        radioGroupPitch = findViewById(R.id.radioGroupPitch)
        editSongNumber = findViewById(R.id.editPlayerSongNumber)
        val btnReset: Button = findViewById(R.id.btnReset)
        val btnAddToPlaylist: Button = findViewById(R.id.btnPlayerAddToPlaylist)

        title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.app_name)
        playlistMode = intent.getBooleanExtra(EXTRA_PLAYLIST_MODE, false)

        btnReset.setOnClickListener {
            radioGroupSpeed.check(R.id.radioSpeed100)
            radioGroupPitch.check(R.id.radioPitch100)
            currentSpeed = 1.00f
            currentPitch = 1.00f
            applyPlaybackParameters()
        }

        setupSelectors()
        setupSongNumberEntry(btnAddToPlaylist)
        setupControlsVisibility()

        val uri = intent.getParcelableExtra<Uri>(EXTRA_VIDEO_URI)
        if (uri == null) {
            Toast.makeText(this, "Nenhum vídeo informado.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        currentUri = uri
        playVideo(uri)
    }

    private fun setupSelectors() {
        radioGroupSpeed.setOnCheckedChangeListener { _, checkedId ->
            currentSpeed = when (checkedId) {
                R.id.radioSpeed90 -> 0.90f
                R.id.radioSpeed95 -> 0.95f
                else -> 1.00f
            }
            applyPlaybackParameters()
        }

        radioGroupPitch.setOnCheckedChangeListener { _, checkedId ->
            currentPitch = when (checkedId) {
                R.id.radioPitch90 -> 0.90f
                R.id.radioPitch95 -> 0.95f
                R.id.radioPitch105 -> 1.05f
                R.id.radioPitch110 -> 1.10f
                else -> 1.00f
            }
            applyPlaybackParameters()
        }
    }

    private fun setupSongNumberEntry(btnAddToPlaylist: Button) {
        btnAddToPlaylist.setOnClickListener { addSongToPlaylistFromPlayer() }
        editSongNumber.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addSongToPlaylistFromPlayer()
                true
            } else {
                false
            }
        }
        editSongNumber.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                addSongToPlaylistFromPlayer()
                true
            } else {
                false
            }
        }
        // Digitar mantém o painel visível (reinicia o temporizador de
        // ocultação automática do ExoPlayer) em vez de deixá-lo sumir
        // enquanto o usuário ainda está preenchendo o número.
        editSongNumber.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                playerView.showController()
            }
        })
        editSongNumber.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) playerView.showController()
        }
    }

    /**
     * Liga a exibição do painel de controles ao próprio mecanismo de
     * mostrar/ocultar do ExoPlayer: tocar a tela já alterna os controles
     * nativos do player, e aqui só espelhamos essa visibilidade no nosso
     * painel (velocidade/pitch/número). Movimento do mouse (hover, comum em
     * telas com ponteiro) também revela os controles. Sempre que o painel
     * aparece, o foco vai para o final do campo "Número da música".
     */
    private fun setupControlsVisibility() {
        playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                controlsPanel.visibility = visibility
                if (visibility == View.VISIBLE) {
                    controlsPanel.post { focusSongNumberAtEnd() }
                }
            }
        )

        playerView.setOnHoverListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> playerView.showController()
            }
            false
        }
    }

    /**
     * Tela cheia imersiva (oculta barra de status e navegação, reveláveis
     * com um swipe), para o vídeo ocupar o maior tamanho possível — usada
     * tanto na reprodução normal quanto no vídeo aleatório tocado quando o
     * app fica ocioso na tela inicial.
     */
    private fun enterImmersiveFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, playerView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterImmersiveFullscreen()
        }
    }

    private fun focusSongNumberAtEnd() {
        editSongNumber.requestFocus()
        val length = editSongNumber.text?.length ?: 0
        editSongNumber.setSelection(length)
    }

    /**
     * Permite que dígitos vindos de um teclado/controle remoto físico
     * (mesmo sem toque na tela) revelem o painel de controles e sejam
     * digitados direto no campo "Número da música", que já estará focado.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isDigitKey = event.keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 ||
            event.keyCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9
        if (event.action == KeyEvent.ACTION_DOWN && isDigitKey) {
            playerView.showController()
            if (!editSongNumber.hasFocus()) {
                focusSongNumberAtEnd()
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun addSongToPlaylistFromPlayer() {
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
        // Se a fila estava vazia, esta música passa a ser a próxima; permite
        // que o aviso de 5 segundos considere-a mesmo que já estejamos perto
        // do fim do vídeo atual.
        if (wasEmpty) warnedForCurrentVideo = false

        editSongNumber.text?.clear()
        Toast.makeText(
            this,
            getString(R.string.song_added_to_playlist, song.musica),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun checkNextSongWarning() {
        if (!playlistMode || warnedForCurrentVideo) return
        val exoPlayer = player ?: return
        val duration = exoPlayer.duration
        if (duration <= 0) return
        val remaining = duration - exoPlayer.currentPosition
        if (remaining in 0..NEXT_SONG_WARNING_MS) {
            val nextSong = PlaylistManager.peekAll().firstOrNull() ?: return
            Toast.makeText(
                this,
                getString(R.string.next_song_warning, nextSong.musica, nextSong.artista, nextSong.codigo),
                Toast.LENGTH_LONG
            ).show()
            warnedForCurrentVideo = true
        }
    }

    private fun applyPlaybackParameters() {
        player?.playbackParameters = PlaybackParameters(currentSpeed, currentPitch)
    }

    private fun playVideo(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            // Alguns provedores de conteúdo (ex.: DocumentFile de árvore) não
            // suportam/permitem permissão persistente adicional aqui; tudo bem, ignore.
        }

        releasePlayer()
        warnedForCurrentVideo = false

        val exoPlayer = ExoPlayer.Builder(this).build()
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Toast.makeText(
                    this@PlayerActivity,
                    "Erro ao reproduzir o vídeo: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && playlistMode) {
                    // Adiado para fora do callback do próprio player, evitando
                    // liberar/recriar o ExoPlayer durante seu próprio evento.
                    playerView.post { playNextFromPlaylist() }
                }
            }
        })

        playerView.player = exoPlayer

        val mediaItem = MediaItem.fromUri(uri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.playbackParameters = PlaybackParameters(currentSpeed, currentPitch)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        player = exoPlayer

        handler.removeCallbacks(nextSongWarningPoller)
        handler.post(nextSongWarningPoller)
    }

    private fun playNextFromPlaylist() {
        val treeUriString = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
            .getString(Prefs.KEY_VIDEOS_TREE_URI, null)
        if (treeUriString == null) return

        val treeUri = Uri.parse(treeUriString)
        val index = fileIndex ?: CatalogRepository.buildFileIndex(this, treeUri).also { fileIndex = it }

        // Pula, em ordem, códigos da fila sem arquivo correspondente na pasta.
        var nextSong = PlaylistManager.dequeue()
        var videoUri = nextSong?.let { index[it.codigo] }
        while (nextSong != null && videoUri == null) {
            Toast.makeText(
                this,
                getString(R.string.error_video_not_found, nextSong.codigo),
                Toast.LENGTH_SHORT
            ).show()
            nextSong = PlaylistManager.dequeue()
            videoUri = nextSong?.let { index[it.codigo] }
        }

        if (nextSong == null || videoUri == null) {
            Toast.makeText(this, R.string.playlist_finished, Toast.LENGTH_SHORT).show()
            return
        }

        currentUri = videoUri
        title = "${nextSong.artista} - ${nextSong.musica}"
        playVideo(videoUri)
    }

    private fun releasePlayer() {
        handler.removeCallbacks(nextSongWarningPoller)
        player?.release()
        player = null
    }

    override fun onStart() {
        super.onStart()
        if (player == null && currentUri != null) {
            playVideo(currentUri!!)
        }
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }
}
