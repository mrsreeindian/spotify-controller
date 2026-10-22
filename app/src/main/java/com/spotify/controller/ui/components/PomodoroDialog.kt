package com.spotify.controller.ui.components

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spotify.controller.data.model.PomodoroPhase
import com.spotify.controller.data.model.PomodoroState
import com.spotify.controller.ui.theme.LocalAlbumArtColors
import kotlin.math.roundToInt

/**
 * Compact Top Bar Pill that displays active Pomodoro countdown and phase.
 * Tapping it opens the full Pomodoro settings and control dialog.
 */
@Composable
fun PomodoroTopBarPill(
    state: PomodoroState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val albumColors = LocalAlbumArtColors.current

    if (state.isIdle) {
        IconButton(
            onClick = onClick,
            modifier = modifier.size(34.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Timer,
                contentDescription = "Pomodoro Timer",
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(19.dp)
            )
        }
    } else {
        val phaseColor by animateColorAsState(
            targetValue = when (state.phase) {
                PomodoroPhase.WORK -> albumColors.tonalPrimary
                PomodoroPhase.BREAK -> Color(0xFFFFB74D)
            },
            label = "pillColor"
        )

        Row(
            modifier = modifier
                .clip(RoundedCornerShape(16.dp))
                .background(phaseColor.copy(alpha = 0.20f))
                .border(1.dp, phaseColor.copy(alpha = 0.50f), RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (state.phase == PomodoroPhase.WORK) Icons.Rounded.Timer else Icons.Rounded.Coffee,
                contentDescription = null,
                tint = phaseColor,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = state.formattedTime,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            if (state.isPaused) {
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "⏸",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 9.sp
                )
            }
        }
    }
}

/**
 * Compact, Non-Scrolling Pomodoro Focus Timer Dialog.
 * Perfectly fitted for standard portrait handhelds and dock landscape displays without scrolling.
 */
