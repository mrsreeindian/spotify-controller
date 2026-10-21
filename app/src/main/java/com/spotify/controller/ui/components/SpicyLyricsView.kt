package com.spotify.controller.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
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
 * - Smooth monotonic audio progress tracking without background poll jitter
 * - True word/syllable sync for tracks with word timestamps (enhanced LRC / TTML)
 * - Pure line-level sync for standard tracks, perfectly aligned with playback
 * - Natural, symmetrical omnidirectional text glow (no clipped or out-of-place shadows)
 * - Safe camera punch-hole clearance (lyrics shifted to the right)
 * - Lead-in vocal interlude bouncing dots (• • •)
 * - Auto-centering auto-scroll with manual drag override & "Sync" button
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

    // Camera punch-hole clearance: guarantees text starts safely to the right of any cutout
    val layoutDirection = LocalLayoutDirection.current
    val cutoutStart = WindowInsets.displayCutout.asPaddingValues().calculateStartPadding(layoutDirection)
    val safeStartPadding = max(cutoutStart + 24.dp, 56.dp)

    // Smooth monotonic clock: avoids 1-second background polling hiccups
    var currentBaseProgress by remember { mutableLongStateOf(progressMs) }
    var currentBaseTimestamp by remember { mutableLongStateOf(lastSyncTimestamp) }
    var liveProgressMs by remember { mutableLongStateOf(progressMs) }

    LaunchedEffect(isPlaying, progressMs, lastSyncTimestamp) {
        val currentPredicted = currentBaseProgress + (SystemClock.elapsedRealtime() - currentBaseTimestamp)
        val diff = kotlin.math.abs(progressMs - currentPredicted)
        if (diff > 400L || !isPlaying || currentBaseTimestamp == 0L) {
            currentBaseProgress = progressMs
            currentBaseTimestamp = if (lastSyncTimestamp > 0L) lastSyncTimestamp else SystemClock.elapsedRealtime()
        }

        if (isPlaying && currentBaseTimestamp > 0L) {
            while (isActive) {
                val elapsedSinceSync = SystemClock.elapsedRealtime() - currentBaseTimestamp
                val current = (currentBaseProgress + elapsedSinceSync).coerceIn(0L, durationMs.coerceAtLeast(1L))
                liveProgressMs = current
                delay(16L) // 60fps clock: fluid, buttery-smooth progressive karaoke sweep
            }
        } else {
            liveProgressMs = progressMs
        }
    }

    val activeIndex by remember(lyrics, liveProgressMs) {
        androidx.compose.runtime.derivedStateOf {
            lyrics.getActiveLineIndex(liveProgressMs)
        }
    }

    // Detect user manual scrolling vs auto-scrolling
    var userScrolled by remember { mutableStateOf(false) }
    var lastUserScrollTime by remember { mutableLongStateOf(0L) }
    var manualSyncTrigger by remember { mutableLongStateOf(0L) }
    var hasInitiallyScrolled by remember { mutableStateOf(false) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            userScrolled = true
            lastUserScrollTime = SystemClock.elapsedRealtime()
        }
    }

    // Reset user scroll after 2.5 seconds of inactivity ONLY IF music is playing
    LaunchedEffect(userScrolled, lastUserScrollTime, isPlaying) {
        if (userScrolled && isPlaying) {
            delay(2500L)
            userScrolled = false
        }
    }

    // Initial scroll to active line when track or lyrics load
    LaunchedEffect(lyrics.trackId) {
        hasInitiallyScrolled = false
    }

    LaunchedEffect(lyrics, activeIndex) {
        if (!hasInitiallyScrolled && activeIndex in lyrics.lines.indices) {
            hasInitiallyScrolled = true
            listState.scrollToItem(
                index = activeIndex,
                scrollOffset = 0
            )
        }
    }

    // Auto-scroll to center the active line:
    // Only auto-scrolls while music is playing, or when the user explicitly triggers a sync/seek!
    LaunchedEffect(activeIndex, userScrolled, isPlaying, manualSyncTrigger) {
        val shouldScroll = (isPlaying && !userScrolled) || manualSyncTrigger > 0L
        if (shouldScroll && activeIndex in lyrics.lines.indices) {
            listState.animateScrollToItem(
                index = activeIndex,
                scrollOffset = 0
            )
            if (manualSyncTrigger > 0L) {
                manualSyncTrigger = 0L
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Main Lyrics Scroll Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = safeStartPadding, // Comfortably clears camera punch hole
                    end = 20.dp,
                    top = 4.dp,
                    bottom = 8.dp
                ),
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
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Loading lyrics...",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 13.sp
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
                            tint = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (!lyrics.plainLyrics.isNullOrBlank()) {
                                lyrics.plainLyrics
                            } else {
                                "No synchronized lyrics available."
                            },
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        FilledTonalButton(
                            onClick = onRetry,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Retry",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry or Load Demo Track", fontSize = 12.sp)
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 90.dp, bottom = 150.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
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
                                    currentPositionMs = if (isActive) liveProgressMs else 0L,
                                    onLineClick = {
                                        userScrolled = false
                                        manualSyncTrigger = SystemClock.elapsedRealtime()
                                        onSeekTo(line.startTimeMs)
                                    },
                                    onWordClick = { wordStartMs ->
                                        userScrolled = false
                                        manualSyncTrigger = SystemClock.elapsedRealtime()
                                        onSeekTo(wordStartMs)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Minimal Close Button
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 12.dp, top = 6.dp)
                .size(34.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close Lyrics",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }

        // Floating Minimal "Sync" button when user scrolled away
        AnimatedVisibility(
            visible = userScrolled && activeIndex in lyrics.lines.indices,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
        ) {
            Surface(
                onClick = {
                    userScrolled = false
                    manualSyncTrigger = SystemClock.elapsedRealtime()
                },
                shape = RoundedCornerShape(20.dp),
                color = albumColors.tonalPrimary,
                contentColor = albumColors.tonalOnPrimary,
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Sync",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Sync",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Renders an individual lyric line.
 * Supports exact word-by-word karaoke synchronization with spring scaling & bloom,
 * or clean line-level highlighting.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpicyLyricLine(
    line: LyricLine,
    isActive: Boolean,
    currentPositionMs: Long,
    onLineClick: () -> Unit,
    onWordClick: (Long) -> Unit
) {
    val lineScale by animateFloatAsState(
        targetValue = if (isActive) 1.04f else 0.96f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "lineScale"
    )

    val lineAlpha by animateFloatAsState(
        targetValue = if (isActive) 1.0f else 0.32f,
        animationSpec = tween(durationMillis = 200),
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
            .padding(vertical = 4.dp, horizontal = 4.dp)
    ) {
        if (line.words.isNotEmpty()) {
            // Word-by-word karaoke line
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                line.words.forEach { wordTiming ->
                    SpicyWordItem(
                        wordTiming = wordTiming,
                        currentPositionMs = currentPositionMs,
                        isLineActive = isActive,
                        onWordClick = { onWordClick(wordTiming.startTimeMs) }
                    )
                }
            }
        } else {
            // Clean line-level text: perfectly centered bloom shadow and razor-sharp typography
            Text(
                text = line.text,
                fontSize = 24.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) Color.White else Color.White.copy(alpha = 0.40f),
                lineHeight = 34.sp,
                style = if (isActive) {
                    TextStyle(
                        shadow = Shadow(
                            color = Color.White.copy(alpha = 0.85f),
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
 * Renders an individual word with progressive karaoke fill sweep, spring scale,
 * weightless vocal lift, and unclipped symmetrical Gaussian bloom shadow.
 * Single-layer design with horizontal gradient shader guarantees zero font-metric ghosting
 * and eliminates sharp scissor-clipped shadow edges.
 */
@Composable
fun SpicyWordItem(
    wordTiming: WordTiming,
    currentPositionMs: Long,
    isLineActive: Boolean,
    onWordClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isWordActive = isLineActive && (currentPositionMs in wordTiming.startTimeMs until wordTiming.endTimeMs)
    val isWordPast = isLineActive && (currentPositionMs >= wordTiming.endTimeMs)

    // Calculate duration & smooth progressive karaoke wipe fraction (0.0 to 1.0)
    val duration = (wordTiming.endTimeMs - wordTiming.startTimeMs).coerceAtLeast(1L)
    val progress = when {
        !isLineActive || currentPositionMs < wordTiming.startTimeMs -> 0.0f
        currentPositionMs >= wordTiming.endTimeMs -> 1.0f
        else -> ((currentPositionMs - wordTiming.startTimeMs).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }

    // Spring scale: zooms smoothly to 1.08x while actively being sung
    val wordScale by animateFloatAsState(
        targetValue = if (isWordActive) 1.08f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "wordScale"
    )

    // Spring vertical lift: floats up 2.5dp while actively sung for weightless karaoke vocal feel
    val liftY by animateDpAsState(
        targetValue = if (isWordActive) (-2.5).dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "liftY"
    )

    var wordWidthPx by remember(wordTiming.word) { mutableFloatStateOf(0f) }

    if (isWordActive && wordWidthPx > 0f) {
        val wipePx = progress * wordWidthPx
        val featherPx = 6f
        val pLeft = ((wipePx - featherPx) / wordWidthPx).coerceIn(0f, 1f)
        val pRight = ((wipePx + featherPx) / wordWidthPx).coerceIn(0f, 1f)

        val karaokeBrush = Brush.horizontalGradient(
            colorStops = arrayOf(
                0.0f to Color.White,
                pLeft to Color.White,
                pRight to Color.White.copy(alpha = 0.35f),
                1.0f to Color.White.copy(alpha = 0.35f)
            ),
            startX = 0f,
            endX = wordWidthPx
        )

        Text(
            text = wordTiming.word,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontStyle = if (wordTiming.isBackground) FontStyle.Italic else FontStyle.Normal,
            lineHeight = 34.sp,
            style = TextStyle(
                brush = karaokeBrush,
                shadow = Shadow(
                    color = Color.White.copy(alpha = 0.85f),
                    offset = Offset.Zero,
                    blurRadius = 14f
                )
            ),
            modifier = modifier
                .onSizeChanged { size ->
                    if (size.width > 0) wordWidthPx = size.width.toFloat()
                }
                .offset(y = liftY)
                .scale(wordScale)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onWordClick
                )
        )
    } else {
        val textColor = when {
            isWordPast -> Color.White.copy(alpha = 0.95f)
            isWordActive -> Color.White
            else -> Color.White.copy(alpha = if (isLineActive) 0.35f else 0.28f)
        }

        Text(
            text = wordTiming.word,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontStyle = if (wordTiming.isBackground) FontStyle.Italic else FontStyle.Normal,
            color = textColor,
            lineHeight = 34.sp,
            modifier = modifier
                .onSizeChanged { size ->
                    if (size.width > 0) wordWidthPx = size.width.toFloat()
                }
                .offset(y = liftY)
                .scale(wordScale)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onWordClick
                )
        )
    }
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
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )

    val dot2Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 120, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )

    val dot3Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 240, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isActive) 1.0f else 0.30f,
        animationSpec = tween(200),
        label = "dotsAlpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .offset(y = if (isActive) dot1Offset.dp else 0.dp)
                .size(8.dp)
                .scale(if (isActive) 1.2f else 1.0f),
            contentAlignment = Alignment.Center
        ) {
            Surface(shape = CircleShape, color = Color.White, modifier = Modifier.fillMaxSize()) {}
        }

        Box(
            modifier = Modifier
                .offset(y = if (isActive) dot2Offset.dp else 0.dp)
                .size(8.dp)
                .scale(if (isActive) 1.2f else 1.0f),
            contentAlignment = Alignment.Center
        ) {
            Surface(shape = CircleShape, color = Color.White, modifier = Modifier.fillMaxSize()) {}
        }

        Box(
            modifier = Modifier
                .offset(y = if (isActive) dot3Offset.dp else 0.dp)
                .size(8.dp)
                .scale(if (isActive) 1.2f else 1.0f),
            contentAlignment = Alignment.Center
        ) {
            Surface(shape = CircleShape, color = Color.White, modifier = Modifier.fillMaxSize()) {}
        }
    }
}
