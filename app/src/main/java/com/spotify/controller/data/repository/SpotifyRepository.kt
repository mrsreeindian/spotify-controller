package com.spotify.controller.data.repository

import com.spotify.controller.data.model.PlaybackState
import com.spotify.controller.data.model.RepeatMode
import com.spotify.controller.data.model.SpotifyCredentials
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
                delay(1500L)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    suspend fun pollPlaybackState() {
        val creds = credentials.value
        val token = spotifyAuthManager.refreshTokenIfNeeded(creds) ?: return
        val state = spotifyApiService.getPlaybackState(token)
        if (state != null) {
            _playbackState.value = state
        }
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
        val newLiked = !currentTrack.isLiked
        _playbackState.update { it.copy(track = currentTrack.copy(isLiked = newLiked)) }

        val token = getValidToken() ?: return
        val success = spotifyApiService.setLiked(token, currentTrack.id, newLiked)
        if (!success) {
            _playbackState.update { it.copy(track = currentTrack.copy(isLiked = !newLiked)) }
        }
    }

    suspend fun testConnection(clientId: String, secret: String, directToken: String): Result<String> {
        val tokenToTest = if (directToken.isNotBlank()) {
            directToken
        } else {
            // Check if existing access token or refresh
            getValidToken() ?: return Result.failure(Exception("Please complete Spotify authorization."))
        }
        return spotifyApiService.testConnection(tokenToTest)
    }

    private suspend fun getValidToken(): String? {
        val creds = credentials.value
        return spotifyAuthManager.refreshTokenIfNeeded(creds)
    }
}
