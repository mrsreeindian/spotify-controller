package com.spotify.controller.data.model

data class SpotifyCredentials(
    val clientId: String = "",
    val clientSecret: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val tokenExpiresAt: Long = 0L,
    val directDevToken: String = ""
) {
    val isConfigured: Boolean
        get() = clientId.isNotBlank() || directDevToken.isNotBlank()

    val hasValidSession: Boolean
        get() = (accessToken.isNotBlank() && System.currentTimeMillis() < tokenExpiresAt) ||
                directDevToken.isNotBlank() ||
                refreshToken.isNotBlank()

    fun getEffectiveToken(): String {
        return if (directDevToken.isNotBlank()) directDevToken else accessToken
    }
}
