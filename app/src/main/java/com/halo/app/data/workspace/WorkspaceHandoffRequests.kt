package com.halo.app.data.workspace

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** Single-consumer presentation request only. No stored command, implicit retry or edit replay. */
data class WorkspaceHandoffRequest(val id: String, val objectId: String, val version: String, val destination: WorkspaceDestination)
enum class WorkspaceHandoffPhase { PENDING, DELIVERED_TO_HOST, REJECTED, EXPIRED }
class WorkspaceHandoffRequests(private val scope: CoroutineScope, private val launchUi: () -> Unit) {
    private val mutable = MutableStateFlow<WorkspaceHandoffRequest?>(null)
    val pending = mutable.asStateFlow()
    private val lock = Any()
    private val receipts = linkedMapOf<String, WorkspaceHandoffPhase>()
    private fun mark(id: String, phase: WorkspaceHandoffPhase) = synchronized(lock) {
        receipts[id] = phase
        while (receipts.size > 32) receipts.remove(receipts.keys.first())
    }
    fun phase(id: String) = synchronized(lock) { receipts[id] }
    fun offer(objectId: String, version: String, destination: WorkspaceDestination): WorkspaceHandoffRequest {
        require(WorkspaceActionIds.isValid(objectId) && version.length in 1..200)
        val request = WorkspaceHandoffRequest(UUID.randomUUID().toString(), objectId, version, destination)
        check(mutable.compareAndSet(null, request)) { "Another handoff is pending" }
        mark(request.id, WorkspaceHandoffPhase.PENDING)
        try { launchUi() } catch (failure: Exception) { mutable.compareAndSet(request, null); mark(request.id, WorkspaceHandoffPhase.REJECTED); throw failure }
        scope.launch { delay(180_000); if (mutable.compareAndSet(request, null)) mark(request.id, WorkspaceHandoffPhase.EXPIRED) }
        return request
    }
    fun claim(request: WorkspaceHandoffRequest): Boolean = mutable.compareAndSet(request, null)
    fun finish(request: WorkspaceHandoffRequest, accepted: Boolean) = mark(request.id,
        if (accepted) WorkspaceHandoffPhase.DELIVERED_TO_HOST else WorkspaceHandoffPhase.REJECTED)
}
