package com.spotify.controller.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotify.controller.data.model.LyricLine
import com.spotify.controller.data.model.Lyrics
import com.spotify.controller.data.model.WordTiming
import com.spotify.controller.ui.theme.LocalAlbumArtColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Port of Spikerko/spicy-lyrics for Android.
 * Features:
 * - Real-time continuous 40ms client-side ticker (25fps)
 * - Syllable/word-by-word progressive karaoke highlighting with spring scale & text bloom
 * - Auto-centering auto-scroll with manual drag override & "Sync" button
 * - Animated vocal lead-in interlude bouncing dots (• • •)
 * - Tap-to-seek to any lyric line or word
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
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

    // Real-time client-side millisecond ticker for ultra-smooth 25fps lyric synchronization
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

    // Detect user manual scrolling vs auto-scrolling
    var userScrolled by remember { mutableStateOf(false) }
    var lastUserScrollTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            userScrolled = true
            lastUserScrollTime = SystemClock.elapsedRealtime()
        }
    }

    // Reset user scroll after 2.5 seconds of inactivity (Spicy Lyrics behavior)
    LaunchedEffect(userScrolled, lastUserScrollTime) {
        if (userScrolled) {
            delay(2500L)
            userScrolled = false
        }
    }

    // Auto-scroll to center the active line
    LaunchedEffect(activeIndex, userScrolled) {
        if (!userScrolled && activeIndex in lyrics.lines.indices) {
            listState.animateScrollToItem(
                index = activeIndex,
                scrollOffset = -220
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header Bar
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Spicy Lyrics",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            if (lyrics.hasWordSync) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = albumColors.tonalPrimary.copy(alpha = 0.25f)
                                ) {
                                    Text(
                                        text = "KARAOKE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = albumColors.tonalPrimary,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "$trackTitle • $artistName",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1
                        )
                    }
                }

                // Close Button
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

            // Main Lyrics Scroll Area
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
                                modifier = Modifier.size(44.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Connecting to Spicy Lyrics engine...",
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
                                Text("Retry or Load Demo Track")
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 100.dp, bottom = 160.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            itemsIndexed(
                                items = lyrics.lines,
                                key = { _, line -> line.id }
                            ) { index, line ->
                                val isActive = index == activeIndex
                                if (line.isDotLine) {
                                    SpicyLeadInDots(isActive = isActive)
                                } else {
                                    SpicyLyricLine(
                                        line = line,
                                        isActive = isActive,
                                        currentPositionMs = liveProgressMs,
                                        glowColor = albumColors.tonalPrimary,
                                        onLineClick = { onSeekTo(line.startTimeMs) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating "Sync with playback" button when user scrolled away
        AnimatedVisibility(
            visible = userScrolled && activeIndex in lyrics.lines.indices,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            Surface(
                onClick = {
                    userScrolled = false
                },
                shape = RoundedCornerShape(24.dp),
                color = albumColors.tonalPrimary,
                contentColor = albumColors.tonalOnPrimary,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Sync",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Sync with playback",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Renders an individual lyric line with syllable word-by-word karaoke highlighting
 * or line-level spring scale & bloom.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpicyLyricLine(
    line: LyricLine,
    isActive: Boolean,
    currentPositionMs: Long,
    glowColor: Color,
    onLineClick: () -> Unit
) {
    val lineScale by animateFloatAsState(
        targetValue = if (isActive) 1.05f else 0.97f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "lineScale"
    )

    val lineAlpha by animateFloatAsState(
        targetValue = if (isActive) 1.0f else 0.32f,
        animationSpec = tween(durationMillis = 220),
        label = "lineAlpha"
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
        if (line.words.isNotEmpty() && isActive) {
            // Word-by-word syllable karaoke rendering
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                line.words.forEach { wordTiming ->
                    SpicyWordItem(
                        wordTiming = wordTiming,
                        currentPositionMs = currentPositionMs,
                        glowColor = glowColor
                    )
                }
            }
        } else {
            // Standard line-level text
            Text(
                text = line.text,
                fontSize = if (isActive) 26.sp else 21.sp,
                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = if (isActive) 34.sp else 29.sp,
                style = if (isActive) {
                    TextStyle(
                        shadow = Shadow(
                            color = Color.White.copy(alpha = 0.5f),
                            offset = Offset.Zero,
                            blurRadius = 14f
                        )
                    )
                } else TextStyle.Default
            )
        }
    }
}

/**
 * Renders an individual word / syllable with spring scale and progressive karaoke fill
 */
@Composable
fun SpicyWordItem(
    wordTiming: WordTiming,
    currentPositionMs: Long,
    glowColor: Color
) {
    val isWordActive = wordTiming.isActiveAt(currentPositionMs)
    val isWordPast = wordTiming.isPast(currentPositionMs)

    val wordScale by animateFloatAsState(
        targetValue = if (isWordActive) 1.12f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "wordScale"
    )

    val wordAlpha by animateFloatAsState(
        targetValue = when {
            isWordActive || isWordPast -> 1.0f
            else -> 0.40f
        },
        animationSpec = tween(durationMillis = 150),
        label = "wordAlpha"
    )

    Text(
        text = wordTiming.word,
        fontSize = 26.sp,
        fontWeight = FontWeight.ExtraBold,
        color = Color.White,
        modifier = Modifier
            .scale(wordScale)
            .alpha(wordAlpha),
        style = if (isWordActive) {
            TextStyle(
                shadow = Shadow(
                    color = Color.White.copy(alpha = 0.85f),
                    offset = Offset.Zero,
                    blurRadius = 16f
                )
            )
        } else TextStyle.Default
    )
}

/**
 * Port of Spicy Lyrics bouncing lead-in dots (• • •)
 */
@Composable
fun SpicyLeadInDots(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dotsTransition")

    val dot1Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )

    val dot2Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 120, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )

    val dot3Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 240, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isActive) 1.0f else 0.35f,
        animationSpec = tween(200),
        label = "dotsAlpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .offset(y = if (isActive) dot1Offset.dp else 0.dp)
                .size(10.dp)
                .scale(if (isActive) 1.2f else 1.0f),
            contentAlignment = Alignment.Center
        ) {
            Surface(shape = CircleShape, color = Color.White, modifier = Modifier.fillMaxSize()) {}
        }

        Box(
            modifier = Modifier
                .offset(y = if (isActive) dot2Offset.dp else 0.dp)
                .size(10.dp)
                .scale(if (isActive) 1.2f else 1.0f),
            contentAlignment = Alignment.Center
        ) {
            Surface(shape = CircleShape, color = Color.White, modifier = Modifier.fillMaxSize()) {}
        }

        Box(
            modifier = Modifier
                .offset(y = if (isActive) dot3Offset.dp else 0.dp)
                .size(10.dp)
                .scale(if (isActive) 1.2f else 1.0f),
            contentAlignment = Alignment.Center
        ) {
            Surface(shape = CircleShape, color = Color.White, modifier = Modifier.fillMaxSize()) {}
        }
    }
}
