package com.spotify.controller.data.remote

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class LrclibResponse(
    val id: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val duration: Double,
    val instrumental: Boolean,
    val plainLyrics: String?,
    val syncedLyrics: String?
)

class LrclibApiService(private val client: OkHttpClient) {

    companion object {
        private const val BASE_URL = "https://lrclib.net/api"
        private const val USER_AGENT = "SpotifyControllerAndroid/1.0.0 (https://github.com/spotify-controller)"
    }

    suspend fun fetchLyrics(
        trackName: String,
        artistName: String,
        albumName: String?,
        durationSec: Int
    ): LrclibResponse? = withContext(Dispatchers.IO) {
        // Step 1: Direct exact match via /api/get
        val directResult = fetchDirect(trackName, artistName, albumName, durationSec)
        if (directResult != null && (directResult.syncedLyrics != null || directResult.plainLyrics != null || directResult.instrumental)) {
            return@withContext directResult
        }

        // Step 2: Fallback search via /api/search
        return@withContext searchLyrics("$trackName $artistName")
    }

    private fun fetchDirect(
        trackName: String,
        artistName: String,
        albumName: String?,
        durationSec: Int
    ): LrclibResponse? {
        try {
            val uriBuilder = Uri.parse("$BASE_URL/get").buildUpon()
                .appendQueryParameter("track_name", cleanTrackTitle(trackName))
                .appendQueryParameter("artist_name", artistName)
                .appendQueryParameter("duration", durationSec.toString())

            if (!albumName.isNullOrBlank()) {
                uriBuilder.appendQueryParameter("album_name", albumName)
            }

            val request = Request.Builder()
                .url(uriBuilder.build().toString())
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null

            val body = response.body?.string() ?: return null
            return parseItem(JSONObject(body))
        } catch (e: Exception) {
            return null
        }
    }

    private fun searchLyrics(query: String): LrclibResponse? {
        try {
            val uri = Uri.parse("$BASE_URL/search").buildUpon()
                .appendQueryParameter("q", query)
                .build()

            val request = Request.Builder()
                .url(uri.toString())
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null

            val body = response.body?.string() ?: return null
            val array = JSONArray(body)
            if (array.length() == 0) return null

            // Prioritize results that have syncedLyrics
            for (i in 0 until array.length()) {
                val item = parseItem(array.getJSONObject(i))
                if (!item.syncedLyrics.isNullOrBlank()) {
                    return item
                }
            }

            return parseItem(array.getJSONObject(0))
        } catch (e: Exception) {
            return null
        }
    }

    private fun parseItem(json: JSONObject): LrclibResponse {
        return LrclibResponse(
            id = json.optLong("id", 0L),
            trackName = json.optString("trackName", ""),
            artistName = json.optString("artistName", ""),
            albumName = json.optString("albumName", ""),
            duration = json.optDouble("duration", 0.0),
            instrumental = json.optBoolean("instrumental", false),
            plainLyrics = json.optString("plainLyrics", null),
            syncedLyrics = json.optString("syncedLyrics", null)
        )
    }

    /**
     * Strips "(Remastered)", "- Single", "[feat. ...]" and bonus annotations
     * to significantly improve lyrics database hit rates.
     */
    private fun cleanTrackTitle(title: String): String {
        return title
            .replace(Regex("""(?i)\s*-\s*remaster(?:ed)?(?:\s*\d{4})?"""), "")
            .replace(Regex("""(?i)\s*\((?:feat\.|with|remastered|bonus track).*?\)"""), "")
            .replace(Regex("""(?i)\s*\[(?:feat\.|with|remastered|bonus track).*?\]"""), "")
            .trim()
    }
}
