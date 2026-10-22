package com.spotify.controller.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.spotify.controller.data.model.PlaybackState
import com.spotify.controller.data.model.PomodoroState
import com.spotify.controller.data.model.QuickPlayItem
import com.spotify.controller.data.model.SpotifyCredentials
import com.spotify.controller.data.model.SpotifyDevice
import com.spotify.controller.data.remote.SpotifyAuthManager
import com.spotify.controller.data.repository.CredentialsRepository
import com.spotify.controller.data.repository.SpotifyRepository
import com.spotify.controller.domain.PomodoroManager
import com.spotify.controller.ui.theme.AlbumArtColors
import com.spotify.controller.ui.theme.PaletteExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerViewModel(
    application: Application,
    private val spotifyRepository: SpotifyRepository,
    private val credentialsRepository: CredentialsRepository,
    private val spotifyAuthManager: SpotifyAuthManager,
    val lyricsViewModel: LyricsViewModel
) : AndroidViewModel(application) {

    val playbackState: StateFlow<PlaybackState> = spotifyRepository.playbackState
    val credentials: StateFlow<SpotifyCredentials> = spotifyRepository.credentials

    private val _albumColors = MutableStateFlow(AlbumArtColors.DEFAULT)
    val albumColors: StateFlow<AlbumArtColors> = _albumColors.asStateFlow()

    private val _showLyrics = MutableStateFlow(false)
    val showLyrics: StateFlow<Boolean> = _showLyrics.asStateFlow()

    private val _isLandscape = MutableStateFlow(false)
    val isLandscape: StateFlow<Boolean> = _isLandscape.asStateFlow()

    private val _showSetupDialog = MutableStateFlow(false)
    val showSetupDialog: StateFlow<Boolean> = _showSetupDialog.asStateFlow()

    val pomodoroManager = PomodoroManager(getApplication(), viewModelScope).apply {
        onWorkSessionCompleted = {
            if (playbackState.value.isPlaying) {
                togglePlayPause()
            }
        }
    }
    val pomodoroState: StateFlow<PomodoroState> = pomodoroManager.state

    private val _showPomodoroDialog = MutableStateFlow(false)
    val showPomodoroDialog: StateFlow<Boolean> = _showPomodoroDialog.asStateFlow()

    private val _availableDevices = MutableStateFlow<List<SpotifyDevice>>(emptyList())
    val availableDevices: StateFlow<List<SpotifyDevice>> = _availableDevices.asStateFlow()

    private val _isDevicesLoading = MutableStateFlow(false)
    val isDevicesLoading: StateFlow<Boolean> = _isDevicesLoading.asStateFlow()

    private val _showDeviceDialog = MutableStateFlow(false)
    val showDeviceDialog: StateFlow<Boolean> = _showDeviceDialog.asStateFlow()

    private val _recentlyPlayedItems = MutableStateFlow<List<QuickPlayItem>>(emptyList())
    val recentlyPlayedItems: StateFlow<List<QuickPlayItem>> = _recentlyPlayedItems.asStateFlow()

    private val _isQuickPlayLoading = MutableStateFlow(false)
    val isQuickPlayLoading: StateFlow<Boolean> = _isQuickPlayLoading.asStateFlow()

    private val _showQuickPlayDialog = MutableStateFlow(false)
    val showQuickPlayDialog: StateFlow<Boolean> = _showQuickPlayDialog.asStateFlow()

    private var lastExtractedUrl: String? = null

    init {
        // Observe playback changes to extract theme colors & update lyrics
        viewModelScope.launch {
            playbackState.collect { state ->
                val token = credentials.value.getEffectiveToken()
                lyricsViewModel.onTrackChanged(state.track, token)

                val artUrl = state.track?.albumArtUrl
                if (!artUrl.isNullOrBlank() && artUrl != lastExtractedUrl) {
                    lastExtractedUrl = artUrl
                    extractColorsFromUrl(artUrl)
                }
            }
        }

        // Check if initial setup is needed
        viewModelScope.launch {
            credentials.collect { creds ->
                if (!creds.isConfigured) {
                    _showSetupDialog.value = true
                }
            }
        }

        // Restore orientation preference
        viewModelScope.launch {
            credentialsRepository.orientationModeFlow.collect { mode ->
                _isLandscape.value = (mode == "landscape")
            }
        }
    }

    private fun extractColorsFromUrl(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val loader = ImageLoader(getApplication())
                val request = ImageRequest.Builder(getApplication())
                    .data(url)
                    .allowHardware(false) // Required for Palette software bitmap access
                    .build()

                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        val colors = PaletteExtractor.extract(bitmap)
                        withContext(Dispatchers.Main) {
                            _albumColors.value = colors
                        }
                    }
                }
            } catch (e: Exception) {
                // Fallback to default
            }
        }
    }

    fun togglePlayPause() {
        viewModelScope.launch { spotifyRepository.togglePlayPause() }
    }

    fun nextTrack() {
        viewModelScope.launch { spotifyRepository.nextTrack() }
    }

    fun previousTrack() {
        viewModelScope.launch { spotifyRepository.previousTrack() }
    }

    fun seekTo(positionMs: Long) {
        viewModelScope.launch { spotifyRepository.seekTo(positionMs) }
    }

    fun setVolume(percent: Int) {
        viewModelScope.launch { spotifyRepository.setVolume(percent) }
    }

    fun toggleShuffle() {
        viewModelScope.launch { spotifyRepository.toggleShuffle() }
    }

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    fun cycleRepeatMode() {
        viewModelScope.launch { spotifyRepository.cycleRepeatMode() }
    }

    fun toggleLike() {
        viewModelScope.launch {
            val result = spotifyRepository.toggleLike()
            result.onFailure { err ->
                _toastEvent.emit(err.message ?: "Failed to update Liked Songs")
            }
        }
    }

    fun toggleLyrics() {
        _showLyrics.value = !_showLyrics.value
    }

    fun setLyricsVisible(visible: Boolean) {
        _showLyrics.value = visible
    }

    fun toggleOrientation(onOrientationChanged: (Boolean) -> Unit) {
        val newLandscape = !_isLandscape.value
        _isLandscape.value = newLandscape
        viewModelScope.launch {
            credentialsRepository.saveOrientationMode(if (newLandscape) "landscape" else "portrait")
        }
        onOrientationChanged(newLandscape)
    }

    fun openSetup() {
        _showSetupDialog.value = true
    }

    fun closeSetup() {
        _showSetupDialog.value = false
    }

    fun openPomodoro() {
        _showPomodoroDialog.value = true
    }

    fun closePomodoro() {
        _showPomodoroDialog.value = false
    }

    fun togglePomodoro() {
        pomodoroManager.toggleStartPause()
    }

    fun startPomodoro() {
        pomodoroManager.start()
    }

    fun pausePomodoro() {
        pomodoroManager.pause()
    }

    fun resetPomodoro() {
        pomodoroManager.reset()
    }

    fun skipPomodoroPhase() {
        pomodoroManager.skipPhase()
    }

    fun setPomodoroWorkDuration(minutes: Int) {
        pomodoroManager.setWorkDuration(minutes)
    }

    fun setPomodoroBreakDuration(minutes: Int) {
        pomodoroManager.setBreakDuration(minutes)
    }

    fun setPomodoroAutoPause(enable: Boolean) {
        pomodoroManager.setAutoPauseSpotifyOnBreak(enable)
    }

    val isDndPermissionGranted: Boolean
        get() = pomodoroManager.dndManager.isDndPermissionGranted

    fun openDndSettings() {
        pomodoroManager.dndManager.openDndSettings()
    }

    fun setPomodoroEnableDnd(enable: Boolean) {
        pomodoroManager.setEnableDnd(enable)
    }

    override fun onCleared() {
        super.onCleared()
        pomodoroManager.cleanup()
    }

    fun openDeviceDialog() {
        _showDeviceDialog.value = true
        refreshDevices()
    }

    fun closeDeviceDialog() {
        _showDeviceDialog.value = false
    }

    fun refreshDevices() {
        viewModelScope.launch {
            _isDevicesLoading.value = true
            val res = spotifyRepository.getAvailableDevices()
            _availableDevices.value = res.getOrDefault(emptyList())
            _isDevicesLoading.value = false
        }
    }

    fun selectDevice(deviceId: String) {
        viewModelScope.launch {
            _isDevicesLoading.value = true
            spotifyRepository.transferPlayback(deviceId)
            refreshDevices()
            _showDeviceDialog.value = false
        }
    }

    fun openQuickPlayDialog() {
        _showQuickPlayDialog.value = true
        refreshRecentlyPlayed()
    }

    fun closeQuickPlayDialog() {
        _showQuickPlayDialog.value = false
    }

    fun refreshRecentlyPlayed() {
        viewModelScope.launch {
            _isQuickPlayLoading.value = true
            val res = spotifyRepository.getRecentlyPlayedQuickList()
            _recentlyPlayedItems.value = res.getOrDefault(emptyList())
            _isQuickPlayLoading.value = false
        }
    }

    fun playQuickItem(item: QuickPlayItem) {
        viewModelScope.launch {
            _showQuickPlayDialog.value = false
            spotifyRepository.playContext(item)
        }
    }

    fun saveCredentials(clientId: String, clientSecret: String, directDevToken: String) {
        val cleanToken = SpotifyCredentials.cleanToken(directDevToken)
        val cleanId = clientId.trim()
        val cleanSecret = clientSecret.trim()
        viewModelScope.launch {
            credentialsRepository.saveClientCredentials(cleanId, cleanSecret, cleanToken)
            if (cleanToken.isNotBlank()) {
                spotifyRepository.pollPlaybackState()
            }
        }
    }

    fun startOAuthLogin(clientId: String) {
        viewModelScope.launch {
            spotifyAuthManager.startAuth(clientId)
        }
    }

    suspend fun testConnection(clientId: String, secret: String, directToken: String): Result<String> {
        return spotifyRepository.testConnection(clientId, secret, directToken)
    }
}
