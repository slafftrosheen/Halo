package com.halo.app.everyday.collectors

import com.halo.app.everyday.EverydayWriteAuthority
import com.halo.app.everyday.knowledge.WikipediaArticleParser
import com.halo.app.everyday.knowledge.WikipediaArticleRequest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/** Internal selected-article adapter. The ordinary provider picker continues to offer plain search. */
object WikipediaArticleResearchProvider : ResearchProviderAdapter {
    override val id = "wikipedia_article"
    override val title = "Wikipedia · CC BY-SA 4.0"
    override fun endpoint(query: String, language: String) = WikipediaArticleRequest.decode(query).endpoint(language.substringBefore('-'))
    override fun parse(bytes: ByteArray, query: String, language: String): List<ArticleFact> =
        WikipediaArticleParser.entries(bytes, language.substringBefore('-'), WikipediaArticleRequest.decode(query)).map { entry ->
            val url = requireNotNull(entry.url)
            ArticleFact("wikiarticle:" + MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) },
                entry.title, entry.text, url, title, null, entry.language, listOf("research", id, "CC BY-SA 4.0"), images = entry.images)
        }
}

data class WikipediaArticleReceipt(val id: String, val version: Long, val fetched: Boolean, val contentRevision: String?, val articleCount: Int)

class ResearchArticleService(private val sources: CollectorRepository, private val coordinator: CollectorCoordinator,
    private val power: () -> Boolean) {
    suspend fun retrieve(sourceId: String, version: Long, contentRevision: String, articleId: String,
        revision: Long? = null, section: String? = null): WikipediaArticleReceipt = coroutineScope {
        EverydayWriteAuthority.requireCurrent(); require(power()) { "Retrieving a full article needs Power; retained evidence remains available offline" }
        val source = requireNotNull(sources.state(sourceId)); require(source.source.version == version && source.source.kind == CollectorKind.RESEARCH &&
            source.source.research?.providerId in setOf("wikipedia", "wikipedia_article") && source.source.selected && source.source.enabled && !source.source.muted)
        val snapshot = requireNotNull(source.snapshot); require(snapshot.sourceVersion == version && snapshot.contentRevision == contentRevision)
        val article = requireNotNull((snapshot.payload as? CollectorPayload.Articles)?.items?.find { it.id == articleId })
        val language = source.source.language.substringBefore('-'); val url = requireNotNull(article.url).toHttpUrl()
        require(url.isHttps && url.host == "$language.wikipedia.org")
        val pageId = requireNotNull(url.queryParameter("curid")?.toLongOrNull())
        val request = WikipediaArticleRequest(pageId = pageId, revision = revision ?: url.queryParameter("oldid")?.toLongOrNull(), section = section)
        val query = ResearchSourceQuery(request.encoded(), WikipediaArticleResearchProvider.id)
        val identity = MessageDigest.getInstance("SHA-256").digest("${query.providerId}|${query.query}|$language".toByteArray()).joinToString("") { "%02x".format(it) }
        val id = "research:$identity"; val prior = sources.source(id)
        if (prior != null) require(prior.kind == CollectorKind.RESEARCH && prior.selected && prior.enabled && !prior.muted && prior.research == query) { "Enable this saved article source before retrieving it" }
        EverydayWriteAuthority.requireCurrent()
        val parent = currentCoroutineContext().job
        val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
            sources.observe().first { rows -> rows.find { it.source.id == sourceId }?.let {
                it.source.version != version || !it.source.selected || !it.source.enabled || it.source.muted ||
                    (id != sourceId && it.snapshot?.contentRevision != contentRevision)
            } != false }
            parent.cancel(CancellationException("Selected article source changed"))
        }
        try {
        val target = prior ?: sources.save(CollectorSource(id, article.title.take(200), WikipediaArticleResearchProvider.title,
            CollectorKind.RESEARCH, SourceCategory.CUSTOM, language, request.endpoint(language).toString(), selected = true,
            refreshMinutes = 240, childApproved = false, research = query), null)
        require(power()); val result = coordinator.refreshDetailed(target.id, manual = true, expectedVersion = target.version).state
        EverydayWriteAuthority.requireCurrent()
        val current = requireNotNull(sources.state(target.id)); require(current.source.version == target.version)
        val fetched = result?.attempt?.state in setOf(CollectorAttemptState.CURRENT, CollectorAttemptState.NOT_MODIFIED) && current.snapshot?.sourceVersion == target.version
        WikipediaArticleReceipt(current.source.id, current.source.version, fetched,
            current.snapshot?.contentRevision, (current.snapshot?.payload as? CollectorPayload.Articles)?.items?.size ?: 0)
        } finally { watcher.cancel() }
    }
}
