package com.example.videospeedpitch

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Tela inicial (hub) do app.
 *
 * Fluxo:
 *  1. O usuário seleciona, uma vez, a pasta onde ficam os arquivos de vídeo
 *     (o nome de cada arquivo deve ser o código numérico da música, ex.:
 *     "18483.mp4"). Essa permissão fica salva entre execuções do app; a
 *     cada abertura, verificamos se o sistema ainda concede a permissão
 *     persistente antes de considerá-la válida.
 *  2. O usuário escolhe QUAL catálogo usar (Karaokê ou Japonês) — os dois
 *     catálogos são sempre mantidos separados; nunca aparecem misturados
 *     na mesma busca.
 *  3. Dentro do catálogo escolhido, busca por cantor/intérprete, música ou
 *     número e toca o vídeo correspondente.
 *  4. Também é possível digitar diretamente o número de uma música (em
 *     qualquer catálogo) para adicioná-la a uma playlist (fila FIFO) que
 *     acumula músicas e as reproduz em sequência.
 *
 * A opção de escolher um vídeo avulso (fora dos catálogos) continua
 * disponível, para manter a funcionalidade original do app.
 *
 * Modo ocioso: se a tela inicial ficar [IDLE_TIMEOUT_MS] sem nenhuma
 * interação (toque ou tecla) e uma pasta de vídeos já tiver sido
 * selecionada, o app toca automaticamente um vídeo aleatório dessa pasta,
 * no maior tamanho possível (o player já abre com os controles ocultos e
 * em tela cheia). O temporizador reinicia a cada interação e sempre que a
 * tela inicial volta a ficar em primeiro plano.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val IDLE_TIMEOUT_MS = 30_000L
    }

    private lateinit var tvFolderStatus: TextView
    private lateinit var editSongNumber: EditText
    private lateinit var tvPlaylistStatus: TextView

    private val idleHandler = Handler(Looper.getMainLooper())
    private val idleRunnable = Runnable { playRandomIdleVideo() }

    private val pickVideoLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                val playerIntent = Intent(this, PlayerActivity::class.java)
                playerIntent.putExtra(PlayerActivity.EXTRA_VIDEO_URI, uri)
                startActivity(playerIntent)
            }
        }

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

        val btnSelectFolder: Button = findViewById(R.id.btnSelectFolder)
        val btnChooseVideo: Button = findViewById(R.id.btnChooseVideo)
        val btnCatalogKaraoke: Button = findViewById(R.id.btnCatalogKaraoke)
        val btnCatalogJapones: Button = findViewById(R.id.btnCatalogJapones)
        val btnAddToPlaylist: Button = findViewById(R.id.btnAddToPlaylist)
        val btnOpenPlaylist: Button = findViewById(R.id.btnOpenPlaylist)

        btnSelectFolder.setOnClickListener {
            pickFolderLauncher.launch(null)
        }

        btnChooseVideo.setOnClickListener {
            pickVideoLauncher.launch(arrayOf("video/*"))
        }

        btnCatalogKaraoke.setOnClickListener {
            openCatalog("catalogo_karaoke.json", getString(R.string.catalog_karaoke_title))
        }

        btnCatalogJapones.setOnClickListener {
            openCatalog("catalogo_japones.json", getString(R.string.catalog_japones_title))
        }

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
    }

    override fun onResume() {
        super.onResume()
        updateFolderStatus()
        updatePlaylistStatus()
        scheduleIdleTimer()
    }

    override fun onPause() {
        super.onPause()
        idleHandler.removeCallbacks(idleRunnable)
    }

    /**
     * Chamado automaticamente pelo Android a cada toque, tecla ou clique
     * despachado para esta Activity — o gatilho ideal para reiniciar a
     * contagem de ociosidade sem precisar interceptar cada View individual.
     */
    override fun onUserInteraction() {
        super.onUserInteraction()
        scheduleIdleTimer()
    }

    private fun scheduleIdleTimer() {
        idleHandler.removeCallbacks(idleRunnable)
        idleHandler.postDelayed(idleRunnable, IDLE_TIMEOUT_MS)
    }

    private fun playRandomIdleVideo() {
        val treeUriString = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
            .getString(Prefs.KEY_VIDEOS_TREE_URI, null) ?: return
        val treeUri = Uri.parse(treeUriString)

        val videos = CatalogRepository.getFileIndex(this, treeUri).values.toList()
        if (videos.isEmpty()) return

        val playerIntent = Intent(this, PlayerActivity::class.java)
        playerIntent.putExtra(PlayerActivity.EXTRA_VIDEO_URI, videos.random())
        playerIntent.putExtra(PlayerActivity.EXTRA_TITLE, getString(R.string.idle_random_video_title))
        startActivity(playerIntent)
    }

    private fun openCatalog(assetName: String, title: String) {
        val intent = Intent(this, CatalogActivity::class.java)
        intent.putExtra(CatalogActivity.EXTRA_ASSET_NAME, assetName)
        intent.putExtra(CatalogActivity.EXTRA_TITLE, title)
        startActivity(intent)
    }

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

        PlaylistManager.enqueue(song)
        editSongNumber.text?.clear()
        Toast.makeText(
            this,
            getString(R.string.song_added_to_playlist, song.musica),
            Toast.LENGTH_SHORT
        ).show()
        updatePlaylistStatus()
    }

    private fun updatePlaylistStatus() {
        tvPlaylistStatus.text = getString(R.string.playlist_status, PlaylistManager.size())
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
