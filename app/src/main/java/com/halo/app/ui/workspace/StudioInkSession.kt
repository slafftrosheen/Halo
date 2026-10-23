package com.halo.app.ui.workspace

import com.halo.app.data.studio.*
import com.halo.sdk.runtime.ink.InkCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Coalesces complete stroke snapshots, never deltas; external edits stop the session instead of being overwritten. */
internal class StudioInkSession(val initial: StudioProject, val layerId: String, projects: StudioProjectRepository,
    scope: CoroutineScope, onCommitted: (StudioProject) -> Unit, private val onFailure: () -> Unit) {
    val ownerToken: String = java.util.UUID.randomUUID().toString()
    private val project get() = initial
    private val mutableFailed = MutableStateFlow(false)
    val failed = mutableFailed.asStateFlow()
    private var latest = requireNotNull(initial.session.current.layers.find { it.id == layerId }).ink
    private val committed = MutableStateFlow(latest)
    private val pending = Channel<String>(Channel.CONFLATED)
    init {
        var revision = project.revision
        var layer = requireNotNull(project.session.current.layers.find { it.id == layerId })
        require(layer.kind == StudioLayerKind.INK && !layer.locked)
        scope.launch {
            try {
                for (encoded in pending) {
                    val updated = projects.apply(project.id, revision, StudioCommand.Replace(layer.copy(ink = encoded)))
                    revision = updated.revision
                    layer = requireNotNull(updated.session.current.layers.find { it.id == layerId })
                    committed.value = encoded
                    onCommitted(updated)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { pending.close(); mutableFailed.value = true; onFailure() }
        }
    }
    fun submit(encoded: String) {
        if (InkCodec.decode(encoded) == null) { mutableFailed.value = true; onFailure(); return }
        latest = encoded
        if (pending.trySend(encoded).isFailure) { mutableFailed.value = true; onFailure() }
    }
    suspend fun awaitPersisted(): Boolean {
        val expected = latest
        return withTimeoutOrNull(10_000) {
            combine(committed, failed) { encoded, failed -> encoded == expected || failed }.first { it }
            !failed.value && committed.value == expected
        } ?: false
    }
    fun draft(): StudioDocument {
        val layer = requireNotNull(initial.session.current.layers.find { it.id == layerId })
        return initial.session.current.apply(StudioCommand.Replace(layer.copy(ink = latest)))
    }
    /** Completed queued edits are drained by the existing worker; dismissal never replays a failed write. */
    fun finish() { pending.close() }
}
