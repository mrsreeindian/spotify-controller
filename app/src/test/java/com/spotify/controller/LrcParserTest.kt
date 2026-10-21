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

        assertEquals("So the bar is where I go", lines[1].text)
        assertEquals(12100L, lines[1].startTimeMs)
        assertEquals(14810L, lines[1].endTimeMs)

        assertEquals("Me and my friends at the table doing shots", lines[2].text)
        assertEquals(14810L, lines[2].startTimeMs)
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
        assertEquals(2, lines.size)
        assertEquals(10000L, lines[0].startTimeMs)
        assertEquals(15000L, lines[0].endTimeMs) // Ended at instrumental blank marker

        assertEquals(30000L, lines[1].startTimeMs)
    }
}
