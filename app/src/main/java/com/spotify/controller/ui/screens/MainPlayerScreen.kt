package com.spotify.controller.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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
