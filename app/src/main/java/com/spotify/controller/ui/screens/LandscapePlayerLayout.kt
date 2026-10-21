package com.spotify.controller.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
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

/**
 * Minimalist Landscape Player Layout with camera notch/cutout safety.
 */
@OptIn(ExperimentalFoundationApi::class)
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
    onRetryLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val albumColors = LocalAlbumArtColors.current
    val track = playbackState.track

    val layoutDirection = LocalLayoutDirection.current
    val cutoutStart = WindowInsets.displayCutout.asPaddingValues().calculateStartPadding(layoutDirection)
    val safeStart = max(cutoutStart, 24.dp)

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = safeStart,
                end = 24.dp,
                top = 16.dp,
                bottom = 16.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Pane (50%): Album Art or Full-Height Spicy Lyrics
        Box(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(
                targetState = showLyrics,
                animationSpec = tween(350),
                label = "landscapeCrossfade"
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
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
                            .padding(8.dp),
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

        // Right Pane (50%): Metadata, Controls, Scrubber, Volume
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Minimal Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Device Name
                Row(verticalAlignment = Alignment.CenterVertically) {
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

                // Minimal Action Icons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(
                        onClick = onToggleLyrics,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lyrics,
                            contentDescription = "Lyrics",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showLyrics) "Cover" else "Lyrics",
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = onToggleOrientation,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ScreenRotation,
                            contentDescription = "Switch to Portrait Mode",
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Setup",
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Compact Track Metadata Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track?.name ?: "No Track Playing",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
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
                        modifier = Modifier.basicMarquee()
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

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

            // Minimal Controls
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
