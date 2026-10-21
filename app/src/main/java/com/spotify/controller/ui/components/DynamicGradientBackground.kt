package com.spotify.controller.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.spotify.controller.ui.theme.LocalAlbumArtColors

@Composable
fun DynamicGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val albumColors = LocalAlbumArtColors.current

    // Smoothly animate colors when album changes
    val topColor by animateColorAsState(
        targetValue = albumColors.dominant.copy(alpha = 0.50f),
        animationSpec = tween(durationMillis = 800),
        label = "bgTopColor"
    )
    val midColor by animateColorAsState(
        targetValue = albumColors.vibrant.copy(alpha = 0.30f),
        animationSpec = tween(durationMillis = 800),
        label = "bgMidColor"
    )
    val darkMutedColor by animateColorAsState(
        targetValue = albumColors.darkMuted.copy(alpha = 0.75f),
        animationSpec = tween(durationMillis = 800),
        label = "bgDarkColor"
    )
    val baseBlack = Color(0xFF090A0C)

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Radial glow centered around top-middle
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(topColor, midColor, Color.Transparent),
                    center = Offset(width * 0.5f, height * 0.25f),
                    radius = width.coerceAtLeast(height) * 0.75f
                )
            )

            // Linear downward deep falloff gradient
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        darkMutedColor.copy(alpha = 0.4f),
                        baseBlack.copy(alpha = 0.85f),
                        baseBlack
                    ),
                    startY = 0f,
                    endY = height
                )
            )
        }

        content()
    }
}
