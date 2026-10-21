package com.spotify.controller.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: String,
    val name: String,
    val artistNames: String,
    val albumName: String,
    val albumArtUrl: String?,
    val durationMs: Long,
    val uri: String,
    val isLiked: Boolean = false
) {
    companion object {
        val EMPTY = Track(
            id = "",
            name = "",
            artistNames = "",
            albumName = "",
            albumArtUrl = null,
            durationMs = 0L,
            uri = ""
        )
    }
}