@Composable
fun PomodoroDialog(
    state: PomodoroState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onSkipPhase: () -> Unit,
    onWorkDurationChange: (Int) -> Unit,
    onBreakDurationChange: (Int) -> Unit,
    onAutoPauseChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val albumColors = LocalAlbumArtColors.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val animatedProgress by animateFloatAsState(
        targetValue = state.progress,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "pomodoroProgress"
    )

    val activePhaseColor = when (state.phase) {
        PomodoroPhase.WORK -> albumColors.tonalPrimary
        PomodoroPhase.BREAK -> Color(0xFFFFB74D)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.82f else 0.90f)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF16161E),
            tonalElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E2E3E))
        ) {
            if (isLandscape) {
                // Landscape: 2-column side-by-side layout (zero scroll)
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Column: Timer & Controls
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        DialogHeader(
                            completedSessions = state.completedSessions,
                            onDismiss = onDismiss
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        CountdownRing(
                            state = state,
                            progress = animatedProgress,
                            activeColor = activePhaseColor,
                            ringSize = 115
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ActionControls(
                            isRunning = state.isRunning,
                            isPaused = state.isPaused,
                            activeColor = activePhaseColor,
                            onStart = onStart,
                            onPause = onPause,
                            onReset = onReset,
                            onSkipPhase = onSkipPhase
                        )
                    }

                    // Right Column: Sliders, Presets & Auto-pause
                    Column(
                        modifier = Modifier.weight(1.15f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        SlidersAndSettingsCard(
                            state = state,
                            albumPrimary = albumColors.tonalPrimary,
                            onWorkDurationChange = onWorkDurationChange,
                            onBreakDurationChange = onBreakDurationChange,
                            onAutoPauseChange = onAutoPauseChange
                        )
                    }
                }
            } else {
                // Portrait: Compact single-screen layout (zero scroll)
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    DialogHeader(
                        completedSessions = state.completedSessions,
                        onDismiss = onDismiss
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    CountdownRing(
                        state = state,
                        progress = animatedProgress,
                        activeColor = activePhaseColor,
                        ringSize = 125
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ActionControls(
                        isRunning = state.isRunning,
                        isPaused = state.isPaused,
                        activeColor = activePhaseColor,
                        onStart = onStart,
                        onPause = onPause,
                        onReset = onReset,
                        onSkipPhase = onSkipPhase
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SlidersAndSettingsCard(
                        state = state,
                        albumPrimary = albumColors.tonalPrimary,
                        onWorkDurationChange = onWorkDurationChange,
                        onBreakDurationChange = onBreakDurationChange,
                        onAutoPauseChange = onAutoPauseChange
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogHeader(
    completedSessions: Int,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Pomodoro Timer",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            if (completedSessions > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "• $completedSessions done",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun CountdownRing(
    state: PomodoroState,
    progress: Float,
    activeColor: Color,
    ringSize: Int
) {
    Box(
        modifier = Modifier.size(ringSize.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { 1f },
            modifier = Modifier.size(ringSize.dp),
            color = Color.White.copy(alpha = 0.08f),
            strokeWidth = 6.dp,
            trackColor = Color.Transparent,
            strokeCap = StrokeCap.Round
        )

        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(ringSize.dp),
            color = activeColor,
            strokeWidth = 6.dp,
            trackColor = Color.Transparent,
            strokeCap = StrokeCap.Round
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Phase Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(activeColor.copy(alpha = 0.20f))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = when (state.phase) {
                        PomodoroPhase.WORK -> "FOCUS"
                        PomodoroPhase.BREAK -> "BREAK"
                    },
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = activeColor,
                    letterSpacing = 0.8.sp
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Tabular Countdown
            Text(
                text = state.formattedTime,
                fontSize = if (ringSize < 120) 28.sp else 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                letterSpacing = (-0.5).sp
            )

            Text(
                text = when {
                    state.isPaused -> "Paused"
                    state.isRunning -> if (state.phase == PomodoroPhase.WORK) "In session" else "Resting"
                    else -> "Ready"
                },
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
private fun ActionControls(
    isRunning: Boolean,
    isPaused: Boolean,
    activeColor: Color,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onSkipPhase: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(
            onClick = onReset,
            modifier = Modifier.size(38.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = Color.White.copy(alpha = 0.10f),
                contentColor = Color.White
            )
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Reset", modifier = Modifier.size(17.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        FilledTonalButton(
            onClick = { if (isRunning) onPause() else onStart() },
            modifier = Modifier
                .height(40.dp)
                .width(125.dp),
            shape = RoundedCornerShape(20.dp),
            colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                containerColor = activeColor,
                contentColor = Color.Black
            )
        ) {
            Icon(
                imageVector = if (isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isRunning) "Pause" else "Start",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isRunning) "Pause" else if (isPaused) "Resume" else "Start",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        FilledTonalIconButton(
            onClick = onSkipPhase,
            modifier = Modifier.size(38.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = Color.White.copy(alpha = 0.10f),
                contentColor = Color.White
            )
        ) {
            Icon(Icons.Rounded.SkipNext, contentDescription = "Skip Phase", modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun SlidersAndSettingsCard(
    state: PomodoroState,
    albumPrimary: Color,
    onWorkDurationChange: (Int) -> Unit,
    onBreakDurationChange: (Int) -> Unit,
    onAutoPauseChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(12.dp)
    ) {
        // Work Duration Slider Row (15 to 60m)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Work",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Text(
                text = "${state.workDurationMinutes} min",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = albumPrimary
            )
        }

        Slider(
            value = state.workDurationMinutes.toFloat(),
            onValueChange = { onWorkDurationChange(it.roundToInt()) },
            valueRange = 15f..60f,
            steps = 44,
            modifier = Modifier.height(28.dp),
            colors = SliderDefaults.colors(
                thumbColor = albumPrimary,
                activeTrackColor = albumPrimary,
                inactiveTrackColor = Color.White.copy(alpha = 0.15f)
            )
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Break Duration Slider Row (0 to 25m)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Break",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Text(
                text = if (state.breakDurationMinutes == 0) "No Break" else "${state.breakDurationMinutes} min",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFB74D)
            )
        }

        Slider(
            value = state.breakDurationMinutes.toFloat(),
            onValueChange = { onBreakDurationChange(it.roundToInt()) },
            valueRange = 0f..25f,
            steps = 24,
            modifier = Modifier.height(28.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFFB74D),
                activeTrackColor = Color(0xFFFFB74D),
                inactiveTrackColor = Color.White.copy(alpha = 0.15f)
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Presets Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            QuickPresetChip(
                label = "25/5",
                isSelected = state.workDurationMinutes == 25 && state.breakDurationMinutes == 5,
                onClick = {
                    onWorkDurationChange(25)
                    onBreakDurationChange(5)
                },
                modifier = Modifier.weight(1f)
            )
            QuickPresetChip(
                label = "50/10",
                isSelected = state.workDurationMinutes == 50 && state.breakDurationMinutes == 10,
                onClick = {
                    onWorkDurationChange(50)
                    onBreakDurationChange(10)
                },
                modifier = Modifier.weight(1f)
            )
            QuickPresetChip(
                label = "15/0",
                isSelected = state.workDurationMinutes == 15 && state.breakDurationMinutes == 0,
                onClick = {
                    onWorkDurationChange(15)
                    onBreakDurationChange(0)
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Auto-pause toggle row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Pause music on break",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.75f)
            )

            Switch(
                checked = state.autoPauseSpotifyOnBreak,
                onCheckedChange = onAutoPauseChange,
                modifier = Modifier.height(24.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = albumPrimary,
                    uncheckedThumbColor = Color.White.copy(alpha = 0.5f),
                    uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
                )
            )
        }
    }
}

@Composable
private fun QuickPresetChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val albumColors = LocalAlbumArtColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) albumColors.tonalPrimary.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.06f))
            .border(
                width = 1.dp,
                color = if (isSelected) albumColors.tonalPrimary.copy(alpha = 0.60f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f),
            textAlign = TextAlign.Center
        )
    }
}
