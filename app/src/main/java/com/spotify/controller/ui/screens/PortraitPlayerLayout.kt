package com.spotify.controller.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotify.controller.data.model.Lyrics
import com.spotify.controller.data.model.PlaybackState
import com.spotify.controller.ui.components.AlbumArtCard
import com.spotify.controller.ui.components.LikeButton
import com.spotify.controller.ui.components.PlayerControls
import com.spotify.controller.ui.components.ProgressBar
import com.spotify.controller.ui.components.SpicyLyricsView
import com.spotify.controller.ui.components.VolumeBar
import com.spotify.controller.ui.theme.LocalAlbumArtColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PortraitPlayerLayout(
    playbackState: PlaybackState,
    lyrics: Lyrics,
    isLyricsLoading: Boolean,
    showLyrics: Boolean,
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
    modifier: Modifier = Modifier
) {
    val albumColors = LocalAlbumArtColors.current
    val track = playbackState.track

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top App Bar: Device indicator, Orientation Toggle, Settings
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Active Device Pill
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Devices,
                        contentDescription = "Device",
                        tint = albumColors.tonalPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = playbackState.deviceName ?: "Spotify Connect",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Right Action Buttons: Orientation & Settings
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Button to change between Landscape and Portrait mode
                IconButton(
                    onClick = onToggleOrientation,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ScreenRotation,
                        contentDescription = "Switch to Landscape Mode",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Spotify Setup",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Center Area: Crossfade between Album Art and Spicy Lyrics
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(
                targetState = showLyrics,
                animationSpec = tween(400),
                label = "artLyricsCrossfade"
            ) { lyricsVisible ->
                if (lyricsVisible) {
                    SpicyLyricsView(
                        lyrics = lyrics,
                        isLoading = isLyricsLoading,
                        currentPlaybackMs = playbackState.getInterpolatedProgress(),
                        trackTitle = track?.name ?: "Unknown Title",
                        artistName = track?.artistNames ?: "Unknown Artist",
                        onSeekTo = onSeek,
                        onClose = onCloseLyrics
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AlbumArtCard(
                            albumArtUrl = track?.albumArtUrl,
                            trackTitle = track?.name ?: "",
                            onTap = onToggleLyrics,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Track Metadata: Title, Artist, Album, Like
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track?.name ?: "No Track Playing",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    modifier = Modifier.basicMarquee()
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = track?.artistNames ?: "Connect Spotify to start",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                                if (!track?.albumName.isNullOrBlank()) {
                                    Text(
                                        text = track.albumName,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        maxLines = 1
                                    )
                                }
                            }

                            LikeButton(
                                isLiked = track?.isLiked == true,
                                onLikeToggle = onLikeToggle
                            )
                        }
                    }
                }
            }
        }

        // Bottom Section: Progress Bar, M3 Tonal Spot Controls, Volume Bar
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

            Spacer(modifier = Modifier.height(8.dp))

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

            Spacer(modifier = Modifier.height(12.dp))

            VolumeBar(
                volumePercent = playbackState.volumePercent,
                onVolumeChange = onVolumeChange
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
