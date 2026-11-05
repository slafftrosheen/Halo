package com.halo.app.everyday.knowledge

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [33], application = android.app.Application::class)
class ReferenceImportParserTest {
    private fun read(name: String, text: String): List<ReferenceEntry> = buildList {
        ReferenceImportParser.parse(name, ByteArrayInputStream(text.toByteArray()), onEntry = { add(it) })
    }
    @Test fun csvPreservesQuotedCommasNewlinesAndSourceMetadata() {
        val value = read("own.csv", "id,title,text,language,url,license\r\n1,\"Water, safely\",\"First line\nSecond \"\"quoted\"\" line\",en,https://example.org/a,CC0\r\n").single()
        assertEquals("Water, safely", value.title)
        assertEquals("First line\nSecond \"quoted\" line", value.text)
        assertEquals("CC0", value.license)
    }
    @Test fun currentWikiXmlRetainsRevisionAndUnexpandedTemplateText() {
        val value = read("wiki.xml", "<mediawiki><siteinfo><base>https://en.wikipedia.org/wiki/Main_Page</base><lang>en</lang></siteinfo><page><title>Water</title><ns>0</ns><id>42</id><revision><id>7</id><timestamp>2026-11-01T00:00:00Z</timestamp><text>== Safety ==\n{{keep|units=mg}} clean</text></revision></page></mediawiki>").single()
        assertEquals("7", value.revision); assertEquals("42", value.id)
        assertEquals(ReferenceTextFormat.WIKITEXT, value.format)
        assertTrue(value.text.contains("{{keep|units=mg}}"))
    }
    @Test fun medlineExternalDtdIsIgnoredWithoutFetchingAndSummariesKeepLinks() {
        val value = read("topics.xml", "<?xml version=\"1.0\"?><!DOCTYPE health-topics SYSTEM \"https://invalid.example/dtd\"><health-topics date-generated=\"2026-11-04\"><health-topic id=\"1\" title=\"First aid\" language=\"English\" url=\"https://medlineplus.gov/firstaid.html\"><full-summary>&lt;p&gt;A &amp;amp; B&lt;/p&gt;</full-summary><also-called>Emergency help</also-called></health-topic></health-topics>").single()
        assertEquals("en", value.language); assertEquals("First aid", value.title)
        assertTrue(value.text.contains("A & B")); assertTrue(value.text.contains("Emergency help"))
        assertEquals("MedlinePlus, National Library of Medicine", value.publisher)
    }
    @Test fun internalEntitiesHistoryAndUnexpectedZipEntriesFailClosed() {
        assertTrue(runCatching { read("bad.xml", "<!DOCTYPE mediawiki [<!ENTITY x 'expanded'>]><mediawiki>&x;</mediawiki>") }.isFailure)
        assertTrue(runCatching { read("history.xml", "<mediawiki><page><title>A</title><id>1</id><revision><id>1</id><text>a</text></revision><revision><id>2</id><text>b</text></revision></page></mediawiki>") }.isFailure)
        val bytes = ByteArrayOutputStream().apply { ZipOutputStream(this).use { zip ->
            for (name in listOf("data.xml", "../other.xml")) { zip.putNextEntry(ZipEntry(name)); zip.write("<health-topics/>".toByteArray()); zip.closeEntry() }
        } }.toByteArray()
        assertTrue(runCatching { ReferenceImportParser.parse("bad.zip", ByteArrayInputStream(bytes)) {} }.isFailure)
    }
    @Test fun anOversizedRecordFailsRatherThanSilentlyPublishingPartialData() {
        assertTrue(runCatching { read("huge.jsonl", "{\"title\":\"Huge\",\"text\":\"${"x".repeat(ReferenceLimits.ENTRY_BYTES + 1)}\"}") }.isFailure)
    }
    @Test fun gzipAndXmlPrefixBoundaryPreserveUtf8AndCdataWithoutAllowingEntities() {
        val text = "ā😀 вода".repeat(1500)
        val xml = "<mediawiki><page><title>Ūdens</title><id>1</id><revision><id>9</id><text><![CDATA[$text <!DOCTYPE literal>]]></text></revision></page></mediawiki>"
        val compressed = ByteArrayOutputStream().apply { java.util.zip.GZIPOutputStream(this).use { it.write(xml.toByteArray(Charsets.UTF_8)) } }.toByteArray()
        val entries = mutableListOf<ReferenceEntry>()
        ReferenceImportParser.parse("wiki.xml.gz", ByteArrayInputStream(compressed), onEntry = { entries.add(it) })
        assertEquals("$text <!DOCTYPE literal>", entries.single().text)
        assertEquals("User-supplied; rights not verified", entries.single().license)
        assertTrue(runCatching { ReferenceImportParser.parse("bad.jsonl", ByteArrayInputStream(byteArrayOf(0xc3.toByte(), 0x28))) {} }.isFailure)
    }
    @Test fun byteAccountingIncludesSkippedBytesAndRejectsAtTheLimit() {
        val stream = ReferenceImportParser.BoundedInput(ByteArrayInputStream(ByteArray(64)), 16)
        assertEquals(8, stream.skip(8)); assertEquals(8, stream.count)
        assertTrue(runCatching { stream.skip(20) }.isFailure)
    }

