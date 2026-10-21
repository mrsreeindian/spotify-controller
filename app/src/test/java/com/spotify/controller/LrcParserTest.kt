package com.spotify.controller

import com.spotify.controller.data.model.Lyrics
import com.spotify.controller.data.repository.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun testParseStandardLrc() {
        val lrc = """
            [00:09.65] The club isn't the best place to find a lover
            [00:12.10] So the bar is where I go
            [00:14.81] Me and my friends at the table doing shots
        """.trimIndent()

        val lines = LrcParser.parse(lrc)
        assertEquals(3, lines.size)

        assertEquals("The club isn't the best place to find a lover", lines[0].text)
        assertEquals(9650L, lines[0].startTimeMs)
        assertEquals(12100L, lines[0].endTimeMs)
        // Standard LRC preserves pure line synchronization to stay 100% in sync with playback
        assertTrue(lines[0].words.isEmpty())

        assertEquals("So the bar is where I go", lines[1].text)
        assertEquals(12100L, lines[1].startTimeMs)
        assertEquals(14810L, lines[1].endTimeMs)
        assertTrue(lines[1].words.isEmpty())

        assertEquals("Me and my friends at the table doing shots", lines[2].text)
        assertEquals(14810L, lines[2].startTimeMs)
        assertTrue(lines[2].words.isEmpty())
    }

    @Test
    fun testParseEnhancedLrcExactWordTimings() {
        val enhancedLrc = """
            [00:10.00] <00:10.00>Never <00:10.80>gonna <00:11.50>give <00:12.20>you <00:12.80>up
            [00:15.00] <00:15.00>Never <00:15.80>gonna <00:16.50>let <00:17.20>you <00:17.80>down
        """.trimIndent()

        val lines = LrcParser.parse(enhancedLrc)
        assertEquals(2, lines.size)

        // Line 1
        val line1 = lines[0]
        assertEquals("Never gonna give you up", line1.text)
        assertEquals(5, line1.words.size)
        assertEquals("Never", line1.words[0].word)
        assertEquals(10000L, line1.words[0].startTimeMs)
        assertEquals(10800L, line1.words[0].endTimeMs)

        assertEquals("gonna", line1.words[1].word)
        assertEquals(10800L, line1.words[1].startTimeMs)
        assertEquals(11500L, line1.words[1].endTimeMs)

        // Last word of line 1 extends towards next line
        assertEquals("up", line1.words[4].word)
        assertEquals(12800L, line1.words[4].startTimeMs)
        assertTrue(line1.words[4].endTimeMs > 12800L)
    }

    @Test
    fun testActiveLineIndexCalculation() {
        val lrc = """
            [00:10.00] Line one
            [00:20.00] Line two
            [00:30.00] Line three
        """.trimIndent()

        val lines = LrcParser.parse(lrc)
        val lyrics = Lyrics(
            trackId = "test",
            title = "Test Song",
            artist = "Test Artist",
            lines = lines,
            isSynced = true
        )

        // Before first line
        assertEquals(-1, lyrics.getActiveLineIndex(5000L))

        // During line 1
        assertEquals(0, lyrics.getActiveLineIndex(10000L))
        assertEquals(0, lyrics.getActiveLineIndex(15000L))

        // During line 2
        assertEquals(1, lyrics.getActiveLineIndex(20000L))
        assertEquals(1, lyrics.getActiveLineIndex(25000L))

        // During line 3
        assertEquals(2, lyrics.getActiveLineIndex(30000L))
        assertEquals(2, lyrics.getActiveLineIndex(34000L))
    }

    @Test
    fun testInstrumentalBlankLineClosesPreviousLine() {
        val lrc = """
            [00:10.00] Verse line
            [00:15.00] 
            [00:30.00] Chorus line
        """.trimIndent()

        val lines = LrcParser.parse(lrc)
        // With interlude detection (30000 - 15000 >= 4000), a dot line is inserted
        val verseLine = lines.first { it.text == "Verse line" }
        assertEquals(10000L, verseLine.startTimeMs)
        assertEquals(15000L, verseLine.endTimeMs)

        val dotLine = lines.first { it.isDotLine }
        assertEquals("• • •", dotLine.text)

        val chorusLine = lines.first { it.text == "Chorus line" }
        assertEquals(30000L, chorusLine.startTimeMs)
    }
}
