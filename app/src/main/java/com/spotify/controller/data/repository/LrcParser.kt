package com.spotify.controller.data.repository

import com.spotify.controller.data.model.LyricLine
import com.spotify.controller.data.model.WordTiming
import java.util.regex.Pattern

object LrcParser {

    private val LINE_TIMESTAMP_REGEX = Pattern.compile("""\[(\d{2}):(\d{2})(?:\.(\d{2,3}))?]""")
    private val WORD_TIMESTAMP_REGEX = Pattern.compile("""<(\d{2}):(\d{2})(?:\.(\d{2,3}))?>([^<]+)""")

    /**
     * Parses standard or enhanced LRC lyrics text into structured LyricLine objects.
     * Example input:
     * [00:12.34] Never gonna give you up
     * [00:15.80] <00:15.80>Never <00:16.10>gonna <00:16.45>let <00:16.80>you <00:17.10>down
     */
    fun parse(lrcContent: String): List<LyricLine> {
        val lines = lrcContent.lines()
        val result = mutableListOf<LyricLine>()
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
                    2 -> msPart.toLongOrNull()?.times(10) ?: 0L
                    else -> msPart.toLongOrNull() ?: 0L
                }
                val startTimeMs = (min * 60 + sec) * 1000 + ms
                val rawLyricText = trimmed.substring(matcher.end()).trim()

                // Check for word-level sync tags e.g. <00:12.30>word
                val words = parseWordTimings(rawLyricText)
                val cleanText = if (words.isNotEmpty()) {
                    words.joinToString(" ") { it.word }
                } else {
                    rawLyricText
                }

                if (cleanText.isNotBlank()) {
                    result.add(
                        LyricLine(
                            id = lineIndex++,
                            startTimeMs = startTimeMs,
                            text = cleanText,
                            words = words
                        )
                    )
                }
            }
        }

        // Sort by start time and compute endTimeMs based on next line's start time
        result.sortBy { it.startTimeMs }
        for (i in 0 until result.size) {
            val current = result[i]
            val nextStart = if (i + 1 < result.size) result[i + 1].startTimeMs else current.startTimeMs + 4000L
            result[i] = current.copy(endTimeMs = nextStart)
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
                2 -> msPart.toLongOrNull()?.times(10) ?: 0L
                else -> msPart.toLongOrNull() ?: 0L
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

        // Fix word end times to match next word's start time
        for (i in 0 until wordList.size - 1) {
            wordList[i] = wordList[i].copy(endTimeMs = wordList[i + 1].startTimeMs)
        }

        return wordList
    }
}
