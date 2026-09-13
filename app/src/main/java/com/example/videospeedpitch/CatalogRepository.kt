package com.example.videospeedpitch

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import java.text.Normalizer

/**
 * Responsável por:
 *  - Carregar os catálogos (arquivos .json empacotados em assets/).
 *  - Indexar a pasta de vídeos escolhida pelo usuário (via Storage Access
 *    Framework) para localizar rapidamente um vídeo a partir do código
 *    numérico da música (nome do arquivo, sem extensão).
 */
object CatalogRepository {

    /** Nomes dos catálogos empacotados em assets/, usados na busca global por código. */
    val CATALOG_ASSETS = listOf("catalogo_karaoke.json", "catalogo_japones.json")

    /** Lê um catálogo de assets/<assetFileName> e retorna a lista de músicas. */
    fun loadCatalog(context: Context, assetFileName: String): List<Song> {
        val json = context.assets.open(assetFileName).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val array = JSONArray(json)
        val songs = ArrayList<Song>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            songs.add(
                Song(
                    artista = obj.optString("a"),
                    codigo = obj.optString("c"),
                    musica = obj.optString("m"),
                    trecho = obj.optString("t")
                )
            )
        }
        return songs
    }

    /** Remove acentos e caixa para permitir busca "acento-insensível". */
    fun normalize(text: String): String {
        val noAccents = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return noAccents.lowercase()
    }

    /**
     * Procura uma música pelo código exato em todos os catálogos (karaokê e
     * japonês), nessa ordem, retornando a primeira ocorrência encontrada.
     * Usado pela playlist global, que não depende de qual catálogo a música
     * pertence.
     */
    fun findSongByCodigo(context: Context, codigo: String): Song? {
        for (assetName in CATALOG_ASSETS) {
            val song = loadCatalog(context, assetName).firstOrNull { it.codigo == codigo }
            if (song != null) return song
        }
        return null
    }

    /**
     * Constrói um índice (código -> Uri do arquivo) varrendo os arquivos
     * diretamente dentro da pasta de vídeos escolhida pelo usuário.
     * O nome do arquivo (sem extensão) deve ser exatamente igual ao código.
     */
    fun buildFileIndex(context: Context, treeUri: Uri): Map<String, Uri> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyMap()
        val index = HashMap<String, Uri>()
        for (file in root.listFiles()) {
            if (!file.isFile) continue
            val name = file.name ?: continue
            val dot = name.lastIndexOf('.')
            val codeFromName = if (dot > 0) name.substring(0, dot) else name
            index[codeFromName] = file.uri
        }
        return index
    }
}
