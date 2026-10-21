package com.spotify.controller.data.model

import kotlinx.serialization.Serializable

@Serializable
data class WordTiming(
    val word: String,
    val startTimeMs: Long,
    val endTimeMs: Long
)

@Serializable
data class LyricLine(
    val id: Int,
    val startTimeMs: Long,
    val endTimeMs: Long = 0L,
    val text: String,
    val words: List<WordTiming> = emptyList()
) {
    fun isActiveAt(currentMs: Long): Boolean {
        return if (endTimeMs > startTimeMs) {
            currentMs in startTimeMs..endTimeMs
        } else {
            currentMs >= startTimeMs
        }
    }
}

@Serializable
data class Lyrics(
    val trackId: String,
    val title: String,
    val artist: String,
    val lines: List<LyricLine> = emptyList(),
    val isSynced: Boolean = false,
    val plainLyrics: String? = null,
    val provider: String = "LRCLIB (Spicy Lyrics Engine)"
) {
    fun getActiveLineIndex(currentPositionMs: Long): Int {
        if (lines.isEmpty()) return -1
        if (currentPositionMs < lines[0].startTimeMs) return -1

        for (i in lines.indices.reversed()) {
            val line = lines[i]
            if (currentPositionMs >= line.startTimeMs) {
                // If the line has an endTime and playback has passed it by more than 500ms,
                // consider it inactive unless it's the very last line
                if (line.endTimeMs > line.startTimeMs && currentPositionMs > (line.endTimeMs + 500L) && i < lines.size - 1) {
                    return -1 // Instrumental break
                }
                return i
            }
        }
        return -1
    }

    companion object {
        val EMPTY = Lyrics(
            trackId = "",
            title = "",
            artist = "",
            lines = emptyList(),
            isSynced = false
        )
    }
}
