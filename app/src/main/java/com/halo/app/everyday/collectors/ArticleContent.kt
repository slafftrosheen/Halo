package com.halo.app.everyday.collectors

import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

/** Inert, attributed content. Asset keys refer only to Halo-owned files, never arbitrary paths. */
data class ArticleImage(val url: String, val caption: String = "", val assetKey: String? = null, val mimeType: String? = null)
data class ArticleContent(val text: String, val images: List<ArticleImage>, val status: String, val pageUrl: String,
    val retrievedAt: String, val omittedImages: Int = 0)

object ArticleExtractor {
    const val MAX_TEXT = 200_000
    const val MAX_IMAGES = 8
    fun images(markup: String, base: String): List<ArticleImage> = images(Jsoup.parse(markup, base)).first
    private fun images(root: org.jsoup.nodes.Element): Pair<List<ArticleImage>, Int> {
        val candidates = root.select("img").mapNotNull { image ->
            val candidates = listOf("data-src", "data-original", "data-lazy-src", "src").map { image.attr(it) } +
                image.attr("srcset").split(',').map { it.trim().substringBefore(' ') } +
                image.attr("data-srcset").split(',').map { it.trim().substringBefore(' ') }
            val url = candidates.firstNotNullOfOrNull { publicLink(it, image.baseUri()) } ?: return@mapNotNull null
            val width = image.attr("width").toIntOrNull()
            val height = image.attr("height").toIntOrNull()
            if (width != null && width <= 2 || height != null && height <= 2) return@mapNotNull null
            ArticleImage(url, image.closest("figure")?.selectFirst("figcaption")?.text()?.take(1000)
                ?: image.attr("alt").take(1000))
        }.distinctBy { it.url }
        return candidates.take(MAX_IMAGES) to (candidates.size - MAX_IMAGES).coerceAtLeast(0)
    }
    fun publicLink(raw: String?, base: String?): String? = runCatching {
        if (raw.isNullOrBlank() || base.isNullOrBlank()) return null
        CollectorUrlPolicy.validate(requireNotNull(CollectorUrlPolicy.validate(base).resolve(raw)).toString()).toString()
    }.getOrNull()
    fun extract(html: String, url: String, retrievedAt: String): ArticleContent {
        require(html.length <= CollectorRefreshPolicy.MAX_BODY_BYTES)
        val document = Jsoup.parse(html, url)
        val restricted = document.select("[itemprop=isAccessibleForFree]").any { it.attr("content") == "false" } ||
            document.select("script[type=application/ld+json]").any {
                Regex("\"isAccessibleForFree\"\\s*:\\s*(false|\"false\")", RegexOption.IGNORE_CASE).containsMatchIn(it.data())
            }
        document.select("script,style,noscript,iframe,object,embed,svg,math,form,input,button,nav,[hidden],[aria-hidden=true]").remove()
        val root = document.select("article,[itemprop=articleBody],main,[role=main]").maxByOrNull { it.text().length } ?: document.body()
        // A publisher's article header/footer can contain headings, bylines and source notes.
        // Strip site chrome only when no article/main container could be identified.
        if (root === document.body()) root.select("header,footer,aside").remove()
        val (images, omitted) = images(root)
        val text = root.wholeText().replace(Regex("[\\p{Cc}&&[^\\n\\t]]"), "")
            .replace(Regex("[ \\t]+"), " ").replace(Regex("\\n[ \\t]*\\n(?:[ \\t]*\\n)+"), "\n\n").trim()
        val status = when { restricted -> "RESTRICTED_PAGE"; text.isBlank() -> "EMPTY_PAGE";
            text.length > MAX_TEXT -> "TEXT_LIMIT"; else -> "PAGE_CAPTURED" }
        return ArticleContent(text.take(MAX_TEXT), images, status, url, retrievedAt, omitted)
    }
}

/** Content-addressed private storage. Reads never download. Quota failures leave the feed usable. */
class ArticleContentStore(private val directory: File, private val maxBytes: Long = 192L * 1024 * 1024) {
    private val keyPattern = Regex("[a-f0-9]{64}")
    @Synchronized private fun write(bytes: ByteArray, suffix: String): String {
        val key = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        check(directory.isDirectory || directory.mkdirs())
        val target = File(directory, "$key.$suffix")
        if (!target.isFile) {
            val used = directory.listFiles().orEmpty().sumOf { it.length() }
            require(used + bytes.size <= maxBytes) { "Saved article storage is full" }
            val pending = File.createTempFile("article-", ".tmp", directory)
            try { pending.outputStream().use { it.write(bytes) }; check(pending.renameTo(target)) }
            finally { pending.delete() }
        }
        target.setLastModified(System.currentTimeMillis())
        return key
    }
    fun save(content: ArticleContent): String = write(JSONObject().put("schema", 1).put("text", content.text)
        .put("status", content.status).put("url", content.pageUrl).put("retrievedAt", content.retrievedAt)
        .put("omittedImages", content.omittedImages).put("images", JSONArray(content.images.map(::imageJson)))
        .toString().toByteArray(Charsets.UTF_8), "json")
    fun read(key: String?): ArticleContent? {
        if (key == null || !keyPattern.matches(key)) return null
        return runCatching {
            val file = File(directory, "$key.json")
            require(file.length() in 1..(2L * 1024 * 1024))
            val bytes = file.readBytes()
            require(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } == key)
            file.setLastModified(System.currentTimeMillis())
            val json = JSONObject(bytes.toString(Charsets.UTF_8)); require(json.getInt("schema") == 1)
            val images = json.getJSONArray("images"); require(images.length() <= ArticleExtractor.MAX_IMAGES)
            ArticleContent(json.getString("text").also { require(it.length <= ArticleExtractor.MAX_TEXT) },
                (0 until images.length()).map { image(images.getJSONObject(it)) }, json.getString("status"),
                json.getString("url"), json.getString("retrievedAt"), json.getInt("omittedImages"))
        }.getOrNull()
    }
    fun saveImage(bytes: ByteArray): String { require(bytes.size in 1..CollectorRefreshPolicy.MAX_BODY_BYTES); return write(bytes, "image") }
    fun imageFile(key: String?): File? = key?.takeIf(keyPattern::matches)?.let { File(directory, "$it.image") }?.takeIf { it.isFile }
    /** Retain committed snapshots and in-flight writes; stale orphans cannot fill storage forever. */
    @Synchronized fun prune(referenced: Set<String>, nowMs: Long = System.currentTimeMillis()) {
        directory.listFiles().orEmpty().filter { file ->
            file.isFile && file.extension in setOf("json", "image") && keyPattern.matches(file.nameWithoutExtension) &&
                file.nameWithoutExtension !in referenced && file.lastModified() < nowMs - 15 * 60_000L
        }.forEach { it.delete() }
    }
    companion object {
        fun imageJson(value: ArticleImage) = JSONObject().put("url", value.url).put("caption", value.caption)
            .put("assetKey", value.assetKey).put("mimeType", value.mimeType)
        fun image(value: JSONObject) = ArticleImage(value.getString("url"), value.optString("caption").take(1000),
            value.optString("assetKey").takeIf { it.isNotBlank() && it != "null" }, value.optString("mimeType").takeIf { it.isNotBlank() && it != "null" })
    }
}
