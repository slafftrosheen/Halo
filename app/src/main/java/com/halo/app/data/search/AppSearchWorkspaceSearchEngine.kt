package com.halo.app.data.search

import android.content.Context
import androidx.appsearch.app.AppSearchSession
import androidx.appsearch.app.PutDocumentsRequest
import androidx.appsearch.app.RemoveByDocumentIdRequest
import androidx.appsearch.app.SearchSpec
import androidx.appsearch.app.SetSchemaRequest
import androidx.appsearch.localstorage.LocalStorage
import com.google.common.util.concurrent.ListenableFuture
import com.halo.app.data.notebook.NotebookDao
import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.notebook.WorkspaceObjectType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { cont ->
    addListener({
        try {
            cont.resume(get())
        } catch (e: Exception) {
            val cause = e.cause ?: e
            if (cont.isActive) cont.resumeWithException(cause)
        }
    }, { it.run() })
    cont.invokeOnCancellation {
        cancel(true)
    }
}

class AppSearchWorkspaceSearchEngine(
    private val context: Context,
    private val dao: NotebookDao
) : WorkspaceSearchEngine {

    private var session: AppSearchSession? = null
    private val initializing = AtomicBoolean(false)

    private suspend fun ensureSession(): AppSearchSession? {
        if (session != null) return session
        if (initializing.compareAndSet(false, true)) {
            try {
                val future = LocalStorage.createSearchSessionAsync(
                    LocalStorage.SearchContext.Builder(context, "halo_workspace").build()
                )
                val s = future.await()
                val setSchemaFuture = s.setSchemaAsync(
                    SetSchemaRequest.Builder()
                        .addDocumentClasses(WorkspaceSearchDocument::class.java)
                        .build()
                )
                setSchemaFuture.await()
                session = s
            } catch (_: Throwable) {
                initializing.set(false)
            }
        }
        return session
    }

    suspend fun clearDerivedIndex() {
        val s = ensureSession() ?: return
        s.removeAsync("", SearchSpec.Builder().setTermMatch(SearchSpec.TERM_MATCH_PREFIX).build()).await()
    }

    override suspend fun indexObject(
        objectId: WorkspaceObjectId,
        type: WorkspaceObjectType,
        title: String,
        content: String,
        updatedAt: Long
    ) {
        val s = ensureSession() ?: return
        try {
            val doc = WorkspaceSearchDocument(
                id = objectId.value,
                title = title,
                content = content,
                type = type.name,
                updatedAt = updatedAt
            )
            val putRequest = PutDocumentsRequest.Builder()
                .addDocuments(doc)
                .build()
            s.putAsync(putRequest).await()
        } catch (_: Throwable) {}
    }

    override suspend fun removeObject(objectId: WorkspaceObjectId) {
        val s = ensureSession() ?: return
        try {
            val removeRequest = RemoveByDocumentIdRequest.Builder("workspace")
                .addIds(objectId.value)
                .build()
            s.removeAsync(removeRequest).await()
        } catch (_: Throwable) {}
    }

    override suspend fun search(query: WorkspaceSearchQuery): WorkspaceSearchResult = withContext(Dispatchers.IO) {
        val rawQuery = query.query.trim()
        if (rawQuery.isEmpty()) {
            return@withContext WorkspaceSearchResult(rawQuery, emptyList())
        }

        val roomHits = dao.searchWorkspaceObjects(
            "%${rawQuery.replace("%", "\\%").replace("_", "\\_")}%",
            query.maxHits
        ).mapNotNull { row ->
            val type = runCatching { WorkspaceObjectType.valueOf(row.type) }.getOrNull()
                ?: return@mapNotNull null
            if (query.types.isNotEmpty() && type !in query.types) return@mapNotNull null
            WorkspaceSearchHit(WorkspaceObjectId(row.id), type, row.title, row.snippet, 0f)
        }
        val s = ensureSession()
        if (s != null) {
            val appSearchResults = try {
                val spec = SearchSpec.Builder()
                    .setResultCountPerPage(query.maxHits)
                    .setTermMatch(SearchSpec.TERM_MATCH_PREFIX)
                    .setRankingStrategy(SearchSpec.RANKING_STRATEGY_RELEVANCE_SCORE)
                    .build()

                val searchResults = s.search(rawQuery, spec)
                val page = searchResults.nextPageAsync.await()

                page.mapNotNull { searchResult ->
                    val doc = searchResult.getDocument(WorkspaceSearchDocument::class.java)
                    val type = runCatching { WorkspaceObjectType.valueOf(doc.type) }.getOrDefault(WorkspaceObjectType.NOTE)
                    if (query.types.isNotEmpty() && type !in query.types) {
                        null
                    } else {
                        val snippet = doc.content.take(120)
                        WorkspaceSearchHit(
                            objectId = WorkspaceObjectId(doc.id),
                            type = type,
                            title = doc.title,
                            snippet = snippet,
                            score = searchResult.rankingSignal.toFloat()
                        )
                    }
                }
            } catch (_: Throwable) {
                null
            }

            if (appSearchResults != null) {
                val merged = (appSearchResults + roomHits)
                    .distinctBy { it.objectId.value }
                    .take(query.maxHits)
                return@withContext WorkspaceSearchResult(rawQuery, merged)
            }
        }

        WorkspaceSearchResult(rawQuery, roomHits)
    }
}
