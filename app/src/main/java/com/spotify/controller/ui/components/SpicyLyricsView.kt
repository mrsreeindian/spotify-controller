package com.spotify.controller.ui.components

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotify.controller.data.model.LyricLine
import com.spotify.controller.data.model.Lyrics
import com.spotify.controller.ui.theme.LocalAlbumArtColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun SpicyLyricsView(
    lyrics: Lyrics,
    isLoading: Boolean,
    isPlaying: Boolean,
    progressMs: Long,
    lastSyncTimestamp: Long,
    durationMs: Long,
    trackTitle: String,
    artistName: String,
    onSeekTo: (Long) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val albumColors = LocalAlbumArtColors.current
    val listState = rememberLazyListState()

    // Smooth real-time client-side ticker (40ms / 25fps) to drive lyric synchronization
    var liveProgressMs by remember { mutableLongStateOf(progressMs) }
    LaunchedEffect(isPlaying, progressMs, lastSyncTimestamp) {
        liveProgressMs = progressMs
        if (isPlaying && lastSyncTimestamp > 0L) {
            while (isActive) {
                val elapsedSinceSync = SystemClock.elapsedRealtime() - lastSyncTimestamp
                val current = (progressMs + elapsedSinceSync).coerceIn(0L, durationMs.coerceAtLeast(1L))
                liveProgressMs = current
                delay(40L)
            }
        }
    }

    val activeIndex = remember(lyrics, liveProgressMs) {
        lyrics.getActiveLineIndex(liveProgressMs)
    }

    // Auto-scroll to center the active line with smooth physics
    LaunchedEffect(activeIndex) {
        if (activeIndex in lyrics.lines.indices && !listState.isScrollInProgress) {
            val targetOffset = -220 // Vertically centers the active lyric line
            listState.animateScrollToItem(
                index = activeIndex,
                scrollOffset = targetOffset
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Spicy Lyrics Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = albumColors.tonalSecondaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = "Live Lyrics",
                            tint = albumColors.tonalOnSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.size(10.dp))
                Column {
                    Text(
                        text = "Spicy Lyrics",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "$trackTitle • $artistName",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1
                    )
                }
            }

            // Return to Album Art Button
            FilledTonalIconButton(
                onClick = onClose,
                modifier = Modifier.size(38.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = albumColors.tonalSecondaryContainer,
                    contentColor = albumColors.tonalOnSecondaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close Lyrics",
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Lyrics Content Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = albumColors.tonalPrimary,
                            modifier = Modifier.size(42.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Syncing with Spicy Lyrics engine...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }

                lyrics.lines.isEmpty() -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = "No Lyrics",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (!lyrics.plainLyrics.isNullOrBlank()) {
                                lyrics.plainLyrics
                            } else {
                                "No synchronized lyrics found for \"$trackTitle\"."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        FilledTonalButton(
                            onClick = onRetry,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Retry",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry Search")
                        }
                    }
                }

                else -> {
                    // Port of Spicy Lyrics: List with active line bolding, scale, and tap-to-seek
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 100.dp, bottom = 140.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(
                            items = lyrics.lines,
                            key = { _, line -> line.id }
                        ) { index, line ->
                            val isActive = index == activeIndex
                            SpicyLyricLineItem(
                                line = line,
                                isActive = isActive,
                                onLineClick = { onSeekTo(line.startTimeMs) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpicyLyricLineItem(
    line: LyricLine,
    isActive: Boolean,
    onLineClick: () -> Unit
) {
    val lineScale by animateFloatAsState(
        targetValue = if (isActive) 1.04f else 0.98f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "lyricScale"
    )

    val lineAlpha by animateFloatAsState(
        targetValue = if (isActive) 1.0f else 0.35f,
        animationSpec = tween(durationMillis = 250),
        label = "lyricAlpha"
    )

    val textColor by animateColorAsState(
        targetValue = if (isActive) {
            Color.White
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 200),
        label = "lyricColor"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(lineScale)
            .alpha(lineAlpha)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onLineClick
            )
            .padding(vertical = 4.dp, horizontal = 8.dp)
    ) {
        Text(
            text = line.text,
            fontSize = if (isActive) 26.sp else 20.sp,
            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = textColor,
            lineHeight = if (isActive) 34.sp else 28.sp
        )
    }
}
