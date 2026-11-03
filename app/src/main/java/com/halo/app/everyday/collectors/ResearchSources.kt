package com.halo.app.everyday.collectors

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.jsoup.Jsoup
import java.security.MessageDigest
import java.time.Instant

data class ResearchSourceQuery(val query: String, val providerId: String) {
    init { require(query.isNotBlank() && query.length <= 400 && providerId.matches(Regex("[a-z][a-z0-9_.-]{0,79}"))) }
}

/** Providers supply evidence, never synthesis or hidden model calls. New adapters need no task-schema change. */
interface ResearchProviderAdapter {
    val id: String
    val title: String
    fun endpoint(query: String, language: String): HttpUrl
    fun parse(bytes: ByteArray, query: String, language: String): List<ArticleFact>
}

class ResearchProviderRegistry(adapters: List<ResearchProviderAdapter>) {
    private val providers = adapters.associateBy { it.id }.also { require(it.size == adapters.size) }
    fun list() = providers.values.toList()
    fun require(id: String) = requireNotNull(providers[id]) { "Research provider is not installed; choose an available source" }
    companion object { val public = ResearchProviderRegistry(listOf(WikipediaResearchProvider, ArchiveResearchProvider, PublicPageResearchProvider)) }
}

private fun plain(text: String, limit: Int) = Jsoup.parse(text).text().take(limit)
private fun evidenceId(provider: String, url: String) = "$provider:" + MessageDigest.getInstance("SHA-256")
    .digest(url.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

/** One explicitly selected public page, robots checked by the governed transfer owner. Never follows links. */
object PublicPageResearchProvider : ResearchProviderAdapter {
    override val id = "public_page"
    override val title = "Public web page"
    override fun endpoint(query: String, language: String) = CollectorUrlPolicy.validate(query).also { require(it.isHttps) }.newBuilder().fragment(null).build()
    override fun parse(bytes: ByteArray, query: String, language: String): List<ArticleFact> = parsePage(bytes, query, language, null)
    fun parsePage(bytes: ByteArray, query: String, language: String, charset: String?): List<ArticleFact> {
        require(bytes.size <= CollectorRefreshPolicy.MAX_BODY_BYTES)
        val url = endpoint(query, language).toString()
        val document = Jsoup.parse(java.io.ByteArrayInputStream(bytes), charset, url)
        val restrictions = document.select("meta[name=robots],meta[name=halo]").joinToString(",") { it.attr("content") }.lowercase(java.util.Locale.ROOT)
        require(listOf("noindex", "noarchive", "none").none { it in restrictions }) { "This page disallows local indexing" }
        val published = document.selectFirst("meta[property=article:published_time]")?.attr("content")?.let { runCatching { Instant.parse(it) }.getOrNull() }
        document.select("script,style,noscript,iframe,form,nav,footer,header,svg,template").remove()
        val text = document.body()?.text().orEmpty().take(8000)
        require(text.isNotBlank()) { "No readable public text is available; dynamic execution is not enabled" }
        return listOf(ArticleFact(evidenceId(id, url), document.title().ifBlank { endpoint(query, language).host }.take(500), text,
            url, endpoint(query, language).host.take(200), published, language, listOf("research", id)))
    }
}

/** Public documented search API; no crawling or paid account. The snippet remains publisher data. */
object WikipediaResearchProvider : ResearchProviderAdapter {
    override val id = "wikipedia"
    override val title = "Wikipedia"
    private fun language(value: String) = value.substringBefore('-').takeIf { it in setOf("en", "ru", "lv", "pl") } ?: "en"
    override fun endpoint(query: String, language: String) = "https://${language(language)}.wikipedia.org/w/api.php".toHttpUrl().newBuilder()
        .addQueryParameter("action", "query").addQueryParameter("list", "search").addQueryParameter("format", "json")
        .addQueryParameter("srsearch", query).addQueryParameter("srlimit", "10").addQueryParameter("utf8", "1").build()
    override fun parse(bytes: ByteArray, query: String, language: String): List<ArticleFact> {
        val root = SafeCollectorJson.objectValue(bytes.toString(Charsets.UTF_8)); require(!root.has("error"))
        val rows = root.getJSONObject("query").getJSONArray("search"); require(rows.length() <= 10)
        return (0 until rows.length()).map { rows.getJSONObject(it) }.map { row ->
            val title = plain(row.getString("title"), 500)
            val url = "https://${language(language)}.wikipedia.org/w/index.php".toHttpUrl().newBuilder()
                .addQueryParameter("curid", row.getLong("pageid").also { require(it > 0) }.toString()).build().toString()
            ArticleFact(evidenceId(id, url), title, plain(row.optString("snippet"), 4000), url, this.title, null,
                language(language), listOf("research", id),
                row.optString("timestamp").takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it) }.getOrNull() })
        }.distinctBy { it.url }
    }
}

