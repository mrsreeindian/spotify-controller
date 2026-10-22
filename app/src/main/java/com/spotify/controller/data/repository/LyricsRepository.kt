package com.spotify.controller.data.repository

import com.spotify.controller.data.model.LyricLine
import com.spotify.controller.data.model.Lyrics
import com.spotify.controller.data.model.SpotifyCredentials
import com.spotify.controller.data.model.Track
import com.spotify.controller.data.remote.LrclibApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class LyricsRepository(
    private val lrclibApiService: LrclibApiService,
    private val okHttpClient: OkHttpClient
) {
    private val cache = ConcurrentHashMap<String, Lyrics>()

    suspend fun getLyrics(track: Track, spotifyToken: String? = null): Result<Lyrics> = withContext(Dispatchers.IO) {
        if (track.id.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Invalid track ID"))
        }

        cache[track.id]?.let {
            return@withContext Result.success(it)
        }

        // Tier 1: Try official Spicy Lyrics Backend (api.spicylyrics.org) if token is available
        if (!spotifyToken.isNullOrBlank()) {
            trySpicyLyricsBackend(track, spotifyToken)?.let { spicyResult ->
                cache[track.id] = spicyResult
                return@withContext Result.success(spicyResult)
            }
        }

        // Tier 2: Try LRCLIB with multi-tier title cleaning & duration scoring
        try {
            val durationSec = (track.durationMs / 1000).toInt()
            val response = lrclibApiService.fetchLyrics(
                trackName = track.name,
                artistName = track.artistNames,
                albumName = track.albumName,
                durationSec = durationSec
            )

            if (response != null) {
                val rawLyrics = response.syncedLyrics
                val isExact = !rawLyrics.isNullOrBlank() && (rawLyrics.contains("<span") || rawLyrics.contains("<00:"))
                val parsedLines = parseRawLyrics(rawLyrics)
                val hasWords = parsedLines.any { it.words.isNotEmpty() }

                val lyrics = Lyrics(
                    trackId = track.id,
                    title = track.name,
                    artist = track.artistNames,
                    lines = parsedLines,
                    isSynced = parsedLines.isNotEmpty(),
                    plainLyrics = response.plainLyrics,
                    provider = "Spicy Lyrics Engine",
                    hasWordSync = hasWords,
                    hasExactWordSync = isExact
                )

                cache[track.id] = lyrics
                return@withContext Result.success(lyrics)
            }
        } catch (e: Exception) {
            // Proceed to error return
        }

        Result.failure(NoSuchElementException("No lyrics found for track"))
    }

    private fun trySpicyLyricsBackend(track: Track, token: String): Lyrics? {
        try {
            val jsonBody = JSONObject().apply {
                val queries = JSONArray().apply {
                    val query = JSONObject().apply {
                        put("operation", "lyrics")
                        put(
                            "variables",
                            JSONObject().apply {
                                put("id", track.id)
                                put("auth", "SpicyLyrics-WebAuth")
                            }
                        )
                    }
                    put(query)
                }
                put("queries", queries)
                put("client", JSONObject().apply { put("version", "1.0.0") })
            }

            val cleanToken = SpotifyCredentials.cleanToken(token)
            val request = Request.Builder()
                .url("https://api.spicylyrics.org/query")
                .header("Content-Type", "application/json")
                .header("X-mode", "2")
                .header("SpicyLyrics-Version", "1.0.0")
                .header("SpicyLyrics-WebAuth", "Bearer $cleanToken")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return null

            val body = response.body?.string() ?: return null
            val root = JSONObject(body)
            val queriesArr = root.optJSONArray("queries") ?: return null
            if (queriesArr.length() == 0) return null

            val resultObj = queriesArr.getJSONObject(0).optJSONObject("result") ?: return null
            val dataStr = resultObj.optString("data", "")
            if (dataStr.isBlank()) return null

            val isExact = dataStr.contains("<span") || dataStr.contains("<00:")
            val lines = parseRawLyrics(dataStr)
            if (lines.isNotEmpty()) {
                val hasWords = lines.any { it.words.isNotEmpty() }
                return Lyrics(
                    trackId = track.id,
                    title = track.name,
                    artist = track.artistNames,
                    lines = lines,
                    isSynced = true,
                    provider = "Spicy Lyrics API",
                    hasWordSync = hasWords,
                    hasExactWordSync = isExact
                )
            }
        } catch (e: Exception) {
            // Ignore backend failure and fallback
        }
        return null
    }

    private fun parseRawLyrics(raw: String?): List<LyricLine> {
        if (raw.isNullOrBlank()) return emptyList()

        return if (raw.contains("<tt") || raw.contains("<p begin")) {
            TtmlParser.parse(raw)
        } else {
            LrcParser.parse(raw)
        }
    }
}
