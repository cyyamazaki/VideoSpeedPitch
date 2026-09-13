package com.example.videospeedpitch

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
 * O vídeo pode ter chegado de três origens: escolha manual de arquivo,
 * catálogo karaokê ou catálogo japonês — todas passam a Uri por
 * [EXTRA_VIDEO_URI].
 */
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val EXTRA_TITLE = "extra_title"
    }

    private var player: ExoPlayer? = null

    private lateinit var playerView: PlayerView
    private lateinit var seekSpeed: SeekBar
    private lateinit var seekPitch: SeekBar
    private lateinit var tvSpeed: TextView
    private lateinit var tvPitch: TextView

    // Valores atuais (1.00x = normal)
    private var currentSpeed = 1.0f
    private var currentPitch = 1.0f

    // Faixa de velocidade: progresso 0..275 -> 0.25x..3.00x
    private val speedMin = 0.25f
    private val speedMax = 3.00f
    private val speedStep = 0.01f

    // Faixa de tom: progresso 0..150 -> 0.50x..2.00x
    private val pitchMin = 0.50f
    private val pitchMax = 2.00f
    private val pitchStep = 0.01f

    private var currentUri: Uri? = null

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.playerView)
        seekSpeed = findViewById(R.id.seekSpeed)
        seekPitch = findViewById(R.id.seekPitch)
        tvSpeed = findViewById(R.id.tvSpeed)
        tvPitch = findViewById(R.id.tvPitch)
        val btnReset: Button = findViewById(R.id.btnReset)

        title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.app_name)

        btnReset.setOnClickListener {
            currentSpeed = 1.0f
            currentPitch = 1.0f
            seekSpeed.progress = speedToProgress(currentSpeed)
            seekPitch.progress = pitchToProgress(currentPitch)
            updateSpeedLabel()
            updatePitchLabel()
            applyPlaybackParameters()
        }

        setupSeekBars()

        val uri = intent.getParcelableExtra<Uri>(EXTRA_VIDEO_URI)
        if (uri == null) {
            Toast.makeText(this, "Nenhum vídeo informado.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        currentUri = uri
        playVideo(uri)
    }

    private fun setupSeekBars() {
        seekSpeed.max = ((speedMax - speedMin) / speedStep).toInt()
        seekSpeed.progress = speedToProgress(currentSpeed)
        updateSpeedLabel()

        seekSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentSpeed = progressToSpeed(progress)
                updateSpeedLabel()
                applyPlaybackParameters()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekPitch.max = ((pitchMax - pitchMin) / pitchStep).toInt()
        seekPitch.progress = pitchToProgress(currentPitch)
        updatePitchLabel()

        seekPitch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentPitch = progressToPitch(progress)
                updatePitchLabel()
                applyPlaybackParameters()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun speedToProgress(speed: Float) = ((speed - speedMin) / speedStep).toInt()
    private fun progressToSpeed(progress: Int) = speedMin + (progress * speedStep)

    private fun pitchToProgress(pitch: Float) = ((pitch - pitchMin) / pitchStep).toInt()
    private fun progressToPitch(progress: Int) = pitchMin + (progress * pitchStep)

    private fun updateSpeedLabel() {
        tvSpeed.text = getString(R.string.speed_label, currentSpeed)
    }

    private fun updatePitchLabel() {
        tvPitch.text = getString(R.string.pitch_label, currentPitch)
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

        val exoPlayer = ExoPlayer.Builder(this).build()
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Toast.makeText(
                    this@PlayerActivity,
                    "Erro ao reproduzir o vídeo: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        })

        playerView.player = exoPlayer

        val mediaItem = MediaItem.fromUri(uri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.playbackParameters = PlaybackParameters(currentSpeed, currentPitch)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        player = exoPlayer
    }

    private fun releasePlayer() {
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
