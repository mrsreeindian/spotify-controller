package com.spotify.controller.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

@Composable
fun LandscapePlayerLayout(
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

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Pane (50%): Album Art or Spicy Lyrics
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(
                targetState = showLyrics,
                animationSpec = tween(400),
                label = "landscapeCrossfade"
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
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        AlbumArtCard(
                            albumArtUrl = track?.albumArtUrl,
                            trackTitle = track?.name ?: "",
                            onTap = onToggleLyrics
                        )
                    }
                }
            }
        }

        // Right Pane (50%): Metadata, Controls, Progress, Volume
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Device Pill
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

                // Actions: Toggle Lyrics, Orientation Button, Settings
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(
                        onClick = onToggleLyrics,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lyrics,
                            contentDescription = "Lyrics",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showLyrics) "Cover" else "Lyrics",
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Button to change from landscape to portrait mode
                    IconButton(
                        onClick = onToggleOrientation,
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ScreenRotation,
                            contentDescription = "Switch to Portrait Mode",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Setup",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Track Title, Artist, Album, Like
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

            // Progress Bar
            ProgressBar(
                isPlaying = playbackState.isPlaying,
                progressMs = playbackState.progressMs,
                lastSyncTimestamp = playbackState.lastSyncTimestamp,
                durationMs = track?.durationMs ?: 0L,
                onSeek = onSeek
            )

            // M3 Tonal Spot Controls
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

            // Volume Bar
            VolumeBar(
                volumePercent = playbackState.volumePercent,
                onVolumeChange = onVolumeChange
            )
        }
    }
}