/** Internet Archive document search: results identify archived material, not guaranteed current pages. */
object ArchiveResearchProvider : ResearchProviderAdapter {
    override val id = "internet_archive"
    override val title = "Internet Archive"
    override fun endpoint(query: String, language: String) = "https://archive.org/advancedsearch.php".toHttpUrl().newBuilder()
        .addQueryParameter("q", query).addQueryParameter("output", "json").addQueryParameter("rows", "10")
        .addQueryParameter("page", "1").apply { listOf("identifier", "title", "description", "date").forEach { addQueryParameter("fl[]", it) } }.build()
    override fun parse(bytes: ByteArray, query: String, language: String): List<ArticleFact> {
        val rows = SafeCollectorJson.objectValue(bytes.toString(Charsets.UTF_8)).getJSONObject("response").getJSONArray("docs"); require(rows.length() <= 10)
        return (0 until rows.length()).map { rows.getJSONObject(it) }.map { row ->
            val identifier = row.getString("identifier").also { require(it.matches(Regex("[A-Za-z0-9_.-]{1,200}"))) }
            val url = "https://archive.org/details/$identifier"
            val description = row.opt("description")?.let { value -> if (value is org.json.JSONArray) (0 until value.length()).take(5).joinToString(" ") { value.optString(it) } else value.toString() }.orEmpty()
            ArticleFact(evidenceId(id, url), plain(row.optString("title", identifier), 500), plain(description, 4000), url, title,
                row.optString("date").takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it) }.getOrNull() }, language, listOf("research", id, "archive"))
        }.distinctBy { it.url }
    }
}

data class ResearchEvidence(val id: String, val query: String, val title: String, val canonicalUrl: String?,
    val sourceId: String, val sourceVersion: Long, val provider: String, val sourceType: String,
    val retrievedAt: Instant, val verifiedAt: Instant, val stale: Boolean, val passage: String,
    val publishedAt: Instant?, val lexicalRelevance: Double, val updatedAt: Instant? = null)

/** Local search over already retained selected evidence; never fetches or asks a model to invent current facts. */
class OrganizerResearch(private val sources: CollectorRepository, private val clock: java.time.Clock = java.time.Clock.systemUTC(),
    private val power: () -> Boolean = { false }) {
    suspend fun saveSource(query: ResearchSourceQuery, language: String, selected: Boolean,
        id: String? = null, version: Long? = null): CollectorSource {
        require((id == null) == (version == null))
        val old = id?.let { requireNotNull(sources.state(it)).source.also { row -> require(row.version == version && row.kind == CollectorKind.RESEARCH) } }
        val provider = ResearchProviderRegistry.public.require(query.providerId)
        val identity = MessageDigest.getInstance("SHA-256").digest("${query.providerId}|${query.query}|$language".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return sources.save(CollectorSource(old?.id ?: "research:$identity", query.query.take(200), provider.title, CollectorKind.RESEARCH,
            SourceCategory.CUSTOM, language, provider.endpoint(query.query, language).toString(), selected = selected,
            enabled = old?.enabled ?: true, muted = old?.muted ?: false, refreshMinutes = old?.refreshMinutes ?: 240,
            childApproved = false, version = old?.version ?: 1, research = query), old?.version)
    }
    suspend fun search(query: String, limit: Int = 10, sourceId: String? = null): List<ResearchEvidence> {
        val disclosure = com.halo.app.everyday.EverydayReadAuthority.capture()
        require(query.isNotBlank() && query.length <= 400 && limit in 1..20)
        val terms = query.lowercase(java.util.Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+" )).filter { it.isNotBlank() }.distinct().take(40)
        require(terms.isNotEmpty())
        val candidates = sources.states().filter { (sourceId == null || it.source.id == sourceId) && it.source.selected && it.source.enabled && !it.source.muted }.flatMap { state ->
            val snapshot = state.snapshot?.takeIf { it.sourceVersion == state.source.version } ?: return@flatMap emptyList()
            (snapshot.payload as? CollectorPayload.Articles)?.items.orEmpty().mapNotNull { article ->
                val text = "${article.title} ${article.text}".lowercase(java.util.Locale.ROOT)
                val score = terms.count { it in text }.toDouble() / terms.size
                if (score == 0.0) null else ResearchEvidence(article.id, query, article.title.take(500), article.url,
                    state.source.id, state.source.version, article.publisher, if (state.source.kind == CollectorKind.RESEARCH) "RESEARCH" else "FEED",
                    snapshot.retrievedAt, snapshot.verifiedAt, snapshot.stale(clock.instant(), state.source.external, power()), article.text.take(4000), article.publishedAt, score, article.updatedAt)
            }
        }
        val selected = candidates.sortedWith(compareByDescending<ResearchEvidence> { it.lexicalRelevance }.thenByDescending { it.verifiedAt })
            .distinctBy { it.canonicalUrl ?: it.id }.take(limit)
        com.halo.app.everyday.EverydayReadAuthority.requireCurrent(disclosure)
        return selected
    }
}
