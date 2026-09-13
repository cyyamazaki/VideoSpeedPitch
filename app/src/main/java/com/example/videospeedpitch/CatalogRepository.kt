package com.example.videospeedpitch

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer

/**
 * Responsável por:
 *  - Carregar os catálogos (arquivos .json empacotados em assets/).
 *  - Indexar a pasta de vídeos escolhida pelo usuário (via Storage Access
 *    Framework) para localizar rapidamente um vídeo a partir do código
 *    numérico da música (nome do arquivo, sem extensão), mantendo esse
 *    índice em cache em disco para não precisar varrer a pasta toda vez
 *    que uma tela precisar dele.
 */
object CatalogRepository {

    private const val FILE_INDEX_CACHE_NAME = "file_index_cache.json"

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
     * Essa varredura via Storage Access Framework é relativamente cara (uma
     * chamada ao content provider por arquivo); prefira [getFileIndex], que
     * cacheia o resultado em disco.
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

    /**
     * Igual a [buildFileIndex], mas lê o resultado de um cache em disco
     * quando ele já existe e ainda é da mesma pasta (evitando varrer a
     * pasta de novo toda vez que uma tela do catálogo abre). Se o cache não
     * existir, estiver corrompido, ou for de outra pasta, varre a pasta uma
     * vez e grava o resultado no cache.
     */
    fun getFileIndex(context: Context, treeUri: Uri): Map<String, Uri> {
        readFileIndexCache(context, treeUri)?.let { return it }

        val fresh = buildFileIndex(context, treeUri)
        writeFileIndexCache(context, treeUri, fresh)
        return fresh
    }

    /** Descarta o cache em disco, forçando a próxima [getFileIndex] a varrer a pasta de novo. */
    fun invalidateFileIndexCache(context: Context) {
        fileIndexCacheFile(context).delete()
    }

    private fun fileIndexCacheFile(context: Context) = File(context.filesDir, FILE_INDEX_CACHE_NAME)

    private fun readFileIndexCache(context: Context, treeUri: Uri): Map<String, Uri>? {
        val file = fileIndexCacheFile(context)
        if (!file.exists()) return null

        return try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            if (root.optString("treeUri") != treeUri.toString()) return null

            val indexObj = root.optJSONObject("index") ?: return null
            val index = HashMap<String, Uri>(indexObj.length())
            val keys = indexObj.keys()
            while (keys.hasNext()) {
                val code = keys.next()
                index[code] = Uri.parse(indexObj.getString(code))
            }
            index
        } catch (e: Exception) {
            // Cache corrompido ou ilegível: trata como se não existisse.
            null
        }
    }

    private fun writeFileIndexCache(context: Context, treeUri: Uri, index: Map<String, Uri>) {
        try {
            val indexObj = JSONObject()
            for ((code, uri) in index) {
                indexObj.put(code, uri.toString())
            }
            val root = JSONObject()
            root.put("treeUri", treeUri.toString())
            root.put("index", indexObj)
            fileIndexCacheFile(context).writeText(root.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            // Cache é apenas uma otimização; falha ao gravar não deve quebrar o app.
        }
    }
}
