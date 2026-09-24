package com.halo.app.di.tools

import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

data class PendingToolApproval(val id: String, val preview: ToolActionPreview)

/** One visible approval at a time, bound to this exact call and cancelled with its turn. */
class InteractiveToolAuthorization(private val isForeground: () -> Boolean) : ToolAuthorizationCoordinator {
    private val mutex = Mutex()
    private val mutablePending = MutableStateFlow<PendingToolApproval?>(null)
    val pending = mutablePending.asStateFlow()
    private data class Decision(val request: PendingToolApproval, val answer: CompletableDeferred<Boolean>)
    private val active = java.util.concurrent.atomic.AtomicReference<Decision?>(null)

    fun respond(id: String, approved: Boolean) {
        val decision = active.get() ?: return
        if (decision.request.id == id) decision.answer.complete(approved && isForeground())
    }

    override suspend fun authorizeRequest(toolCall: ToolCall, preview: ToolActionPreview): ToolAuthorization = mutex.withLock {
        if (!isForeground()) return@withLock ToolAuthorization.Rejected(toolCall, "Open Halo to approve this action")
        val decision = CompletableDeferred<Boolean>()
        val request = PendingToolApproval(UUID.randomUUID().toString(), preview)
        active.set(Decision(request, decision))
        mutablePending.value = request
        try {
            if (withTimeoutOrNull(120_000L) { decision.await() } == true && isForeground()) {
                ToolAuthorization.Authorized(toolCall)
            } else ToolAuthorization.Rejected(toolCall, "Action was declined or approval expired")
        } finally {
            mutablePending.value = null
            active.set(null)
            decision.cancel()
        }
    }
}
