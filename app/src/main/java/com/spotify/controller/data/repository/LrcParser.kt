package com.spotify.controller.data.repository

import com.spotify.controller.data.model.LyricLine
import com.spotify.controller.data.model.WordTiming
import java.util.regex.Pattern

object LrcParser {

    private val LINE_TIMESTAMP_REGEX = Pattern.compile("""\[(\d{2}):(\d{2})(?:\.(\d{1,3}))?]""")
    private val WORD_TIMESTAMP_REGEX = Pattern.compile("""<(\d{2}):(\d{2})(?:\.(\d{1,3}))?>([^<]+)""")

    /**
     * Parses standard or enhanced LRC lyrics text into structured LyricLine objects.
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
                val words = parseWordTimings(rawLyricText)
                val cleanText = if (words.isNotEmpty()) {
                    words.joinToString(" ") { it.word }
                } else {
                    rawLyricText
                }

                rawEntries.add(RawLineEntry(startTimeMs, cleanText, words))
            }
        }

        rawEntries.sortBy { it.startTimeMs }

        val result = mutableListOf<LyricLine>()
        for (i in rawEntries.indices) {
            val current = rawEntries[i]
            // If line is empty (e.g. instrumental pause marker [01:23.45]), skip adding as a line
            // but use it to close the previous line
            if (current.text.isBlank()) continue

            // Find next non-empty entry or next entry timestamp
            val nextTimestamp = if (i + 1 < rawEntries.size) {
                rawEntries[i + 1].startTimeMs
            } else {
                current.startTimeMs + 4500L
            }

            result.add(
                LyricLine(
                    id = lineIndex++,
                    startTimeMs = current.startTimeMs,
                    endTimeMs = nextTimestamp,
                    text = current.text,
                    words = current.words
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
        val words: List<WordTiming>
    )
}
