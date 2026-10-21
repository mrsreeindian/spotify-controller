package com.spotify.controller.data.remote

import android.os.SystemClock
import com.spotify.controller.data.model.PlaybackState
import com.spotify.controller.data.model.RepeatMode
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

    suspend fun testConnection(token: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/me")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(body)
                val displayName = json.optString("display_name", "Spotify User")
                Result.success(displayName)
            } else {
                Result.failure(Exception("Connection failed (${response.code}): $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPlaybackState(token: String): PlaybackState? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/me/player")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.code == 204) {
                // 204 No Content -> Spotify is not active/playing
                return@withContext PlaybackState.IDLE
            }
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)

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
                val id = itemObj.optString("id", "")
                val name = itemObj.optString("name", "Unknown Title")
                val durationMs = itemObj.optLong("duration_ms", 0L)
                val uri = itemObj.optString("uri", "")

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
                    imagesArr.getJSONObject(0).optString("url", null)
                } else null

                // Check saved/liked state
                val isLiked = if (id.isNotEmpty()) checkIsLiked(token, id) else false

                Track(
                    id = id,
                    name = name,
                    artistNames = artistNames,
                    albumName = albumName,
                    albumArtUrl = albumArtUrl,
                    durationMs = durationMs,
                    uri = uri,
                    isLiked = isLiked
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
                .header("Authorization", "Bearer $token")
                .put(EMPTY_BODY)
                .build()
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setVolume(token: String, percent: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val clamped = percent.coerceIn(0, 100)
            val request = Request.Builder()
                .url("$BASE_URL/me/player/volume?volume_percent=$clamped")
                .header("Authorization", "Bearer $token")
                .put(EMPTY_BODY)
                .build()
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setShuffle(token: String, state: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/me/player/shuffle?state=$state")
                .header("Authorization", "Bearer $token")
                .put(EMPTY_BODY)
                .build()
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setRepeat(token: String, mode: RepeatMode): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/me/player/repeat?state=${mode.apiValue}")
                .header("Authorization", "Bearer $token")
                .put(EMPTY_BODY)
                .build()
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun checkIsLiked(token: String, trackId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/me/tracks/contains?ids=$trackId")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext false
            val body = response.body?.string() ?: return@withContext false
            val jsonArr = JSONArray(body)
            if (jsonArr.length() > 0) jsonArr.getBoolean(0) else false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setLiked(token: String, trackId: String, isLiked: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = if (isLiked) {
                Request.Builder()
                    .url("$BASE_URL/me/tracks?ids=$trackId")
                    .header("Authorization", "Bearer $token")
                    .put(EMPTY_BODY)
                    .build()
            } else {
                Request.Builder()
                    .url("$BASE_URL/me/tracks?ids=$trackId")
                    .header("Authorization", "Bearer $token")
                    .delete(EMPTY_BODY)
                    .build()
            }
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun executePlayerCommand(token: String, endpoint: String, method: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val reqBuilder = Request.Builder()
                .url("$BASE_URL/me/player/$endpoint")
                .header("Authorization", "Bearer $token")

            val request = when (method) {
                "PUT" -> reqBuilder.put(EMPTY_BODY).build()
                "POST" -> reqBuilder.post(EMPTY_BODY).build()
                else -> reqBuilder.get().build()
            }

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}
