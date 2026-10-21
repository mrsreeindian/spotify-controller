package com.spotify.controller.data.repository

import com.spotify.controller.data.model.LyricLine
import com.spotify.controller.data.model.WordTiming
import java.util.regex.Pattern

object LrcParser {

    private val LINE_TIMESTAMP_REGEX = Pattern.compile("""\[(\d{2}):(\d{2})(?:\.(\d{1,3}))?]""")
    private val WORD_TIMESTAMP_REGEX = Pattern.compile("""<(\d{2}):(\d{2})(?:\.(\d{1,3}))?>([^<]+)""")

    /**
     * Parses standard or enhanced LRC lyrics into structured LyricLine objects.
     * Extracts exact syllable/word timings when available (<mm:ss.xx>), or provides
     * intelligent phoneme cadence timings for standard line-synced lyrics.
     */
    fun parse(lrcContent: String): List<LyricLine> {
        val lines = lrcContent.lines()
        val rawEntries = mutableListOf<RawLineEntry>()
        var lineIndex = 0

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val matcher = LINE_TIMESTAMP_REGEX.matcher(trimmed)
            if (matcher.find()) {
                val min = matcher.group(1)?.toLongOrNull() ?: 0L
                val sec = matcher.group(2)?.toLongOrNull() ?: 0L
                val msPart = matcher.group(3) ?: "0"
                val ms = when (msPart.length) {
                    1 -> (msPart.toLongOrNull() ?: 0L) * 100
                    2 -> (msPart.toLongOrNull() ?: 0L) * 10
                    else -> msPart.take(3).toLongOrNull() ?: 0L
                }
                val startTimeMs = (min * 60 + sec) * 1000 + ms
                val rawLyricText = trimmed.substring(matcher.end()).trim()

                // Check for word-level sync tags e.g. <00:12.30>word
                val exactWords = parseWordTimings(rawLyricText)
                val cleanText = if (exactWords.isNotEmpty()) {
                    exactWords.joinToString(" ") { it.word }
                } else {
                    rawLyricText
                }

                rawEntries.add(
                    RawLineEntry(
                        startTimeMs = startTimeMs,
                        text = cleanText,
                        exactWords = exactWords
                    )
                )
            }
        }

        rawEntries.sortBy { it.startTimeMs }

        val result = mutableListOf<LyricLine>()
        for (i in rawEntries.indices) {
            val current = rawEntries[i]
            // Skip empty/instrumental marker lines, but use them to close previous lines
            if (current.text.isBlank()) continue

            // Determine line end timestamp
            val nextTimestamp = if (i + 1 < rawEntries.size) {
                rawEntries[i + 1].startTimeMs
            } else {
                current.startTimeMs + 4500L
            }

            // Word-by-word timing resolution
            val words = if (current.exactWords.isNotEmpty()) {
                val list = current.exactWords.toMutableList()
                // Ensure last word runs until near line end
                if (list.isNotEmpty() && nextTimestamp > list.last().startTimeMs) {
                    val last = list.last()
                    val lastEnd = (nextTimestamp - 150L).coerceAtLeast(last.startTimeMs + 200L)
                    list[list.lastIndex] = last.copy(endTimeMs = lastEnd)
                }
                list
            } else {
                // Generate cadence-based word timings for standard LRC lines
                WordTimingEstimator.estimate(current.text, current.startTimeMs, nextTimestamp)
            }

            // Insert lead-in dots for vocal breaks >= 4 seconds
            if (result.isNotEmpty()) {
                val prevEnd = result.last().endTimeMs
                if (current.startTimeMs - prevEnd >= 4000L) {
                    result.add(
                        LyricLine(
                            id = lineIndex++,
                            startTimeMs = prevEnd + 300L,
                            endTimeMs = current.startTimeMs - 200L,
                            text = "• • •",
                            isDotLine = true
                        )
                    )
                }
            }

            result.add(
                LyricLine(
                    id = lineIndex++,
                    startTimeMs = current.startTimeMs,
                    endTimeMs = nextTimestamp,
                    text = current.text,
                    words = words
                )
            )
        }

        return result
    }

    private fun parseWordTimings(text: String): List<WordTiming> {
        val matcher = WORD_TIMESTAMP_REGEX.matcher(text)
        val wordList = mutableListOf<WordTiming>()

        while (matcher.find()) {
            val min = matcher.group(1)?.toLongOrNull() ?: 0L
            val sec = matcher.group(2)?.toLongOrNull() ?: 0L
            val msPart = matcher.group(3) ?: "0"
            val ms = when (msPart.length) {
                1 -> (msPart.toLongOrNull() ?: 0L) * 100
                2 -> (msPart.toLongOrNull() ?: 0L) * 10
                else -> msPart.take(3).toLongOrNull() ?: 0L
            }
            val startMs = (min * 60 + sec) * 1000 + ms
            val word = matcher.group(4)?.trim() ?: ""

            if (word.isNotBlank()) {
                wordList.add(
                    WordTiming(
                        word = word,
                        startTimeMs = startMs,
                        endTimeMs = startMs + 400L
                    )
                )
            }
        }

        for (i in 0 until wordList.size - 1) {
            wordList[i] = wordList[i].copy(endTimeMs = wordList[i + 1].startTimeMs)
        }

        return wordList
    }

    private data class RawLineEntry(
        val startTimeMs: Long,
        val text: String,
        val exactWords: List<WordTiming>
    )
}
