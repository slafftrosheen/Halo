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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
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
    private var nativeIndexUnavailable = false

    private suspend fun ensureSession(): AppSearchSession? = sessionMutex.withLock {
        if (session != null) return@withLock session
        if (nativeIndexUnavailable) return@withLock null
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (unavailable: LinkageError) {
            nativeIndexUnavailable = true
            Log.w("AppSearch", "Native ranking is unavailable. Using local workspace search.", unavailable)
            null
        } catch (e: Exception) {
            Log.w("AppSearch", "Failed to initialize AppSearch session", e)
            null
        }
    }

    suspend fun clearDerivedIndex() {
        val s = ensureSession() ?: return
        try {
            s.removeAsync("", SearchSpec.Builder().setTermMatch(SearchSpec.TERM_MATCH_PREFIX).build()).await()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
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
        val current = dao.getWorkspaceObject(objectId.value)
        if (current == null || current.lifecycle != "ACTIVE" || current.title != title ||
            !WorkspaceLocalContentSearch.safe(title + "\n" + content)) {
            removeObject(objectId)
            return
        }
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
            if (dao.getWorkspaceObject(objectId.value)?.lifecycle != "ACTIVE") removeObject(objectId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.w("AppSearch", "Failed to remove object ${objectId.value}", e)
        }
    }

    override suspend fun search(query: WorkspaceSearchQuery): WorkspaceSearchResult = withContext(Dispatchers.IO) {
        val rawQuery = query.query.trim()
        if (rawQuery.isEmpty()) {
            return@withContext WorkspaceSearchResult(rawQuery, emptyList())
        }

        val limit = query.maxHits.coerceIn(1, 100)
        val cardHits = mutableListOf<WorkspaceSearchHit>()
        var offset = 0
        do {
            ensureActive()
            val page = dao.canvasSearchCandidates(offset = offset)
            cardHits += WorkspaceLocalContentSearch.cards(page, query)
            offset += page.size
        } while (page.size == 64 && cardHits.size < limit)
        val roomHits = mutableListOf<WorkspaceSearchHit>()
        if (query.canvasId == null) {
            offset = 0
            do {
                ensureActive()
                val page = dao.localSearchCandidates(offset = offset)
                roomHits += page.mapNotNull { row ->
                    val type = runCatching { WorkspaceObjectType.valueOf(row.type) }.getOrNull() ?: return@mapNotNull null
                    if (query.types.isNotEmpty() && type !in query.types) return@mapNotNull null
                    if (query.sourceTypes.isNotEmpty() && type !in query.sourceTypes) return@mapNotNull null
                    val text = row.title + "\n" + row.snippet.orEmpty()
                    if (!WorkspaceLocalContentSearch.matches(text, rawQuery) || !WorkspaceLocalContentSearch.safe(text)) return@mapNotNull null
                    WorkspaceSearchHit(WorkspaceObjectId(row.id), type, row.title, WorkspaceLocalContentSearch.snippet(text, rawQuery), 0f)
                }
                offset += page.size
            } while (page.size == 64 && roomHits.distinctBy { it.objectId }.size < limit)
        }
        val s = ensureSession()
        val ranking = if (s != null) {
            try {
                val spec = SearchSpec.Builder()
                    .setResultCountPerPage(limit)
                    .setTermMatch(SearchSpec.TERM_MATCH_PREFIX)
                    .setRankingStrategy(SearchSpec.RANKING_STRATEGY_RELEVANCE_SCORE)
                    .build()
                val searchResults = s.search(rawQuery, spec)
                try {
                    searchResults.nextPageAsync.await().associate { result ->
                        result.genericDocument.id to result.rankingSignal.toFloat()
                    }
                } finally {
                    searchResults.close()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.w("AppSearch", "AppSearch search failed", e)
                emptyMap()
            }
        } else emptyMap()

        // A lock can commit while AppSearch is ranking an earlier Room page.
        val merged = (cardHits + roomHits).distinctBy { it.objectId.value to it.nodeId }
            .map { it.copy(score = maxOf(it.score, ranking[it.objectId.value] ?: 0f)) }
            .sortedByDescending { it.score }
        val visible = ArrayList<WorkspaceSearchHit>(limit)
        for (hit in merged) {
            if (dao.getWorkspaceObject(hit.objectId.value)?.lifecycle != "ACTIVE") continue
            if (hit.sourceObjectId != null && dao.getWorkspaceObject(hit.sourceObjectId)?.lifecycle != "ACTIVE") continue
            visible += hit
            if (visible.size == limit) break
        }
        WorkspaceSearchResult(rawQuery, visible)
    }
}
