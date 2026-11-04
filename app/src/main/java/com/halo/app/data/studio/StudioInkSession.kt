package com.halo.app.data.studio

import com.halo.sdk.runtime.ink.InkCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Coalesces complete stroke snapshots, never deltas; external edits stop the session instead of being overwritten. */
internal class StudioInkSession(val initial: StudioProject, val layerId: String, projects: StudioProjectRepository,
    scope: CoroutineScope, onCommitted: (StudioProject) -> Unit, private val onFailure: () -> Unit,
    newLayer:StudioLayer?=null) {
    val initialLayer=requireNotNull(initial.session.current.layers.find {it.id==layerId} ?: newLayer?.takeIf {it.id==layerId})
    private val adding=initial.session.current.layers.none {it.id==layerId}
    private val otherPoints=initial.session.current.layers.filter {it.id!=layerId&&it.kind==StudioLayerKind.INK}.sumOf {row->
        requireNotNull(InkCodec.decode(row.ink)).strokes.sumOf {it.points.size}
    }
    fun fitsPreview(ink:com.halo.sdk.runtime.ink.InkDocument):Boolean = otherPoints+ink.strokes.sumOf {it.points.size}<=StudioDocument.MAX_DOCUMENT_POINTS
    val ownerToken: String = java.util.UUID.randomUUID().toString()
    private val project get() = initial
    private val mutableFailed = MutableStateFlow(false)
    val failed = mutableFailed.asStateFlow()
    private val mutableSaving=MutableStateFlow(false)
    val saving=mutableSaving.asStateFlow()
    private val stateLock=Any()
    @Volatile private var latest = initialLayer.ink
    private data class PersistedInk(val encoded: String, val revision: Long)
    private val committed = MutableStateFlow(PersistedInk(latest, initial.revision))
    /** Revision written by this input owner, never advanced by unrelated Room emissions. */
    val persistedRevision: Long get() = committed.value.revision
    val fullyPersisted:Boolean get()=!failed.value&&committed.value.encoded==latest
    private val pending = Channel<String>(Channel.CONFLATED)
    private val writer:Job
    init {
        var revision = project.revision
        var layer = initialLayer
        require(layer.kind == StudioLayerKind.INK)
        if(adding) {require(newLayer!=null&&layer.parentId==null);initial.session.current.apply(StudioCommand.Add(layer))}
        else require(initial.session.current.isEditable(layer))
        writer=scope.launch {
            var created=!adding
            try {
                for (encoded in pending) {
                    val command=if(created) StudioCommand.Replace(layer.copy(ink=encoded)) else StudioCommand.Add(layer.copy(ink=encoded))
                    val updated = projects.apply(project.id, revision, command)
                    created=true
                    revision = updated.revision
                    layer = requireNotNull(updated.session.current.layers.find { it.id == layerId })
                    synchronized(stateLock) {
                        committed.value = PersistedInk(encoded, updated.revision)
                        mutableSaving.value=encoded!=latest
                    }
                    onCommitted(updated)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { pending.close();mutableSaving.value=false; mutableFailed.value = true; onFailure() }
        }
    }
    fun submit(encoded: String) {
        if (InkCodec.decode(encoded) == null) { mutableFailed.value = true; onFailure(); return }
        synchronized(stateLock) {
            latest = encoded
            mutableSaving.value=true
            if (pending.trySend(encoded).isFailure) {mutableSaving.value=false; mutableFailed.value = true; onFailure() }
        }
    }
    suspend fun awaitPersisted(): Boolean {
        val expected = latest
        return withTimeoutOrNull(10_000) {
            combine(committed, failed) { saved, failed -> saved.encoded == expected || failed }.first { it }
            !failed.value && committed.value.encoded == expected
        } ?: false
    }
    fun draft(): StudioDocument {
        val marks=initialLayer.copy(ink=latest)
        return runCatching {preview(latest).also(StudioDocument::requireValid)}.getOrElse {
            // A valid layer can exceed the composition's aggregate quota. Recover the marks independently.
            StudioDocument(initial.session.current.width,initial.session.current.height,recipe=initial.session.current.recipe,
                layers=listOf(marks.copy(parentId=null)),selectedLayerId=marks.id).also(StudioDocument::requireValid)
        }
    }
    /** Data-only transient preview; the IO renderer and persistence owner validate before use. */
    fun preview(encoded:String):StudioDocument {
        val marks=initialLayer.copy(ink=encoded)
        return initial.session.current.copy(layers=if(adding) initial.session.current.layers+marks else initial.session.current.layers.map {if(it.id==layerId) marks else it},
            selectedLayerId=layerId)
    }
    /** Completed queued edits are drained by the existing worker; dismissal never replays a failed write. */
    fun finish() { pending.close() }
    suspend fun finishAndAwait():Boolean {
        finish();writer.join()
        return fullyPersisted
    }
}
