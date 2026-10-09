package com.halo.app.study

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class StudyDocxTextTest {
    private fun archive(xml: String): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip -> zip.putNextEntry(ZipEntry("word/document.xml")); zip.write(xml.toByteArray()); zip.closeEntry() }
    }.toByteArray()
    @Test fun paragraphTabsBreaksAndDiacriticsArePreserved() {
        val xml="""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>Latviešu</w:t><w:tab/><w:t>āčēģ</w:t><w:br/><w:t>next</w:t></w:r></w:p><w:p><w:r><w:t>Second</w:t></w:r></w:p></w:body></w:document>"""
        assertEquals("Latviešu\tāčēģ\nnext\nSecond",StudyDocxText.read(archive(xml).inputStream()))
    }
    @Test fun externalEntitiesAreRejected() {
        val xml="""<!DOCTYPE doc [<!ENTITY secret SYSTEM "file:///private/secret">]><doc>&secret;</doc>"""
        assertTrue(runCatching { StudyDocxText.read(archive(xml).inputStream()) }.isFailure)
    }
    @Test fun archiveExpansionIsBounded() {
        assertTrue(runCatching { StudyDocxText.read(archive("x".repeat(12*1024*1024+1)).inputStream()) }.isFailure)
    }
    @Test fun sentenceRangesKeepWhitespaceAndRepeatedTextIdentity() {
        val text="  Hello.\n\nHello.   Ābols! "
        val ranges=SentenceSegmenter.ranges(text)
        assertEquals(listOf("Hello.","Hello.","Ābols!"),ranges.map {it.text})
        assertEquals(listOf(2,10,19),ranges.map {it.start})
        ranges.forEach {assertEquals(it.text,text.substring(it.start,it.end))}
    }
}
