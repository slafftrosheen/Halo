package com.halo.app.everyday.collectors

import java.io.ByteArrayInputStream
import java.io.StringReader
import java.security.MessageDigest
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.parsers.SAXParserFactory
import org.jsoup.Jsoup
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

/** Feed HTML becomes inert text. This parser never resolves a URL or requests any resource. */
object SafeFeedText {
    fun plain(markup: String, maximum: Int = 16000): String {
        require(markup.length <= 65536 && maximum in 1..16000)
        val document = Jsoup.parseBodyFragment(markup)
        document.select("script,style,noscript,iframe,object,embed,svg,math,form,input,button").remove()
        return document.body().text().replace(Regex("[\\p{Cc}&&[^\\n\\t]]"), "").trim().take(maximum)
    }
}

data class ParsedFeed(val articles: List<ArticleFact>, val warnings: List<WarningFact>, val itemCount: Int)

object FeedParser {
    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun date(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value.trim()) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value.trim()).toInstant() }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()
    }
    private fun link(raw: String?, base: String?): String? {
        if (raw.isNullOrBlank() || base == null) return null
        return runCatching {
            val resolved = CollectorUrlPolicy.validate(base).resolve(raw.trim()) ?: return null
            CollectorUrlPolicy.validate(resolved.toString()).toString()
        }.getOrNull()
    }
    private class Item {
        var id: String? = null; var title = ""; var text = ""; var url: String? = null
        var published: String? = null; var language: String? = null
        val categories = mutableListOf<String>()
        val cap = mutableMapOf<String, String>()
    }
    fun parse(bytes: ByteArray, source: CollectorSource): ParsedFeed {
        require(bytes.size <= CollectorRefreshPolicy.MAX_BODY_BYTES) { "Feed exceeds size limit" }
        require(bytes.none { it == 0.toByte() }) { "Use a UTF-8-compatible XML feed" }
        val declarations = bytes.toString(Charsets.ISO_8859_1).uppercase(Locale.ROOT)
        require("<!DOCTYPE" !in declarations && "<!ENTITY" !in declarations) { "Feed entity declarations are not supported" }
        val articles = linkedMapOf<String, ArticleFact>()
        val warnings = linkedMapOf<String, WarningFact>()
        var count = 0; var depth = 0; var elements = 0; var root: String? = null
        var item: Item? = null; var itemDepth = 0
        var field: String? = null; var fieldDepth = 0; val buffer = StringBuilder()
        val fields = setOf("guid", "id", "title", "description", "summary", "content", "encoded", "link", "pubdate", "published", "updated", "date", "category",
            "headline", "event", "severity", "areadesc", "onset", "effective", "expires", "web")
        val handler = object : DefaultHandler() {
            override fun resolveEntity(publicId: String?, systemId: String?): InputSource = InputSource(StringReader(""))
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
                depth++; elements++
                if (depth > 64 || elements > 20000) throw SAXException("Feed structure exceeds parser limits")
                val name = localName?.takeIf { it.isNotEmpty() }?.lowercase(Locale.ROOT) ?: qName.orEmpty().substringAfter(':').lowercase(Locale.ROOT)
                if (depth == 1) { require(name in setOf("rss", "feed", "rdf")) { "This URL is not an RSS or Atom feed" }; root = name }
                if (name in setOf("item", "entry") && depth <= 3 && item == null) {
                    count++; item = Item(); itemDepth = depth
                    item!!.language = attributes.getValue("http://www.w3.org/XML/1998/namespace", "lang") ?: attributes.getValue("xml:lang")
                }
                val active = item ?: return
                if (name == "link" && attributes.getValue("href") != null && attributes.getValue("rel") in setOf(null, "alternate"))
                    active.url = active.url ?: link(attributes.getValue("href"), source.endpoint)
                if (name == "category" && active.categories.size < 20) attributes.getValue("term")?.let { active.categories += SafeFeedText.plain(it.take(65536), 80) }
                if (name in fields && field == null) { field = name; fieldDepth = depth; buffer.setLength(0) }
            }
            override fun characters(chars: CharArray, start: Int, length: Int) {
                if (field != null && buffer.length < 32768) buffer.append(chars, start, minOf(length, 32768 - buffer.length))
            }
            override fun endElement(uri: String?, localName: String?, qName: String?) {
                val active = item
                if (active != null && field != null && depth == fieldDepth) {
                    val value = buffer.toString().trim()
                    when (field) {
                        "guid", "id" -> if (active.id == null && value.isNotEmpty()) active.id = value.take(2000)
                        "title" -> if (active.title.isEmpty()) active.title = SafeFeedText.plain(value, 500)
                        "description", "summary", "content", "encoded" -> if (value.length > active.text.length) active.text = SafeFeedText.plain(value)
                        "link" -> active.url = active.url ?: link(value, source.endpoint)
                        "pubdate", "published", "date", "updated" -> if (active.published == null && date(value) != null) active.published = value
                        "category" -> if (active.categories.size < 20 && value.isNotBlank()) active.categories += SafeFeedText.plain(value, 80)
                        else -> active.cap[field!!] = SafeFeedText.plain(value, if (field == "areadesc") 1000 else 500)
                    }
                    field = null; buffer.setLength(0)
                } else if (active != null && field != null && depth > fieldDepth && buffer.length < 32768) buffer.append(' ')
                if (active != null && depth == itemDepth) {
                    if (articles.size < CollectorRefreshPolicy.MAX_ITEMS && active.title.isNotBlank()) {
                        val identity = source.id + ":" + digest(active.id ?: active.url ?: "${active.title}\n${active.published}")
                        articles.putIfAbsent(identity, ArticleFact(identity, active.title, active.text, active.url, source.publisher,
                            date(active.published), active.language?.take(35) ?: source.language, active.categories.distinct()))
                        if (source.kind == CollectorKind.ALERTS) warnings.putIfAbsent(identity, WarningFact(identity,
                            active.cap["headline"]?.takeIf { it.isNotBlank() } ?: active.title, active.text, active.cap["areadesc"], active.cap["severity"],
                            date(active.cap["onset"] ?: active.cap["effective"]), date(active.cap["expires"]), link(active.cap["web"], source.endpoint) ?: active.url, source.publisher))
                    }
                    item = null; field = null; buffer.setLength(0)
                }
                depth--
            }
        }
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        factory.newSAXParser().parse(ByteArrayInputStream(bytes), handler)
        require(root != null && depth == 0 && (count == 0 || articles.isNotEmpty())) { "Feed has no readable entries" }
        return ParsedFeed(articles.values.toList(), warnings.values.toList(), count)
    }
}
