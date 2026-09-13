package com.example.videospeedpitch

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
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
 *     "18483.mp4"). Essa permissão fica salva entre execuções do app.
 *  2. O usuário escolhe QUAL catálogo usar (Karaokê ou Japonês) — os dois
 *     catálogos são sempre mantidos separados; nunca aparecem misturados
 *     na mesma busca.
 *  3. Dentro do catálogo escolhido, busca por cantor/intérprete ou música
 *     e toca o vídeo correspondente.
 *
 * A opção de escolher um vídeo avulso (fora dos catálogos) continua
 * disponível, para manter a funcionalidade original do app.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var tvFolderStatus: TextView

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
                updateFolderStatus()
                Toast.makeText(this, R.string.folder_selected_ok, Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvFolderStatus = findViewById(R.id.tvFolderStatus)

        val btnSelectFolder: Button = findViewById(R.id.btnSelectFolder)
        val btnChooseVideo: Button = findViewById(R.id.btnChooseVideo)
        val btnCatalogKaraoke: Button = findViewById(R.id.btnCatalogKaraoke)
        val btnCatalogJapones: Button = findViewById(R.id.btnCatalogJapones)

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
    }

    override fun onResume() {
        super.onResume()
        updateFolderStatus()
    }

    private fun openCatalog(assetName: String, title: String) {
        val intent = Intent(this, CatalogActivity::class.java)
        intent.putExtra(CatalogActivity.EXTRA_ASSET_NAME, assetName)
        intent.putExtra(CatalogActivity.EXTRA_TITLE, title)
        startActivity(intent)
    }

    private fun updateFolderStatus() {
        val treeUriString = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
            .getString(Prefs.KEY_VIDEOS_TREE_URI, null)
        tvFolderStatus.text = if (treeUriString != null) {
            getString(R.string.folder_selected_status, Uri.parse(treeUriString).path)
        } else {
            getString(R.string.folder_not_selected_status)
        }
    }
}
