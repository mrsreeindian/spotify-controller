package com.spotify.controller.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class WordTiming(
    val word: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val isBackground: Boolean = false
) {
    fun isActiveAt(currentMs: Long): Boolean = currentMs in startTimeMs..endTimeMs

    fun isPast(currentMs: Long): Boolean = currentMs > endTimeMs

    /**
     * Normalized completion progress in [0.0f, 1.0f] for progressive karaoke wipes.
     */
    fun progress(currentMs: Long): Float {
        if (currentMs <= startTimeMs) return 0f
        if (currentMs >= endTimeMs) return 1f
        val dur = (endTimeMs - startTimeMs).coerceAtLeast(1L)
        return ((currentMs - startTimeMs).toFloat() / dur.toFloat()).coerceIn(0f, 1f)
    }
}

@Immutable
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
