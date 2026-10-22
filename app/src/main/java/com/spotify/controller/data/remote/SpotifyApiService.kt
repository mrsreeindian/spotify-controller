package com.spotify.controller.data.remote

import android.os.SystemClock
import com.spotify.controller.data.model.PlaybackState
import com.spotify.controller.data.model.QuickPlayItem
import com.spotify.controller.data.model.QuickPlayType
import com.spotify.controller.data.model.RepeatMode
import com.spotify.controller.data.model.SpotifyCredentials
import com.spotify.controller.data.model.SpotifyDevice
import com.spotify.controller.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class SpotifyApiService(private val client: OkHttpClient) {

    companion object {
        private const val BASE_URL = "https://api.spotify.com/v1"
        private val EMPTY_BODY = "".toRequestBody("application/json".toMediaType())
    }

    private fun authHeader(token: String): String {
        return "Bearer ${SpotifyCredentials.cleanToken(token)}"
    }

    fun parseSpotifyErrorMessage(statusCode: Int, body: String): String {
        try {
            if (body.isNotBlank()) {
                val json = JSONObject(body)
                val errorObj = json.optJSONObject("error")
                if (errorObj != null) {
                    val msg = errorObj.optString("message")
                    if (msg.isNotBlank()) {
                        return when {
                            statusCode == 401 && msg.contains("expired", ignoreCase = true) ->
                                "Access token expired. Please generate a new token."
                            statusCode == 401 && msg.contains("invalid", ignoreCase = true) ->
                                "Invalid access token. Please check your token."
                            statusCode == 403 ->
                                "Permission denied: $msg. Ensure required scopes are granted."
                            else -> "Spotify error ($statusCode): $msg"
                        }
                    }
                }
                val errorDesc = json.optString("error_description")
                if (errorDesc.isNotBlank()) {
                    return "Spotify error ($statusCode): $errorDesc"
                }
                val errorStr = json.optString("error")
                if (errorStr.isNotBlank()) {
                    return "Spotify error ($statusCode): $errorStr"
                }
            }
        } catch (_: Exception) {}
        return when (statusCode) {
            400 -> "Bad Request (400). Please verify token format."
            401 -> "Unauthorized (401). Access token is expired or invalid."
            403 -> "Forbidden (403). Your account or token lacks required permissions (e.g. user-library-modify)."
            404 -> "Not found (404)."
            429 -> {
                val rem = SpotifyRateLimiter.remainingSeconds
                if (rem > 0) "Spotify rate limit active. Please wait ${rem}s before retrying."
                else "Spotify rate limit reached (429). Please wait a moment."
            }
            in 500..599 -> "Spotify server error ($statusCode). Please try again later."
            else -> "Connection failed ($statusCode)."
        }
    }

    suspend fun testConnection(token: String): Result<String> = withContext(Dispatchers.IO) {
        val cleaned = SpotifyCredentials.cleanToken(token)
        if (cleaned.isBlank()) {
            return@withContext Result.failure(Exception("Access token is empty."))
        }
        if (SpotifyRateLimiter.isRateLimited) {
            return@withContext Result.failure(
                Exception("Spotify rate limit active. Please wait ${SpotifyRateLimiter.remainingSeconds}s before retrying.")
            )
        }
        try {
            val request = Request.Builder()
                .url("$BASE_URL/me")
                .header("Authorization", authHeader(cleaned))
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    SpotifyRateLimiter.reset()
                    val json = JSONObject(body)
                    val displayName = json.optString("display_name").takeIf { it.isNotBlank() }
                        ?: json.optString("id").takeIf { it.isNotBlank() }
                        ?: "Spotify User"
                    Result.success(displayName)
                } else {
                    if (response.code == 429) {
                        val retryAfter = response.header("Retry-After")?.toLongOrNull()
                        SpotifyRateLimiter.onRateLimit(retryAfter)
                    }
                    val msg = parseSpotifyErrorMessage(response.code, body)
                    Result.failure(Exception(msg))
                }
            }
        } catch (e: Exception) {
            val friendlyMsg = when (e) {
                is SpotifyRateLimitException -> e.message ?: "Spotify rate limit active. Please wait."
                is java.net.UnknownHostException -> "Network error: Unable to reach Spotify. Check your internet connection."
                is java.net.SocketTimeoutException -> "Network error: Connection to Spotify timed out."
                else -> e.message ?: "Failed to connect to Spotify."
            }
            Result.failure(Exception(friendlyMsg, e))
        }
    }

    suspend fun getPlaybackState(token: String, isLiked: Boolean? = null): PlaybackState? = withContext(Dispatchers.IO) {
        if (SpotifyRateLimiter.isRateLimited) {
            return@withContext null
        }
        try {
            val request = Request.Builder()
                .url("$BASE_URL/me/player")
                .header("Authorization", authHeader(token))
                .get()
                .build()

            val rawData = client.newCall(request).execute().use { response ->
                if (response.code == 429) {
                    val retryAfter = response.header("Retry-After")?.toLongOrNull()
                    SpotifyRateLimiter.onRateLimit(retryAfter)
                    return@withContext null
                }
                if (response.code == 204) {
                    // 204 No Content -> Spotify is not active/playing
                    return@withContext PlaybackState.IDLE
                }
                if (!response.isSuccessful) return@withContext null
                response.body?.string()
            } ?: return@withContext null

            val json = JSONObject(rawData)

            val isPlaying = json.optBoolean("is_playing", false)
            val progressMs = json.optLong("progress_ms", 0L)
            val shuffleState = json.optBoolean("shuffle_state", false)
            val repeatStateStr = json.optString("repeat_state", "off")
            val repeatMode = RepeatMode.fromApiValue(repeatStateStr)

            // Device
            val deviceObj = json.optJSONObject("device")
            val deviceName = deviceObj?.optString("name")
            val deviceId = deviceObj?.optString("id")
            val volumePercent = deviceObj?.optInt("volume_percent", 50) ?: 50
            val isDeviceActive = deviceObj?.optBoolean("is_active", false) ?: false

            // Item (Track)
            val itemObj = json.optJSONObject("item")
            val track = if (itemObj != null) {
                val rawId = itemObj.optString("id", "")
                val uri = itemObj.optString("uri", "")
                val id = if (rawId.isNotBlank()) {
                    rawId.removePrefix("spotify:track:").trim()
                } else if (uri.startsWith("spotify:track:")) {
                    uri.removePrefix("spotify:track:").trim()
                } else ""
                val name = itemObj.optString("name", "Unknown Title")
                val durationMs = itemObj.optLong("duration_ms", 0L)

                // Artists
                val artistsArr = itemObj.optJSONArray("artists")
                val artistNames = if (artistsArr != null && artistsArr.length() > 0) {
                    val list = mutableListOf<String>()
                    for (i in 0 until artistsArr.length()) {
                        list.add(artistsArr.getJSONObject(i).optString("name", ""))
                    }
                    list.joinToString(", ")
                } else {
                    "Unknown Artist"
                }

                // Album & Album Art
                val albumObj = itemObj.optJSONObject("album")
                val albumName = albumObj?.optString("name", "Unknown Album") ?: "Unknown Album"
                val imagesArr = albumObj?.optJSONArray("images")
                val albumArtUrl = if (imagesArr != null && imagesArr.length() > 0) {
                    val imgObj = imagesArr.getJSONObject(0)
                    if (imgObj.isNull("url")) null else imgObj.optString("url")
                } else null

                // Check saved/liked state
                val resolvedLiked = isLiked ?: if (id.isNotEmpty()) checkIsLiked(token, id) else false

                Track(
                    id = id,
                    name = name,
                    artistNames = artistNames,
                    albumName = albumName,
                    albumArtUrl = albumArtUrl,
                    durationMs = durationMs,
                    uri = uri,
                    isLiked = resolvedLiked
                )
            } else null

            PlaybackState(
                track = track,
                isPlaying = isPlaying,
                progressMs = progressMs,
                lastSyncTimestamp = SystemClock.elapsedRealtime(),
                volumePercent = volumePercent,
                isShuffle = shuffleState,
                repeatMode = repeatMode,
                deviceName = deviceName,
                deviceId = deviceId,
                isDeviceActive = isDeviceActive
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun play(token: String): Boolean = executePlayerCommand(token, "play", "PUT")
    suspend fun pause(token: String): Boolean = executePlayerCommand(token, "pause", "PUT")
    suspend fun next(token: String): Boolean = executePlayerCommand(token, "next", "POST")
    suspend fun previous(token: String): Boolean = executePlayerCommand(token, "previous", "POST")

    suspend fun seek(token: String, positionMs: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/me/player/seek?position_ms=$positionMs")
                .header("Authorization", authHeader(token))
                .put(EMPTY_BODY)
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setVolume(token: String, percent: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val clamped = percent.coerceIn(0, 100)
            val request = Request.Builder()
                .url("$BASE_URL/me/player/volume?volume_percent=$clamped")
                .header("Authorization", authHeader(token))
                .put(EMPTY_BODY)
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setShuffle(token: String, state: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/me/player/shuffle?state=$state")
                .header("Authorization", authHeader(token))
                .put(EMPTY_BODY)
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setRepeat(token: String, mode: RepeatMode): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/me/player/repeat?state=${mode.apiValue}")
                .header("Authorization", authHeader(token))
                .put(EMPTY_BODY)
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun checkIsLiked(token: String, trackId: String): Boolean = withContext(Dispatchers.IO) {
        val cleanId = trackId.removePrefix("spotify:track:").trim()
        if (cleanId.isBlank() || SpotifyRateLimiter.isRateLimited) return@withContext false
        try {
            val request = Request.Builder()
                .url("$BASE_URL/me/tracks/contains?ids=$cleanId")
                .header("Authorization", authHeader(token))
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code == 429) {
                    val retryAfter = response.header("Retry-After")?.toLongOrNull()
                    SpotifyRateLimiter.onRateLimit(retryAfter)
                    return@withContext false
                }
                if (!response.isSuccessful) return@withContext false
                val body = response.body?.string() ?: return@withContext false
                val jsonArr = JSONArray(body)
                if (jsonArr.length() > 0) jsonArr.getBoolean(0) else false
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setLiked(token: String, trackId: String, isLiked: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanId = trackId.removePrefix("spotify:track:").trim()
        if (cleanId.isBlank()) return@withContext Result.failure(Exception("Invalid track ID"))

        if (SpotifyRateLimiter.isRateLimited) {
            return@withContext Result.failure(
                Exception("Spotify rate limit active (${SpotifyRateLimiter.remainingSeconds}s remaining). Please wait.")
            )
        }

        try {
            val jsonPayload = JSONObject().apply {
                put("ids", JSONArray().put(cleanId))
            }
            val requestBody = jsonPayload.toString().toRequestBody("application/json".toMediaType())

            val request = if (isLiked) {
                Request.Builder()
                    .url("$BASE_URL/me/tracks")
                    .header("Authorization", authHeader(token))
                    .put(requestBody)
                    .build()
            } else {
                Request.Builder()
                    .url("$BASE_URL/me/tracks")
                    .header("Authorization", authHeader(token))
                    .delete(requestBody)
                    .build()
            }

            client.newCall(request).execute().use { response ->
                if (response.code == 429) {
                    val retryAfter = response.header("Retry-After")?.toLongOrNull()
                    SpotifyRateLimiter.onRateLimit(retryAfter)
                    return@withContext Result.failure(
                        Exception("Spotify rate limit reached (${SpotifyRateLimiter.remainingSeconds}s remaining).")
                    )
                }

                if (response.isSuccessful || response.code in 200..204) {
                    Result.success(Unit)
                } else {
                    val body = response.body?.string().orEmpty()
                    val msg = parseSpotifyErrorMessage(response.code, body)
                    Result.failure(Exception(msg))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAvailableDevices(token: String): List<SpotifyDevice> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/me/player/devices")
                .header("Authorization", authHeader(token))
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(body)
                val devicesArray = json.optJSONArray("devices") ?: return@withContext emptyList()

                val list = mutableListOf<SpotifyDevice>()
                for (i in 0 until devicesArray.length()) {
                    val d = devicesArray.getJSONObject(i)
                    val id = d.optString("id", "")
                    if (id.isBlank()) continue
                    list.add(
                        SpotifyDevice(
                            id = id,
                            name = d.optString("name", "Unknown Device"),
                            type = d.optString("type", "Speaker"),
                            isActive = d.optBoolean("is_active", false),
                            isPrivateSession = d.optBoolean("is_private_session", false),
                            isRestricted = d.optBoolean("is_restricted", false),
                            volumePercent = d.optInt("volume_percent", 50),
                            supportsVolume = d.optBoolean("supports_volume", true)
                        )
                    )
                }
                list
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun transferPlayback(token: String, deviceId: String, play: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("device_ids", JSONArray().put(deviceId))
                put("play", play)
            }
            val body = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL/me/player")
                .header("Authorization", authHeader(token))
                .put(body)
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful || response.code == 204
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun playContext(
        token: String,
        contextUri: String,
        trackUris: List<String>? = null,
        deviceId: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                if (!trackUris.isNullOrEmpty()) {
                    put("uris", JSONArray(trackUris))
                } else if (contextUri.startsWith("spotify:track:")) {
                    put("uris", JSONArray().put(contextUri))
                } else if (contextUri.isNotBlank()) {
                    put("context_uri", contextUri)
                }
            }
            val body = payload.toString().toRequestBody("application/json".toMediaType())
            val urlBuilder = StringBuilder("$BASE_URL/me/player/play")
            if (!deviceId.isNullOrBlank()) {
                urlBuilder.append("?device_id=").append(deviceId)
            }
            val request = Request.Builder()
                .url(urlBuilder.toString())
                .header("Authorization", authHeader(token))
                .put(body)
                .build()

            val needRetry = client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 204) {
                    return@withContext true
                }
                response.code == 404 && !deviceId.isNullOrBlank()
            }

            // If 404 (e.g. specified device is inactive), retry without device_id query param
            if (needRetry) {
                val fallbackReq = Request.Builder()
                    .url("$BASE_URL/me/player/play")
                    .header("Authorization", authHeader(token))
                    .put(body)
                    .build()
                return@withContext client.newCall(fallbackReq).execute().use { fallbackResp ->
                    fallbackResp.isSuccessful || fallbackResp.code == 204
                }
            }

            false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getRecentlyPlayed(token: String): List<QuickPlayItem> = withContext(Dispatchers.IO) {
        try {
            val userPlaylists = mutableListOf<QuickPlayItem>()
            val userPlaylistsMap = mutableMapOf<String, QuickPlayItem>()

            // 1. Fetch user's own playlists (up to 100 items)
            try {
                for (offset in listOf(0, 50)) {
                    val playlistsReq = Request.Builder()
                        .url("$BASE_URL/me/playlists?limit=50&offset=$offset")
                        .header("Authorization", authHeader(token))
                        .get()
                        .build()

                    val shouldBreak = client.newCall(playlistsReq).execute().use { plResp ->
                        if (!plResp.isSuccessful) return@use true

                        val plBody = plResp.body?.string() ?: ""
                        val plJson = JSONObject(plBody)
                        val items = plJson.optJSONArray("items") ?: return@use true
                        if (items.length() == 0) return@use true

                        for (i in 0 until items.length()) {
                            val pl = items.getJSONObject(i)
                            val id = pl.optString("id", "")
                            val name = pl.optString("name", "Playlist")
                            val uri = pl.optString("uri", "spotify:playlist:$id")
                            val imgArr = pl.optJSONArray("images")
                            val imgUrl = if (imgArr != null && imgArr.length() > 0) {
                                imgArr.getJSONObject(0).optString("url")
                            } else null

                            val ownerObj = pl.optJSONObject("owner")
                            val ownerName = ownerObj?.optString("display_name", "") ?: ""
                            val tracksTotal = pl.optJSONObject("tracks")?.optInt("total", 0) ?: 0
                            val subtitle = if (ownerName.isNotBlank()) "$ownerName • $tracksTotal tracks" else "$tracksTotal tracks • Playlist"

                            val playlistItem = QuickPlayItem(
                                id = uri,
                                title = name,
                                subtitle = subtitle,
                                imageUrl = imgUrl,
                                uri = uri,
                                type = QuickPlayType.PLAYLIST
                            )
                            userPlaylists.add(playlistItem)
                            userPlaylistsMap[uri] = playlistItem
                        }

                        val total = plJson.optInt("total", 0)
                        offset + 50 >= total
                    }

                    if (shouldBreak) break
                }
            } catch (_: Exception) {}

            // 2. Fetch recently played tracks (to identify recent contexts)
            val recentPlaylists = mutableListOf<QuickPlayItem>()
            val recentAlbumsAndArtists = mutableListOf<QuickPlayItem>()
            val seenUris = mutableSetOf<String>()

            try {
                val req = Request.Builder()
                    .url("$BASE_URL/me/player/recently-played?limit=50")
                    .header("Authorization", authHeader(token))
                    .get()
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val json = JSONObject(body)
                        val items = json.optJSONArray("items")
                        if (items != null) {
                            for (i in 0 until items.length()) {
                                val itemObj = items.getJSONObject(i)
                                val trackObj = itemObj.optJSONObject("track") ?: continue
                                val albumObj = trackObj.optJSONObject("album")
                                val artistsArr = trackObj.optJSONArray("artists")
                                val primaryArtist = if (artistsArr != null && artistsArr.length() > 0) {
                                    artistsArr.getJSONObject(0).optString("name", "Unknown Artist")
                                } else "Unknown Artist"

                                val albumImages = albumObj?.optJSONArray("images")
                                val albumArtUrl = if (albumImages != null && albumImages.length() > 0) {
                                    albumImages.getJSONObject(0).optString("url")
                                } else null

                                val contextObj = itemObj.optJSONObject("context")
                                val contextType = contextObj?.optString("type")
                                val contextUri = contextObj?.optString("uri")

                                if (!contextUri.isNullOrBlank() && contextUri !in seenUris) {
                                    when (contextType) {
                                        "playlist" -> {
                                            seenUris.add(contextUri)
                                            val matchedPl = userPlaylistsMap[contextUri]
                                            if (matchedPl != null) {
                                                recentPlaylists.add(matchedPl)
                                            } else {
                                                recentPlaylists.add(
                                                    QuickPlayItem(
                                                        id = contextUri,
                                                        title = "Playlist",
                                                        subtitle = "Recently Played • Playlist",
                                                        imageUrl = albumArtUrl,
                                                        uri = contextUri,
                                                        type = QuickPlayType.PLAYLIST
                                                    )
                                                )
                                            }
                                        }
                                        "album" -> {
                                            seenUris.add(contextUri)
                                            recentAlbumsAndArtists.add(
                                                QuickPlayItem(
                                                    id = contextUri,
                                                    title = albumObj?.optString("name", "Album") ?: "Album",
                                                    subtitle = "$primaryArtist • Album",
                                                    imageUrl = albumArtUrl,
                                                    uri = contextUri,
                                                    type = QuickPlayType.ALBUM
                                                )
                                            )
                                        }
                                        "artist" -> {
                                            seenUris.add(contextUri)
                                            recentAlbumsAndArtists.add(
                                                QuickPlayItem(
                                                    id = contextUri,
                                                    title = primaryArtist,
                                                    subtitle = "Artist",
                                                    imageUrl = albumArtUrl,
                                                    uri = contextUri,
                                                    type = QuickPlayType.ARTIST
                                                )
                                            )
                                        }
                                    }
                                } else if (albumObj != null) {
                                    val albumUri = albumObj.optString("uri")
                                    if (albumUri.isNotBlank() && albumUri !in seenUris) {
                                        seenUris.add(albumUri)
                                        recentAlbumsAndArtists.add(
                                            QuickPlayItem(
                                                id = albumUri,
                                                title = albumObj.optString("name", "Album"),
                                                subtitle = "$primaryArtist • Album",
                                                imageUrl = albumArtUrl,
                                                uri = albumUri,
                                                type = QuickPlayType.ALBUM
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            // 3. Assemble final combined list:
            // - Recent playlists first
            // - User's other playlists from library
            // - Recent albums and artists
            val combinedList = mutableListOf<QuickPlayItem>()
            val addedUris = mutableSetOf<String>()

            for (pl in recentPlaylists) {
                if (addedUris.add(pl.uri)) {
                    combinedList.add(pl)
                }
            }

            for (pl in userPlaylists) {
                if (addedUris.add(pl.uri)) {
                    combinedList.add(pl)
                }
            }

            for (item in recentAlbumsAndArtists) {
                if (addedUris.add(item.uri)) {
                    combinedList.add(item)
                }
            }

            combinedList
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun executePlayerCommand(token: String, endpoint: String, method: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val reqBuilder = Request.Builder()
                .url("$BASE_URL/me/player/$endpoint")
                .header("Authorization", authHeader(token))

            val request = when (method) {
                "PUT" -> reqBuilder.put(EMPTY_BODY).build()
                "POST" -> reqBuilder.post(EMPTY_BODY).build()
                else -> reqBuilder.get().build()
            }

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }
}
