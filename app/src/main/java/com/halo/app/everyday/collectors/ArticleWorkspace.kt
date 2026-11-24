package com.halo.app.everyday.collectors

import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.app.data.ObjectProvenance
import com.halo.app.data.notebook.NotebookNote
import com.halo.app.notebook.NotebookUseCases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class SavedArticleIdentity(val sourceId: String, val sourceVersion: Long, val contentRevision: String, val articleId: String)

/** Materialize the exact saved article as an ordinary local note for the existing Workspace tools. */
class ArticleWorkspace(
    private val database: AppDatabase,
    private val sources: CollectorRepository,
    private val body: (String?) -> ArticleContent?,
    private val notebook: NotebookUseCases,
) {
    private suspend fun current(identity: SavedArticleIdentity): ArticleFact {
        val state = sources.state(identity.sourceId) ?: throw CollectorConflict("Source unavailable")
        val source = state.source
        val snapshot = state.snapshot ?: throw CollectorConflict("Saved article unavailable")
        if (source.version != identity.sourceVersion || !source.selected || !source.enabled || source.muted ||
            snapshot.sourceVersion != source.version || snapshot.contentRevision != identity.contentRevision
        ) throw CollectorConflict("Saved article changed")
        return (snapshot.payload as? CollectorPayload.Articles)?.items?.firstOrNull { it.id == identity.articleId }
            ?: throw CollectorConflict("Saved article unavailable")
    }

    suspend fun keep(identity: SavedArticleIdentity, verify: () -> Unit): NotebookNote = withContext(Dispatchers.IO) {
        verify()
        val article = current(identity)
        val content = body(article.contentKey)
        database.withTransaction {
            verify()
            if (current(identity) != article) throw CollectorConflict("Saved article changed")
            val note = notebook.createNote(articleWorkspaceText(article, content), article.title.take(120))
            database.workspaceContextDao().provenance(ObjectProvenance(note.id, identity.sourceId, identity.contentRevision,
                JSONObject().put("operation", "keep-saved-article").put("sourceVersion", identity.sourceVersion)
                    .put("articleId", identity.articleId).toString()))
            verify()
            note
        }
    }
}

internal fun articleWorkspaceText(article: ArticleFact, content: ArticleContent?): String {
    val savedText = content?.text?.takeIf(String::isNotBlank)
    return buildString {
        appendLine(article.publisher)
        article.publishedAt?.let { appendLine(it.toString()) }
        (content?.pageUrl ?: article.url)?.let { appendLine(it) }
        if (savedText == null) appendLine("Saved feed excerpt")
        else if (content?.status != "PAGE_CAPTURED") appendLine("Saved article text may be partial")
        appendLine()
        append(savedText ?: article.text)
    }
}
