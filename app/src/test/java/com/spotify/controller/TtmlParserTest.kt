package com.spotify.controller

import com.spotify.controller.data.repository.TtmlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtmlParserTest {

    @Test
    fun testParseTtmlWithWordSpans() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
                <body>
                    <div>
                        <p begin="00:10.00" end="00:15.00">
                            <span begin="00:10.00" end="00:12.00">Hello </span>
                            <span begin="00:12.00" end="00:15.00">World</span>
                        </p>
                        <p begin="00:20.00" end="00:25.00">
                            <span begin="00:20.00" end="00:22.50">Second </span>
                            <span begin="00:22.50" end="00:25.00">Line</span>
                        </p>
                    </div>
                </body>
            </tt>
        """.trimIndent()

        val lines = TtmlParser.parse(ttml)
        // Note: Because gap between 15.00 and 20.00 is >= 4000ms, an instrumental dot line is inserted!
        assertEquals(3, lines.size)

        // Line 1
        assertEquals("Hello World", lines[0].text)
        assertEquals(10000L, lines[0].startTimeMs)
        assertEquals(15000L, lines[0].endTimeMs)
        assertEquals(2, lines[0].words.size)
        assertEquals("Hello", lines[0].words[0].word)
        assertEquals(10000L, lines[0].words[0].startTimeMs)
        assertEquals(12000L, lines[0].words[0].endTimeMs)
        assertEquals("World", lines[0].words[1].word)
        assertEquals(12000L, lines[0].words[1].startTimeMs)
        assertEquals(15000L, lines[0].words[1].endTimeMs)

        // Dot line (Interlude)
        assertTrue(lines[1].isDotLine)
        assertEquals("• • •", lines[1].text)

        // Line 2
        assertEquals("Second Line", lines[2].text)
        assertEquals(20000L, lines[2].startTimeMs)
        assertEquals(25000L, lines[2].endTimeMs)
    }

    @Test
    fun testParseTimeToMsFormats() {
        assertEquals(12340L, TtmlParser.parseTimeToMs("12.34s"))
        assertEquals(5500L, TtmlParser.parseTimeToMs("5500ms"))
        assertEquals(72450L, TtmlParser.parseTimeToMs("01:12.45"))
    }
}
