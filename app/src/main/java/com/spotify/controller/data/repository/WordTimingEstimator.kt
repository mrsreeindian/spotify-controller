package com.spotify.controller.data.repository

import com.spotify.controller.data.model.WordTiming
import kotlin.math.max
import kotlin.math.min

/**
 * Natural vocal tempo word timing estimator.
 * Used for standard line-synced lyrics where server provides line timestamps but not inner word spans.
 *
 * Calculates realistic vocal phrasing duration (250ms - 450ms per word based on phonemes and vowels),
 * ensuring words NEVER stretch artificially across instrumental solos or long breaks.
 */
object WordTimingEstimator {

    private val VOWELS = setOf('a', 'e', 'i', 'o', 'u', 'y', 'A', 'E', 'I', 'O', 'U', 'Y')
    private val PUNCTUATION_HOLD = setOf(',', '.', '!', '?', ';', ':', '—', '-', '…')

    fun estimate(lineText: String, lineStartMs: Long, nextLineStartMs: Long): List<WordTiming> {
        val trimmed = lineText.trim()
        if (trimmed.isEmpty() || nextLineStartMs <= lineStartMs) return emptyList()

        val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()

        // Natural vocal duration: average human singing speed is ~350ms per word + vowel holds
        val naturalDurationMs = tokens.sumOf { word ->
            var dur = 260L + (word.length * 35L)
            val vowels = word.count { it in VOWELS }
            dur += (vowels * 65L)
            if (word.any { it in PUNCTUATION_HOLD }) {
                dur += 160L
            }
            dur
        }

        // Available time before next line (leaving at least 400ms pause)
        val maxAvailableDuration = if (nextLineStartMs > lineStartMs) {
            max(600L, nextLineStartMs - lineStartMs - 400L)
        } else {
            naturalDurationMs
        }

        // Cap to natural singing duration: never stretch a 3-second phrase over a 15-second guitar solo!
        val activeSingDuration = min(naturalDurationMs, maxAvailableDuration)

        // Musical onset breath lead-in (capped at 120ms)
        val introPause = (activeSingDuration * 0.04f).toLong().coerceIn(0L, 120L)
        val singingPool = max(activeSingDuration - introPause, tokens.size * 160L)

        // Calculate proportional weights per word
        val weights = tokens.map { word ->
            var weight = word.length.toFloat()
            val vowels = word.count { it in VOWELS }
            weight += (vowels * 1.5f)
            if (word.any { it in PUNCTUATION_HOLD }) {
                weight += 2.0f
            }
            max(weight, 1.0f)
        }

        val totalWeight = weights.sum().coerceAtLeast(1.0f)
        var currentStart = lineStartMs + introPause
        val wordTimings = ArrayList<WordTiming>(tokens.size)

        for (i in tokens.indices) {
            val word = tokens[i]
            val share = weights[i] / totalWeight
            val wordDuration = (singingPool * share).toLong().coerceAtLeast(140L)
            val wordEnd = currentStart + wordDuration

            wordTimings.add(
                WordTiming(
                    word = word,
                    startTimeMs = currentStart,
                    endTimeMs = wordEnd
                )
            )

            currentStart = wordEnd
        }

        return wordTimings
    }
}
