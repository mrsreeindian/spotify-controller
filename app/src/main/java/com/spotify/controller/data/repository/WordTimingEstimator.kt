package com.spotify.controller.data.repository

import com.spotify.controller.data.model.WordTiming
import kotlin.math.max

/**
 * Intelligent phoneme and cadence-based word timing interpolator.
 * Used as a fallback when songs only provide line-level synchronization (standard LRC / plain TTML).
 *
 * Spreads line duration across each word proportionally according to:
 * - Word character length
 * - Vowel weight (vowels are sustained by vocalists, consonants are brief)
 * - Punctuation hold weight (commas, periods, dashes introduce musical cadence)
 * - Natural vocal onset (intro breath) and decay release (outro hold)
 */
object WordTimingEstimator {

    private val VOWELS = setOf('a', 'e', 'i', 'o', 'u', 'y', 'A', 'E', 'I', 'O', 'U', 'Y')
    private val PUNCTUATION_HOLD = setOf(',', '.', '!', '?', ';', ':', '—', '-', '…')

    fun estimate(lineText: String, lineStartMs: Long, lineEndMs: Long): List<WordTiming> {
        val trimmed = lineText.trim()
        if (trimmed.isEmpty() || lineEndMs <= lineStartMs) return emptyList()

        val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()

        val totalDuration = max(600L, lineEndMs - lineStartMs)

        // Musical onset: small vocal breath lead-in (3% - 5%, capped at 180ms)
        val introPause = (totalDuration * 0.04f).toLong().coerceIn(0L, 180L)
        // Musical release: trailing breath decay (6% - 10%, capped at 300ms)
        val trailPause = (totalDuration * 0.08f).toLong().coerceIn(40L, 300L)

        val singDuration = max(totalDuration - introPause - trailPause, tokens.size * 180L)

        // Weight calculation per word
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
            val wordDuration = (singDuration * share).toLong().coerceAtLeast(140L)
            val wordEnd = if (i == tokens.lastIndex) {
                lineEndMs - (trailPause / 2)
            } else {
                currentStart + wordDuration
            }

            wordTimings.add(
                WordTiming(
                    word = word,
                    startTimeMs = currentStart,
                    endTimeMs = max(wordEnd, currentStart + 80L)
                )
            )

            currentStart = wordEnd
        }

        return wordTimings
    }
}
