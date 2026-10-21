package com.spotify.controller.data.repository

import com.spotify.controller.data.model.LyricLine
import com.spotify.controller.data.model.WordTiming
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Timed Text Markup Language (TTML) parser supporting Apple Music / Musixmatch / Spicy Lyrics format.
 * Accurately extracts syllable & word-level timing spans, with intelligent cadence fallback for plain lines.
 */
object TtmlParser {

    fun parse(ttmlContent: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        var lineIndex = 0

        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(InputSource(StringReader(ttmlContent)))

            val pList = doc.getElementsByTagName("p")
            for (i in 0 until pList.length) {
                val p = pList.item(i) as? Element ?: continue
                val pBegin = parseTimeToMs(p.getAttribute("begin"))
                var pEnd = parseTimeToMs(p.getAttribute("end"))

                val words = mutableListOf<WordTiming>()
                val allSpans = p.getElementsByTagName("span")

                // Filter to leaf spans (spans that don't contain other spans)
                val leafSpans = mutableListOf<Element>()
                for (j in 0 until allSpans.length) {
                    val s = allSpans.item(j) as? Element ?: continue
                    if (s.getElementsByTagName("span").length == 0) {
                        leafSpans.add(s)
                    }
                }

                if (leafSpans.isNotEmpty()) {
                    for (span in leafSpans) {
                        val rawText = span.textContent ?: ""
                        val wordText = rawText.trim()
                        if (wordText.isNotEmpty()) {
                            val spanBegin = parseTimeToMs(span.getAttribute("begin"))
                            val spanEnd = parseTimeToMs(span.getAttribute("end"))
                            val isBg = span.getAttribute("ttm:role") == "x-bg"

                            val start = if (spanBegin > 0L) spanBegin else pBegin
                            val end = if (spanEnd > start) spanEnd else (if (pEnd > start) pEnd else start + 400L)

                            words.add(
                                WordTiming(
                                    word = wordText,
                                    startTimeMs = start,
                                    endTimeMs = end,
                                    isBackground = isBg
                                )
                            )
                        }
                    }
                }

                val fullText = if (words.isNotEmpty()) {
                    words.joinToString(" ") { it.word }
                } else {
                    p.textContent?.trim().orEmpty()
                }

                if (fullText.isNotBlank()) {
                    if (pEnd <= pBegin) {
                        pEnd = if (words.isNotEmpty()) words.last().endTimeMs else pBegin + 4000L
                    }

                    // Fallback to intelligent cadence estimator if line has no inner spans
                    val finalWords = if (words.isEmpty()) {
                        WordTimingEstimator.estimate(fullText, pBegin, pEnd)
                    } else {
                        // Ensure last word closes with the line
                        if (words.size > 1 && words.last().endTimeMs < pEnd - 200L) {
                            val last = words.last()
                            words[words.lastIndex] = last.copy(endTimeMs = pEnd)
                        }
                        words
                    }

                    // Insert lead-in dots for instrumental breaks >= 4s (Spicy Lyrics feature)
                    if (lines.isNotEmpty()) {
                        val prevEnd = lines.last().endTimeMs
                        if (pBegin - prevEnd >= 4000L) {
                            lines.add(
                                LyricLine(
                                    id = lineIndex++,
                                    startTimeMs = prevEnd + 300L,
                                    endTimeMs = pBegin - 200L,
                                    text = "• • •",
                                    isDotLine = true
                                )
                            )
                        }
                    }

                    lines.add(
                        LyricLine(
                            id = lineIndex++,
                            startTimeMs = pBegin,
                            endTimeMs = pEnd,
                            text = fullText,
                            words = finalWords
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Return any lines successfully parsed
        }

        return lines.sortedBy { it.startTimeMs }
    }

    fun parseTimeToMs(timeStr: String?): Long {
        if (timeStr.isNullOrBlank()) return 0L
        val clean = timeStr.trim()

        return try {
            when {
                clean.endsWith("ms", ignoreCase = true) -> {
                    clean.removeSuffix("ms").removeSuffix("MS").toDouble().toLong()
                }
                clean.endsWith("s", ignoreCase = true) -> {
                    (clean.removeSuffix("s").removeSuffix("S").toDouble() * 1000).toLong()
                }
                clean.contains(":") -> {
                    val parts = clean.split(":")
                    if (parts.size == 2) {
                        val min = parts[0].toLongOrNull() ?: 0L
                        val secParts = parts[1].split(".")
                        val sec = secParts[0].toLongOrNull() ?: 0L
                        val ms = if (secParts.size > 1) {
                            val frac = secParts[1]
                            when (frac.length) {
                                1 -> (frac.toLongOrNull() ?: 0L) * 100
                                2 -> (frac.toLongOrNull() ?: 0L) * 10
                                else -> frac.take(3).toLongOrNull() ?: 0L
                            }
                        } else 0L
                        (min * 60 + sec) * 1000 + ms
                    } else 0L
                }
                else -> {
                    (clean.toDoubleOrNull()?.times(1000))?.toLong() ?: 0L
                }
            }
        } catch (e: Exception) {
            0L
        }
    }
}
