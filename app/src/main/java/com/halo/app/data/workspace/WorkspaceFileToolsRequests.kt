package com.halo.app.data.workspace

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class WorkspaceFileToolsRequest(val id: String, val objectId: String?)

/** Ephemeral single-consumer presentation only. Runtime retains approval/dispatch truth. No operation is replayed. */
class WorkspaceFileToolsRequests(private val scope: CoroutineScope, private val launchUi: () -> Unit) {
    private val mutable = MutableStateFlow<WorkspaceFileToolsRequest?>(null)
    val pending = mutable.asStateFlow()
    fun offer(objectId: String?): WorkspaceFileToolsRequest {
        require(objectId == null || objectId.matches(Regex("[A-Za-z0-9_-]{1,100}")))
        val request = WorkspaceFileToolsRequest(UUID.randomUUID().toString(), objectId)
        check(mutable.compareAndSet(null, request)) { "An existing File tools request is pending" }
        try { launchUi() } catch (failure: Exception) { mutable.compareAndSet(request, null); throw failure }
        scope.launch { delay(180_000); mutable.compareAndSet(request, null) }
        return request
    }
    fun claim(request: WorkspaceFileToolsRequest): WorkspaceFileToolsRequest? =
        if (mutable.compareAndSet(request, null)) request else null
}
