package com.spotify.controller.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
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
    }

    /**
     * Initiates OAuth 2.0 PKCE flow by opening Spotify authorization page in Chrome Custom Tabs.
     */
    suspend fun startAuth(clientId: String) {
        val verifier = generateCodeVerifier()
        credentialsRepository.saveCodeVerifier(verifier)
        val challenge = generateCodeChallenge(verifier)

        val authUri = Uri.parse(AUTH_ENDPOINT).buildUpon()
            .appendQueryParameter("client_id", clientId)
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

            val formBuilder = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
                .add("client_id", clientId)
                .add("code_verifier", verifier)

            val requestBuilder = Request.Builder()
                .url(TOKEN_ENDPOINT)
                .post(formBuilder.build())

            // Optional client secret for standard web app flow
            if (!clientSecret.isNullOrBlank()) {
                val basicAuth = Base64.encodeToString("$clientId:$clientSecret".toByteArray(), Base64.NO_WRAP)
                requestBuilder.header("Authorization", "Basic $basicAuth")
            }

            val (isSuccess, body, statusCode) = okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                Triple(response.isSuccessful, response.body?.string() ?: "", response.code)
            }

            if (!isSuccess) {
                return@withContext Result.failure(Exception("Token exchange failed ($statusCode): $body"))
            }

            val json = JSONObject(body)
            val accessToken = json.getString("access_token")
            val refreshToken = json.optString("refresh_token", "")
            val expiresIn = json.getLong("expires_in")

            credentialsRepository.saveTokens(accessToken, refreshToken.takeIf { it.isNotEmpty() }, expiresIn)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Refreshes access token if expired.
     */
    suspend fun refreshTokenIfNeeded(credentials: SpotifyCredentials): String? = withContext(Dispatchers.IO) {
        if (credentials.directDevToken.isNotBlank()) {
            return@withContext credentials.directDevToken
        }

        // If current token is still valid (with 60-second buffer), use it
        if (credentials.accessToken.isNotBlank() && System.currentTimeMillis() < (credentials.tokenExpiresAt - 60_000L)) {
            return@withContext credentials.accessToken
        }

        if (credentials.refreshToken.isBlank() || credentials.clientId.isBlank()) {
            return@withContext null
        }

        try {
            val form = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", credentials.refreshToken)
                .add("client_id", credentials.clientId)
                .build()

            val requestBuilder = Request.Builder()
                .url(TOKEN_ENDPOINT)
                .post(form)

            if (credentials.clientSecret.isNotBlank()) {
                val basicAuth = Base64.encodeToString("${credentials.clientId}:${credentials.clientSecret}".toByteArray(), Base64.NO_WRAP)
                requestBuilder.header("Authorization", "Basic $basicAuth")
            }

            val (isSuccess, body) = okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                Pair(response.isSuccessful, response.body?.string() ?: "")
            }

            if (!isSuccess) {
                return@withContext null
            }

            val json = JSONObject(body)
            val newAccessToken = json.getString("access_token")
            val newRefreshToken = json.optString("refresh_token", credentials.refreshToken)
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
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
