package com.spotify.controller.ui.theme

import android.graphics.Bitmap
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette

data class AlbumArtColors(
    val dominant: Color,
    val vibrant: Color,
    val darkMuted: Color,
    val lightVibrant: Color,
    val tonalPrimary: Color,
    val tonalOnPrimary: Color,
    val tonalPrimaryContainer: Color,
    val tonalOnPrimaryContainer: Color,
    val tonalSecondary: Color,
    val tonalSecondaryContainer: Color,
    val tonalOnSecondaryContainer: Color,
    val gradientStops: List<Color>,
    val colorScheme: ColorScheme
) {
    companion object {
        val DEFAULT = AlbumArtColors(
            dominant = SpotifyGreen,
            vibrant = SpotifyGreen,
            darkMuted = Color(0xFF141915),
            lightVibrant = Color(0xFF70F891),
            tonalPrimary = DefaultPrimary,
            tonalOnPrimary = DefaultOnPrimary,
            tonalPrimaryContainer = DefaultPrimaryContainer,
            tonalOnPrimaryContainer = DefaultOnPrimaryContainer,
            tonalSecondary = DefaultSecondary,
            tonalSecondaryContainer = DefaultSecondaryContainer,
            tonalOnSecondaryContainer = DefaultOnSecondaryContainer,
            gradientStops = listOf(
                SpotifyGreen.copy(alpha = 0.45f),
                Color(0xFF16251B),
                Color(0xFF0D0F0E),
                Color(0xFF080908)
            ),
            colorScheme = darkColorScheme(
                primary = DefaultPrimary,
                onPrimary = DefaultOnPrimary,
                primaryContainer = DefaultPrimaryContainer,
                onPrimaryContainer = DefaultOnPrimaryContainer,
                secondary = DefaultSecondary,
                onSecondary = DefaultOnSecondary,
                secondaryContainer = DefaultSecondaryContainer,
                onSecondaryContainer = DefaultOnSecondaryContainer,
                background = DefaultBackground,
                onBackground = DefaultOnBackground,
                surface = DefaultSurface,
                onSurface = DefaultOnSurface,
                surfaceVariant = DefaultSurfaceVariant,
                onSurfaceVariant = DefaultOnSurfaceVariant
            )
        )
    }
}

object PaletteExtractor {

