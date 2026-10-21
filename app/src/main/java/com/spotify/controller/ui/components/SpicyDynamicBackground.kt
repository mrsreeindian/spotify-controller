package com.spotify.controller.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of Spikerko/spicy-lyrics Dynamic Background (dynamicBackground.ts & CanvasImmersive.ts).
 * Renders organic, floating, fluid color orbs extracted from the album art that subtly drift,
 * pulse, and blend into a deep dark backdrop.
 */
@Composable
fun SpicyDynamicBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val albumColors = LocalAlbumArtColors.current

    val infiniteTransition = rememberInfiniteTransition(label = "spicyBgTransition")

    // Slow organic sinusoidal motion phases
    val phaseA by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phaseA"
    )

    val phaseB by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 31000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phaseB"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val dominantColor = albumColors.dominant
    val vibrantColor = albumColors.vibrant
    val darkMutedColor = albumColors.darkMuted
    val lightVibrantColor = albumColors.lightVibrant
    val baseBlack = Color(0xFF090A0C)

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val maxDim = maxOf(width, height)

            // Deep background fill
            drawRect(color = baseBlack)

            // Orb 1 (Dominant color): Drifts organically around top-center
            val orb1X = width * 0.5f + (cos(phaseA.toDouble()) * width * 0.22f).toFloat()
            val orb1Y = height * 0.25f + (sin(phaseA.toDouble()) * height * 0.15f).toFloat()
            val orb1Radius = maxDim * 0.70f * pulseScale
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        dominantColor.copy(alpha = 0.55f),
                        dominantColor.copy(alpha = 0.20f),
                        Color.Transparent
                    ),
                    center = Offset(orb1X, orb1Y),
                    radius = orb1Radius
                ),
                center = Offset(orb1X, orb1Y),
                radius = orb1Radius
            )

            // Orb 2 (Vibrant/Accent color): Drifts around bottom-right
            val orb2X = width * 0.75f + (sin(phaseB.toDouble()) * width * 0.20f).toFloat()
            val orb2Y = height * 0.65f + (cos(phaseB.toDouble()) * height * 0.18f).toFloat()
            val orb2Radius = maxDim * 0.65f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        vibrantColor.copy(alpha = 0.40f),
                        vibrantColor.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = Offset(orb2X, orb2Y),
                    radius = orb2Radius
                ),
                center = Offset(orb2X, orb2Y),
                radius = orb2Radius
            )

            // Orb 3 (Light vibrant highlight): Subtly drifts top-left
            val orb3X = width * 0.20f + (cos((phaseB * 0.8f).toDouble()) * width * 0.15f).toFloat()
            val orb3Y = height * 0.40f + (sin((phaseA * 0.7f).toDouble()) * height * 0.12f).toFloat()
            val orb3Radius = maxDim * 0.50f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        lightVibrantColor.copy(alpha = 0.25f),
                        Color.Transparent
                    ),
                    center = Offset(orb3X, orb3Y),
                    radius = orb3Radius
                ),
                center = Offset(orb3X, orb3Y),
                radius = orb3Radius
            )

            // Linear downward deep falloff scrim for perfect readability
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        baseBlack.copy(alpha = 0.25f),
                        darkMutedColor.copy(alpha = 0.45f),
                        baseBlack.copy(alpha = 0.85f),
                        baseBlack.copy(alpha = 0.98f)
                    ),
                    startY = 0f,
                    endY = height
                )
            )
        }

        content()
    }
}
