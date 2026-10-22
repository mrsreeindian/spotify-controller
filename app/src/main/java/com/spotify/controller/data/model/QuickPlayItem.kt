package com.spotify.controller.data.model

import androidx.compose.runtime.Immutable

enum class QuickPlayType {
    PLAYLIST,
    ALBUM,
    ARTIST
}

@Immutable
data class QuickPlayItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val uri: String,
    val type: QuickPlayType,
    val trackUris: List<String>? = null
)
