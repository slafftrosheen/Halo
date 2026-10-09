package com.halo.app.capture

import com.halo.sdk.runtime.multimodal.*
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

/** App-private, bounded cache. Only opaque UUIDs resolve; caller-supplied paths never do. */
class EphemeralMediaAssetStore(
    root: File,
    private val maxBytes: Long = 64L * 1024 * 1024,
    private val maxAgeMs: Long = 15 * 60_000L,
    scope: kotlinx.coroutines.CoroutineScope,
    eventBus: com.halo.sdk.runtime.EventBus,
) : MediaAssetStore {
    private val directory = File(root, "interaction-media").apply { mkdirs() }
    private val entries = linkedMapOf<String, MediaAssetRef>()
    private val lock = Any()
    private val owners = mutableMapOf<String, MutableSet<String>>()
    private fun owns(ref: MediaAssetRef): Boolean = entries[ref.id]?.copy(interactionId = ref.interactionId) == ref && ref.interactionId in owners[ref.id].orEmpty()

    init {
        // Identities are process scoped. Orphan captures from a prior process have no owner.
        directory.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            while (true) {
                kotlinx.coroutines.delay(60_000)
                synchronized(lock) { prune() }
            }
        }
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            eventBus.observe(com.halo.sdk.runtime.InteractionTurnChanged::class.java).collect { event ->
                when (val state = event.state) {
                    is com.halo.sdk.runtime.InteractionTurnState.Completed,
                    is com.halo.sdk.runtime.InteractionTurnState.Cancelled,
                    is com.halo.sdk.runtime.InteractionTurnState.Failed,
                    is com.halo.sdk.runtime.InteractionTurnState.Interrupted -> state.interactionId?.let { releaseInteraction(it) }
                    else -> Unit
                }
            }
        }
    }

    override suspend fun transferOwnership(ref: MediaAssetRef, interactionId: String): MediaAssetRef = access {
        prune()
        val existing = entries[ref.id]?.takeIf { owns(ref) } ?: throw MediaAcquisitionException(MediaFailure.SOURCE_UNAVAILABLE)
        existing.copy(interactionId = interactionId).also { owners.getValue(ref.id).add(interactionId) }
    }

    override suspend fun ingest(content: MediaAssetContent, provenance: MediaProvenance, interactionId: String, sourceDeviceId: String?, workspaceObjectId: String?): MediaAssetRef = access {
        prune()
        if (!content.mimeType.startsWith("image/") && !content.mimeType.startsWith("audio/")) throw MediaAcquisitionException(MediaFailure.UNSUPPORTED_FORMAT)
        if (content.bytes.isEmpty() || content.bytes.size.toLong() > maxBytes) throw MediaAcquisitionException(MediaFailure.TOO_LARGE)
        while (entries.values.sumOf { it.bytes ?: 0 } + content.bytes.size > maxBytes) {
            remove(entries.keys.first())
        }
        val id = UUID.randomUUID().toString()
        val ref = MediaAssetRef(id, if (content.mimeType.startsWith("audio/")) MediaKind.AUDIO else MediaKind.IMAGE,
            content.mimeType, content.bytes.size.toLong(), MessageDigest.getInstance("SHA-256").digest(content.bytes).joinToString("") { "%02x".format(it) },
            System.currentTimeMillis(), provenance, interactionId = interactionId, sourceDeviceId = sourceDeviceId, workspaceObjectId = workspaceObjectId)
        File(directory, id).writeBytes(content.bytes)
        entries[id] = ref
        owners[id] = mutableSetOf(interactionId)
        ref
    }

    override suspend fun metadata(ref: MediaAssetRef): MediaAssetMetadata? = access {
        prune()
        entries[ref.id]?.takeIf { owns(ref) }?.let { MediaAssetMetadata(ref) }
    }

    override suspend fun open(ref: MediaAssetRef): MediaAssetContent = access {
        prune()
        val owned = entries[ref.id]?.takeIf { owns(ref) } ?: throw MediaAcquisitionException(MediaFailure.SOURCE_UNAVAILABLE)
        val file = File(directory, owned.id)
        if (!file.isFile || file.length() != owned.bytes) throw MediaAcquisitionException(MediaFailure.TRANSFER_FAILED)
        val bytes = file.readBytes()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        if (hash != owned.sha256) throw MediaAcquisitionException(MediaFailure.TRANSFER_FAILED)
        MediaAssetContent(bytes, owned.mimeType)
    }

    override suspend fun releaseInteraction(interactionId: String) = access {
        owners.keys.toList().forEach { id ->
            owners[id]?.remove(interactionId)
            if (owners[id].isNullOrEmpty()) remove(id)
        }
    }

    private suspend fun <T> access(block: () -> T): T = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        synchronized(lock) { block() }
    }

    private fun prune() {
        val now = System.currentTimeMillis()
        entries.values.filter { now - it.createdAtMs !in 0..maxAgeMs }.map { it.id }.forEach(::remove)
    }
    private fun remove(id: String) {
        if (!File(directory, id).exists() || File(directory, id).delete()) { entries.remove(id); owners.remove(id) }
        else throw MediaAcquisitionException(MediaFailure.TRANSFER_FAILED)
    }
}
