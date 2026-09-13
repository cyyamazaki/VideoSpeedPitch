package com.example.videospeedpitch

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Lógica compartilhada para começar a tocar a fila da playlist a partir do
 * início dela, pulando (com toast) códigos sem vídeo correspondente na
 * pasta selecionada. Usada pela tela inicial (primeira música da fila),
 * pela tela de playlist ("Tocar playlist") e pela escolha de uma música no
 * catálogo destinada a "entrar na fila e tocar em seguida".
 */
object PlaylistPlayer {

    /** Retorna `true` se algo foi tocado (a fila e a pasta selecionada não precisam ser verificadas por quem chama). */
    fun playNextFromQueue(context: Context): Boolean {
        val treeUriString = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .getString(Prefs.KEY_VIDEOS_TREE_URI, null)
        if (treeUriString == null) {
            Toast.makeText(context, R.string.error_no_folder_selected, Toast.LENGTH_LONG).show()
            return false
        }

        val treeUri = Uri.parse(treeUriString)
        val index = CatalogRepository.getFileIndex(context, treeUri)

        var song = PlaylistManager.peekAll().firstOrNull()
        while (song != null && !index.containsKey(song.codigo)) {
            PlaylistManager.dequeue()
            Toast.makeText(
                context,
                context.getString(R.string.error_video_not_found, song.musica, song.artista, song.codigo),
                Toast.LENGTH_SHORT
            ).show()
            song = PlaylistManager.peekAll().firstOrNull()
        }

        if (song == null) return false
        val videoUri = index[song.codigo] ?: return false
        PlaylistManager.dequeue()

        val playerIntent = Intent(context, PlayerActivity::class.java)
        playerIntent.putExtra(PlayerActivity.EXTRA_VIDEO_URI, videoUri)
        PlayerActivity.putSongExtras(playerIntent, song)
        playerIntent.putExtra(PlayerActivity.EXTRA_PLAYLIST_MODE, true)
        context.startActivity(playerIntent)
        return true
    }
}
