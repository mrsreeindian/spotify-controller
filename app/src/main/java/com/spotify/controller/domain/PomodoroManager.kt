package com.spotify.controller.domain

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.spotify.controller.data.model.PomodoroPhase
import com.spotify.controller.data.model.PomodoroState
import com.spotify.controller.data.model.PomodoroStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manages the Pomodoro focus/break timer lifecycle, countdown ticker,
 * phase transitions, Spotify auto-pause triggers, and haptic/audio alerts.
 */
class PomodoroManager(
    private val context: Context? = null,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(PomodoroState())
    val state: StateFlow<PomodoroState> = _state.asStateFlow()

    private var tickerJob: Job? = null

    /**
     * Callback triggered when a work session concludes and break begins.
     */
    var onWorkSessionCompleted: (() -> Unit)? = null

    fun start() {
        if (_state.value.status == PomodoroStatus.RUNNING) return

        if (_state.value.remainingSeconds <= 0) {
            val resetSeconds = when (_state.value.phase) {
                PomodoroPhase.WORK -> _state.value.workDurationMinutes * 60
                PomodoroPhase.BREAK -> _state.value.breakDurationMinutes * 60
            }
            _state.update { it.copy(remainingSeconds = resetSeconds) }
        }

        _state.update { it.copy(status = PomodoroStatus.RUNNING) }

        tickerJob?.cancel()
        tickerJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000L)
                val current = _state.value
                if (current.status != PomodoroStatus.RUNNING) break

                if (current.remainingSeconds > 1) {
                    _state.update { it.copy(remainingSeconds = it.remainingSeconds - 1) }
                } else {
                    _state.update { it.copy(remainingSeconds = 0) }
                    triggerAlert()
                    handlePhaseCompletion()
                    break
                }
            }
        }
    }

    fun pause() {
        tickerJob?.cancel()
        _state.update { it.copy(status = PomodoroStatus.PAUSED) }
    }

    fun toggleStartPause() {
        if (_state.value.isRunning) {
            pause()
        } else {
            start()
        }
    }

    fun reset() {
        tickerJob?.cancel()
        _state.update { current ->
            val defaultSeconds = when (current.phase) {
                PomodoroPhase.WORK -> current.workDurationMinutes * 60
                PomodoroPhase.BREAK -> current.breakDurationMinutes * 60
            }
            current.copy(
                status = PomodoroStatus.IDLE,
                remainingSeconds = defaultSeconds
            )
        }
    }

    fun skipPhase() {
        tickerJob?.cancel()
        val current = _state.value
        val nextPhase = if (current.phase == PomodoroPhase.WORK) {
            PomodoroPhase.BREAK
        } else {
            PomodoroPhase.WORK
        }

        val nextSeconds = when (nextPhase) {
            PomodoroPhase.WORK -> current.workDurationMinutes * 60
            PomodoroPhase.BREAK -> current.breakDurationMinutes * 60
        }

        _state.update {
            it.copy(
                phase = nextPhase,
                remainingSeconds = nextSeconds
            )
        }

        if (current.status == PomodoroStatus.RUNNING) {
            start()
        } else {
            _state.update { it.copy(status = PomodoroStatus.IDLE) }
        }
    }

    fun setWorkDuration(minutes: Int) {
        val clamped = minutes.coerceIn(15, 60)
        _state.update { current ->
            val updated = current.copy(workDurationMinutes = clamped)
            if (current.phase == PomodoroPhase.WORK && current.status == PomodoroStatus.IDLE) {
                updated.copy(remainingSeconds = clamped * 60)
            } else {
                updated
            }
        }
    }

    fun setBreakDuration(minutes: Int) {
        val clamped = minutes.coerceIn(0, 25)
        _state.update { current ->
            val updated = current.copy(breakDurationMinutes = clamped)
            if (current.phase == PomodoroPhase.BREAK && current.status == PomodoroStatus.IDLE) {
                updated.copy(remainingSeconds = clamped * 60)
            } else {
                updated
            }
        }
    }

    fun setAutoPauseSpotifyOnBreak(enabled: Boolean) {
        _state.update { it.copy(autoPauseSpotifyOnBreak = enabled) }
    }

    private fun handlePhaseCompletion() {
        val current = _state.value
        if (current.phase == PomodoroPhase.WORK) {
            val newCompletedCount = current.completedSessions + 1

            if (current.autoPauseSpotifyOnBreak) {
                onWorkSessionCompleted?.invoke()
            }

            if (current.breakDurationMinutes > 0) {
                _state.update {
                    it.copy(
                        phase = PomodoroPhase.BREAK,
                        remainingSeconds = it.breakDurationMinutes * 60,
                        completedSessions = newCompletedCount,
                        status = PomodoroStatus.RUNNING
                    )
                }
                start() // Automatically start the break countdown
            } else {
                // Break is 0 mins; reset to next Work session ready to begin
                _state.update {
                    it.copy(
                        phase = PomodoroPhase.WORK,
                        remainingSeconds = it.workDurationMinutes * 60,
                        completedSessions = newCompletedCount,
                        status = PomodoroStatus.IDLE
                    )
                }
            }
        } else {
            // Break session concluded; transition back to Work session
            _state.update {
                it.copy(
                    phase = PomodoroPhase.WORK,
                    remainingSeconds = it.workDurationMinutes * 60,
                    status = PomodoroStatus.IDLE
                )
            }
        }
    }

    private fun triggerAlert() {
        val ctx = context ?: return
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 350)
        } catch (_: Exception) {}

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 250, 150, 350), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 150, 350), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 250, 150, 350), -1)
                }
            }
        } catch (_: Exception) {}
    }
}
