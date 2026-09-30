package com.halo.app.data.attention

import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.attention.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.time.Instant

/** Bounded presentation DTO only. Provider context and notification bodies never enter this queue. */
class RoomAttentionQueue(private val db: AppDatabase, scope: CoroutineScope) : AttentionQueue, AttentionReceiptStore {
    private val dao = db.attentionDao()
    private val state = MutableStateFlow<List<QueuedAttentionRequest>>(emptyList())
    override val pending = state.asStateFlow()
    val receipts = dao.observeReceipts().map { rows -> rows.map { it.toReceipt() } }.stateIn(scope, SharingStarted.Eagerly, emptyList())
    init { scope.launch(Dispatchers.IO) { dao.observe().collect { rows ->
        state.value = rows.mapNotNull { row -> runCatching { decode(row.json) }.getOrNull().also { if(it == null) dao.remove(row.id) } }
    } } }
    override suspend fun enqueue(request: QueuedAttentionRequest) { db.withTransaction {
        if(terminal(request.request.id) != null) return@withTransaction
        dao.removeDuplicates(request.request.id.value, request.request.deduplicationKey)
        check(dao.count() < 50) { "Attention queue full" }
        dao.put(AttentionPendingEntity(request.request.id.value, request.request.deduplicationKey, encode(request), request.queuedAt.toEpochMilli()))
    } }
    override suspend fun remove(requestId: AttentionRequestId) = dao.remove(requestId.value)
    override suspend fun releaseEligible(context: AttentionContext) = Unit
    override suspend fun terminal(id: AttentionRequestId): DeliveryReceipt? = dao.receipt(id.value)?.takeIf { it.outcome != DeliveryOutcome.QUEUED.name }?.toReceipt()
    override suspend fun claim(request: AttentionRequest, channel: DeliveryChannel?) = db.withTransaction {
        val id = request.id
        val disruptive = channel == DeliveryChannel.AndroidNotification || channel is DeliveryChannel.WearableSpeech || channel == DeliveryChannel.PhoneSpeech
        val existing = dao.receipt(id.value)
        if(existing?.outcome == DeliveryOutcome.QUEUED.name) {
            dao.receiptPut(existing.copy(outcome = DeliveryOutcome.COMPLETION_UNKNOWN.name, at = System.currentTimeMillis(), channel = encodeChannel(channel), disruptive = disruptive, dedupKey = request.deduplicationKey))
            true
        } else dao.claim(AttentionReceiptEntity(id.value, DeliveryOutcome.COMPLETION_UNKNOWN.name, encodeChannel(channel), "CLAIMED", System.currentTimeMillis(), request.deduplicationKey, disruptive)) != -1L
    }
    override suspend fun record(request: AttentionRequest, receipt: DeliveryReceipt) { db.withTransaction {
        dao.receiptPut(AttentionReceiptEntity(request.id.value, receipt.outcome.name, encodeChannel(receipt.channel),
            (if(receipt.outcome == DeliveryOutcome.QUEUED || receipt.outcome == DeliveryOutcome.SUPPRESSED || receipt.outcome == DeliveryOutcome.SURFACED_SILENTLY) receipt.reason else receipt.outcome.name)?.take(120), receipt.deliveredAt?.toEpochMilli() ?: System.currentTimeMillis(), request.deduplicationKey,
            receipt.channel == DeliveryChannel.AndroidNotification || receipt.channel is DeliveryChannel.WearableSpeech || receipt.channel == DeliveryChannel.PhoneSpeech))
        if(receipt.outcome != DeliveryOutcome.QUEUED) dao.remove(request.id.value)
        dao.prune(System.currentTimeMillis() - 30L * 86400000)
    } }
    override suspend fun history() = dao.history().map { it.toReceipt() }
    suspend fun dispatches() = dao.dispatches(System.currentTimeMillis() - 3600000).map { Instant.ofEpochMilli(it.at) to it.dedupKey }
    private fun AttentionReceiptEntity.toReceipt() = DeliveryReceipt(AttentionRequestId(id),
        runCatching { DeliveryOutcome.valueOf(outcome) }.getOrDefault(DeliveryOutcome.COMPLETION_UNKNOWN), decodeChannel(channel), Instant.ofEpochMilli(at), reason)
    private fun encodeChannel(c: DeliveryChannel?): String? = when(c) {
        null -> null
        DeliveryChannel.InApp -> "in_app"
        DeliveryChannel.AndroidNotification -> "notification"
        DeliveryChannel.PhoneSpeech -> "phone"
        is DeliveryChannel.WearableSpeech -> "speech:${c.deviceId}"
        is DeliveryChannel.WearableVisual -> "visual:${c.deviceId}"
    }
    private fun decodeChannel(c: String?): DeliveryChannel? = when {
        c == null -> null
        c == "in_app" -> DeliveryChannel.InApp
        c == "notification" -> DeliveryChannel.AndroidNotification
        c == "phone" -> DeliveryChannel.PhoneSpeech
        c.startsWith("speech:") -> DeliveryChannel.WearableSpeech(c.removePrefix("speech:"))
        c.startsWith("visual:") -> DeliveryChannel.WearableVisual(c.removePrefix("visual:"))
        else -> error("Unknown attention channel")
    }
    private fun encode(q: QueuedAttentionRequest): String {
        val r = q.request
        require(r.id.value.length <= 512 && (r.deduplicationKey?.length ?: 0) <= 512 && (r.interactionId?.value?.length ?: 0) <= 512) { "Attention identity too large" }
        val intent = JSONObject()
        when(val i = r.deliveryIntent) {
            is DeliveryIntent.Inform -> intent.put("type", "inform").put("title", i.content.title.take(120)).put("body", i.content.body.take(2400))
            is DeliveryIntent.RequestApproval -> intent.put("type", "approval").put("id", i.approvalId).put("body", i.summary.take(2400))
            is DeliveryIntent.RequestClarification -> intent.put("type", "clarification").put("id", i.clarificationId).put("body", i.question.take(2400))
            is DeliveryIntent.ActionRequired -> intent.put("type", "required").put("body", i.summary.take(2400))
        }
        return JSONObject().put("id", r.id.value).put("source", r.source.javaClass.simpleName).put("interaction", r.interactionId?.value)
            .put("importance", r.importance.name).put("urgency", r.urgency.name).put("intent", intent)
            .put("expires", q.expiresAt?.toEpochMilli()).put("dedup", r.deduplicationKey).put("created", r.createdAt.toEpochMilli())
            .put("queued", q.queuedAt.toEpochMilli()).put("release", q.releaseCondition.name).put("reason", q.decisionReason.name)
            .put("digestChannel", encodeChannel(r.preferredDigestChannel)).put("routineChannel", encodeChannel(r.preferredRoutineChannel)).toString()
    }
    private fun decode(raw: String): QueuedAttentionRequest {
        require(raw.length <= 12000)
        val j = JSONObject(raw); val i = j.getJSONObject("intent")
        val source = when(j.getString("source")) {
            "DirectUserRequest" -> AttentionSource.DirectUserRequest
            "AgentCompletion" -> AttentionSource.AgentCompletion
            "Routine" -> AttentionSource.Routine
            "NotificationDigest" -> AttentionSource.NotificationDigest
            "Reminder" -> AttentionSource.Reminder
            "DeviceEvent" -> AttentionSource.DeviceEvent
            "SystemHealth" -> AttentionSource.SystemHealth
            else -> error("Unknown source")
        }
        val intent = when(i.getString("type")) {
            "inform" -> DeliveryIntent.Inform(UserFacingContent(i.getString("title"), i.getString("body")))
            "approval" -> DeliveryIntent.RequestApproval(i.getString("id"), i.getString("body"))
            "clarification" -> DeliveryIntent.RequestClarification(i.getString("id"), i.getString("body"))
            "required" -> DeliveryIntent.ActionRequired(i.getString("body"))
            else -> error("Unknown intent")
        }
        fun optional(k: String) = if(j.isNull(k)) null else j.getString(k)
        val expires = if(j.isNull("expires")) Instant.ofEpochMilli(j.getLong("created")).plusSeconds(86400) else Instant.ofEpochMilli(j.getLong("expires"))
        val request = AttentionRequest(AttentionRequestId(j.getString("id")), source, optional("interaction")?.let(::InteractionId),
            AttentionImportance.valueOf(j.getString("importance")), AttentionUrgency.valueOf(j.getString("urgency")), intent,
            expiresAt = expires, deduplicationKey = optional("dedup"), createdAt = Instant.ofEpochMilli(j.getLong("created")),
            preferredDigestChannel = decodeChannel(optional("digestChannel")), preferredRoutineChannel = decodeChannel(optional("routineChannel")))
        return QueuedAttentionRequest(request, AttentionDecisionReason.valueOf(j.getString("reason")),
            Instant.ofEpochMilli(j.getLong("queued")), expires, AttentionReleaseCondition.valueOf(j.getString("release")))
    }
}
