package com.spotify.controller.data.repository

import com.spotify.controller.data.model.PlaybackState
import com.spotify.controller.data.model.QuickPlayItem
import com.spotify.controller.data.model.QuickPlayType
import com.spotify.controller.data.model.RepeatMode
import com.spotify.controller.data.model.SpotifyCredentials
import com.spotify.controller.data.model.SpotifyDevice
import com.spotify.controller.data.remote.SpotifyApiService
import com.spotify.controller.data.remote.SpotifyAuthManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SpotifyRepository(
    private val spotifyApiService: SpotifyApiService,
    private val spotifyAuthManager: SpotifyAuthManager,
    private val credentialsRepository: CredentialsRepository,
    private val appScope: CoroutineScope
) {

    val credentials: StateFlow<SpotifyCredentials> = credentialsRepository.credentialsFlow
        .stateIn(appScope, SharingStarted.Eagerly, SpotifyCredentials())

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var pollingJob: Job? = null

    init {
        // Start polling when valid credentials become available
        appScope.launch {
            credentials.collect { creds ->
                if (creds.hasValidSession) {
                    startPolling()
                } else {
                    stopPolling()
                }
            }
        }
    }

    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = appScope.launch(Dispatchers.IO) {
            while (isActive) {
                pollPlaybackState()
                val isPlaying = _playbackState.value.isPlaying
                val delayMs = if (isPlaying) 1500L else 4000L
                delay(delayMs)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private var lastLikedTrackId: String? = null
    private var lastLikedStatus: Boolean = false
    private var lastLikedCheckTimestamp: Long = 0L

    suspend fun pollPlaybackState() {
        val creds = credentials.value
        val token = spotifyAuthManager.refreshTokenIfNeeded(creds) ?: return

        val currentTrackId = _playbackState.value.track?.id?.removePrefix("spotify:track:")?.trim()
        val now = android.os.SystemClock.elapsedRealtime()
        val isSameTrack = (!currentTrackId.isNullOrBlank() && currentTrackId == lastLikedTrackId)
        val shouldCheckLiked = !isSameTrack || (now - lastLikedCheckTimestamp > 30_000L)

        val cachedLiked = if (isSameTrack && !shouldCheckLiked) lastLikedStatus else null
        val state = spotifyApiService.getPlaybackState(token, cachedLiked) ?: return

        val stateTrack = state.track
        if (stateTrack != null && stateTrack.id.isNotBlank()) {
            val trackId = stateTrack.id.removePrefix("spotify:track:").trim()
            lastLikedTrackId = trackId
            lastLikedStatus = stateTrack.isLiked
            if (shouldCheckLiked) {
                lastLikedCheckTimestamp = now
            }
        }

        _playbackState.value = state
    }

    suspend fun togglePlayPause() {
        val currentState = _playbackState.value
        val isCurrentlyPlaying = currentState.isPlaying
        // Optimistic update
        _playbackState.update { it.copy(isPlaying = !isCurrentlyPlaying) }

        val token = getValidToken() ?: return
        val success = if (isCurrentlyPlaying) {
            spotifyApiService.pause(token)
        } else {
            spotifyApiService.play(token)
        }

        if (!success) {
            // Revert on failure
            _playbackState.update { it.copy(isPlaying = isCurrentlyPlaying) }
        } else {
            // Quick refresh to sync accurate progress
            delay(300L)
            pollPlaybackState()
        }
    }

    suspend fun nextTrack() {
        val token = getValidToken() ?: return
        spotifyApiService.next(token)
        delay(400L)
        pollPlaybackState()
    }

    suspend fun previousTrack() {
        val token = getValidToken() ?: return
        spotifyApiService.previous(token)
        delay(400L)
        pollPlaybackState()
    }

    suspend fun seekTo(positionMs: Long) {
        val track = _playbackState.value.track ?: return
        val clamped = positionMs.coerceIn(0L, track.durationMs)
        _playbackState.update {
            it.copy(
                progressMs = clamped,
                lastSyncTimestamp = android.os.SystemClock.elapsedRealtime()
            )
        }

        val token = getValidToken() ?: return
        spotifyApiService.seek(token, clamped)
    }

    suspend fun setVolume(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        _playbackState.update { it.copy(volumePercent = clamped) }

        val token = getValidToken() ?: return
        spotifyApiService.setVolume(token, clamped)
    }

    suspend fun toggleShuffle() {
        val currentShuffle = _playbackState.value.isShuffle
        val target = !currentShuffle
        _playbackState.update { it.copy(isShuffle = target) }

        val token = getValidToken() ?: return
        val success = spotifyApiService.setShuffle(token, target)
        if (!success) {
            _playbackState.update { it.copy(isShuffle = currentShuffle) }
        }
    }

    suspend fun cycleRepeatMode() {
        val current = _playbackState.value.repeatMode
        val next = current.next()
        _playbackState.update { it.copy(repeatMode = next) }

        val token = getValidToken() ?: return
        val success = spotifyApiService.setRepeat(token, next)
        if (!success) {
            _playbackState.update { it.copy(repeatMode = current) }
        }
    }

    suspend fun toggleLike() {
        val currentTrack = _playbackState.value.track ?: return
        val trackId = currentTrack.id.removePrefix("spotify:track:").trim()
        if (trackId.isBlank()) return

        val newLiked = !currentTrack.isLiked
        lastLikedTrackId = trackId
        lastLikedStatus = newLiked
        lastLikedCheckTimestamp = android.os.SystemClock.elapsedRealtime()
        _playbackState.update { it.copy(track = currentTrack.copy(isLiked = newLiked)) }

        val token = getValidToken() ?: return
        val success = spotifyApiService.setLiked(token, trackId, newLiked)
        if (!success) {
            // Revert on failure
            lastLikedStatus = !newLiked
            _playbackState.update { it.copy(track = currentTrack.copy(isLiked = !newLiked)) }
        }
    }

    suspend fun getAvailableDevices(): Result<List<SpotifyDevice>> {
        val token = getValidToken() ?: return Result.failure(Exception("Not authenticated"))
        val devices = spotifyApiService.getAvailableDevices(token)
        return Result.success(devices)
    }

    suspend fun transferPlayback(deviceId: String): Result<Unit> {
        val token = getValidToken() ?: return Result.failure(Exception("Not authenticated"))
        val success = spotifyApiService.transferPlayback(token, deviceId)
        if (success) {
            delay(400L)
            pollPlaybackState()
            return Result.success(Unit)
        }
        return Result.failure(Exception("Failed to transfer playback to device"))
    }

    suspend fun getRecentlyPlayedQuickList(): Result<List<QuickPlayItem>> {
        val token = getValidToken() ?: return Result.failure(Exception("Not authenticated"))
        val items = spotifyApiService.getRecentlyPlayed(token)
        return Result.success(items)
    }

    suspend fun playContext(item: QuickPlayItem): Result<Unit> {
        val token = getValidToken() ?: return Result.failure(Exception("Not authenticated"))
        val currentDeviceId = _playbackState.value.deviceId
        val success = spotifyApiService.playContext(
            token = token,
            contextUri = item.uri,
            trackUris = item.trackUris,
            deviceId = currentDeviceId
        )
        if (success) {
            delay(500L)
            pollPlaybackState()
            return Result.success(Unit)
        }
        return Result.failure(Exception("Failed to play selected item"))
    }

    suspend fun playContext(contextUri: String): Result<Unit> {
        return playContext(
            QuickPlayItem(
                id = contextUri,
                title = "",
                subtitle = "",
                imageUrl = null,
                uri = contextUri,
                type = QuickPlayType.PLAYLIST
            )
        )
    }

    suspend fun testConnection(clientId: String, secret: String, directToken: String): Result<String> {
        val cleanedToken = SpotifyCredentials.cleanToken(directToken)
        val cleanedClientId = clientId.trim()
        val cleanedSecret = secret.trim()

        // 1. Direct developer token entered
        if (cleanedToken.isNotBlank()) {
            val apiRes = spotifyApiService.testConnection(cleanedToken)
            return apiRes.map { displayName -> "Connected successfully as $displayName!" }
        }

        // 2. Check if we have an active user session (accessToken or refreshToken)
        val creds = credentials.value
        val credsToUse = creds.copy(
            clientId = if (cleanedClientId.isNotBlank()) cleanedClientId else creds.clientId,
            clientSecret = if (cleanedSecret.isNotBlank()) cleanedSecret else creds.clientSecret
        )

        if (credsToUse.hasValidSession) {
            val validToken = spotifyAuthManager.refreshTokenIfNeeded(credsToUse)
            if (validToken != null) {
                val apiRes = spotifyApiService.testConnection(validToken)
                return apiRes.map { displayName -> "Connected successfully as $displayName!" }
            } else {
                return Result.failure(
                    Exception("Spotify session expired or revoked. Please tap 'Authorize with Spotify Login' to reconnect.")
                )
            }
        }

        // 3. No active user session. If client credentials entered, test them directly
        if (cleanedClientId.isNotBlank() && cleanedSecret.isNotBlank()) {
            return spotifyAuthManager.testClientCredentials(cleanedClientId, cleanedSecret)
        }

        // 4. Only Client ID entered (PKCE flow, no secret)
        if (cleanedClientId.isNotBlank()) {
            return Result.failure(
                Exception("Client ID saved. Please tap 'Authorize with Spotify Login' above to connect your account, or paste a Direct Access Token.")
            )
        }

        return Result.failure(
            Exception("Please enter your Spotify Client ID or a Direct Developer Token.")
        )
    }

    private suspend fun getValidToken(): String? {
        val creds = credentials.value
        return spotifyAuthManager.refreshTokenIfNeeded(creds)
    }
}
