package com.spotify.controller.data.model

data class SpotifyCredentials(
    val clientId: String = "",
    val clientSecret: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val tokenExpiresAt: Long = 0L,
    val directDevToken: String = ""
) {
    companion object {
        fun cleanToken(rawToken: String?): String {
            if (rawToken == null) return ""
            var token = rawToken.replace("\r", "").replace("\n", "").trim()
            if (token.startsWith("Bearer ", ignoreCase = true)) {
                token = token.substring(7).trim()
            } else if (token.equals("Bearer", ignoreCase = true)) {
                token = ""
            }
            if ((token.startsWith("\"") && token.endsWith("\"")) || (token.startsWith("'") && token.endsWith("'"))) {
                token = token.substring(1, token.length - 1).trim()
            }
            return token
        }
    }

    val isConfigured: Boolean
        get() = clientId.isNotBlank() || directDevToken.isNotBlank()

    val hasValidSession: Boolean
        get() = (accessToken.isNotBlank() && System.currentTimeMillis() < tokenExpiresAt) ||
                directDevToken.isNotBlank() ||
                refreshToken.isNotBlank()

    fun getEffectiveToken(): String {
        val cleanDirect = cleanToken(directDevToken)
        return if (cleanDirect.isNotBlank()) cleanDirect else cleanToken(accessToken)
    }
}

