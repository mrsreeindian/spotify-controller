package com.spotify.controller.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalAlbumArtColors = staticCompositionLocalOf { AlbumArtColors.DEFAULT }

@Composable
fun SpotifyControllerTheme(
    albumColors: AlbumArtColors? = null,
    content: @Composable () -> Unit
) {
    val activeColors = albumColors ?: AlbumArtColors.DEFAULT

    CompositionLocalProvider(LocalAlbumArtColors provides activeColors) {
        MaterialTheme(
            colorScheme = activeColors.colorScheme,
            typography = Typography,
            content = content
        )
    }
}
