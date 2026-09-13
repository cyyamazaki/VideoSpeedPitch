package com.example.videospeedpitch

import android.content.Intent
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
import android.widget.TextView
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
 * vídeo atual. Ao terminar o vídeo — ou, em modo playlist, ao esvaziar a
 * fila — a tela inicial é reaberta automaticamente.
 *
 * O ExoPlayer roda sem seus controles nativos (`use_controller=false`): eles
 * escureceriam o vídeo com um scrim atrás dos botões, atrapalhando a leitura
 * de legendas embutidas. Em vez disso, a própria Activity mostra/oculta um
 * painel próprio (velocidade, pitch e número da música), oculto por padrão
 * para o vídeo ocupar o máximo de espaço possível, que aparece ao tocar a
 * tela, mover o mouse sobre o vídeo ou digitar um número, e some sozinho
 * após alguns segundos sem interação. Sempre que aparece, o foco vai
 * automaticamente para o final do campo "Número da música".
 *
 * No alto do vídeo, independente do painel de controles, um overlay mostra
 * sempre os dados da música atual (cantor, música, código e início da
 * letra), de forma mais discreta os da próxima música da playlist (quando
 * houver uma pendente na fila) e, com a mesma discrição, botões fora dos
 * controles principais: pausar/retomar, avançar para a próxima música da
 * playlist, finalizar a playlist (só enquanto o modo playlist estiver
 * ativo; esvazia a fila e desliga o avanço automático) e voltar para a
 * tela inicial a qualquer momento.
 *
 * Ao girar a tela, a Activity é recriada normalmente pelo Android (não
 * usamos o truque de `configChanges` para suprimir isso); [onSaveInstanceState]
 * guarda vídeo atual, título, modo playlist, velocidade/pitch escolhidos e a
 * posição de reprodução, e [onCreate] restaura tudo isso antes de retomar o
 * vídeo de onde parou.
 */
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_PLAYLIST_MODE = "extra_playlist_mode"
        const val EXTRA_SONG_ARTISTA = "extra_song_artista"
        const val EXTRA_SONG_MUSICA = "extra_song_musica"
        const val EXTRA_SONG_TRECHO = "extra_song_trecho"
        const val EXTRA_SONG_CODIGO = "extra_song_codigo"
        private const val NEXT_SONG_WARNING_MS = 5000L
        private const val POLL_INTERVAL_MS = 500L
        private const val CONTROLS_AUTO_HIDE_MS = 5000L

        private const val STATE_VIDEO_URI = "state_video_uri"
        private const val STATE_TITLE = "state_title"
        private const val STATE_PLAYLIST_MODE = "state_playlist_mode"
        private const val STATE_SPEED = "state_speed"
        private const val STATE_PITCH = "state_pitch"
        private const val STATE_POSITION_MS = "state_position_ms"
        private const val STATE_PLAY_WHEN_READY = "state_play_when_ready"
        private const val STATE_SONG_ARTISTA = "state_song_artista"
        private const val STATE_SONG_MUSICA = "state_song_musica"
        private const val STATE_SONG_TRECHO = "state_song_trecho"
        private const val STATE_SONG_CODIGO = "state_song_codigo"

        /** Anexa os dados completos da música (usados nos overlays do player) ao Intent. */
        fun putSongExtras(intent: Intent, song: Song) {
            intent.putExtra(EXTRA_SONG_ARTISTA, song.artista)
            intent.putExtra(EXTRA_SONG_MUSICA, song.musica)
            intent.putExtra(EXTRA_SONG_TRECHO, song.trecho)
            intent.putExtra(EXTRA_SONG_CODIGO, song.codigo)
        }
    }

    private var player: ExoPlayer? = null

    private lateinit var playerView: PlayerView
    private lateinit var controlsPanel: View
    private lateinit var radioGroupSpeed: RadioGroup
    private lateinit var radioGroupPitch: RadioGroup
    private lateinit var editSongNumber: EditText
    private lateinit var tvCurrentSongInfo: TextView
    private lateinit var tvNextSongInfo: TextView
    private lateinit var btnTogglePause: Button
    private lateinit var btnSkipPlaylist: Button
    private lateinit var btnEndPlaylist: Button
    private lateinit var btnGoHome: Button

    // Valores atuais (1.00x = normal)
    private var currentSpeed = 1.0f
    private var currentPitch = 1.0f

    private var currentUri: Uri? = null
    private var playlistMode = false

    // Dados completos (cantor, música, trecho, código) da música atual, para
    // o overlay no alto do vídeo. Pode ser nulo se o vídeo não veio
    // acompanhado desses dados (nesse caso o overlay simplesmente não aparece).
    private var currentSong: Song? = null

    // Consumidos uma única vez, na primeira playVideo() após onCreate (para
    // restaurar posição/estado de reprodução ao recriar a Activity, p.ex.
    // por rotação de tela); chamadas seguintes de playVideo (avanço de
    // playlist) sempre começam do zero, tocando.
    private var pendingSeekPositionMs = 0L
    private var pendingPlayWhenReady = true

    private val handler = Handler(Looper.getMainLooper())
    private var warnedForCurrentVideo = false
    private val nextSongWarningPoller = object : Runnable {
        override fun run() {
            checkNextSongWarning()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    // Como o player roda sem controles nativos, o painel de controles
    // próprio (velocidade/pitch/número) precisa do seu próprio temporizador
    // de ocultação automática.
    private val hideControlsRunnable = Runnable { hideControlsPanel() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.playerView)
        controlsPanel = findViewById(R.id.controlsPanel)
        enterImmersiveFullscreen()
        radioGroupSpeed = findViewById(R.id.radioGroupSpeed)
        radioGroupPitch = findViewById(R.id.radioGroupPitch)
        editSongNumber = findViewById(R.id.editPlayerSongNumber)
        tvCurrentSongInfo = findViewById(R.id.tvCurrentSongInfo)
        tvNextSongInfo = findViewById(R.id.tvNextSongInfo)
        btnTogglePause = findViewById(R.id.btnTogglePause)
        btnSkipPlaylist = findViewById(R.id.btnSkipPlaylist)
        btnEndPlaylist = findViewById(R.id.btnEndPlaylist)
        btnGoHome = findViewById(R.id.btnGoHome)
        val btnReset: Button = findViewById(R.id.btnReset)
        val btnAddToPlaylist: Button = findViewById(R.id.btnPlayerAddToPlaylist)

        title = savedInstanceState?.getString(STATE_TITLE)
            ?: intent.getStringExtra(EXTRA_TITLE)
            ?: getString(R.string.app_name)
        playlistMode = savedInstanceState?.getBoolean(STATE_PLAYLIST_MODE)
            ?: intent.getBooleanExtra(EXTRA_PLAYLIST_MODE, false)
        updatePlaylistModeUi()
        currentSpeed = savedInstanceState?.getFloat(STATE_SPEED) ?: 1.0f
        currentPitch = savedInstanceState?.getFloat(STATE_PITCH) ?: 1.0f
        pendingSeekPositionMs = savedInstanceState?.getLong(STATE_POSITION_MS) ?: 0L
        pendingPlayWhenReady = savedInstanceState?.getBoolean(STATE_PLAY_WHEN_READY) ?: true
        currentSong = readSongExtras(savedInstanceState)
        updateCurrentSongOverlay()

        btnReset.setOnClickListener {
            radioGroupSpeed.check(R.id.radioSpeed100)
            radioGroupPitch.check(R.id.radioPitch100)
            currentSpeed = 1.00f
            currentPitch = 1.00f
            applyPlaybackParameters()
        }

        setupSelectors()
        // Reflete a velocidade/pitch restaurados (ou o padrão de 100%) nos
        // botões de seleção antes de começar a tocar.
        radioGroupSpeed.check(speedToRadioId(currentSpeed))
        radioGroupPitch.check(pitchToRadioId(currentPitch))
        setupSongNumberEntry(btnAddToPlaylist)
        setupControlsVisibility()

        btnTogglePause.setOnClickListener { togglePause() }
        btnSkipPlaylist.setOnClickListener { skipToNextInPlaylist() }
        btnEndPlaylist.setOnClickListener { endPlaylist() }
        btnGoHome.setOnClickListener { returnToHome() }

        val uri = savedInstanceState?.getParcelable<Uri>(STATE_VIDEO_URI)
            ?: intent.getParcelableExtra<Uri>(EXTRA_VIDEO_URI)
        if (uri == null) {
            Toast.makeText(this, "Nenhum vídeo informado.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        currentUri = uri
        playVideo(uri)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(STATE_VIDEO_URI, currentUri)
        outState.putString(STATE_TITLE, title?.toString())
        outState.putBoolean(STATE_PLAYLIST_MODE, playlistMode)
        outState.putFloat(STATE_SPEED, currentSpeed)
        outState.putFloat(STATE_PITCH, currentPitch)
        // player pode já ter sido liberado (onStop já rodou antes deste
        // callback em algumas versões do Android) — nesse caso, pendingSeek*
        // já guarda a última posição/estado capturados em onStop().
        outState.putLong(STATE_POSITION_MS, player?.currentPosition ?: pendingSeekPositionMs)
        outState.putBoolean(STATE_PLAY_WHEN_READY, player?.playWhenReady ?: pendingPlayWhenReady)
        currentSong?.let { song ->
            outState.putString(STATE_SONG_ARTISTA, song.artista)
            outState.putString(STATE_SONG_MUSICA, song.musica)
            outState.putString(STATE_SONG_TRECHO, song.trecho)
            outState.putString(STATE_SONG_CODIGO, song.codigo)
        }
    }

    /** Lê os dados da música do savedInstanceState (rotação) ou, na primeira vez, do Intent. */
    private fun readSongExtras(savedInstanceState: Bundle?): Song? {
        val artista = savedInstanceState?.getString(STATE_SONG_ARTISTA)
            ?: intent.getStringExtra(EXTRA_SONG_ARTISTA)
        val musica = savedInstanceState?.getString(STATE_SONG_MUSICA)
            ?: intent.getStringExtra(EXTRA_SONG_MUSICA)
        if (artista == null || musica == null) return null
        val trecho = savedInstanceState?.getString(STATE_SONG_TRECHO)
            ?: intent.getStringExtra(EXTRA_SONG_TRECHO)
            ?: ""
        val codigo = savedInstanceState?.getString(STATE_SONG_CODIGO)
            ?: intent.getStringExtra(EXTRA_SONG_CODIGO)
            ?: ""
        return Song(artista = artista, codigo = codigo, musica = musica, trecho = trecho)
    }

    /** Mostra, sempre que houver, os dados da música atual no alto do vídeo. */
    private fun updateCurrentSongOverlay() {
        val song = currentSong
        if (song == null) {
            tvCurrentSongInfo.visibility = View.GONE
        } else {
            tvCurrentSongInfo.visibility = View.VISIBLE
            tvCurrentSongInfo.text = song.toDisplayLine(this)
        }
    }

    /**
     * Mostra, de forma discreta, os dados da próxima música da playlist logo
     * abaixo da atual, junto com o botão de avançar para ela — só quando há
     * uma fila com item pendente.
     */
    private fun updateNextSongOverlay() {
        val next = PlaylistManager.peekAll().firstOrNull()
        if (next == null) {
            tvNextSongInfo.visibility = View.GONE
            btnSkipPlaylist.visibility = View.GONE
        } else {
            tvNextSongInfo.visibility = View.VISIBLE
            tvNextSongInfo.text = getString(
                R.string.next_song_overlay_format,
                next.musica,
                next.artista,
                next.codigo
            )
            btnSkipPlaylist.visibility = View.VISIBLE
        }
    }

    /** Alterna pausar/retomar o vídeo atual — único jeito de pausar, já que não há controles nativos. */
    private fun togglePause() {
        val exoPlayer = player ?: return
        exoPlayer.playWhenReady = !exoPlayer.playWhenReady
        updatePauseButtonIcon()
    }

    private fun updatePauseButtonIcon() {
        val isPlaying = player?.playWhenReady ?: true
        btnTogglePause.text = getString(if (isPlaying) R.string.pause_icon else R.string.play_icon)
    }

    /**
     * Encerra o vídeo atual e avança imediatamente para a próxima música da
     * playlist, sem esperar o fim do vídeo — passa a valer o modo playlist
     * dali em diante (avanço automático e aviso dos 5 segundos).
     */
    private fun skipToNextInPlaylist() {
        if (PlaylistManager.isEmpty()) return
        playlistMode = true
        updatePlaylistModeUi()
        playNextFromPlaylist()
    }

    /** Mostra o botão de finalizar playlist só enquanto o modo playlist estiver ativo. */
    private fun updatePlaylistModeUi() {
        btnEndPlaylist.visibility = if (playlistMode) View.VISIBLE else View.GONE
    }

    /**
     * Finaliza a playlist: esvazia a fila e desativa o modo playlist, para
     * que o vídeo atual não avance mais sozinho ao terminar (volta a
     * simplesmente retornar à tela inicial, como um vídeo avulso).
     */
    private fun endPlaylist() {
        PlaylistManager.clear()
        playlistMode = false
        updatePlaylistModeUi()
        updateNextSongOverlay()
        Toast.makeText(this, R.string.playlist_ended_toast, Toast.LENGTH_SHORT).show()
    }

    private fun speedToRadioId(speed: Float) = when (speed) {
        0.90f -> R.id.radioSpeed90
        0.95f -> R.id.radioSpeed95
        else -> R.id.radioSpeed100
    }

    private fun pitchToRadioId(pitch: Float) = when (pitch) {
        0.90f -> R.id.radioPitch90
        0.95f -> R.id.radioPitch95
        1.05f -> R.id.radioPitch105
        1.10f -> R.id.radioPitch110
        else -> R.id.radioPitch100
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
        // ocultação automática) em vez de deixá-lo sumir enquanto o usuário
        // ainda está preenchendo o número.
        editSongNumber.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                showControlsPanel()
            }
        })
        editSongNumber.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showControlsPanel()
        }
    }

    /**
     * Como o player roda sem controles nativos (`use_controller=false`, para
     * não escurecer o vídeo nem atrapalhar legendas), a própria Activity
     * decide quando mostrar/ocultar o painel: toque na tela alterna
     * (mostra/some), movimento do mouse (hover, comum em telas com
     * ponteiro) sempre revela, e ele some sozinho após alguns segundos sem
     * interação. Sempre que aparece, o foco vai para o final do campo
     * "Número da música".
     */
    private fun setupControlsVisibility() {
        playerView.setOnClickListener { toggleControlsPanel() }

        playerView.setOnHoverListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> showControlsPanel()
            }
            false
        }
    }

    private fun toggleControlsPanel() {
        if (controlsPanel.visibility == View.VISIBLE) {
            hideControlsPanel()
        } else {
            showControlsPanel()
        }
    }

    private fun showControlsPanel() {
        controlsPanel.visibility = View.VISIBLE
        controlsPanel.post { focusSongNumberAtEnd() }
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, CONTROLS_AUTO_HIDE_MS)
    }

    private fun hideControlsPanel() {
        controlsPanel.visibility = View.GONE
        handler.removeCallbacks(hideControlsRunnable)
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
            showControlsPanel()
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
        updateNextSongOverlay()

        editSongNumber.text?.clear()
        Toast.makeText(
            this,
            "${getString(R.string.toast_song_added_prefix)}\n${song.toDisplayLine(this)}",
            Toast.LENGTH_LONG
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
                Intent.FLAG_GRANT_READ_URI_PERMISSION
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
                if (playbackState == Player.STATE_ENDED) {
                    // Adiado para fora do callback do próprio player, evitando
                    // liberar/recriar o ExoPlayer durante seu próprio evento.
                    playerView.post {
                        if (playlistMode) {
                            playNextFromPlaylist()
                        } else {
                            returnToHome()
                        }
                    }
                }
            }
        })

        playerView.player = exoPlayer

        val mediaItem = MediaItem.fromUri(uri)
        exoPlayer.setMediaItem(mediaItem)
        if (pendingSeekPositionMs > 0L) {
            exoPlayer.seekTo(pendingSeekPositionMs)
            pendingSeekPositionMs = 0L
        }
        exoPlayer.playbackParameters = PlaybackParameters(currentSpeed, currentPitch)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = pendingPlayWhenReady
        pendingPlayWhenReady = true

        player = exoPlayer

        updateNextSongOverlay()
        updatePauseButtonIcon()

        handler.removeCallbacks(nextSongWarningPoller)
        handler.post(nextSongWarningPoller)
    }

    private fun playNextFromPlaylist() {
        val treeUriString = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
            .getString(Prefs.KEY_VIDEOS_TREE_URI, null)
        if (treeUriString == null) {
            returnToHome()
            return
        }

        val treeUri = Uri.parse(treeUriString)
        val index = CatalogRepository.getFileIndex(this, treeUri)

        // Pula, em ordem, códigos da fila sem arquivo correspondente na pasta.
        var nextSong = PlaylistManager.dequeue()
        var videoUri = nextSong?.let { index[it.codigo] }
        while (nextSong != null && videoUri == null) {
            Toast.makeText(
                this,
                getString(R.string.error_video_not_found, nextSong.musica, nextSong.artista, nextSong.codigo),
                Toast.LENGTH_SHORT
            ).show()
            nextSong = PlaylistManager.dequeue()
            videoUri = nextSong?.let { index[it.codigo] }
        }

        if (nextSong == null || videoUri == null) {
            Toast.makeText(this, R.string.playlist_finished, Toast.LENGTH_SHORT).show()
            returnToHome()
            return
        }

        currentUri = videoUri
        currentSong = nextSong
        title = "${nextSong.artista} - ${nextSong.musica}"
        updateCurrentSongOverlay()
        playVideo(videoUri)
    }

    /** Fecha o player e volta para a tela inicial, limpando o restante da pilha. */
    private fun returnToHome() {
        val homeIntent = Intent(this, MainActivity::class.java)
        homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(homeIntent)
        finish()
    }

    private fun releasePlayer() {
        handler.removeCallbacks(nextSongWarningPoller)
        handler.removeCallbacks(hideControlsRunnable)
        player?.release()
        player = null
    }

    override fun onStart() {
        super.onStart()
        // pendingSeekPositionMs/pendingPlayWhenReady já trazem a posição e o
        // estado (tocando/pausado) salvos em onStop, então playVideo() retoma
        // exatamente de onde o app foi deixado, mesmo sem o Android ter
        // recriado a Activity.
        if (player == null && currentUri != null) {
            playVideo(currentUri!!)
        }
    }

    override fun onStop() {
        super.onStop()
        // Salva onde o vídeo parou antes de liberar o player, para retomar
        // do mesmo ponto quando o app voltar ao primeiro plano (sem precisar
        // que a Activity seja recriada) — o app fica em segundo plano, não
        // fechado, mas o ExoPlayer não pode continuar rodando sem a tela.
        pendingSeekPositionMs = player?.currentPosition ?: 0L
        pendingPlayWhenReady = player?.playWhenReady ?: true
        releasePlayer()
    }
}
