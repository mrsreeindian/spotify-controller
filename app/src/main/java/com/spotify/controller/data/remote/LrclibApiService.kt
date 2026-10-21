package com.spotify.controller.data.remote

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

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
        private const val CLIENT_HEADER = "SpotifyControllerAndroid/1.0.0 (https://github.com/spotify-controller)"
        private const val USER_AGENT = "SpotifyController/1.0.0"
    }

    suspend fun fetchLyrics(
        trackName: String,
        artistName: String,
        albumName: String?,
        durationSec: Int
    ): LrclibResponse? = withContext(Dispatchers.IO) {
        val cleanTitle = cleanTrackTitle(trackName)
        val primaryArtist = cleanArtistName(artistName)

        // Tier 1: /api/get with clean title and primary artist (most accurate and fast)
        fetchDirect(cleanTitle, primaryArtist)?.let { return@withContext it }

        // Tier 2: /api/get with raw title (in case clean removed something significant)
        if (cleanTitle != trackName) {
            fetchDirect(trackName, primaryArtist)?.let { return@withContext it }
        }

        // Tier 3: /api/search with specific track_name and artist_name parameters
        searchByParams(cleanTitle, primaryArtist, durationSec)?.let { return@withContext it }

        // Tier 4: /api/search with free text query "Title Artist"
        searchByQuery("$cleanTitle $primaryArtist", durationSec)?.let { return@withContext it }

        // Tier 5: /api/search with just clean track title
        searchByQuery(cleanTitle, durationSec)?.let { return@withContext it }

        null
    }

    private fun fetchDirect(trackName: String, artistName: String): LrclibResponse? {
        try {
            val uri = Uri.parse("$BASE_URL/get").buildUpon()
                .appendQueryParameter("track_name", trackName)
                .appendQueryParameter("artist_name", artistName)
                .build()

            val request = Request.Builder()
                .url(uri.toString())
                .header("User-Agent", USER_AGENT)
                .header("Lrclib-Client", CLIENT_HEADER)
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null

            val body = response.body?.string() ?: return null
            val item = parseItem(JSONObject(body))
            return if (!item.syncedLyrics.isNullOrBlank() || !item.plainLyrics.isNullOrBlank() || item.instrumental) {
                item
            } else null
        } catch (e: Exception) {
            return null
        }
    }

    private fun searchByParams(trackName: String, artistName: String, targetDurationSec: Int): LrclibResponse? {
        try {
            val uri = Uri.parse("$BASE_URL/search").buildUpon()
                .appendQueryParameter("track_name", trackName)
                .appendQueryParameter("artist_name", artistName)
                .build()

            return executeSearch(uri.toString(), targetDurationSec)
        } catch (e: Exception) {
            return null
        }
    }

    private fun searchByQuery(query: String, targetDurationSec: Int): LrclibResponse? {
        try {
            val uri = Uri.parse("$BASE_URL/search").buildUpon()
                .appendQueryParameter("q", query)
                .build()

            return executeSearch(uri.toString(), targetDurationSec)
        } catch (e: Exception) {
            return null
        }
    }

    private fun executeSearch(url: String, targetDurationSec: Int): LrclibResponse? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Lrclib-Client", CLIENT_HEADER)
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return null

        val body = response.body?.string() ?: return null
        val array = JSONArray(body)
        if (array.length() == 0) return null

        val candidates = mutableListOf<LrclibResponse>()
        for (i in 0 until array.length()) {
            candidates.add(parseItem(array.getJSONObject(i)))
        }

        // 1. Prioritize candidates with syncedLyrics
        val syncedCandidates = candidates.filter { !it.syncedLyrics.isNullOrBlank() }
        if (syncedCandidates.isNotEmpty()) {
            if (targetDurationSec > 0) {
                return syncedCandidates.minByOrNull { abs(it.duration - targetDurationSec) }
            }
            return syncedCandidates.first()
        }

        // 2. Fallback to candidates with plainLyrics or instrumental
        return candidates.firstOrNull { !it.plainLyrics.isNullOrBlank() || it.instrumental }
    }

    private fun parseItem(json: JSONObject): LrclibResponse {
        return LrclibResponse(
            id = json.optLong("id", 0L),
            trackName = json.optString("trackName", ""),
            artistName = json.optString("artistName", ""),
            albumName = json.optString("albumName", ""),
            duration = json.optDouble("duration", 0.0),
            instrumental = json.optBoolean("instrumental", false),
            plainLyrics = if (json.isNull("plainLyrics")) null else json.optString("plainLyrics").takeIf { it.isNotBlank() },
            syncedLyrics = if (json.isNull("syncedLyrics")) null else json.optString("syncedLyrics").takeIf { it.isNotBlank() }
        )
    }

    private fun cleanTrackTitle(title: String): String {
        return title
            .replace(Regex("""(?i)\s*-\s*remaster(?:ed)?(?:\s*\d{4})?"""), "")
            .replace(Regex("""(?i)\s*-\s*\d{4}\s*remaster(?:ed)?"""), "")
            .replace(Regex("""(?i)\s*-\s*single\s*version"""), "")
            .replace(Regex("""(?i)\s*-\s*radio\s*edit"""), "")
            .replace(Regex("""(?i)\s*-\s*acoustic(?:\s*version)?"""), "")
            .replace(Regex("""(?i)\s*-\s*live(?:\s*from.*)?"""), "")
            .replace(Regex("""(?i)\s*\((?:feat\.|with|remastered|bonus track|deluxe).*?\)"""), "")
            .replace(Regex("""(?i)\s*\[(?:feat\.|with|remastered|bonus track|deluxe).*?\]"""), "")
            .trim()
    }

    private fun cleanArtistName(artists: String): String {
        return artists.split(",").firstOrNull()?.split("&")?.firstOrNull()?.trim() ?: artists.trim()
    }
}
