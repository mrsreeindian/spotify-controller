package com.spotify.controller.data.repository

import com.spotify.controller.data.model.LyricLine
import com.spotify.controller.data.model.Lyrics
import com.spotify.controller.data.model.Track
import com.spotify.controller.data.remote.LrclibApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class LyricsRepository(
    private val lrclibApiService: LrclibApiService
) {
    // In-memory cache keyed by track ID
    private val cache = ConcurrentHashMap<String, Lyrics>()

    suspend fun getLyrics(track: Track): Result<Lyrics> = withContext(Dispatchers.IO) {
        if (track.id.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Invalid track ID"))
        }

        cache[track.id]?.let {
            return@withContext Result.success(it)
        }

        try {
            val durationSec = (track.durationMs / 1000).toInt()
            val response = lrclibApiService.fetchLyrics(
                trackName = track.name,
                artistName = track.artistNames.split(",").firstOrNull()?.trim() ?: track.artistNames,
                albumName = track.albumName,
                durationSec = durationSec
            )

            if (response == null) {
                return@withContext Result.failure(NoSuchElementException("No lyrics found for track"))
            }

            val parsedLines: List<LyricLine> = if (!response.syncedLyrics.isNullOrBlank()) {
                LrcParser.parse(response.syncedLyrics)
            } else emptyList()

            val lyrics = Lyrics(
                trackId = track.id,
                title = track.name,
                artist = track.artistNames,
                lines = parsedLines,
                isSynced = parsedLines.isNotEmpty(),
                plainLyrics = response.plainLyrics,
                provider = "LRCLIB (Spicy Lyrics Engine)"
            )

            cache[track.id] = lyrics
            Result.success(lyrics)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
