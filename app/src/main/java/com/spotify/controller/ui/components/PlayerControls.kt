package com.spotify.controller.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.spotify.controller.data.model.RepeatMode
import com.spotify.controller.ui.theme.LocalAlbumArtColors

@Composable
fun PlayerControls(
    isPlaying: Boolean,
    isShuffle: Boolean,
    repeatMode: RepeatMode,
    isLiked: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onLikeToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val albumColors = LocalAlbumArtColors.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Shuffle Button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(
                onClick = onShuffleToggle,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (isShuffle) {
                        albumColors.tonalPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                    modifier = Modifier.size(24.dp)
                )
            }
            if (isShuffle) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(albumColors.tonalPrimary, CircleShape)
                )
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        // Previous Track Button (M3 Tonal Spot container)
        FilledTonalIconButton(
            onClick = onPrevious,
            modifier = Modifier.size(54.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = albumColors.tonalSecondaryContainer,
                contentColor = albumColors.tonalOnSecondaryContainer
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Previous Track",
                modifier = Modifier.size(30.dp)
            )
        }

        // Play / Pause Primary Button (Prominent M3 Tonal Primary FAB)
        FilledIconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(72.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = albumColors.tonalPrimary,
                contentColor = albumColors.tonalOnPrimary
            )
        ) {
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = {
                    fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
                },
                label = "playPauseAnimation"
            ) { playing ->
                Icon(
                    imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // Next Track Button (M3 Tonal Spot container)
        FilledTonalIconButton(
            onClick = onNext,
            modifier = Modifier.size(54.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = albumColors.tonalSecondaryContainer,
                contentColor = albumColors.tonalOnSecondaryContainer
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Next Track",
                modifier = Modifier.size(30.dp)
            )
        }

        // Repeat Mode Button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val repeatIcon = when (repeatMode) {
                RepeatMode.TRACK -> Icons.Rounded.RepeatOne
                else -> Icons.Rounded.Repeat
            }
            val isRepeatActive = repeatMode != RepeatMode.OFF

            IconButton(
                onClick = onRepeatToggle,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = repeatIcon,
                    contentDescription = "Repeat: $repeatMode",
                    tint = if (isRepeatActive) {
                        albumColors.tonalPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                    modifier = Modifier.size(24.dp)
                )
            }
            if (isRepeatActive) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(albumColors.tonalPrimary, CircleShape)
                )
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
fun LikeButton(
    isLiked: Boolean,
    onLikeToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val albumColors = LocalAlbumArtColors.current

    val heartScale by animateFloatAsState(
        targetValue = if (isLiked) 1.15f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "heartScale"
    )

    val tintColor by animateColorAsState(
        targetValue = if (isLiked) {
            albumColors.tonalPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        },
        animationSpec = tween(200),
        label = "heartColor"
    )

    IconButton(
        onClick = onLikeToggle,
        modifier = modifier
            .size(44.dp)
            .scale(heartScale)
    ) {
        Icon(
            imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = if (isLiked) "Unlike" else "Like",
            tint = tintColor,
            modifier = Modifier.size(26.dp)
        )
    }
}
