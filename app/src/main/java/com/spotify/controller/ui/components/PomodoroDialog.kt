package com.spotify.controller.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spotify.controller.data.model.PomodoroPhase
import com.spotify.controller.data.model.PomodoroState
import com.spotify.controller.data.model.PomodoroStatus
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
        // Active/Paused running chip
        val phaseColor by animateColorAsState(
            targetValue = when (state.phase) {
                PomodoroPhase.WORK -> albumColors.tonalPrimary
                PomodoroPhase.BREAK -> Color(0xFFFFB74D) // Warm amber for break
            },
            label = "pillColor"
        )

        Row(
            modifier = modifier
                .clip(RoundedCornerShape(16.dp))
                .background(phaseColor.copy(alpha = 0.20f))
                .border(1.dp, phaseColor.copy(alpha = 0.50f), RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (state.phase == PomodoroPhase.WORK) Icons.Rounded.Timer else Icons.Rounded.Coffee,
                contentDescription = null,
                tint = phaseColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = state.formattedTime,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            if (state.isPaused) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "⏸",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * Full Pomodoro Focus Timer Dialog with custom duration pickers,
 * circular countdown visualization, phase controls, and presets.
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
    val animatedProgress by animateFloatAsState(
        targetValue = state.progress,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
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
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF16161E),
            tonalElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E2E3E))
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Pomodoro Timer",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (state.completedSessions > 0) "${state.completedSessions} focus session(s) completed" else "Spotify Focus Companion",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Hero Circular Countdown Ring
                Box(
                    modifier = Modifier.size(190.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Inactive background ring
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.size(190.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        strokeWidth = 10.dp,
                        trackColor = Color.Transparent,
                        strokeCap = StrokeCap.Round
                    )

                    // Active progress ring
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.size(190.dp),
                        color = activePhaseColor,
                        strokeWidth = 10.dp,
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
                                .clip(RoundedCornerShape(12.dp))
                                .background(activePhaseColor.copy(alpha = 0.20f))
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = when (state.phase) {
                                    PomodoroPhase.WORK -> "FOCUS"
                                    PomodoroPhase.BREAK -> "BREAK"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = activePhaseColor,
                                letterSpacing = 1.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Large Tabular Countdown Clock
                        Text(
                            text = state.formattedTime,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = (-1).sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Status Subtitle
                        Text(
                            text = when {
                                state.isPaused -> "Paused"
                                state.isRunning && state.phase == PomodoroPhase.WORK -> "Stay in the flow"
                                state.isRunning && state.phase == PomodoroPhase.BREAK -> "Relax & recharge"
                                else -> "Ready to focus"
                            },
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Primary Play / Pause / Reset / Skip Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reset Button
                    FilledTonalIconButton(
                        onClick = onReset,
                        modifier = Modifier.size(46.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.10f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Reset Timer",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Big Start / Pause Button
                    FilledTonalButton(
                        onClick = { if (state.isRunning) onPause() else onStart() },
                        modifier = Modifier
                            .height(52.dp)
                            .width(150.dp),
                        shape = RoundedCornerShape(26.dp),
                        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                            containerColor = activePhaseColor,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(
                            imageVector = if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.isRunning) "Pause" else "Start",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (state.isRunning) "Pause" else if (state.isPaused) "Resume" else "Start",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Skip Phase Button
                    FilledTonalIconButton(
                        onClick = onSkipPhase,
                        modifier = Modifier.size(46.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.10f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Skip Phase",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Duration Configuration Sliders (enabled when timer is IDLE or PAUSED)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .padding(16.dp)
                ) {
                    // Work Duration Slider (15 to 60 min, default 25)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Work Duration",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        Text(
                            text = "${state.workDurationMinutes} min",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = albumColors.tonalPrimary
                        )
                    }

                    Slider(
                        value = state.workDurationMinutes.toFloat(),
                        onValueChange = { onWorkDurationChange(it.roundToInt()) },
                        valueRange = 15f..60f,
                        steps = 44, // 15 to 60 min in 1-min increments
                        colors = SliderDefaults.colors(
                            thumbColor = albumColors.tonalPrimary,
                            activeTrackColor = albumColors.tonalPrimary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Break Duration Slider (0 to 25 min, default 5)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Break Duration",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        Text(
                            text = if (state.breakDurationMinutes == 0) "No Break" else "${state.breakDurationMinutes} min",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFB74D)
                        )
                    }

                    Slider(
                        value = state.breakDurationMinutes.toFloat(),
                        onValueChange = { onBreakDurationChange(it.roundToInt()) },
                        valueRange = 0f..25f,
                        steps = 24, // 0 to 25 min in 1-min increments
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFFB74D),
                            activeTrackColor = Color(0xFFFFB74D),
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quick Preset Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickPresetChip(
                            label = "25 / 5 Classic",
                            isSelected = state.workDurationMinutes == 25 && state.breakDurationMinutes == 5,
                            onClick = {
                                onWorkDurationChange(25)
                                onBreakDurationChange(5)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        QuickPresetChip(
                            label = "50 / 10 Deep",
                            isSelected = state.workDurationMinutes == 50 && state.breakDurationMinutes == 10,
                            onClick = {
                                onWorkDurationChange(50)
                                onBreakDurationChange(10)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        QuickPresetChip(
                            label = "15 / 0 Sprint",
                            isSelected = state.workDurationMinutes == 15 && state.breakDurationMinutes == 0,
                            onClick = {
                                onWorkDurationChange(15)
                                onBreakDurationChange(0)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Auto-pause Spotify on break toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pause Spotify on Break",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        Text(
                            text = "Automatically pause playback when focus time ends",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.55f)
                        )
                    }

                    Switch(
                        checked = state.autoPauseSpotifyOnBreak,
                        onCheckedChange = onAutoPauseChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = albumColors.tonalPrimary,
                            uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                            uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
                        )
                    )
                }
            }
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
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) albumColors.tonalPrimary.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.06f))
            .border(
                width = 1.dp,
                color = if (isSelected) albumColors.tonalPrimary.copy(alpha = 0.60f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
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
