package com.example.videospeedpitch

import android.content.Context

/**
 * Representa uma música de um catálogo (karaokê ou japonês).
 *
 * @param artista Nome do intérprete/cantor.
 * @param codigo Código numérico da música. É este número que forma o nome
 *               do arquivo de vídeo a ser localizado na pasta selecionada
 *               (ex.: código "18483" -> arquivo "18483.mp4").
 * @param musica Título da música.
 * @param trecho Pequeno trecho da letra (usado apenas como informação extra).
 */
data class Song(
    val artista: String,
    val codigo: String,
    val musica: String,
    val trecho: String
)

/**
 * Formata música, cantor, código e (quando disponível) o início da letra
 * para exibição — usado em listas, overlays do player e toasts, sempre que
 * os dados da música forem mostrados ao usuário.
 */
fun Song.toDisplayLine(context: Context): String {
    return if (trecho.isNotBlank()) {
        context.getString(R.string.song_display_with_trecho, musica, artista, codigo, trecho)
    } else {
        context.getString(R.string.song_display_no_trecho, musica, artista, codigo)
    }
}
