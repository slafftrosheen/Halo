package com.halo.app.notification

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle

/**
 * MINING-P1-001 notification RemoteInput reply.
 *
 * `PendingIntent` / `Notification.Action` are live platform objects and are therefore NEVER written
 * to Room. This registry is a bounded, in-memory map from a notification key to the live reply action
 * captured by the listener service. Execution requires the notification to still be live, so a stale
 * database row can never become a reply.
 *
 * Harvested conceptually from Brownie's notification RemoteInput reply; Halo keeps its own
 * untrusted-content rules.
 */
data class LiveReplyAction(
    val notificationKey: String,
    val actionIndex: Int,
    /** Label shown to the user, e.g. "Reply". */
    val label: String,
    /** Stable identity of the action, safe to persist or log. */
    val actionFingerprint: String,
    val capturedAtMs: Long,
    /** Live platform object. Never persisted, never logged. */
    internal val action: Notification.Action,
)

class NotificationReplyRegistry(
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val entries = LinkedHashMap<String, LiveReplyAction>()

    /** Called when a notification is posted or reconciled while the listener is connected. */
    @Synchronized
    fun replace(notificationKey: String, actions: List<Notification.Action>) {
        evictExpired()
        val reply = actions.withIndex().firstOrNull { (_, action) -> hasRemoteInput(action) }
        if (reply == null) {
            entries.remove(notificationKey)
            return
        }
        while (entries.size >= maxEntries) {
            val oldest = entries.keys.firstOrNull() ?: break
            entries.remove(oldest)
        }
        val (index, action) = reply
        entries[notificationKey] = LiveReplyAction(
            notificationKey = notificationKey,
            actionIndex = index,
            label = action.title?.toString()?.take(40).orEmpty().ifBlank { "Reply" },
            actionFingerprint = fingerprint(action),
            capturedAtMs = clock(),
            action = action,
        )
    }

    @Synchronized
    fun remove(notificationKey: String) {
        entries.remove(notificationKey)
    }

    @Synchronized
    fun clear() = entries.clear()

    @Synchronized
    fun replyActionFor(notificationKey: String): LiveReplyAction? {
        evictExpired()
        val entry = entries[notificationKey] ?: return null
        return entry.takeIf { it.capturedAtMs + ttlMs > clock() }
    }

    @Synchronized
    fun supportsReply(notificationKey: String): Boolean = replyActionFor(notificationKey) != null

    @Synchronized
    fun size(): Int {
        evictExpired()
        return entries.size
    }

    private fun evictExpired() {
        val now = clock()
        entries.entries.removeAll { it.value.capturedAtMs + ttlMs <= now }
    }

    private fun hasRemoteInput(action: Notification.Action): Boolean =
        runCatching { !action.remoteInputs.isNullOrEmpty() }.getOrDefault(false)

    /** Content-free identity: label + whether a RemoteInput exists + action index space. */
    private fun fingerprint(action: Notification.Action): String {
        val label = action.title?.toString().orEmpty()
        return "reply:$label:${if (hasRemoteInput(action)) "ri" else "none"}"
    }

    companion object {
        /**
         * Process-wide instance shared by the framework-boundary listener service and the
         * application graph. The platform constructs the service, so a singleton accessor is the
         * only way both can see the same live actions.
         */
        val shared: NotificationReplyRegistry by lazy { NotificationReplyRegistry() }

        const val DEFAULT_TTL_MS = 10 * 60_000L
        const val DEFAULT_MAX_ENTRIES = 32
    }
}

/** Result of attempting to hand a reply to another app. Deliberately not "SENT"/"DELIVERED". */
enum class NotificationReplyOutcome {
    /** `PendingIntent.send()` succeeded. The other app now owns the text. */
    DISPATCHED,
    NOT_LIVE,
    NO_REPLY_ACTION,
    EMPTY_REPLY,
    FAILED,
}

class NotificationReplyDispatcher(
    private val registry: NotificationReplyRegistry = NotificationReplyRegistry.shared,
    private val maxReplyChars: Int = MAX_REPLY_CHARS,
) {

    fun dispatch(context: Context, notificationKey: String, replyText: String): NotificationReplyOutcome {
        val text = replyText.trim()
        if (text.isEmpty()) return NotificationReplyOutcome.EMPTY_REPLY
        if (text.length > maxReplyChars) return NotificationReplyOutcome.EMPTY_REPLY
        val live = registry.replyActionFor(notificationKey) ?: return NotificationReplyOutcome.NOT_LIVE
        val remoteInputs = runCatching { live.action.remoteInputs }.getOrNull()
        if (remoteInputs.isNullOrEmpty()) return NotificationReplyOutcome.NO_REPLY_ACTION

        val results = Bundle()
        // Every declared free-form input of the action receives the same text; Halo never guesses
        // which one the app expects.
        remoteInputs.forEach { input -> results.putCharSequence(input.resultKey, text) }

        val fillInIntent = Intent()
        RemoteInput.addResultsToIntent(remoteInputs, fillInIntent, results)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Q+ requires the results source to be declared or the framework rejects the intent.
            RemoteInput.setResultsSource(fillInIntent, RemoteInput.SOURCE_FREE_FORM_INPUT)
        }

        return try {
            live.action.actionIntent.send(context, 0, fillInIntent)
            NotificationReplyOutcome.DISPATCHED
        } catch (_: android.app.PendingIntent.CanceledException) {
            // The action is no longer valid; drop the claim so the UI cannot imply success.
            registry.remove(notificationKey)
            NotificationReplyOutcome.FAILED
        } catch (_: SecurityException) {
            NotificationReplyOutcome.FAILED
        }
    }

    private companion object {
        const val MAX_REPLY_CHARS = 500
    }
}
