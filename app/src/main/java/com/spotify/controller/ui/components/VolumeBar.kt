package com.spotify.controller.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Pixel-perfect Minimalist Volume Bar.
 * The dot and track share the exact same canvas coordinates.
 */
@Composable
fun VolumeBar(
    volumePercent: Int,
    onVolumeChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(volumePercent / 100f) }
    var previousNonZeroVolume by remember { mutableIntStateOf(if (volumePercent > 0) volumePercent else 50) }

    val currentFraction = if (isDragging) dragFraction else (volumePercent / 100f).coerceIn(0f, 1f)
    val displayPercent = (currentFraction * 100).toInt()

    val volumeIcon = when {
        displayPercent == 0 -> Icons.AutoMirrored.Rounded.VolumeMute
        displayPercent < 50 -> Icons.AutoMirrored.Rounded.VolumeDown
        else -> Icons.AutoMirrored.Rounded.VolumeUp
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                if (displayPercent > 0) {
                    previousNonZeroVolume = displayPercent
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

        // Pixel-perfect Volume Scrubber Box
        Box(
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (size.width > 0) {
                            val newFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            onVolumeChange((newFraction * 100).toInt())
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (size.width > 0) {
                                isDragging = true
                                dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            onVolumeChange((dragFraction * 100).toInt())
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            if (size.width > 0) {
                                dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val trackY = size.height / 2f
                val strokePx = 3.dp.toPx()
                val thumbRadiusPx = if (isDragging) 5.5.dp.toPx() else 4.dp.toPx()
                val currentX = (size.width * currentFraction).coerceIn(0f, size.width)

                // 1. Inactive background track
                drawLine(
                    color = Color.White.copy(alpha = 0.18f),
                    start = Offset(0f, trackY),
                    end = Offset(size.width, trackY),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )

                // 2. Active foreground track
                if (currentX > 0f) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.85f),
                        start = Offset(0f, trackY),
                        end = Offset(currentX, trackY),
                        strokeWidth = strokePx,
                        cap = StrokeCap.Round
                    )
                }

                // 3. Thumb Dot
                drawCircle(
                    color = Color.White,
                    radius = thumbRadiusPx,
                    center = Offset(currentX, trackY)
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        IconButton(
            onClick = {
                val bumped = (displayPercent + 10).coerceAtMost(100)
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
