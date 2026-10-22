package com.spotify.controller.data.model

import androidx.compose.runtime.Immutable

enum class PomodoroPhase {
    WORK,
    BREAK
}

enum class PomodoroStatus {
    IDLE,
    RUNNING,
    PAUSED
}

@Immutable
data class PomodoroState(
    val status: PomodoroStatus = PomodoroStatus.IDLE,
    val phase: PomodoroPhase = PomodoroPhase.WORK,
    val workDurationMinutes: Int = 25,
    val breakDurationMinutes: Int = 5,
    val remainingSeconds: Int = 25 * 60,
    val autoPauseSpotifyOnBreak: Boolean = true,
    val enableDndDuringWork: Boolean = true,
    val isDndActive: Boolean = false,
    val completedSessions: Int = 0
) {
    val totalPhaseSeconds: Int
        get() = when (phase) {
            PomodoroPhase.WORK -> workDurationMinutes * 60
            PomodoroPhase.BREAK -> breakDurationMinutes * 60
        }.coerceAtLeast(1)

    val progress: Float
        get() = (1f - (remainingSeconds.toFloat() / totalPhaseSeconds.toFloat())).coerceIn(0f, 1f)

    val formattedTime: String
        get() {
            val minutes = remainingSeconds / 60
            val seconds = remainingSeconds % 60
            return "%02d:%02d".format(minutes, seconds)
        }

    val isRunning: Boolean
        get() = status == PomodoroStatus.RUNNING

    val isPaused: Boolean
        get() = status == PomodoroStatus.PAUSED

    val isIdle: Boolean
        get() = status == PomodoroStatus.IDLE
}