    /**
     * Extracts Palette swatches from an Android Bitmap and synthesizes
     * Material 3 Tonal Spot color scheme + album art gradient colors.
     */
    fun extract(bitmap: Bitmap): AlbumArtColors {
        val palette = Palette.from(bitmap).generate()

        val dominantInt = palette.getDominantColor(SpotifyGreen.toArgb())
        val vibrantInt = palette.getVibrantColor(dominantInt)
        val darkMutedInt = palette.getDarkMutedColor(Color(0xFF121413).toArgb())
        val lightVibrantInt = palette.getLightVibrantColor(dominantInt)

        // Seed HSL
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(vibrantInt, hsl)

        // Generate M3 Tonal Spot Colors:
        // Tone 80 for dark theme primary (accessible contrast on dark surfaces)
        val primaryTone80Int = toneColor(hsl, 0.80f)
        val onPrimaryTone20Int = toneColor(hsl, 0.20f)
        val primaryContainerTone30Int = toneColor(hsl, 0.30f)
        val onPrimaryContainerTone90Int = toneColor(hsl, 0.90f)

        // Secondary has slightly reduced chroma/saturation
        val secondaryHsl = floatArrayOf(hsl[0], hsl[1] * 0.45f, 0.70f)
        val secondaryTone70Int = ColorUtils.HSLToColor(secondaryHsl)
        val secondaryContainerHsl = floatArrayOf(hsl[0], hsl[1] * 0.40f, 0.25f)
        val secondaryContainerTone25Int = ColorUtils.HSLToColor(secondaryContainerHsl)
        val onSecondaryContainerTone90Int = toneColor(secondaryHsl, 0.90f)

        // Tertiary has hue rotated by ~60 degrees
        val tertiaryHue = (hsl[0] + 60f) % 360f
        val tertiaryHsl = floatArrayOf(tertiaryHue, hsl[1] * 0.5f, 0.75f)
        val tertiaryTone75Int = ColorUtils.HSLToColor(tertiaryHsl)
        val tertiaryContainerHsl = floatArrayOf(tertiaryHue, hsl[1] * 0.4f, 0.30f)
        val tertiaryContainerInt = ColorUtils.HSLToColor(tertiaryContainerHsl)

        // Deep surface tinted with dark muted hue
        val surfaceHsl = floatArrayOf(hsl[0], (hsl[1] * 0.15f).coerceAtMost(0.20f), 0.08f)
        val surfaceInt = ColorUtils.HSLToColor(surfaceHsl)
        val surfaceVariantHsl = floatArrayOf(hsl[0], (hsl[1] * 0.20f).coerceAtMost(0.25f), 0.15f)
        val surfaceVariantInt = ColorUtils.HSLToColor(surfaceVariantHsl)

        val dominantColor = Color(dominantInt)
        val vibrantColor = Color(vibrantInt)
        val darkMutedColor = Color(darkMutedInt)
        val lightVibrantColor = Color(lightVibrantInt)

        val tonalPrimary = Color(primaryTone80Int)
        val tonalOnPrimary = Color(onPrimaryTone20Int)
        val tonalPrimaryContainer = Color(primaryContainerTone30Int)
        val tonalOnPrimaryContainer = Color(onPrimaryContainerTone90Int)

        val tonalSecondary = Color(secondaryTone70Int)
        val tonalSecondaryContainer = Color(secondaryContainerTone25Int)
        val tonalOnSecondaryContainer = Color(onSecondaryContainerTone90Int)

        val dynamicColorScheme = darkColorScheme(
            primary = tonalPrimary,
            onPrimary = tonalOnPrimary,
            primaryContainer = tonalPrimaryContainer,
            onPrimaryContainer = tonalOnPrimaryContainer,
            secondary = tonalSecondary,
            onSecondary = Color(0xFF141F17),
            secondaryContainer = tonalSecondaryContainer,
            onSecondaryContainer = tonalOnSecondaryContainer,
            tertiary = Color(tertiaryTone75Int),
            onTertiary = Color(0xFF102830),
            tertiaryContainer = Color(tertiaryContainerInt),
            background = Color(0xFF090A0A),
            onBackground = Color(0xFFECEFF1),
            surface = Color(surfaceInt),
            onSurface = Color(0xFFECEFF1),
            surfaceVariant = Color(surfaceVariantInt),
            onSurfaceVariant = Color(0xFFC7CDC9)
        )

        // Dynamic multi-stop gradient derived from album art
        val gradientStops = listOf(
            dominantColor.copy(alpha = 0.55f),
            vibrantColor.copy(alpha = 0.35f),
            darkMutedColor.copy(alpha = 0.70f),
            Color(0xFF08090A)
        )

        return AlbumArtColors(
            dominant = dominantColor,
            vibrant = vibrantColor,
            darkMuted = darkMutedColor,
            lightVibrant = lightVibrantColor,
            tonalPrimary = tonalPrimary,
            tonalOnPrimary = tonalOnPrimary,
            tonalPrimaryContainer = tonalPrimaryContainer,
            tonalOnPrimaryContainer = tonalOnPrimaryContainer,
            tonalSecondary = tonalSecondary,
            tonalSecondaryContainer = tonalSecondaryContainer,
            tonalOnSecondaryContainer = tonalOnSecondaryContainer,
            gradientStops = gradientStops,
            colorScheme = dynamicColorScheme
        )
    }

    private fun toneColor(sourceHsl: FloatArray, targetLightness: Float): Int {
        val outHsl = floatArrayOf(
            sourceHsl[0],
            (sourceHsl[1] * 0.85f).coerceIn(0.2f, 0.95f),
            targetLightness.coerceIn(0.05f, 0.95f)
        )
        return ColorUtils.HSLToColor(outHsl)
    }
}
