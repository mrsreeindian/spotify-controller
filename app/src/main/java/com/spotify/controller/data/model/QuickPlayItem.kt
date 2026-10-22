package com.spotify.controller.data.model

enum class QuickPlayType {
    ON_REPEAT,
    PLAYLIST,
    ALBUM,
    ARTIST
}

data class QuickPlayItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val uri: String,
    val type: QuickPlayType
)