    @Test fun aggregateTopicBudgetStopsReadingBeforeRetainingAnUnboundedSynonymList() {
        val xml = "<health-topics><health-topic id='1' title='Owned' language='English'><full-summary>Summary</full-summary>" +
            ("<also-called>" + "x".repeat(4096) + "</also-called>").repeat(1024) + "</health-topic></health-topics>"
        var consumed = 0L
        assertTrue(runCatching { ReferenceImportParser.parse("medical.xml", xml.byteInputStream(), { consumed = it }) {} }.isFailure)
        assertTrue("Parser consumed $consumed before rejecting aggregate content", consumed < 512 * 1024)
    }
    @Test fun incompleteMainNamespaceArticleCannotBeSilentlyDroppedFromAReadyImport() {
        val valid = "<page><title>First</title><ns>0</ns><id>1</id><revision><id>9</id><text>Valid</text></revision></page>"
        val missing = "<page><title>Missing</title><ns>0</ns><id>2</id><revision><id>10</id></revision></page>"
        assertTrue(runCatching { read("wiki.xml", "<mediawiki>$valid$missing</mediawiki>") }.isFailure)
    }

    @Test fun emptyOptionalCsvCellsUseHonestDefaultsInsteadOfFailingDuringPublication() {
        val entry = read("optional.csv", "title,text,language,format,publisher,license\nOwned,Body,,,,\n").single()
        assertEquals("und", entry.language); assertEquals(ReferenceTextFormat.TEXT, entry.format)
        assertEquals("Imported library", entry.publisher); assertEquals("User-supplied; rights not verified", entry.license)
    }

    @Test fun aValidSingleXmlZipParsesWithoutClosingTheCallersStream() {
        val xml = "<health-topics><health-topic id='1' title='Water' language='English'><full-summary>Owned summary</full-summary></health-topic></health-topics>"
        val bytes = ByteArrayOutputStream().apply { ZipOutputStream(this).use { zip ->
            zip.putNextEntry(ZipEntry("topics.xml")); zip.write(xml.toByteArray()); zip.closeEntry()
        } }.toByteArray()
        var closed = false
        val input = object : java.io.FilterInputStream(ByteArrayInputStream(bytes)) {
            override fun close() { closed = true; super.close() }
            override fun read(): Int { check(!closed); return super.read() }
            override fun read(b: ByteArray, off: Int, len: Int): Int { check(!closed); return super.read(b, off, len) }
        }
        val entries = mutableListOf<ReferenceEntry>()
        ReferenceImportParser.parse("topics.zip", input, onEntry = { entries.add(it) })
        assertEquals("Owned summary", entries.single().text)
        assertFalse("Only the caller owns its input stream", closed)
        input.close(); assertTrue(closed)
    }
    @Test fun jsonLinesRejectDeepNestingBeforeRecursiveParsingButAcceptEscapedBodiesAtTheDecodedLimit() {
        val deep = "{\"title\":\"Nested\",\"text\":\"Body\",\"extra\":" + "[".repeat(5000) + "0" + "]".repeat(5000) + "}"
        assertTrue(runCatching { read("deep.jsonl", deep) }.exceptionOrNull() is IllegalArgumentException)
        val escaped = "{\"title\":\"Escaped\",\"text\":\"" + "\\u0061".repeat(ReferenceLimits.ENTRY_BYTES) + "\"}"
        assertEquals("a".repeat(ReferenceLimits.ENTRY_BYTES), read("escaped.jsonl", escaped).single().text)
    }

}
