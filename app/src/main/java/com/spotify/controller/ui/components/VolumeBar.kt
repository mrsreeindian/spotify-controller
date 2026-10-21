package com.spotify.controller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.spotify.controller.ui.theme.LocalAlbumArtColors

/**
 * Minimalist, ultra-sleek volume bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeBar(
    volumePercent: Int,
    onVolumeChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val albumColors = LocalAlbumArtColors.current
    var isDragging by remember { mutableStateOf(false) }
    var dragVolume by remember { mutableFloatStateOf(volumePercent.toFloat()) }
    var previousNonZeroVolume by remember { mutableIntStateOf(if (volumePercent > 0) volumePercent else 50) }

    val currentVolume = if (isDragging) dragVolume.toInt() else volumePercent

    val volumeIcon = when {
        currentVolume == 0 -> Icons.AutoMirrored.Rounded.VolumeMute
        currentVolume < 50 -> Icons.AutoMirrored.Rounded.VolumeDown
        else -> Icons.AutoMirrored.Rounded.VolumeUp
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Minimal mute / toggle button
        IconButton(
            onClick = {
                if (currentVolume > 0) {
                    previousNonZeroVolume = currentVolume
                    onVolumeChange(0)
                } else {
                    onVolumeChange(previousNonZeroVolume)
                }
            },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = volumeIcon,
                contentDescription = "Mute / Unmute",
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Slim volume slider
        Slider(
            value = (currentVolume / 100f).coerceIn(0f, 1f),
            onValueChange = { frac ->
                isDragging = true
                dragVolume = frac * 100f
            },
            onValueChangeFinished = {
                isDragging = false
                onVolumeChange(dragVolume.toInt())
            },
            interactionSource = remember { MutableInteractionSource() },
            thumb = {
                Box(
                    modifier = Modifier
                        .size(if (isDragging) 10.dp else 7.dp)
                        .background(Color.White, CircleShape)
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(3.dp),
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color.White.copy(alpha = 0.85f),
                        inactiveTrackColor = Color.White.copy(alpha = 0.18f)
                    )
                )
            },
            modifier = Modifier
                .weight(1f)
                .height(20.dp)
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Subtle volume up step button
        IconButton(
            onClick = {
                val bumped = (currentVolume + 10).coerceAtMost(100)
                onVolumeChange(bumped)
            },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = "Volume Up",
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
