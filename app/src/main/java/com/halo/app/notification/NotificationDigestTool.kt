package com.halo.app.notification

import com.halo.app.data.notification.*
import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.attention.*
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import java.time.Instant

enum class NotificationSummaryMode { DETERMINISTIC }
data class NotificationDigestSection(val label: String, val count: Int, val sources: List<String>)
data class NotificationDigest(val id: String, val from: Instant, val to: Instant, val sourceCount: Int,
    val notificationCount: Int, val sections: List<NotificationDigestSection>, val generatedAt: Instant,
    val summaryMode: NotificationSummaryMode = NotificationSummaryMode.DETERMINISTIC) {
    fun text() = "$notificationCount notifications from $sourceCount apps · Local · no cloud sharing\n" +
        sections.joinToString("\n") { "${it.label} · ${it.count}\n${it.sources.joinToString("\n")}" }
}
data class NotificationDigestInput(val id: String, val from: Instant, val to: Instant, val notifications: List<HaloNotification>)
interface NotificationDigestSummarizer { suspend fun summarize(input: NotificationDigestInput): NotificationDigest }
/** Counts and app labels only: notification prose never becomes an instruction or tool argument. */
class DeterministicNotificationDigestSummarizer : NotificationDigestSummarizer {
    override suspend fun summarize(input: NotificationDigestInput): NotificationDigest {
        val items = input.notifications.take(500)
        val sections = items.groupBy { when (it.category) { "msg" -> "Messages"; "event" -> "Calendar"; else -> "Other" } }
            .toSortedMap().map { (category, rows) -> NotificationDigestSection(category, rows.size,
                rows.groupBy { it.packageName }.toSortedMap().map { (_, app) ->
                    "${app.first().appLabel ?: app.first().packageName} — ${app.size} notifications"
                }) }
        return NotificationDigest(input.id, input.from, input.to, items.map { it.packageName }.distinct().size,
            items.size, sections, Instant.now())
    }
}
class NotificationDigestTool(private val repository: NotificationRepository,
    private val attention: AttentionController, private val accessGranted: () -> Boolean,
    private val delivery: () -> DeliveryChannel? = { null }) : ToolExecutor {
    override fun definitions() = listOf(ToolDefinition(ID, ID, "Build a private, offline notification digest",
        mapOf("type" to "object", "properties" to mapOf("window_minutes" to mapOf("type" to "integer", "minimum" to 15, "maximum" to 1440)))))
    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        if (!accessGranted()) return ToolExecutionResult.Rejected(toolCall.id, ID, "Notification listener access required; open Notifications & Digests settings")
        val minutes = (toolCall.arguments["window_minutes"] as? Number)?.toLong()?.coerceIn(15, 1440) ?: 60L
        val now = Instant.now()
        val from = now.minusSeconds(minutes * 60)
        val digest = DeterministicNotificationDigestSummarizer().summarize(NotificationDigestInput(toolCall.id,
            from, now, repository.recentForDigest(from, 500)))
        val interaction = toolCall.id.substringBefore(":action:")
        val receipt = attention.submit(AttentionRequest(AttentionRequestId("digest:${toolCall.id}"),
            AttentionSource.NotificationDigest, InteractionId(interaction), importance = AttentionImportance.BACKGROUND,
            deliveryIntent = DeliveryIntent.Inform(UserFacingContent("Notification digest", digest.text())),
            preferredDigestChannel = delivery(), deduplicationKey = "digest:${toolCall.id}", expiresAt = now.plusSeconds(3600)))
        val text = digest.text() + "\nDelivery: ${receipt.outcome} ${receipt.channel ?: "Activity"} ${receipt.reason.orEmpty()}\nReceipt: ${receipt.requestId.value}"
        return if (receipt.outcome == DeliveryOutcome.FAILED) ToolExecutionResult.Failed(toolCall.id, ID, text)
            else ToolExecutionResult.Success(toolCall.id, ID, text)
    }
    companion object { const val ID = "halo_notification_digest" }
}
