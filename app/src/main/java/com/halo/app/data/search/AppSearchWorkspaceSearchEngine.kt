package com.halo.app.data.search

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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
    private val sessionMutex = Mutex()

    private suspend fun ensureSession(): AppSearchSession? = sessionMutex.withLock {
        if (session != null) return@withLock session
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
            s
        } catch (e: Throwable) {
            Log.w("AppSearch", "Failed to initialize AppSearch session", e)
            null
        }
    }

    suspend fun clearDerivedIndex() {
        val s = ensureSession() ?: return
        try {
            s.removeAsync("", SearchSpec.Builder().setTermMatch(SearchSpec.TERM_MATCH_PREFIX).build()).await()
        } catch (e: Throwable) {
            Log.w("AppSearch", "Failed to clear derived index", e)
        }
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
        } catch (e: Throwable) {
            Log.w("AppSearch", "Failed to index object ${objectId.value}", e)
        }
    }

    override suspend fun removeObject(objectId: WorkspaceObjectId) {
        val s = ensureSession() ?: return
        try {
            val removeRequest = RemoveByDocumentIdRequest.Builder("workspace")
                .addIds(objectId.value)
                .build()
            s.removeAsync(removeRequest).await()
        } catch (e: Throwable) {
            Log.w("AppSearch", "Failed to remove object ${objectId.value}", e)
        }
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
                    .setSnippetCount(query.maxHits)
                    .setSnippetCountPerProperty(1)
                    .setMaxSnippetSize(180)
                    .build()

                val searchResults = s.search(rawQuery, spec)
                val page = searchResults.nextPageAsync.await()

                page.mapNotNull { searchResult ->
                    val doc = searchResult.getDocument(WorkspaceSearchDocument::class.java)
                    val type = runCatching { WorkspaceObjectType.valueOf(doc.type) }.getOrDefault(WorkspaceObjectType.NOTE)
                    if (query.types.isNotEmpty() && type !in query.types) {
                        null
                    } else {
                        val matchSnippet = searchResult.matchInfos.firstOrNull()?.exactMatchRange?.let { range ->
                            val start = (range.start - 20).coerceAtLeast(0)
                            val end = (range.end + 100).coerceAtMost(doc.content.length)
                            if (start < end) "..." + doc.content.substring(start, end) + "..." else null
                        } ?: doc.content.take(120)

                        WorkspaceSearchHit(
                            objectId = WorkspaceObjectId(doc.id),
                            type = type,
                            title = doc.title,
                            snippet = matchSnippet,
                            score = searchResult.rankingSignal.toFloat()
                        )
                    }
                }
            } catch (e: Throwable) {
                Log.w("AppSearch", "AppSearch search failed", e)
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
