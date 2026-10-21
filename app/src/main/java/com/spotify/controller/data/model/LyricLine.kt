package com.spotify.controller.data.model

import kotlinx.serialization.Serializable

@Serializable
data class WordTiming(
    val word: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val isBackground: Boolean = false
) {
    fun isActiveAt(currentMs: Long): Boolean = currentMs in startTimeMs..endTimeMs
    fun isPast(currentMs: Long): Boolean = currentMs > endTimeMs
}

@Serializable
data class LyricLine(
    val id: Int,
    val startTimeMs: Long,
    val endTimeMs: Long = 0L,
    val text: String,
    val words: List<WordTiming> = emptyList(),
    val isDotLine: Boolean = false,
    val isBackgroundLine: Boolean = false
) {
    fun isActiveAt(currentMs: Long): Boolean {
        return if (endTimeMs > startTimeMs) {
            currentMs in startTimeMs..endTimeMs
        } else {
            currentMs >= startTimeMs
        }
    }
}
