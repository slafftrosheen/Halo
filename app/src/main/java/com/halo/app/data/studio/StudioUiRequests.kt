package com.halo.app.data.studio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class StudioUiRequestKind { OPEN, AI_REVIEW }
enum class StudioUiRequestPhase { PENDING_UI, UI_ACCEPTED, ATTACHED, REVIEW_READY, PREVIEW_READY, KEPT, DISCARDED, STALE, FAILED, EXPIRED }
data class StudioUiRequest(val id: String, val projectId: String, val kind: StudioUiRequestKind,
    val revision: Long?, val prompt: String?, val createdNanos: Long,
    val provenance: com.halo.sdk.provider.RemoteDispatchProvenance? = null)
data class StudioUiReceipt(val id: String, val projectId: String, val kind: StudioUiRequestKind,
    val phase: StudioUiRequestPhase, val resultProjectId: String? = null)

/** Bounded, ephemeral presentation handoff only. Runtime and image consent owners retain execution authority. */
class StudioUiRequests(private val scope: CoroutineScope, private val launchUi: () -> Unit,
    private val now: () -> Long = System::nanoTime) {
    private val lock = Any()
    private val mutable = MutableStateFlow<StudioUiRequest?>(null)
    val pending = mutable.asStateFlow()
    private val receipts = linkedMapOf<String, StudioUiReceipt>()
    private var expiry: Job? = null
    fun offer(projectId: String, kind: StudioUiRequestKind, revision: Long? = null, prompt: String? = null,
        provenance: com.halo.sdk.provider.RemoteDispatchProvenance? = null): StudioUiRequest {
        require(projectId.matches(Regex("[A-Za-z0-9_-]{1,100}")))
        if (kind == StudioUiRequestKind.AI_REVIEW) require(revision != null && revision > 0 && prompt != null && prompt.isNotBlank() && prompt.length <= 4000)
        else require(prompt == null && revision == null)
        val request = synchronized(lock) {
            expire()
            check(mutable.value == null) { "Complete the existing Studio review first" }
            StudioUiRequest(UUID.randomUUID().toString(), projectId, kind, revision, prompt, now(), provenance?.copy(categories = provenance.categories.toSet())).also {
                mutable.value = it
                receipts[it.id] = StudioUiReceipt(it.id, projectId, kind, StudioUiRequestPhase.PENDING_UI)
                while (receipts.size > 16) receipts.remove(receipts.keys.first())
                expiry?.cancel()
                expiry = scope.launch { delay(TTL_MILLIS); synchronized(lock) { expire() } }
            }
        }
        try { launchUi() }
        catch (failure: Exception) {
            synchronized(lock) {
                if (mutable.value?.id == request.id) { mutable.value = null; expiry?.cancel() }
                mark(request.id, StudioUiRequestPhase.FAILED)
            }
            throw failure
        }
        return request
    }
    fun claim(id: String): StudioUiRequest? = synchronized(lock) {
        expire()
        val request = mutable.value?.takeIf { it.id == id } ?: return@synchronized null
        mutable.value = null; expiry?.cancel()
        mark(id, StudioUiRequestPhase.UI_ACCEPTED)
        request
    }
    fun attached(projectId: String) = synchronized(lock) {
        receipts.values.filter { it.projectId == projectId && it.kind == StudioUiRequestKind.OPEN &&
            it.phase == StudioUiRequestPhase.UI_ACCEPTED }.forEach { mark(it.id, StudioUiRequestPhase.ATTACHED) }
    }
    fun receipt(id: String): StudioUiReceipt? = synchronized(lock) { expire(); receipts[id] }
    fun mark(id: String, phase: StudioUiRequestPhase, resultProjectId: String? = null) = synchronized(lock) {
        val previous = receipts[id] ?: return@synchronized
        if (previous.phase in TERMINAL) return@synchronized
        receipts[id] = previous.copy(phase = phase, resultProjectId = resultProjectId)
    }
    private fun expire() {
        val pending = mutable.value ?: return
        if (now() - pending.createdNanos >= TTL_MILLIS * 1_000_000) {
            mutable.value = null
            mark(pending.id, StudioUiRequestPhase.EXPIRED)
        }
    }
    companion object {
        const val TTL_MILLIS = 180_000L
        private val TERMINAL = setOf(StudioUiRequestPhase.ATTACHED, StudioUiRequestPhase.KEPT, StudioUiRequestPhase.DISCARDED,
            StudioUiRequestPhase.STALE, StudioUiRequestPhase.FAILED, StudioUiRequestPhase.EXPIRED)
    }
}
