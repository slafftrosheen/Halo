package com.halo.host.ink

import com.halo.sdk.runtime.ink.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select

enum class InkSenderState { WAITING, SENDING, SAVED, DISCONNECTED, CONFLICT }

/**
 * One request/receipt at a time. At most one queued complete local document and one replaceable
 * preview; late previews cannot starve durable ink. Local persistence remains the editor's owner.
 * The host must close this on background, navigation, revoked consent or lost channel.
 */
class InkSenderHost(
    private val channel: InkTlsChannel,
    val offer: InkSessionOffer,
    scope: CoroutineScope,
    nowMs: () -> Long = System::currentTimeMillis,
) : java.io.Closeable {
    private val publisher = RemoteInkPublisher(offer, nowMs)
    private data class Checkpoint(val document: InkDocument, val sha256: String)
    private data class Outbound(val frame: ByteArray, val checkpointSha256: String? = null)
    private val checkpoints = Channel<Checkpoint>(Channel.CONFLATED)
    private val previews = Channel<InkEventPayload>(Channel.CONFLATED)
    private val selections = Channel<InkSelectionContext>(Channel.CONFLATED)
    private val mutableState = MutableStateFlow(InkSenderState.WAITING)
    val state: StateFlow<InkSenderState> = mutableState.asStateFlow()
    private val mutableConfirmedDocument = MutableStateFlow<String?>(null)
    val confirmedDocumentSha256 = mutableConfirmedDocument.asStateFlow()
    private val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            while (isActive) {
                val immediate = checkpoints.tryReceive().getOrNull()
                @OptIn(ExperimentalCoroutinesApi::class)
                val outbound = if (immediate != null) Outbound(publisher.checkpoint(immediate.document), immediate.sha256) else select<Outbound?> {
                    checkpoints.onReceive { Outbound(publisher.checkpoint(it.document), it.sha256) }
                    selections.onReceive { publisher.selection(it)?.let { frame -> Outbound(frame) } }
                    previews.onReceive { payload -> when (payload) {
                        is InkEventPayload.Preview -> publisher.preview(payload.stroke)
                        else -> publisher.clearPreview()
                    }?.let { frame -> Outbound(frame) } }
                    onTimeout(3000) { publisher.heartbeat()?.let { frame -> Outbound(frame) } }
                }
                if (outbound == null) continue
                mutableState.value = InkSenderState.SENDING
                channel.write(outbound.frame)
                val receipt = publisher.acknowledge(channel.read())
                if (receipt.status in setOf(InkReceiveStatus.SAVED, InkReceiveStatus.DUPLICATE))
                    mutableConfirmedDocument.value = requireNotNull(outbound.checkpointSha256)
                mutableState.value = when (receipt.status) {
                    InkReceiveStatus.SAVED, InkReceiveStatus.DUPLICATE -> InkSenderState.SAVED
                    InkReceiveStatus.PREVIEW -> InkSenderState.WAITING
                    InkReceiveStatus.CONFLICT -> InkSenderState.CONFLICT
                    else -> InkSenderState.DISCONNECTED
                }
                if (receipt.status !in setOf(InkReceiveStatus.SAVED, InkReceiveStatus.DUPLICATE, InkReceiveStatus.PREVIEW)) break
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutableState.value = InkSenderState.DISCONNECTED }
        finally {
            if (mutableState.value != InkSenderState.CONFLICT) mutableState.value = InkSenderState.DISCONNECTED
            publisher.stop(); checkpoints.close(); previews.close(); selections.close(); channel.close()
        }
    }

    /** Freeze now, not after suspension; the UI is allowed to mutate its own editor lists. */
    fun checkpoint(document: InkDocument): Boolean {
        if (!job.isActive) return false
        val encoded = InkCodec.encode(document)
        val frozen = requireNotNull(InkCodec.decode(encoded))
        return checkpoints.trySend(Checkpoint(frozen, InkDocumentIdentity.encodedSha256(encoded))).isSuccess
    }
    fun preview(stroke: InkStroke): Boolean {
        if (!job.isActive) return false
        val frozen = stroke.copy(points = stroke.points.takeLast(InkEventPayload.MAX_PREVIEW_POINTS).toList())
        return previews.trySend(InkEventPayload.Preview(frozen)).isSuccess
    }
    fun clearPreview(): Boolean = job.isActive && previews.trySend(InkEventPayload.ClearPreview).isSuccess
    fun selection(context: InkSelectionContext): Boolean = job.isActive && selections.trySend(
        context.copy(selectedStrokeIds = context.selectedStrokeIds.toList())).isSuccess
    override fun close() { job.cancel(); channel.close() }
}
