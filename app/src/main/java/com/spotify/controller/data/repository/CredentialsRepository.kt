package com.spotify.controller.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.spotify.controller.data.model.SpotifyCredentials
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "spotify_prefs")

class CredentialsRepository(private val context: Context) {

    private object Keys {
        val CLIENT_ID = stringPreferencesKey("client_id")
        val CLIENT_SECRET = stringPreferencesKey("client_secret")
        val DIRECT_DEV_TOKEN = stringPreferencesKey("direct_dev_token")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val TOKEN_EXPIRES_AT = longPreferencesKey("token_expires_at")
        val CODE_VERIFIER = stringPreferencesKey("code_verifier")
        val ORIENTATION_MODE = stringPreferencesKey("orientation_mode")
    }

    val credentialsFlow: Flow<SpotifyCredentials> = context.dataStore.data.map { prefs ->
        SpotifyCredentials(
            clientId = prefs[Keys.CLIENT_ID] ?: "",
            clientSecret = prefs[Keys.CLIENT_SECRET] ?: "",
            directDevToken = prefs[Keys.DIRECT_DEV_TOKEN] ?: "",
            accessToken = prefs[Keys.ACCESS_TOKEN] ?: "",
            refreshToken = prefs[Keys.REFRESH_TOKEN] ?: "",
            tokenExpiresAt = prefs[Keys.TOKEN_EXPIRES_AT] ?: 0L
        )
    }

    val orientationModeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.ORIENTATION_MODE] ?: "portrait"
    }

    suspend fun saveClientCredentials(clientId: String, clientSecret: String, directDevToken: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CLIENT_ID] = clientId.trim()
            prefs[Keys.CLIENT_SECRET] = clientSecret.trim()
            prefs[Keys.DIRECT_DEV_TOKEN] = SpotifyCredentials.cleanToken(directDevToken)
        }
    }

    suspend fun saveTokens(accessToken: String, refreshToken: String?, expiresInSec: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = SpotifyCredentials.cleanToken(accessToken)
            if (!refreshToken.isNullOrBlank()) {
                prefs[Keys.REFRESH_TOKEN] = refreshToken.trim()
            }
            prefs[Keys.TOKEN_EXPIRES_AT] = System.currentTimeMillis() + (expiresInSec * 1000L)
        }
    }

    suspend fun saveCodeVerifier(verifier: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CODE_VERIFIER] = verifier
        }
    }

    suspend fun getCodeVerifier(): String? {
        var verifier: String? = null
        context.dataStore.edit { prefs ->
            verifier = prefs[Keys.CODE_VERIFIER]
        }
        return verifier
    }

    suspend fun saveOrientationMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ORIENTATION_MODE] = mode
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.ACCESS_TOKEN)
            prefs.remove(Keys.REFRESH_TOKEN)
            prefs.remove(Keys.TOKEN_EXPIRES_AT)
            prefs.remove(Keys.DIRECT_DEV_TOKEN)
        }
    }
}
