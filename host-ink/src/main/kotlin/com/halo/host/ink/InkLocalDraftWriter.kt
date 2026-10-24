package com.halo.host.ink

import com.halo.sdk.runtime.ink.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class InkLocalSaveState { SAVED, SAVING, FAILED }

/** Serial, bounded local persistence. An older completion cannot acknowledge a newer edit. */
class InkLocalDraftWriter(
    scope: CoroutineScope,
    private val write: suspend (InkDocument) -> Unit,
    private val onPersisted: (InkDocument) -> Unit,
) : java.io.Closeable {
    private data class Edit(val version: Long, val document: InkDocument, val share: Boolean)
    private val lock = Any()
    private var version = 0L
    private var closed = false
    private val edits = Channel<Edit>(Channel.CONFLATED)
    private val mutableState = MutableStateFlow(InkLocalSaveState.SAVED)
    val state = mutableState.asStateFlow()
    private val job = scope.launch(Dispatchers.IO) {
        for (edit in edits) {
            try {
                write(edit.document)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                synchronized(lock) { if (edit.version == version) mutableState.value = InkLocalSaveState.FAILED }
                continue
            }
            synchronized(lock) {
                if (!closed && edit.share) {
                    try { onPersisted(edit.document) } catch (_: Exception) { /* Local save remains truthful; no remote receipt is invented. */ }
                }
                if (edit.version == version) mutableState.value = InkLocalSaveState.SAVED
            }
        }
    }
    fun edit(document: InkDocument, share: Boolean): Boolean {
        val frozen = requireNotNull(InkCodec.decode(InkCodec.encode(document)))
        return synchronized(lock) {
            if (closed || !job.isActive || version == Long.MAX_VALUE) return@synchronized false
            version++
            mutableState.value = InkLocalSaveState.SAVING
            edits.trySend(Edit(version, frozen, share)).isSuccess
        }
    }
    override fun close() = synchronized(lock) {
        closed = true
        edits.close() // Drain the latest complete local snapshot, but stop network publication.
        Unit
    }
    suspend fun awaitClosed() { job.join() }
}
