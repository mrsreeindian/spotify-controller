package com.spotify.controller.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Lyrics(
    val trackId: String,
    val title: String,
    val artist: String,
    val lines: List<LyricLine> = emptyList(),
    val isSynced: Boolean = false,
    val plainLyrics: String? = null,
    val provider: String = "Spicy Lyrics Engine",
    val hasWordSync: Boolean = false,
    val hasExactWordSync: Boolean = false
) {
    fun getActiveLineIndex(currentPositionMs: Long): Int {
        if (lines.isEmpty()) return -1
        if (currentPositionMs < lines[0].startTimeMs) return -1

        for (i in lines.indices.reversed()) {
            val line = lines[i]
            if (currentPositionMs >= line.startTimeMs) {
                if (line.endTimeMs > line.startTimeMs && currentPositionMs > (line.endTimeMs + 600L) && i < lines.size - 1) {
                    val nextLine = lines[i + 1]
                    if (nextLine.startTimeMs - currentPositionMs > 3000L) {
                        return -1 // Instrumental break
                    }
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
