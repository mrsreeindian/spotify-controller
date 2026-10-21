package com.spotify.controller.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import com.spotify.controller.data.model.LyricLine
import com.spotify.controller.data.model.Lyrics
import com.spotify.controller.data.model.WordTiming
import com.spotify.controller.ui.theme.LocalAlbumArtColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.sin

/**
 * Hardware-accelerated horizontal scissoring shape for buttery 60-120fps progressive karaoke text wipes.
 */
class HorizontalProgressShape(private val fraction: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val progress = fraction.coerceIn(0f, 1f)
        return Outline.Rectangle(
            Rect(
                left = 0f,
                top = 0f,
                right = size.width * progress,
                bottom = size.height
            )
        )
    }
}

/**
 * Port of Spikerko/spicy-lyrics for Android.
 * Features:
 * - 60fps continuous millisecond client ticker
 * - Word-by-word progressive karaoke wipe (Apple Music & Spicy Lyrics style)
 * - Camera punch-hole clearance (lyrics safely shifted to the right)
 * - Minimal, uncluttered aesthetic
 * - Kinetic spring bounce & sinusoidal vertical breath lift per singing syllable
 * - Glowing bloom on active vocal syllables
 * - Tap any word or line to seek Spotify playback directly
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

    // Camera punch-hole safe clearance: ensures lyrics are never obstructed by corner or left cutouts
    val layoutDirection = LocalLayoutDirection.current
    val cutoutStart = WindowInsets.displayCutout.asPaddingValues().calculateStartPadding(layoutDirection)
    val safeStartPadding = max(cutoutStart + 20.dp, 56.dp)

    // 60fps real-time client-side millisecond ticker for ultra-smooth progressive character wipe
    var liveProgressMs by remember { mutableLongStateOf(progressMs) }
    LaunchedEffect(isPlaying, progressMs, lastSyncTimestamp) {
        liveProgressMs = progressMs
        if (isPlaying && lastSyncTimestamp > 0L) {
            while (isActive) {
                val elapsedSinceSync = SystemClock.elapsedRealtime() - lastSyncTimestamp
                val current = (progressMs + elapsedSinceSync).coerceIn(0L, durationMs.coerceAtLeast(1L))
                liveProgressMs = current
                delay(16L) // 60fps refresh
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
                .padding(
                    start = safeStartPadding, // Shifts lyrics comfortably to the right past the camera punch hole
                    end = 20.dp,
                    top = 10.dp,
                    bottom = 12.dp
                )
        ) {
            // Minimal Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = "Live Lyrics",
                        tint = albumColors.tonalPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Spicy Lyrics",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            if (lyrics.hasWordSync) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = albumColors.tonalPrimary.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = if (lyrics.hasExactWordSync) "KARAOKE" else "WORD SYNC",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = albumColors.tonalPrimary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "$trackTitle • $artistName",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1
                        )
                    }
                }

                // Minimal Close Button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close Lyrics",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
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
                                        currentPositionMs = liveProgressMs,
                                        glowColor = albumColors.tonalPrimary,
                                        onLineClick = { onSeekTo(line.startTimeMs) },
                                        onWordClick = { wordStartMs -> onSeekTo(wordStartMs) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Minimal "Sync with playback" pill
        AnimatedVisibility(
            visible = userScrolled && activeIndex in lyrics.lines.indices,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
        ) {
            Surface(
                onClick = { userScrolled = false },
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
 * Renders an individual lyric line with uniform word layout, preserving geometry across active/inactive transitions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpicyLyricLine(
    line: LyricLine,
    isActive: Boolean,
    currentPositionMs: Long,
    glowColor: Color,
    onLineClick: () -> Unit,
    onWordClick: (Long) -> Unit
) {
    val lineScale by animateFloatAsState(
        targetValue = if (isActive) 1.03f else 0.96f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "lineScale"
    )

    val lineAlpha by animateFloatAsState(
        targetValue = if (isActive) 1.0f else 0.30f,
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
            .padding(vertical = 3.dp, horizontal = 4.dp)
    ) {
        if (line.words.isNotEmpty()) {
            // Word-by-word progressive karaoke rendering
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                line.words.forEach { wordTiming ->
                    SpicyWordItem(
                        wordTiming = wordTiming,
                        currentPositionMs = currentPositionMs,
                        isLineActive = isActive,
                        glowColor = glowColor,
                        onWordClick = { onWordClick(wordTiming.startTimeMs) }
                    )
                }
            }
        } else {
            // Fallback for empty word list
            Text(
                text = line.text,
                fontSize = 24.sp,
                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = if (isActive) Color.White else Color.White.copy(alpha = 0.5f),
                lineHeight = 32.sp,
                style = if (isActive) {
                    TextStyle(
                        shadow = Shadow(
                            color = Color.White.copy(alpha = 0.6f),
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
 * Renders an individual word / syllable with progressive karaoke wipe, spring bounce,
 * sinusoidal breath lift, and blooming text shadow.
 */
@Composable
fun SpicyWordItem(
    wordTiming: WordTiming,
    currentPositionMs: Long,
    isLineActive: Boolean,
    glowColor: Color,
    onWordClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isWordPast = currentPositionMs >= wordTiming.endTimeMs
    val isWordActive = isLineActive && (currentPositionMs in wordTiming.startTimeMs until wordTiming.endTimeMs)
    val progress = wordTiming.progress(currentPositionMs)

    // Spring scale: zooms smoothly to 1.08x while actively being sung
    val wordScale by animateFloatAsState(
        targetValue = if (isWordActive) 1.08f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "wordScale"
    )

    // Sinusoidal breath lift (-2.5dp) during vocal delivery
    val verticalLift = if (isWordActive) {
        (-2.5f * sin(progress * PI.toFloat())).dp
    } else 0.dp

    // Dynamic bloom glow radius tracking progress
    val glowBlur = if (isWordActive) {
        14f + (6f * sin(progress * PI.toFloat()))
    } else 0f

    Box(
        modifier = modifier
            .offset(y = verticalLift)
            .scale(wordScale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onWordClick
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        // Layer 1: Base translucent un-sung text
        Text(
            text = wordTiming.word,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = if (isLineActive) 0.35f else 0.25f),
            lineHeight = 32.sp
        )

        // Layer 2: Illuminated active/sung text with hardware-accelerated progressive horizontal wipe
        if (isWordPast || (isWordActive && progress > 0.005f)) {
            val fillFraction = if (isWordPast) 1.0f else progress
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        clip = true
                        shape = HorizontalProgressShape(fillFraction)
                    }
            ) {
                Text(
                    text = wordTiming.word,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    lineHeight = 32.sp,
                    style = if (isWordActive) {
                        TextStyle(
                            shadow = Shadow(
                                color = Color.White.copy(alpha = 0.92f),
                                offset = Offset.Zero,
                                blurRadius = glowBlur
                            )
                        )
                    } else TextStyle.Default
                )
            }
        }
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
