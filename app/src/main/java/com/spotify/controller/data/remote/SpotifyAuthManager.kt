package com.spotify.controller.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import com.spotify.controller.data.model.SpotifyCredentials
import com.spotify.controller.data.repository.CredentialsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

class SpotifyAuthManager(
    private val context: Context,
    private val credentialsRepository: CredentialsRepository,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        const val REDIRECT_URI = "spotifycontroller://callback"
        const val AUTH_ENDPOINT = "https://accounts.spotify.com/authorize"
        const val TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token"

        val SCOPES = listOf(
            "user-read-playback-state",
            "user-modify-playback-state",
            "user-read-currently-playing",
            "user-library-read",
            "user-library-modify",
            "user-read-recently-played",
            "user-top-read",
            "playlist-read-private",
            "playlist-read-collaborative"
        ).joinToString(" ")

        fun parseTokenEndpointError(statusCode: Int, body: String): String {
            try {
                if (body.isNotBlank()) {
                    val json = JSONObject(body)
                    val desc = json.optString("error_description")
                    if (desc.isNotBlank()) {
                        return when {
                            desc.contains("client secret", ignoreCase = true) -> "Invalid Client Secret."
                            desc.contains("client", ignoreCase = true) -> "Invalid Client ID."
                            else -> desc
                        }
                    }
                    val err = json.optString("error")
                    if (err.isNotBlank()) return "Spotify Auth error: $err"
                }
            } catch (_: Exception) {}
            return "Credentials verification failed ($statusCode)."
        }
    }

    /**
     * Initiates OAuth 2.0 PKCE flow by opening Spotify authorization page in Chrome Custom Tabs.
     */
    suspend fun startAuth(clientId: String) {
        val verifier = generateCodeVerifier()
        credentialsRepository.saveCodeVerifier(verifier)
        val challenge = generateCodeChallenge(verifier)

        val authUri = Uri.parse(AUTH_ENDPOINT).buildUpon()
            .appendQueryParameter("client_id", clientId.trim())
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("scope", SCOPES)
            .build()

        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()

        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        customTabsIntent.launchUrl(context, authUri)
    }

    /**
     * Handles the redirect intent Uri containing code=... or error=...
     */
    suspend fun handleRedirectUri(uri: Uri, clientId: String, clientSecret: String?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val error = uri.getQueryParameter("error")
            if (error != null) {
                return@withContext Result.failure(Exception("Spotify Auth Error: $error"))
            }

            val code = uri.getQueryParameter("code") ?: return@withContext Result.failure(Exception("No auth code in redirect"))
            val verifier = credentialsRepository.getCodeVerifier() ?: return@withContext Result.failure(Exception("Missing code verifier"))

            val cleanClientId = clientId.trim()
            val formBuilder = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
                .add("client_id", cleanClientId)
                .add("code_verifier", verifier)

            val requestBuilder = Request.Builder()
                .url(TOKEN_ENDPOINT)
                .post(formBuilder.build())

            // Optional client secret for standard web app flow
            val cleanSecret = clientSecret?.trim()
            if (!cleanSecret.isNullOrBlank()) {
                val basicAuth = Base64.getEncoder().encodeToString("$cleanClientId:$cleanSecret".toByteArray())
                requestBuilder.header("Authorization", "Basic $basicAuth")
            }

            val (isSuccess, body, statusCode) = okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                Triple(response.isSuccessful, response.body?.string() ?: "", response.code)
            }

            if (!isSuccess) {
                val parsed = parseTokenEndpointError(statusCode, body)
                return@withContext Result.failure(Exception("Token exchange failed ($statusCode): $parsed"))
            }

            val json = JSONObject(body)
            val accessToken = SpotifyCredentials.cleanToken(json.getString("access_token"))
            val refreshToken = json.optString("refresh_token", "").trim()
            val expiresIn = json.getLong("expires_in")

            credentialsRepository.saveTokens(accessToken, refreshToken.takeIf { it.isNotEmpty() }, expiresIn)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Verifies Developer App credentials directly with Spotify's token endpoint.
     */
    suspend fun testClientCredentials(clientId: String, clientSecret: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanId = clientId.trim()
        val cleanSecret = clientSecret.trim()
        if (cleanId.isBlank()) {
            return@withContext Result.failure(Exception("Spotify Client ID is required."))
        }
        if (cleanSecret.isBlank()) {
            return@withContext Result.failure(Exception("Client Secret is required to verify Developer App credentials."))
        }

        try {
            val form = FormBody.Builder()
                .add("grant_type", "client_credentials")
                .build()

            val basicAuth = Base64.getEncoder().encodeToString("$cleanId:$cleanSecret".toByteArray())
            val request = Request.Builder()
                .url(TOKEN_ENDPOINT)
                .header("Authorization", "Basic $basicAuth")
                .post(form)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    Result.success("Developer App credentials verified! Tap 'Authorize with Spotify Login' to connect your account.")
                } else {
                    val parsed = parseTokenEndpointError(response.code, body)
                    Result.failure(Exception(parsed))
                }
            }
        } catch (e: Exception) {
            val friendlyMsg = when (e) {
                is java.net.UnknownHostException -> "Network error: Unable to reach Spotify. Check your internet connection."
                is java.net.SocketTimeoutException -> "Network error: Connection to Spotify timed out."
                else -> e.message ?: "Failed to verify credentials."
            }
            Result.failure(Exception(friendlyMsg, e))
        }
    }

    /**
     * Refreshes access token if expired.
     */
    suspend fun refreshTokenIfNeeded(credentials: SpotifyCredentials): String? = withContext(Dispatchers.IO) {
        val directClean = SpotifyCredentials.cleanToken(credentials.directDevToken)
        if (directClean.isNotBlank()) {
            return@withContext directClean
        }

        // If current token is still valid (with 60-second buffer), use it
        val accessClean = SpotifyCredentials.cleanToken(credentials.accessToken)
        if (accessClean.isNotBlank() && System.currentTimeMillis() < (credentials.tokenExpiresAt - 60_000L)) {
            return@withContext accessClean
        }

        val refreshClean = credentials.refreshToken.trim()
        val clientClean = credentials.clientId.trim()
        if (refreshClean.isBlank() || clientClean.isBlank()) {
            return@withContext null
        }

        try {
            val form = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshClean)
                .add("client_id", clientClean)
                .build()

            val requestBuilder = Request.Builder()
                .url(TOKEN_ENDPOINT)
                .post(form)

            val secretClean = credentials.clientSecret.trim()
            if (secretClean.isNotBlank()) {
                val basicAuth = Base64.getEncoder().encodeToString("$clientClean:$secretClean".toByteArray())
                requestBuilder.header("Authorization", "Basic $basicAuth")
            }

            val (isSuccess, body) = okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                Pair(response.isSuccessful, response.body?.string() ?: "")
            }

            if (!isSuccess) {
                return@withContext null
            }

            val json = JSONObject(body)
            val newAccessToken = SpotifyCredentials.cleanToken(json.getString("access_token"))
            val newRefreshToken = json.optString("refresh_token", refreshClean).trim()
            val expiresIn = json.getLong("expires_in")

            credentialsRepository.saveTokens(newAccessToken, newRefreshToken, expiresIn)
            newAccessToken
        } catch (e: Exception) {
            null
        }
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }
}

