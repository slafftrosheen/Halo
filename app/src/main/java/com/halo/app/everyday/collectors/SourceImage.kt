package com.halo.app.everyday.collectors

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Inert publisher-supplied references. Parsing never downloads or executes image content. */
data class SourceImage(val url: String, val caption: String = "", val credit: String? = null,
    val license: String? = null, val sourceUrl: String? = null) {
    fun validate() {
        require(url.length <= 2048 && CollectorUrlPolicy.validate(url).isHttps)
        require(caption.length <= 500 && (credit?.length ?: 0) <= 500 && (license?.length ?: 0) <= 500)
        sourceUrl?.let { require(it.length <= 2048); CollectorUrlPolicy.validate(it) }
    }
}
object SourceImages {
    const val MAX_IMAGES = 3
    fun encode(values: List<SourceImage>): JSONArray {
        require(values.size <= MAX_IMAGES)
        return JSONArray(values.map { image -> image.validate(); JSONObject().put("url", image.url).put("caption", image.caption)
            .apply { image.credit?.let { put("credit", it) }; image.license?.let { put("license", it) }; image.sourceUrl?.let { put("sourceUrl", it) } } })
    }
    fun decode(value: JSONArray?): List<SourceImage> {
        if (value == null) return emptyList()
        require(value.length() <= MAX_IMAGES)
        return (0 until value.length()).map { index -> val row = value.getJSONObject(index)
            SourceImage(row.getString("url"), row.optString("caption"), row.optString("credit").takeIf { it.isNotBlank() },
                row.optString("license").takeIf { it.isNotBlank() }, row.optString("sourceUrl").takeIf { it.isNotBlank() }).also { it.validate() }
        }
    }
    fun resolve(raw: String?, base: String, caption: String = "", sourceUrl: String? = base): SourceImage? = runCatching {
        require(!raw.isNullOrBlank())
        val url = CollectorUrlPolicy.validate(base).resolve(raw) ?: error("Invalid image URL")
        require(url.isHttps && url.username.isBlank() && url.password.isBlank())
        require(!url.encodedPath.substringAfterLast('.').lowercase(java.util.Locale.ROOT).let { it == "svg" || it == "gif" })
        SourceImage(url.toString(), caption.take(500), sourceUrl = sourceUrl).also { it.validate() }
    }.getOrNull()
    fun fromHtml(root: Element, base: String): List<SourceImage> = root.select("img[src]").asSequence().filter {
        it.attr("width").toIntOrNull()?.let { width -> width >= 32 } != false && it.attr("height").toIntOrNull()?.let { height -> height >= 32 } != false
    }.mapNotNull { element ->
        val caption = element.closest("figure")?.selectFirst("figcaption")?.text()?.takeIf { it.isNotBlank() } ?: element.attr("alt")
        resolve(element.attr("src"), base, caption)
    }.distinctBy { it.url }.take(MAX_IMAGES).toList()
    fun fromMarkup(markup: String, base: String): List<SourceImage> = fromHtml(Jsoup.parseBodyFragment(markup, base).body(), base)
}
