package com.spotify.controller

import com.spotify.controller.data.model.PomodoroPhase
import com.spotify.controller.data.model.PomodoroStatus
import com.spotify.controller.domain.PomodoroManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PomodoroManagerTest {

    private val testScope = CoroutineScope(Job() + Dispatchers.Unconfined)

    @Test
    fun testDefaultDurationsAndState() {
        val manager = PomodoroManager(null, testScope)
        val state = manager.state.value

        assertEquals(25, state.workDurationMinutes)
        assertEquals(5, state.breakDurationMinutes)
        assertEquals(25 * 60, state.remainingSeconds)
        assertEquals(PomodoroPhase.WORK, state.phase)
        assertEquals(PomodoroStatus.IDLE, state.status)
        assertEquals("25:00", state.formattedTime)
        assertTrue(state.autoPauseSpotifyOnBreak)
        assertEquals(0, state.completedSessions)
    }

    @Test
    fun testClampingWorkDuration() {
        val manager = PomodoroManager(null, testScope)

        // Lower than min (15) -> clamps to 15
        manager.setWorkDuration(10)
        assertEquals(15, manager.state.value.workDurationMinutes)
        assertEquals(15 * 60, manager.state.value.remainingSeconds)

        // Higher than max (60) -> clamps to 60
        manager.setWorkDuration(75)
        assertEquals(60, manager.state.value.workDurationMinutes)
        assertEquals(60 * 60, manager.state.value.remainingSeconds)

        // Valid mid range
        manager.setWorkDuration(45)
        assertEquals(45, manager.state.value.workDurationMinutes)
        assertEquals(45 * 60, manager.state.value.remainingSeconds)
    }

    @Test
    fun testClampingBreakDuration() {
        val manager = PomodoroManager(null, testScope)

        // Lower than min (0) -> clamps to 0
        manager.setBreakDuration(-5)
        assertEquals(0, manager.state.value.breakDurationMinutes)

        // Higher than max (25) -> clamps to 25
        manager.setBreakDuration(30)
        assertEquals(25, manager.state.value.breakDurationMinutes)

        // Valid 0 min break
        manager.setBreakDuration(0)
        assertEquals(0, manager.state.value.breakDurationMinutes)
    }

    @Test
    fun testStartPauseResetLifecycle() {
        val manager = PomodoroManager(null, testScope)
        manager.setWorkDuration(20)

        assertEquals(PomodoroStatus.IDLE, manager.state.value.status)
        assertEquals(20 * 60, manager.state.value.remainingSeconds)

        // Start
        manager.start()
        assertEquals(PomodoroStatus.RUNNING, manager.state.value.status)
        assertTrue(manager.state.value.isRunning)

        // Pause
        manager.pause()
        assertEquals(PomodoroStatus.PAUSED, manager.state.value.status)
        assertTrue(manager.state.value.isPaused)

        // Reset
        manager.reset()
        assertEquals(PomodoroStatus.IDLE, manager.state.value.status)
        assertEquals(20 * 60, manager.state.value.remainingSeconds)
    }

    @Test
    fun testSkipPhase() {
        val manager = PomodoroManager(null, testScope)
        manager.setWorkDuration(25)
        manager.setBreakDuration(5)

        assertEquals(PomodoroPhase.WORK, manager.state.value.phase)
        assertEquals(25 * 60, manager.state.value.remainingSeconds)

        // Skip to Break
        manager.skipPhase()
        assertEquals(PomodoroPhase.BREAK, manager.state.value.phase)
        assertEquals(5 * 60, manager.state.value.remainingSeconds)

        // Skip back to Work
        manager.skipPhase()
        assertEquals(PomodoroPhase.WORK, manager.state.value.phase)
        assertEquals(25 * 60, manager.state.value.remainingSeconds)
    }

    @Test
    fun testFormattedTimeAndProgress() {
        val manager = PomodoroManager(null, testScope)
        manager.setWorkDuration(15) // 900 seconds

        assertEquals("15:00", manager.state.value.formattedTime)
        assertEquals(0f, manager.state.value.progress, 0.01f)
    }
}
