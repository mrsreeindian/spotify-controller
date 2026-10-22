package com.spotify.controller.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.spotify.controller.ui.components.PomodoroDialog
import com.spotify.controller.ui.components.SpicyDynamicBackground
import com.spotify.controller.ui.theme.SpotifyControllerTheme
import com.spotify.controller.ui.viewmodel.PlayerViewModel

@Composable
fun MainPlayerScreen(
    viewModel: PlayerViewModel,
    onToggleOrientation: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val albumColors by viewModel.albumColors.collectAsState()
    val showLyrics by viewModel.showLyrics.collectAsState()
    val isLandscapeLocked by viewModel.isLandscape.collectAsState()
    val showSetupDialog by viewModel.showSetupDialog.collectAsState()
    val credentials by viewModel.credentials.collectAsState()
    val pomodoroState by viewModel.pomodoroState.collectAsState()
    val showPomodoroDialog by viewModel.showPomodoroDialog.collectAsState()

    val lyrics by viewModel.lyricsViewModel.lyrics.collectAsState()
    val isLyricsLoading by viewModel.lyricsViewModel.isLoading.collectAsState()

    val configuration = LocalConfiguration.current
    val isSystemLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isLandscapeMode = isLandscapeLocked || isSystemLandscape

    SpotifyControllerTheme(albumColors = albumColors) {
        SpicyDynamicBackground(modifier = modifier.fillMaxSize()) {
            if (isLandscapeMode) {
                LandscapePlayerLayout(
                    playbackState = playbackState,
                    lyrics = lyrics,
                    isLyricsLoading = isLyricsLoading,
                    showLyrics = showLyrics,
                    pomodoroState = pomodoroState,
                    onOpenPomodoro = { viewModel.openPomodoro() },
                    onToggleLyrics = { viewModel.toggleLyrics() },
                    onCloseLyrics = { viewModel.setLyricsVisible(false) },
                    onToggleOrientation = {
                        viewModel.toggleOrientation(onToggleOrientation)
                    },
                    onOpenSettings = { viewModel.openSetup() },
                    onPlayPause = { viewModel.togglePlayPause() },
                    onNext = { viewModel.nextTrack() },
                    onPrevious = { viewModel.previousTrack() },
                    onSeek = { posMs -> viewModel.seekTo(posMs) },
                    onVolumeChange = { vol -> viewModel.setVolume(vol) },
                    onShuffleToggle = { viewModel.toggleShuffle() },
                    onRepeatToggle = { viewModel.cycleRepeatMode() },
                    onLikeToggle = { viewModel.toggleLike() },
                    onRetryLyrics = { viewModel.lyricsViewModel.retry() }
                )
            } else {
                PortraitPlayerLayout(
                    playbackState = playbackState,
                    lyrics = lyrics,
                    isLyricsLoading = isLyricsLoading,
                    showLyrics = showLyrics,
                    pomodoroState = pomodoroState,
                    onOpenPomodoro = { viewModel.openPomodoro() },
                    onToggleLyrics = { viewModel.toggleLyrics() },
                    onCloseLyrics = { viewModel.setLyricsVisible(false) },
                    onToggleOrientation = {
                        viewModel.toggleOrientation(onToggleOrientation)
                    },
                    onOpenSettings = { viewModel.openSetup() },
                    onPlayPause = { viewModel.togglePlayPause() },
                    onNext = { viewModel.nextTrack() },
                    onPrevious = { viewModel.previousTrack() },
                    onSeek = { posMs -> viewModel.seekTo(posMs) },
                    onVolumeChange = { vol -> viewModel.setVolume(vol) },
                    onShuffleToggle = { viewModel.toggleShuffle() },
                    onRepeatToggle = { viewModel.cycleRepeatMode() },
                    onLikeToggle = { viewModel.toggleLike() },
                    onRetryLyrics = { viewModel.lyricsViewModel.retry() }
                )
            }

            // Pomodoro Focus Timer Dialog
            if (showPomodoroDialog) {
                PomodoroDialog(
                    state = pomodoroState,
                    isDndPermissionGranted = viewModel.isDndPermissionGranted,
                    onStart = { viewModel.startPomodoro() },
                    onPause = { viewModel.pausePomodoro() },
                    onReset = { viewModel.resetPomodoro() },
                    onSkipPhase = { viewModel.skipPomodoroPhase() },
                    onWorkDurationChange = { mins -> viewModel.setPomodoroWorkDuration(mins) },
                    onBreakDurationChange = { mins -> viewModel.setPomodoroBreakDuration(mins) },
                    onAutoPauseChange = { autoPause -> viewModel.setPomodoroAutoPause(autoPause) },
                    onEnableDndChange = { enable -> viewModel.setPomodoroEnableDnd(enable) },
                    onOpenDndSettings = { viewModel.openDndSettings() },
                    onDismiss = { viewModel.closePomodoro() }
                )
            }

            // Initial Setup Dialog / Settings Modal
            if (showSetupDialog) {
                SetupScreen(
                    credentials = credentials,
                    onSave = { clientId, secret, token ->
                        viewModel.saveCredentials(clientId, secret, token)
                    },
                    onStartOAuth = { clientId ->
                        viewModel.startOAuthLogin(clientId)
                    },
                    onTestConnection = { clientId, secret, token ->
                        viewModel.testConnection(clientId, secret, token)
                    },
                    onDismiss = { viewModel.closeSetup() }
                )
            }
        }
    }
}
