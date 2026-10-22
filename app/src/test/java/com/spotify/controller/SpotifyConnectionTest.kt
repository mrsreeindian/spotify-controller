package com.spotify.controller

import com.spotify.controller.data.model.SpotifyCredentials
import com.spotify.controller.data.remote.SpotifyApiService
import com.spotify.controller.data.remote.SpotifyAuthManager
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyConnectionTest {

    private val apiService = SpotifyApiService(OkHttpClient())

    @Test
    fun `cleanToken trims whitespace and removes Bearer prefix`() {
        assertEquals("BQC12345", SpotifyCredentials.cleanToken("Bearer BQC12345"))
        assertEquals("BQC12345", SpotifyCredentials.cleanToken("bearer BQC12345"))
        assertEquals("BQC12345", SpotifyCredentials.cleanToken("BEARER BQC12345"))
        assertEquals("BQC12345", SpotifyCredentials.cleanToken("  Bearer   BQC12345  "))
    }

    @Test
    fun `cleanToken removes newlines and carriage returns`() {
        assertEquals("BQC12345", SpotifyCredentials.cleanToken("Bearer BQC12345\n"))
        assertEquals("BQC12345", SpotifyCredentials.cleanToken("\r\nBearer BQC12345\r\n"))
        assertEquals("BQC12345", SpotifyCredentials.cleanToken("BQC12345\n"))
    }

    @Test
    fun `cleanToken removes surrounding quotes`() {
        assertEquals("BQC12345", SpotifyCredentials.cleanToken("\"BQC12345\""))
        assertEquals("BQC12345", SpotifyCredentials.cleanToken("'BQC12345'"))
        assertEquals("BQC12345", SpotifyCredentials.cleanToken("Bearer \"BQC12345\""))
        assertEquals("BQC12345", SpotifyCredentials.cleanToken("Bearer 'BQC12345'"))
    }

    @Test
    fun `cleanToken handles empty and whitespace-only inputs`() {
        assertEquals("", SpotifyCredentials.cleanToken(""))
        assertEquals("", SpotifyCredentials.cleanToken("   "))
        assertEquals("", SpotifyCredentials.cleanToken(null))
        assertEquals("", SpotifyCredentials.cleanToken("Bearer "))
        assertEquals("", SpotifyCredentials.cleanToken("Bearer"))
        assertEquals("", SpotifyCredentials.cleanToken("bearer"))
    }

    @Test
    fun `getEffectiveToken cleans directDevToken and accessToken`() {
        val credsDirect = SpotifyCredentials(
            directDevToken = "Bearer BQC_DEV_TOKEN\n"
        )
        assertEquals("BQC_DEV_TOKEN", credsDirect.getEffectiveToken())

        val credsOAuth = SpotifyCredentials(
            accessToken = "Bearer BQC_OAUTH_TOKEN  "
        )
        assertEquals("BQC_OAUTH_TOKEN", credsOAuth.getEffectiveToken())
    }

    @Test
    fun `testConnection fails immediately when token is empty or blank`() = runBlocking {
        val res = apiService.testConnection("")
        assertTrue(res.isFailure)
        assertEquals("Access token is empty.", res.exceptionOrNull()?.message)

        val resWhitespace = apiService.testConnection("   ")
        assertTrue(resWhitespace.isFailure)
        assertEquals("Access token is empty.", resWhitespace.exceptionOrNull()?.message)

        val resJustBearer = apiService.testConnection("Bearer ")
        assertTrue(resJustBearer.isFailure)
        assertEquals("Access token is empty.", resJustBearer.exceptionOrNull()?.message)
    }

    @Test
    fun `parseSpotifyErrorMessage parses expired token JSON correctly`() {
        val json = """{"error":{"status":401,"message":"The access token expired"}}"""
        val msg = apiService.parseSpotifyErrorMessage(401, json)
        assertEquals("Access token expired. Please generate a new token.", msg)
    }

    @Test
    fun `parseSpotifyErrorMessage parses invalid access token JSON correctly`() {
        val json = """{"error":{"status":401,"message":"Invalid access token"}}"""
        val msg = apiService.parseSpotifyErrorMessage(401, json)
        assertEquals("Invalid access token. Please check your token.", msg)
    }

    @Test
    fun `parseSpotifyErrorMessage parses insufficient scope correctly`() {
        val json = """{"error":{"status":403,"message":"Insufficient client scope"}}"""
        val msg = apiService.parseSpotifyErrorMessage(403, json)
        assertEquals("Permission denied: Insufficient client scope. Ensure required scopes are granted.", msg)
    }

    @Test
    fun `parseSpotifyErrorMessage falls back to descriptive status messages`() {
        assertEquals("Spotify is temporarily busy. Please try again shortly.", apiService.parseSpotifyErrorMessage(429, ""))
        assertEquals("Spotify server error (502). Please try again later.", apiService.parseSpotifyErrorMessage(502, ""))
        assertEquals("Unauthorized (401). Access token is expired or invalid.", apiService.parseSpotifyErrorMessage(401, "invalid json"))
        assertEquals("Bad Request (400). Please verify token format.", apiService.parseSpotifyErrorMessage(400, ""))
        assertEquals("Forbidden (403). Your account or token lacks required permissions (e.g. user-library-modify).", apiService.parseSpotifyErrorMessage(403, ""))
    }

    @Test
    fun `parseTokenEndpointError parses client secret error correctly`() {
        val json = """{"error":"invalid_client","error_description":"Invalid client secret"}"""
        val msg = SpotifyAuthManager.parseTokenEndpointError(400, json)
        assertEquals("Invalid Client Secret.", msg)
    }

    @Test
    fun `parseTokenEndpointError parses client id error correctly`() {
        val json = """{"error":"invalid_client","error_description":"Invalid client"}"""
        val msg = SpotifyAuthManager.parseTokenEndpointError(400, json)
        assertEquals("Invalid Client ID.", msg)
    }

    @Test
    fun `parseTokenEndpointError falls back on status code`() {
        val msg = SpotifyAuthManager.parseTokenEndpointError(401, "")
        assertEquals("Credentials verification failed (401).", msg)
    }

    @Test
    fun `checkIsLiked returns false immediately for blank trackId`() = runBlocking {
        assertFalse(apiService.checkIsLiked("valid_token", ""))
        assertFalse(apiService.checkIsLiked("valid_token", "   "))
        assertFalse(apiService.checkIsLiked("valid_token", "spotify:track:"))
    }

    @Test
    fun `setLiked returns failure immediately for blank trackId`() = runBlocking {
        assertTrue(apiService.setLiked("valid_token", "", true).isFailure)
        assertTrue(apiService.setLiked("valid_token", "   ", false).isFailure)
        assertTrue(apiService.setLiked("valid_token", "spotify:track:", true).isFailure)
    }
}
