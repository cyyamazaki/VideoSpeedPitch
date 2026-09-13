package com.example.videospeedpitch

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL

/**
 * Busca no YouTube o vídeo de uma música, sem precisar de uma chave de API:
 * faz uma requisição à página pública de resultados de busca e extrai o
 * primeiro `videoId` que aparecer na resposta. Se achar, abre o vídeo
 * (no app do YouTube, se instalado, ou no navegador); se não achar — ou a
 * busca falhar por qualquer motivo (sem internet, etc.) —, avisa por toast.
 *
 * A requisição roda em uma thread separada; o resultado é aplicado na UI via
 * [Handler], então esta função pode ser chamada diretamente do clique de um
 * botão, em qualquer Activity.
 */
object YouTubeSearchHelper {

    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 8000
    private const val MAX_BUFFER_CHARS = 32_768

    private val videoIdRegex = Regex("\"videoId\":\"([a-zA-Z0-9_-]{11})\"")
    private val handler = Handler(Looper.getMainLooper())

    fun searchAndOpen(context: Context, song: Song) {
        val appContext = context.applicationContext
        val query = "${song.artista} ${song.musica}"

        Thread {
            val videoId = try {
                fetchFirstVideoId(query)
            } catch (e: Exception) {
                null
            }
            handler.post { onResult(appContext, song, videoId) }
        }.start()
    }

    private fun onResult(context: Context, song: Song, videoId: String?) {
        if (videoId == null) {
            Toast.makeText(
                context,
                context.getString(R.string.youtube_not_found, song.musica, song.artista),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val videoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId"))
        videoIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(videoIntent)
    }

    /** Lê a resposta em blocos, parando assim que achar o primeiro videoId, sem carregar tudo. */
    private fun fetchFirstVideoId(query: String): String? {
        val searchUrl = "https://www.youtube.com/results?search_query=${Uri.encode(query)}"
        val connection = URL(searchUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("User-Agent", "Mozilla/5.0")

        return try {
            connection.inputStream.bufferedReader().use { reader ->
                val buffer = StringBuilder()
                val chunk = CharArray(8192)
                while (true) {
                    val read = reader.read(chunk)
                    if (read == -1) break
                    buffer.append(chunk, 0, read)

                    val match = videoIdRegex.find(buffer)
                    if (match != null) return match.groupValues[1]

                    if (buffer.length > MAX_BUFFER_CHARS) {
                        buffer.delete(0, buffer.length - 4096)
                    }
                }
                null
            }
        } finally {
            connection.disconnect()
        }
    }
}
