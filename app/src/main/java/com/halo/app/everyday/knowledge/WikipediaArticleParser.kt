package com.halo.app.everyday.knowledge

import com.halo.app.everyday.collectors.SafeCollectorJson
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

data class WikipediaArticleRequest(val pageId: Long? = null, val title: String? = null, val revision: Long? = null, val section: String? = null) {
    init {
        require((pageId == null) != (title == null)); require(pageId == null || pageId > 0)
        require(title == null || title.isNotBlank() && title.length <= 200)
        require(revision == null || revision > 0); require(section == null || section.matches(Regex("[0-9]{1,4}")))
    }
    fun encoded() = JSONObject().apply { pageId?.let { put("pageId", it) }; title?.let { put("title", it) }; revision?.let { put("revision", it) }; section?.let { put("section", it) } }.toString()
    fun endpoint(language: String): HttpUrl {
        require(language in setOf("en", "ru", "lv", "pl"))
        return "https://$language.wikipedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "parse").addQueryParameter("format", "json").addQueryParameter("formatversion", "2")
            .addQueryParameter("prop", "text|revid|tocdata").addQueryParameter("disableeditsection", "1").apply {
                if (revision == null) { pageId?.let { addQueryParameter("pageid", it.toString()) }; title?.let { addQueryParameter("page", it) } }
                revision?.let { addQueryParameter("oldid", it.toString()) }; section?.let { addQueryParameter("section", it) }
            }.build()
    }
    companion object {
        fun decode(text: String): WikipediaArticleRequest {
            val value = JSONObject(text); require(value.keys().asSequence().all { it in setOf("pageId", "title", "revision", "section") })
            return WikipediaArticleRequest(value.opt("pageId")?.let { (it as Number).toLong() }, value.opt("title") as? String,
                value.opt("revision")?.let { (it as Number).toLong() }, value.opt("section") as? String)
        }
    }
}

/** Keeps table cells, units, paragraphs and headings as text; never fetches resources. */
object ReferenceHtmlText {
    fun extract(root: Element): String {
        val out = StringBuilder()
        NodeTraversor.traverse(object : NodeVisitor {
            override fun head(node: Node, depth: Int) {
                if (node is TextNode) out.append(node.wholeText)
                if (node is Element && node.normalName() == "br") out.append('\n')
            }
            override fun tail(node: Node, depth: Int) {
                if (node is Element) when (node.normalName()) {
                    "td", "th" -> out.append(" | ")
                    "p", "div", "section", "li", "tr", "h1", "h2", "h3", "h4", "h5", "h6" -> out.append('\n')
                    else -> Unit
                }
            }
        }, root)
        return out.toString().lineSequence().map { it.trim() }.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
    }
}

object WikipediaArticleParser {
    fun entries(bytes: ByteArray, language: String, requested: WikipediaArticleRequest): List<ReferenceEntry> {
        require(bytes.size <= 2 * 1024 * 1024)
        val root = SafeCollectorJson.objectValue(bytes.toString(Charsets.UTF_8)); require(!root.has("error")) { "Wikipedia could not return this article" }
        val parsed = root.getJSONObject("parse"); val revision = parsed.getLong("revid"); require(revision > 0)
        val pageId = parsed.getLong("pageid"); require(pageId > 0 && (requested.pageId == null || requested.pageId == pageId))
        require(requested.revision == null || requested.revision == revision) { "Article revision changed" }
        val title = parsed.getString("title").also { require(it.length <= 500) }
        val html = when (val text = parsed.get("text")) { is String -> text; is JSONObject -> text.getString("*"); else -> error("Unsupported article representation") }
        val document = Jsoup.parse(html); document.select("script,style,noscript,iframe,form,nav,.mw-editsection").remove()
        val content = document.selectFirst(".mw-parser-output") ?: document.body()
        val images = com.halo.app.everyday.collectors.SourceImages.fromHtml(content, "https://$language.wikipedia.org/")
        val groups = mutableListOf<Triple<String, String, String>>()
        var heading = title; var anchor = ""; val body = StringBuilder()
        fun flush() { val text = body.toString().trim(); if (text.isNotBlank()) groups.add(Triple(heading, anchor, text)); body.setLength(0); require(groups.size <= 100) { "Article has too many sections; select a smaller section" } }
        NodeTraversor.traverse(object : NodeVisitor {
            override fun head(node: Node, depth: Int) {
                if (node is Element && node.normalName() in setOf("h1", "h2", "h3", "h4", "h5", "h6")) {
                    flush(); heading = node.text().take(500); anchor = node.id()
                }
                if (node is TextNode) body.append(node.wholeText)
                if (node is Element && node.normalName() == "br") body.append('\n')
                require(body.length <= 100000) { "This section exceeds the retained-text limit; choose a smaller section" }
            }
            override fun tail(node: Node, depth: Int) {
                if (node is Element) when (node.normalName()) {
                    "td", "th" -> body.append(" | ")
                    "p", "div", "section", "li", "tr", "h1", "h2", "h3", "h4", "h5", "h6" -> body.append('\n')
                    else -> Unit
                }
            }
        }, content)
        flush(); require(groups.isNotEmpty()) { "No readable article text" }
        return groups.mapIndexed { index, (sectionTitle, fragment, text) ->
            val url = "https://$language.wikipedia.org/w/index.php".toHttpUrl().newBuilder().addQueryParameter("curid", pageId.toString())
                .addQueryParameter("oldid", revision.toString()).fragment(fragment.takeIf { it.isNotBlank() }).build().toString()
            ReferenceEntry("$pageId:$revision:$index", if (sectionTitle == title) title else "$title — $sectionTitle".take(500), text,
                language, url, "Wikipedia", revision.toString(), "CC BY-SA 4.0; attribution and contributor history at the source URL", images = if (index == 0) images.map { it.copy(sourceUrl = url) } else emptyList())
        }
    }
}
