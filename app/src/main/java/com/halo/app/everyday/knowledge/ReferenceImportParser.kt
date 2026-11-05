package com.halo.app.everyday.knowledge

import java.io.*
import java.nio.charset.CodingErrorAction
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.json.JSONObject
import org.jsoup.Jsoup
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

/** Streaming only. No network, extraction, external entities, SQL or model calls. */
object ReferenceImportParser {
    private class Utf8Budget(private val limit: Int) {
        private var bytes = 0
        private var highSurrogate = false
        fun append(chars: CharArray, start: Int, length: Int) {
            for (index in start until start + length) {
                val char = chars[index]
                bytes += when {
                    highSurrogate && Character.isLowSurrogate(char) -> 1 // Paired surrogate: 3 + 1 bytes.
                    char.code <= 0x7f -> 1
                    char.code <= 0x7ff -> 2
                    else -> 3
                }
                highSurrogate = Character.isHighSurrogate(char)
                require(bytes <= limit) { "XML topic exceeds its aggregate entry limit; choose a smaller subset" }
            }
        }
        fun append(text: String) = append(text.toCharArray(), 0, text.length)
    }
    /** Android's SAX implementations support different feature switches. Enforce this
     * boundary in the byte stream as well, before a parser can act on a declaration. */
    private class NoDtdInput(input: InputStream) : FilterInputStream(input) {
        private val tail = StringBuilder()
        private var comment = false
        private var cdata = false
        private var markup = false
        private var markupBytes = 0
        private var declarationBytes = 0
        private fun scan(value: Int) {
            require(value != 0) { "XML must use UTF-8, without NUL bytes" }
            tail.append(value.toChar()); if (tail.length > 12) tail.deleteCharAt(0)
            if (cdata) {
                require(++declarationBytes <= ReferenceLimits.ENTRY_BYTES + 3) { "XML CDATA exceeds the entry limit" }
                if (value == '>'.code && tail.endsWith("]]>")) { cdata = false; markup = false; tail.setLength(0) }; return
            }
            if (comment) {
                require(++declarationBytes <= ReferenceLimits.METADATA_BYTES + 3) { "XML comment exceeds the metadata limit" }
                if (value == '>'.code && tail.endsWith("-->")) { comment = false; markup = false; tail.setLength(0) }; return
            }
            if (value == '<'.code) { markup = true; markupBytes = 0 }
            if (markup) require(++markupBytes <= ReferenceLimits.METADATA_BYTES) { "XML tag or attributes exceed the metadata limit" }
            if (value == '>'.code) markup = false
            if (value == '['.code && tail.endsWith("<![CDATA[")) { cdata = true; declarationBytes = 0; return }
            if (value == '-'.code && tail.endsWith("<!--")) { comment = true; declarationBytes = 0; return }
            require(!(value == 'E'.code && tail.endsWith("<!DOCTYPE") || value == 'Y'.code && tail.endsWith("<!ENTITY"))) { "DTD and entity declarations are not supported" }
        }
        override fun skip(n: Long): Long {
            require(n >= 0); var remaining = n; val buffer = ByteArray(8192)
            while (remaining > 0) { val got = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt()); if (got < 0) break; remaining -= got }
            return n - remaining
        }
        override fun read(): Int = `in`.read().also { if (it >= 0) scan(it) }
        override fun read(bytes: ByteArray, off: Int, len: Int): Int = `in`.read(bytes, off, len).also { n -> for (i in 0 until maxOf(0, n)) scan(bytes[off + i].toInt() and 255) }
    }
    class BoundedInput(input: InputStream, private val limit: Long, private val progress: (Long) -> Unit = {}) : FilterInputStream(input) {
        var count = 0L; private set
        private fun account(n: Int) { if (n > 0) { count += n; require(count <= limit) { "Import exceeds its byte limit; choose a smaller subset" }; progress(count) } }
        override fun read(): Int = `in`.read().also { if (it >= 0) account(1) }
        override fun read(bytes: ByteArray, off: Int, len: Int): Int = `in`.read(bytes, off, len).also(::account)
        override fun skip(n: Long): Long { var remaining = n; val b = ByteArray(8192); while (remaining > 0) { val k = read(b, 0, minOf(b.size.toLong(), remaining).toInt()); if (k < 0) break; remaining -= k }; return n - remaining }
    }

    fun parse(fileName: String, input: InputStream, progress: (Long) -> Unit = {}, onEntry: (ReferenceEntry) -> Unit) {
        val compressed = BoundedInput(input, ReferenceLimits.INPUT_BYTES, progress)
        val buffered = BufferedInputStream(compressed)
        buffered.mark(8); val magic = ByteArray(4); val n = buffered.read(magic); buffered.reset()
        val zipped = n >= 2 && magic[0] == 0x50.toByte() && magic[1] == 0x4b.toByte()
        val zip = if (zipped) ZipInputStream(buffered) else null
        val expanded = when {
            zip != null -> {
                val entry = requireNotNull(zip.nextEntry) { "Empty archive" }
                require(!entry.isDirectory && !entry.name.contains('/') && !entry.name.contains('\\') && !entry.name.contains("..") && entry.name.lowercase(Locale.ROOT).endsWith(".xml")) { "Only a single XML data file is supported in ZIP" }
                zip
            }
            n >= 2 && magic[0] == 0x1f.toByte() && magic[1] == 0x8b.toByte() -> GZIPInputStream(buffered)
            n >= 3 && magic[0] == 'B'.code.toByte() && magic[1] == 'Z'.code.toByte() && magic[2] == 'h'.code.toByte() -> BZip2CompressorInputStream(buffered, true)
            else -> buffered
        }
        val bounded = BoundedInput(expanded, ReferenceLimits.EXPANDED_BYTES) { progress(compressed.count) }
        var ordinal = 0
        val emit: (ReferenceEntry) -> Unit = { value -> ReferenceLimits.validate(value); ordinal++; onEntry(value) }
        val name = fileName.lowercase(Locale.ROOT).removeSuffix(".gz").removeSuffix(".bz2")
        when {
            zip != null || name.endsWith(".xml") -> xml(bounded, emit)
            name.endsWith(".jsonl") || name.endsWith(".ndjson") -> jsonLines(bounded, emit)
            name.endsWith(".csv") -> csv(bounded, emit)
            else -> error("Choose UTF-8 XML, JSONL or CSV, optionally gzip/bzip2, or the MedlinePlus XML ZIP")
        }
        require(zip?.nextEntry == null) { "Archive contains unexpected additional files" }
        // Count trailing bytes as well; parser EOF is not permission to ignore an oversized input.
        while (compressed.read() >= 0) Unit
    }

    private fun reader(input: InputStream): Reader = InputStreamReader(input, Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)).buffered()

    private fun jsonLines(input: InputStream, emit: (ReferenceEntry) -> Unit) {
        val r = reader(input); val line = StringBuilder(); var ordinal = 0
        fun flush() { if (line.isNotBlank()) { ordinal++; emit(fromJson(JSONObject(line.toString()), ordinal)) }; line.setLength(0) }
        while (true) { val c = r.read(); if (c < 0) break; if (c == 10) flush() else { require(line.length < ReferenceLimits.ENTRY_BYTES + ReferenceLimits.METADATA_BYTES) { "JSONL record exceeds limit" }; line.append(c.toChar()) } }
        flush()
    }
    private fun fromJson(row: JSONObject, ordinal: Int) = ReferenceEntry(
        row.optString("id").ifBlank { ordinal.toString() }, row.getString("title"), row.getString("text"),
        row.optString("language", "und").ifBlank { "und" }, row.optString("url").takeIf { it.isNotBlank() }, row.optString("publisher", "Imported library").ifBlank { "Imported library" },
        row.optString("revision").takeIf { it.isNotBlank() }, row.optString("license", "User-supplied; rights not verified").ifBlank { "User-supplied; rights not verified" },
        row.optString("updatedAt").takeIf { it.isNotBlank() },
        row.optString("format", "TEXT").ifBlank { "TEXT" }.let(ReferenceTextFormat::valueOf), com.halo.app.everyday.collectors.SourceImages.decode(row.optJSONArray("images")))

    private fun csv(input: InputStream, emit: (ReferenceEntry) -> Unit) {
        val r = PushbackReader(reader(input), 1); var header: List<String>? = null; val row = mutableListOf<String>(); val field = StringBuilder(); var quoted = false; var closedQuote = false; var ordinal = 0
        fun cell() { require(row.size < 32); row.add(field.toString()); field.setLength(0); closedQuote = false }
        fun record() {
            cell()
            if (header == null) { header = row.map { it.removePrefix("\uFEFF").trim() }; require(header!!.distinct().size == header!!.size && header!!.containsAll(listOf("title", "text"))) { "CSV needs title and text headers; optional id, language, url, publisher, revision, license, updatedAt, format" } }
            else if (row.any { it.isNotEmpty() }) { require(row.size == header!!.size) { "CSV field count differs from header" }; ordinal++; emit(fromJson(JSONObject(header!!.zip(row).toMap()), ordinal)) }
            row.clear()
        }
        while (true) {
            val value = r.read(); if (value < 0) break; val c = value.toChar()
            if (quoted) {
                if (c == '"') { val next = r.read(); if (next == '"'.code) field.append('"') else { quoted = false; closedQuote = true; if (next >= 0) r.unread(next) } }
                else field.append(c)
            } else when (c) {
                '"' -> { require(field.isEmpty() && !closedQuote); quoted = true }
                ',' -> cell()
                '\n' -> record()
                '\r' -> { val next = r.read(); if (next >= 0 && next != 10) r.unread(next); record() }
                else -> { require(!closedQuote) { "Unexpected text after quoted CSV field" }; field.append(c) }
            }
            val body = header?.getOrNull(row.size) == "text"
            require(field.length <= if (body) ReferenceLimits.ENTRY_BYTES else ReferenceLimits.METADATA_BYTES) { "CSV field exceeds limit" }
        }
        require(!quoted) { "Unclosed CSV quote" }
        if (field.isNotEmpty() || row.isNotEmpty()) record()
        require(header != null) { "Empty CSV" }
    }

    private fun xml(input: InputStream, emit: (ReferenceEntry) -> Unit) {
        // NLM includes a public external DTD. Strip only that simple declaration, never load it.
        // All remaining DOCTYPE/entity declarations are rejected by the parser.
        val buffer = ByteArray(8192); var used = 0
        while (used < buffer.size) { val n = input.read(buffer, used, buffer.size - used); if (n < 0) break; used += n }
        val prefix = buffer.copyOf(used)
        require(prefix.none { it == 0.toByte() }) { "XML must use UTF-8" }
        val header = prefix.toString(Charsets.ISO_8859_1)
        Regex("<\\?xml[^?]*encoding\\s*=\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE).find(header)?.groupValues?.get(1)?.let {
            require(it.equals("UTF-8", true) || it.equals("UTF8", true)) { "XML must use UTF-8" }
        }
        val safe = header.replace(Regex("<!DOCTYPE\\s+health-topics\\s+(?:SYSTEM\\s+['\"][^'\"<>\\[\\]]+['\"]|PUBLIC\\s+['\"][^'\"<>\\[\\]]+['\"]\\s+['\"][^'\"<>\\[\\]]+['\"])\\s*>", RegexOption.IGNORE_CASE), "")
        val stream = SequenceInputStream(ByteArrayInputStream(safe.toByteArray(Charsets.ISO_8859_1)), input)
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            isValidating = false
            // NoDtdInput below is authoritative even when Expat lacks a Xerces feature.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val parser = factory.newSAXParser().xmlReader
        parser.entityResolver = org.xml.sax.EntityResolver { _, _ -> throw IllegalArgumentException("External entities are not supported") }
        parser.contentHandler = object : DefaultHandler() {
            private val stack = mutableListOf<String>(); private val buffers = mutableListOf<StringBuilder>()
            private var wiki = false; private var medline = false; private var page = mutableMapOf<String, String>(); private var revisions = 0
            private var topic = mutableMapOf<String, String>(); private var synonyms = mutableListOf<String>(); private var date: String? = null; private var base = ""; private var language = "und"
            private var topicBudget = Utf8Budget(ReferenceLimits.ENTRY_BYTES)
            override fun startElement(uri: String?, local: String?, qName: String?, attributes: Attributes) {
                val tag = local?.takeIf { it.isNotBlank() } ?: qName.orEmpty(); stack.add(tag); buffers.add(StringBuilder())
                require(stack.size <= 64) { "XML nesting exceeds limit" }
                if (stack.size == 1) { wiki = tag == "mediawiki"; medline = tag == "health-topics"; require(wiki || medline) { "Unsupported XML root" }; date = attributes.getValue("date-generated"); language = attributes.getValue("xml:lang") ?: "und" }
                if (wiki && tag == "page") { page = mutableMapOf(); revisions = 0 }
                if (wiki && tag == "revision") { revisions++; require(revisions == 1) { "History dumps are not supported; choose current-content XML" } }
                if (medline && tag == "health-topic") { topic = mutableMapOf(); synonyms = mutableListOf(); topicBudget = Utf8Budget(ReferenceLimits.ENTRY_BYTES); for (key in listOf("id", "title", "language", "url")) topic[key] = attributes.getValue(key).orEmpty() }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (buffers.isNotEmpty() && (stack.last() in setOf("title", "ns", "id", "timestamp", "text", "base", "lang", "full-summary", "also-called", "mesh-heading", "descriptor") || "full-summary" in stack)) {
                    if (medline && "health-topic" in stack) topicBudget.append(ch, start, length)
                    val buffer = buffers.last(); require(buffer.length + length <= ReferenceLimits.ENTRY_BYTES) { "XML entry exceeds limit" }; buffer.append(ch, start, length)
                }
            }
            override fun endElement(uri: String?, local: String?, qName: String?) {
                val tag = stack.last(); val text = buffers.removeAt(buffers.lastIndex).toString(); val parent = stack.getOrNull(stack.lastIndex - 1)
                if (wiki) when {
                    tag == "base" && parent == "siteinfo" -> base = text
                    tag == "lang" && parent == "siteinfo" -> language = text
                    tag in setOf("title", "ns", "id") && parent == "page" -> page[tag] = text
                    tag in setOf("id", "timestamp", "text") && parent == "revision" -> page["revision:$tag"] = text
                    tag == "page" && (page["ns"] ?: "0") == "0" -> {
                        val body = page["revision:text"].orEmpty()
                        require(body.isNotBlank() && page["revision:id"]?.toLongOrNull()?.let { it > 0 } == true) { "Wiki article text or revision is missing; choose a complete current-content subset" }
                        emit(ReferenceEntry(requireNotNull(page["id"]), requireNotNull(page["title"]), body, language,
                            base.substringBeforeLast('/').takeIf { it.startsWith("https://") }?.let { "$it/${java.net.URLEncoder.encode(page["title"], "UTF-8").replace("+", "_")}" },
                            if (base.matches(Regex("https://[a-z-]+\\.wikipedia\\.org/.*"))) "Wikipedia" else "Imported MediaWiki library", page["revision:id"],
                            if (base.matches(Regex("https://[a-z-]+\\.wikipedia\\.org/.*"))) "CC BY-SA 4.0; attribution and history at original source" else "User-supplied; rights not verified", page["revision:timestamp"], ReferenceTextFormat.WIKITEXT))
                    }
                }
                if (medline) when (tag) {
                    "full-summary" -> topic["text"] = Jsoup.parse(text).wholeText().trim()
                    "also-called", "descriptor" -> if (text.isNotBlank()) {
                        topicBudget.append(if (synonyms.isEmpty()) "\n\nAlso called: " else "; ")
                        synonyms.add(text)
                    }
                    "health-topic" -> {
                        val lang = when (topic["language"]?.lowercase(Locale.ROOT)) { "english" -> "en"; "spanish", "español" -> "es"; else -> topic["language"].orEmpty().ifBlank { "und" } }
                        emit(ReferenceEntry(requireNotNull(topic["id"]), requireNotNull(topic["title"]), topic["text"].orEmpty() + if (synonyms.isEmpty()) "" else "\n\nAlso called: ${synonyms.joinToString("; ")}", lang, topic["url"], "MedlinePlus, National Library of Medicine", date, "Public-domain topic summary; Source: MedlinePlus, National Library of Medicine", null))
                    }
                }
                if (medline && tag != "full-summary" && "full-summary" in stack && buffers.isNotEmpty()) {
                    val target = buffers.last(); require(target.length + text.length + 1 <= ReferenceLimits.ENTRY_BYTES)
                    target.append(text); if (tag in setOf("p", "li", "tr", "div", "br")) { topicBudget.append("\n"); target.append('\n') }
                }
                stack.removeAt(stack.lastIndex)
            }
        }
        parser.parse(InputSource(object : FilterInputStream(NoDtdInput(stream)) { override fun close() = Unit }))
    }
}
