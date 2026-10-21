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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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

/**
 * Minimalist playback control bar with Material 3 tonal spot theming.
 */
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
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Shuffle Button (Minimalist borderless)
        IconButton(
            onClick = onShuffleToggle,
            modifier = Modifier.size(38.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = "Shuffle",
                tint = if (isShuffle) albumColors.tonalPrimary else Color.White.copy(alpha = 0.45f),
                modifier = Modifier.size(20.dp)
            )
        }

        // Previous Track Button (Refined 44dp tonal circle)
        FilledTonalIconButton(
            onClick = onPrevious,
            modifier = Modifier.size(44.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = Color.White.copy(alpha = 0.12f),
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Previous Track",
                modifier = Modifier.size(24.dp)
            )
        }

        // Play / Pause Primary Button (Proportional 58dp M3 Primary FAB)
        FilledIconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(58.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = albumColors.tonalPrimary,
                contentColor = albumColors.tonalOnPrimary
            )
        ) {
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = {
                    fadeIn(animationSpec = tween(140)) togetherWith fadeOut(animationSpec = tween(140))
                },
                label = "playPauseAnimation"
            ) { playing ->
                Icon(
                    imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        // Next Track Button (Refined 44dp tonal circle)
        FilledTonalIconButton(
            onClick = onNext,
            modifier = Modifier.size(44.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = Color.White.copy(alpha = 0.12f),
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Next Track",
                modifier = Modifier.size(24.dp)
            )
        }

        // Repeat Mode Button (Minimalist borderless)
        val repeatIcon = when (repeatMode) {
            RepeatMode.TRACK -> Icons.Rounded.RepeatOne
            else -> Icons.Rounded.Repeat
        }
        val isRepeatActive = repeatMode != RepeatMode.OFF

        IconButton(
            onClick = onRepeatToggle,
            modifier = Modifier.size(38.dp)
        ) {
            Icon(
                imageVector = repeatIcon,
                contentDescription = "Repeat: $repeatMode",
                tint = if (isRepeatActive) albumColors.tonalPrimary else Color.White.copy(alpha = 0.45f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Minimalist, spring-animated Like Button.
 */
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
            Color.White.copy(alpha = 0.55f)
        },
        animationSpec = tween(180),
        label = "heartColor"
    )

    IconButton(
        onClick = onLikeToggle,
        modifier = modifier
            .size(36.dp)
            .scale(heartScale)
    ) {
        Icon(
            imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = if (isLiked) "Unlike" else "Like",
            tint = tintColor,
            modifier = Modifier.size(22.dp)
        )
    }
}
