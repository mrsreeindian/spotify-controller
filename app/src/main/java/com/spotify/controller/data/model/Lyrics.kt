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
            if (currentPositionMs >= lines[i].startTimeMs) {
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
