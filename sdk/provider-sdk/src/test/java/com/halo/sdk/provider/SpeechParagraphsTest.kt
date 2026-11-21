package com.halo.sdk.provider

import org.junit.Assert.*
import org.junit.Test

class SpeechParagraphsTest {
    @Test fun sendsCompleteParagraphsWithOriginalLineBreaksWhenTheyFit() {
        val first = "Halo checks the current time. It then explains the answer."
        val second = "A second paragraph follows. This stays together too."
        val text = "$first\r\n\r\n$second"
        assertEquals(listOf(text), SpeechParagraphs.sections(text).map { it.text }.toList())
        val sections = SpeechParagraphs.sections(text, packParagraphs = false).toList()
        assertEquals(listOf(first, second), sections.map { it.text })
        assertEquals(text.indexOf(second), sections.last().start)
    }

    @Test fun prefersAParagraphBoundaryAndKeepsAllNonWhitespace() {
        val first = "One sentence. Another sentence in the same paragraph."
        val text = "$first\n\n" + "A complete phrase. ".repeat(35).trim()
        val sections = SpeechParagraphs.sections(text, maxChars = 128).toList()
        assertEquals(first, sections.first().text)
        assertTrue(sections.all { it.text.length <= 128 && it.text == text.substring(it.start, it.end) })
        assertEquals(text.filterNot(Char::isWhitespace), sections.joinToString("") { it.text }.filterNot(Char::isWhitespace))
    }

    @Test fun preservesUnicodeWhenALongWordCrossesTheLimit() {
        val text = "a".repeat(127) + "🙂" + "b".repeat(115)
        val sections = SpeechParagraphs.sections(text, maxChars = 128).toList()
        assertFalse(sections.first().text.last().isHighSurrogate())
        assertEquals(text, sections.joinToString("") { it.text })
    }
}
