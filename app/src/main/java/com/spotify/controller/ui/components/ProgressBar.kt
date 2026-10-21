package com.spotify.controller.ui.components

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotify.controller.ui.theme.LocalAlbumArtColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

/**
 * Pixel-perfect Media Progress Scrubber.
 * The dot and track share the exact same canvas coordinates, guaranteed never to be out of place.
 */
@Composable
fun ProgressBar(
    isPlaying: Boolean,
    progressMs: Long,
    lastSyncTimestamp: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val albumColors = LocalAlbumArtColors.current
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    // Smooth monotonic clock: avoids 1-second background polling hiccups
    var currentBaseProgress by remember { mutableLongStateOf(progressMs) }
    var currentBaseTimestamp by remember { mutableLongStateOf(lastSyncTimestamp) }
    var interpolatedProgressMs by remember { mutableLongStateOf(progressMs) }

    LaunchedEffect(isPlaying, progressMs, lastSyncTimestamp) {
        val currentPredicted = currentBaseProgress + (SystemClock.elapsedRealtime() - currentBaseTimestamp)
        val diff = kotlin.math.abs(progressMs - currentPredicted)
        if (diff > 400L || !isPlaying || currentBaseTimestamp == 0L) {
            currentBaseProgress = progressMs
            currentBaseTimestamp = lastSyncTimestamp
        }

        if (isPlaying && currentBaseTimestamp > 0L) {
            while (isActive) {
                val elapsedSinceSync = SystemClock.elapsedRealtime() - currentBaseTimestamp
                val current = (currentBaseProgress + elapsedSinceSync).coerceIn(0L, durationMs.coerceAtLeast(1L))
                interpolatedProgressMs = current
                delay(50L) // 20fps clock
            }
        } else {
            interpolatedProgressMs = progressMs
        }
    }

    val displayProgressMs = if (isDragging) {
        (dragFraction * durationMs).toLong()
    } else {
        interpolatedProgressMs
    }

    val progressFraction = if (durationMs > 0) {
        (displayProgressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Column(modifier = modifier.fillMaxWidth()) {
        // Pixel-perfect Canvas Scrubber
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        if (size.width > 0 && durationMs > 0) {
                            val newFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            val targetMs = (newFraction * durationMs).toLong()
                            onSeek(targetMs)
                        }
                    }
                }
                .pointerInput(durationMs) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (size.width > 0) {
                                isDragging = true
                                dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            val targetMs = (dragFraction * durationMs).toLong()
                            onSeek(targetMs)
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
                val thumbRadiusPx = if (isDragging) 6.5.dp.toPx() else 4.5.dp.toPx()
                val currentX = (size.width * progressFraction).coerceIn(0f, size.width)

                // 1. Inactive background track
                drawLine(
                    color = Color.White.copy(alpha = 0.2f),
                    start = Offset(0f, trackY),
                    end = Offset(size.width, trackY),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )

                // 2. Active foreground track
                if (currentX > 0f) {
                    drawLine(
                        color = albumColors.tonalPrimary,
                        start = Offset(0f, trackY),
                        end = Offset(currentX, trackY),
                        strokeWidth = strokePx,
                        cap = StrokeCap.Round
                    )
                }

                // 3. Thumb Dot: centered exactly at (currentX, trackY)
                drawCircle(
                    color = albumColors.tonalPrimary,
                    radius = thumbRadiusPx,
                    center = Offset(currentX, trackY)
                )
                // Crisp white inner core for thumb
                drawCircle(
                    color = Color.White,
                    radius = thumbRadiusPx * 0.60f,
                    center = Offset(currentX, trackY)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(displayProgressMs),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                color = Color.White.copy(alpha = 0.55f)
            )
            Text(
                text = formatDuration(durationMs),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                color = Color.White.copy(alpha = 0.55f)
            )
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
