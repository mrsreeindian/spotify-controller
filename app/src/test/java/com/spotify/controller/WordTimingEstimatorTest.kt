package com.spotify.controller

import com.spotify.controller.data.model.WordTiming
import com.spotify.controller.data.repository.WordTimingEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordTimingEstimatorTest {

    @Test
    fun testWordProgressCalculation() {
        val word = WordTiming(
            word = "Never",
            startTimeMs = 1000L,
            endTimeMs = 2000L
        )

        // Before start
        assertEquals(0f, word.progress(500L), 0.001f)
        assertEquals(0f, word.progress(1000L), 0.001f)

        // Halfway
        assertEquals(0.5f, word.progress(1500L), 0.001f)

        // 75%
        assertEquals(0.75f, word.progress(1750L), 0.001f)

        // At or after end
        assertEquals(1.0f, word.progress(2000L), 0.001f)
        assertEquals(1.0f, word.progress(2500L), 0.001f)
    }

    @Test
    fun testEstimateWordTimingsForStandardLine() {
        val line = "The club isn't the best place to find a lover"
        val startMs = 10000L
        val endMs = 15000L

        val words = WordTimingEstimator.estimate(line, startMs, endMs)

        // Should split into all 10 words
        assertEquals(10, words.size)
        assertEquals("The", words[0].word)
        assertEquals("lover", words[9].word)

        // All timestamps must be non-decreasing
        for (i in 0 until words.size - 1) {
            assertTrue(
                "Word $i (${words[i].word}) end (${words[i].endTimeMs}) <= word ${i+1} (${words[i+1].word}) start (${words[i+1].startTimeMs})",
                words[i].endTimeMs <= words[i + 1].startTimeMs + 1L
            )
            assertTrue(
                "Word ${words[i].word} duration must be positive",
                words[i].endTimeMs > words[i].startTimeMs
            )
        }

        // Start should be near line start, and end should be near line end
        assertTrue(words.first().startTimeMs >= startMs)
        assertTrue(words.last().endTimeMs <= endMs)
    }

    @Test
    fun testEmptyOrInvalidInput() {
        assertTrue(WordTimingEstimator.estimate("", 1000L, 2000L).isEmpty())
        assertTrue(WordTimingEstimator.estimate("   ", 1000L, 2000L).isEmpty())
        assertTrue(WordTimingEstimator.estimate("Hello", 2000L, 1000L).isEmpty())
    }
}
