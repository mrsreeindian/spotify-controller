package com.spotify.controller.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import com.spotify.controller.data.model.Lyrics
import com.spotify.controller.data.model.PlaybackState
import com.spotify.controller.data.model.PomodoroState
import com.spotify.controller.ui.components.AlbumArtCard
import com.spotify.controller.ui.components.LikeButton
import com.spotify.controller.ui.components.PlayerControls
import com.spotify.controller.ui.components.PomodoroTopBarPill
import com.spotify.controller.ui.components.ProgressBar
import com.spotify.controller.ui.components.SpicyLyricsView
import com.spotify.controller.ui.components.VolumeBar
import com.spotify.controller.ui.theme.LocalAlbumArtColors

/**
 * Minimalist, uncluttered Portrait Player Layout.
 * Features generous breathing room, refined typography, and ultra-sleek controls.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PortraitPlayerLayout(
    playbackState: PlaybackState,
    lyrics: Lyrics,
    isLyricsLoading: Boolean,
    showLyrics: Boolean,
    pomodoroState: PomodoroState,
    onOpenPomodoro: () -> Unit,
    onToggleLyrics: () -> Unit,
    onCloseLyrics: () -> Unit,
    onToggleOrientation: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onLikeToggle: () -> Unit,
    onRetryLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val albumColors = LocalAlbumArtColors.current
    val track = playbackState.track

    val layoutDirection = LocalLayoutDirection.current
    val cutoutStart = WindowInsets.displayCutout.asPaddingValues().calculateStartPadding(layoutDirection)
    val safeHorizontalPadding = max(cutoutStart, 24.dp)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = safeHorizontalPadding,
                end = 24.dp,
                top = 16.dp,
                bottom = 20.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Minimal Top Bar: Discreet Device Text & Clean Action Icons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Subtle Device Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Devices,
                    contentDescription = "Device",
                    tint = albumColors.tonalPrimary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = playbackState.deviceName ?: "Spotify Connect",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

            // Minimalist Action Icons
            Row(verticalAlignment = Alignment.CenterVertically) {
                PomodoroTopBarPill(
                    state = pomodoroState,
                    onClick = onOpenPomodoro
                )

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = onToggleOrientation,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ScreenRotation,
                        contentDescription = "Switch to Landscape Mode",
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(19.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Spotify Setup",
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        // Center Area: Album Art or Full-Screen Spicy Lyrics
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(
                targetState = showLyrics,
                animationSpec = tween(350),
                label = "artLyricsCrossfade"
            ) { lyricsVisible ->
                if (lyricsVisible) {
                    SpicyLyricsView(
                        lyrics = lyrics,
                        isLoading = isLyricsLoading,
                        isPlaying = playbackState.isPlaying,
                        progressMs = playbackState.progressMs,
                        lastSyncTimestamp = playbackState.lastSyncTimestamp,
                        durationMs = track?.durationMs ?: 0L,
                        trackTitle = track?.name ?: "Unknown Title",
                        artistName = track?.artistNames ?: "Unknown Artist",
                        onSeekTo = onSeek,
                        onRetry = onRetryLyrics,
                        onClose = onCloseLyrics
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Pristine Album Art Card
                        AlbumArtCard(
                            albumArtUrl = track?.albumArtUrl,
                            trackTitle = track?.name ?: "",
                            onTap = onToggleLyrics,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Compact, Minimal Track Metadata
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track?.name ?: "No Track Playing",
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val subtitleText = when {
                                    track != null && !track.albumName.isNullOrBlank() ->
                                        "${track.artistNames} • ${track.albumName}"
                                    track != null -> track.artistNames
                                    else -> "Connect Spotify to start"
                                }
                                Text(
                                    text = subtitleText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = Color.White.copy(alpha = 0.65f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            LikeButton(
                                isLiked = track?.isLiked == true,
                                onLikeToggle = onLikeToggle
                            )
                        }
                    }
                }
            }
        }

        // Bottom Section: Sleek Scrubber, Minimal Controls, Volume Bar
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ProgressBar(
                isPlaying = playbackState.isPlaying,
                progressMs = playbackState.progressMs,
                lastSyncTimestamp = playbackState.lastSyncTimestamp,
                durationMs = track?.durationMs ?: 0L,
                onSeek = onSeek
            )

            Spacer(modifier = Modifier.height(10.dp))

            PlayerControls(
                isPlaying = playbackState.isPlaying,
                isShuffle = playbackState.isShuffle,
                repeatMode = playbackState.repeatMode,
                isLiked = track?.isLiked == true,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onShuffleToggle = onShuffleToggle,
                onRepeatToggle = onRepeatToggle,
                onLikeToggle = onLikeToggle
            )

            Spacer(modifier = Modifier.height(10.dp))

            VolumeBar(
                volumePercent = playbackState.volumePercent,
                onVolumeChange = onVolumeChange
            )
        }
    }
}
