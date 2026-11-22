package com.halo.app.everyday.collectors

import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

/** Inert, attributed content. Asset keys refer only to Halo-owned files, never arbitrary paths. */
enum class ArticleImageKind { IMAGE, PHOTO, ILLUSTRATION }
enum class ArticleImageSensitivity { UNKNOWN, CLEAR, SENSITIVE }

data class ArticleImage(val url: String, val caption: String = "", val assetKey: String? = null, val mimeType: String? = null,
    val credit: String? = null, val kind: ArticleImageKind = ArticleImageKind.IMAGE,
    val sensitivity: ArticleImageSensitivity = ArticleImageSensitivity.UNKNOWN,
    val fromArticle: Boolean = false, val license: String? = null, val previewUrl: String? = null,
    val previewOnly: Boolean = false) {
    val canSave: Boolean get() = fromArticle && credit != null && license?.trimEnd('/') in SAVE_LICENSES

    private companion object {
        val SAVE_LICENSES = setOf("https://creativecommons.org/publicdomain/zero/1.0",
            "https://creativecommons.org/licenses/by/4.0")
    }
}
data class ArticleContent(val text: String, val images: List<ArticleImage>, val status: String, val pageUrl: String,
    val retrievedAt: String, val omittedImages: Int = 0)

object ArticleExtractor {
    const val MAX_TEXT = 200_000
    const val MAX_IMAGES = 24
    fun images(markup: String, base: String): List<ArticleImage> = images(Jsoup.parse(markup, base)).first
    private fun images(root: org.jsoup.nodes.Element): Pair<List<ArticleImage>, Int> {
        val candidates = root.select("img").mapNotNull { image ->
            if (image.closest("aside, [itemprop=comment], [role=complementary], [role=navigation]") != null) return@mapNotNull null
            val figure = image.closest("figure")
            val scopedImage = image.closest("[itemscope][itemtype$=ImageObject], [itemscope][itemtype$=Photograph]")
            val metadata = scopedImage ?: figure ?: image
            val responsive = (image.closest("picture")?.select("source").orEmpty() + image).flatMap { element ->
                (element.attr("srcset") + "," + element.attr("data-srcset")).split(',').mapNotNull responsiveEntry@ { entry ->
                    val width = entry.trim().substringAfterLast(' ').removeSuffix("w").toIntOrNull() ?: return@responsiveEntry null
                    publicLink(entry.trim().substringBefore(' '), image.baseUri())?.let { width to it }
                }
            }.filter { it.first > 2 }.sortedBy { it.first }
            val candidates = listOf("data-src", "data-original", "data-lazy-src", "src").map { image.attr(it) } +
                responsive.map { it.second } +
                (image.attr("srcset") + "," + image.attr("data-srcset")).split(',').map { it.trim().substringBefore(' ') }
            val url = candidates.firstNotNullOfOrNull { publicLink(it, image.baseUri()) } ?: return@mapNotNull null
            val width = image.attr("width").toIntOrNull()
            val height = image.attr("height").toIntOrNull()
            if (width != null && width <= 2 || height != null && height <= 2) return@mapNotNull null
            val caption = figure?.selectFirst("figcaption")?.text().orEmpty().ifBlank { image.attr("alt") }.take(1000)
            val credit = sequenceOf(image.attr("data-credit"), metadata.attr("data-credit"),
                metadata.selectFirst("[itemprop=creditText], [itemprop=copyrightNotice], [itemprop=creator], .photo-credit, .image-credit")
                    ?.let { it.attr("content").ifBlank { it.text() } }.orEmpty()).firstOrNull { it.isNotBlank() }?.take(300)
            val type = image.attr("data-image-kind").ifBlank { metadata.attr("data-image-kind") }.lowercase(java.util.Locale.ROOT)
            val kind = when {
                type in setOf("illustration", "generated", "ai", "ai-generated", "graphic") ||
                    Regex("(?i)^(illustration|ai[- ]generated|graphic)([: .]|$)").containsMatchIn(caption) -> ArticleImageKind.ILLUSTRATION
                type == "photo" || scopedImage?.attr("itemtype")?.endsWith("Photograph") == true -> ArticleImageKind.PHOTO
                else -> ArticleImageKind.IMAGE
            }
            val sensitivity = when (image.attr("data-sensitive").ifBlank { metadata.attr("data-sensitive") }.lowercase(java.util.Locale.ROOT)) {
                "true", "sensitive", "graphic", "disturbing" -> ArticleImageSensitivity.SENSITIVE
                "false", "clear" -> ArticleImageSensitivity.CLEAR
                else -> ArticleImageSensitivity.UNKNOWN
            }
            val license = publicLink(image.attr("data-license").ifBlank { metadata.attr("data-license") }.ifBlank {
                metadata.selectFirst("[itemprop=license], [rel=license]")?.let { it.attr("href").ifBlank { it.attr("content") } }.orEmpty()
            }, image.baseUri())
            val preview = responsive.firstOrNull { it.first <= 800 }?.second
                ?: url.takeIf { width != null && width in 3..800 && (height == null || height <= 800) }
            ArticleImage(url, caption, credit = credit, kind = kind, sensitivity = sensitivity, fromArticle = true,
                license = license, previewUrl = preview)
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
        val articleRoot = document.select("article,[itemprop=articleBody]").maxByOrNull { it.text().length }
        val root = articleRoot ?: document.select("main,[role=main]").maxByOrNull { it.text().length } ?: document.body()
        // A publisher's article header/footer can contain headings, bylines and source notes.
        // Strip site chrome only when no article/main container could be identified.
        if (root === document.body()) root.select("header,footer,aside").remove()
        val (inlineImages, omitted) = articleRoot?.let(::images) ?: (emptyList<ArticleImage>() to 0)
        val images = inlineImages.ifEmpty {
            if (!document.select("meta[property=og:type]").any { it.attr("content").equals("article", true) }) emptyList()
            else document.select("meta[property=og:image], meta[property=og:image:secure_url]").asSequence()
                .mapNotNull { publicLink(it.attr("content"), url) }.distinct().take(1)
                .map { picture -> ArticleImage(picture,
                    caption = document.selectFirst("meta[property=og:image:alt]")?.attr("content").orEmpty().take(1000),
                    fromArticle = true) }.toList()
        }
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
            .put("assetKey", value.assetKey).put("mimeType", value.mimeType).put("credit", value.credit)
            .put("kind", value.kind.name).put("sensitivity", value.sensitivity.name).put("fromArticle", value.fromArticle)
            .put("license", value.license).put("previewUrl", value.previewUrl).put("previewOnly", value.previewOnly)
        fun image(value: JSONObject): ArticleImage {
            fun optional(key: String) = value.optString(key).takeIf { it.isNotBlank() && it != "null" }
            return ArticleImage(value.getString("url"), value.optString("caption").take(1000), optional("assetKey"), optional("mimeType"),
                optional("credit")?.take(300), ArticleImageKind.entries.find { it.name == optional("kind") } ?: ArticleImageKind.IMAGE,
                ArticleImageSensitivity.entries.find { it.name == optional("sensitivity") } ?: ArticleImageSensitivity.UNKNOWN,
                value.optBoolean("fromArticle"), optional("license"), optional("previewUrl"), value.optBoolean("previewOnly"))
        }
    }
}
