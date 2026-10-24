package com.halo.app.context

import kotlinx.coroutines.*
import java.util.UUID

data class CanvasWorkflowStatus(val canvasId: String, val version: String, val busy: Boolean,
    val reviewedDraftToken: String? = null, val draftHash: String? = null, val draftCharacters: Int = 0,
    val sourceNodeId: String? = null, val sourceFileVersion: String? = null)
data class CanvasWorkflowReceipt(val canvasId: String, val version: String, val phase: String,
    val noteId: String? = null, val nodeId: String? = null, val edgeId: String? = null,
    val reviewedDraftToken: String? = null, val draftHash: String? = null, val draftCharacters: Int = 0,
    val navigationTarget: String? = null)
class CanvasWorkflowConflict(message: String) : IllegalStateException(message)
fun requireCanvasWorkflow(value: Boolean, message: String) { if (!value) throw CanvasWorkflowConflict(message) }

/** Awaitable operations implemented by the existing active Canvas editor owner, never a second edit executor. */
interface CanvasWorkflowHost {
    val workflowCanvasId: String
    suspend fun workflowStatus(): CanvasWorkflowStatus
    suspend fun workflowPalette(version: String, nodeId: String, fileVersion: String, guard: () -> Unit): CanvasWorkflowReceipt
    suspend fun workflowTranscribe(version: String, nodeId: String, fileVersion: String, guard: () -> Unit): CanvasWorkflowReceipt
    suspend fun workflowReviewedText(version: String, token: String, draftHash: String, action: String, text: String?, guard: () -> Unit): CanvasWorkflowReceipt
    suspend fun workflowExport(version: String, startStudy: Boolean, guard: () -> Unit): CanvasWorkflowReceipt
}

/** Active-view lease only. Domain work runs in the caller's existing turn coroutine. */
class CanvasWorkflowBridge {
    private class Binding(val host: CanvasWorkflowHost) { val jobs = mutableSetOf<Job>() }
    private val lock = Any()
    private val bindings = linkedMapOf<String, Binding>()
    fun attach(host: CanvasWorkflowHost): String = synchronized(lock) {
        UUID.randomUUID().toString().also { bindings[it] = Binding(host) }
    }
    fun detach(token: String) {
        val jobs = synchronized(lock) { bindings.remove(token)?.jobs?.toList().orEmpty() }
        jobs.forEach { it.cancel(CancellationException("Canvas view closed")) }
    }
    suspend fun <T> withHost(canvasId: String?, block: suspend (CanvasWorkflowHost, () -> Unit) -> T): T = coroutineScope {
        val job = requireNotNull(currentCoroutineContext()[Job])
        val selected = synchronized(lock) {
            val values = bindings.values.filter { canvasId == null || it.host.workflowCanvasId == canvasId }
            requireCanvasWorkflow(values.size == 1, "Open one matching Canvas view before requesting this workflow.")
            values.single().also { it.jobs += job }
        }
        val guard: () -> Unit = {
            job.ensureActive()
            synchronized(lock) { requireCanvasWorkflow(bindings.values.count { it.host.workflowCanvasId == selected.host.workflowCanvasId } == 1 &&
                bindings.values.any { it === selected }, "Canvas view changed or closed") }
        }
        try { guard(); block(selected.host, guard) }
        finally { synchronized(lock) { selected.jobs.remove(job) } }
    }
}
