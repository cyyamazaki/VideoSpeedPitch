package com.example.videospeedpitch

import java.util.ArrayDeque

/**
 * Fila FIFO de músicas adicionadas pelo número do código, compartilhada por
 * todo o app (independe de qual catálogo a música pertence). Mantida em
 * memória enquanto o processo do app estiver vivo.
 */
object PlaylistManager {

    private val queue = ArrayDeque<Song>()

    fun enqueue(song: Song) {
        queue.addLast(song)
    }

    fun dequeue(): Song? = queue.pollFirst()

    fun remove(song: Song) {
        queue.remove(song)
    }

    fun peekAll(): List<Song> = queue.toList()

    fun isEmpty(): Boolean = queue.isEmpty()

    fun size(): Int = queue.size

    fun clear() = queue.clear()
}