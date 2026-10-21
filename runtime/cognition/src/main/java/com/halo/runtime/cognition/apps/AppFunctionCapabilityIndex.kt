package com.halo.runtime.cognition.apps

import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppFunctionAvailability { UNSUPPORTED, AVAILABLE, PERMISSION_DENIED, ERROR }
data class AppCapabilitySnapshot(val generationId: String = UUID.randomUUID().toString(), val createdAt: Instant = Instant.now(),
    val availability: AppFunctionAvailability = AppFunctionAvailability.UNSUPPORTED, val currentPackage: String? = null,
    val activityIdentity: String? = null, val capabilities: List<SemanticAppCapability> = emptyList(),
    val accessibilityInspectable: Boolean = false)

/** Process-local, bounded, immutable generations. Metadata is data, never authorization. */
class AppFunctionCapabilityIndex(private val accessAllowed: (String, String) -> Boolean = { _, _ -> true }) {
    private val mutable = MutableStateFlow(AppCapabilitySnapshot())
    val state = mutable.asStateFlow()
    @Synchronized fun publish(availability: AppFunctionAvailability, currentPackage: String?, activityIdentity: String?,
        capabilities: List<SemanticAppCapability>, accessibilityInspectable: Boolean = false): AppCapabilitySnapshot {
        val safe = capabilities.asSequence().take(1024).filter { it.packageName.matches(PACKAGE) && it.id.length in 1..512 &&
            it.parameters.size <= 24 && it.parameters.all { p -> p.name.matches(NAME) } }
            .groupBy { it.packageName }.entries.take(32).flatMap { (_, values) -> values.distinctBy { it.route to it.id }.take(64) }
            .map { it.copy(actionName = clean(it.actionName, 120), description = it.description?.let { d -> clean(d, 500) },
                trust = "UNTRUSTED_CAPABILITY_METADATA") }
        val next = AppCapabilitySnapshot(availability = availability, currentPackage = currentPackage,
            activityIdentity = activityIdentity, capabilities = safe, accessibilityInspectable = accessibilityInspectable)
        val old = mutable.value
        if (old.availability == next.availability && old.currentPackage == next.currentPackage && old.activityIdentity == next.activityIdentity &&
            old.capabilities == next.capabilities && old.accessibilityInspectable == next.accessibilityInspectable) return old
        mutable.value = next
        return next
    }
    @Synchronized fun invalidate() { mutable.value = mutable.value.copy(generationId = UUID.randomUUID().toString(),
        createdAt = Instant.now(), capabilities = emptyList()) }
    fun currentCandidates(): List<SemanticAppCapability> = state.value.let { s -> s.capabilities.filter {
        it.packageName == s.currentPackage && it.enabled && isAllowed(it) && (it.scope == CapabilityScope.GLOBAL ||
            it.activityIdentity != null && it.activityIdentity == s.activityIdentity) }.take(16) }
    fun isAllowed(capability: SemanticAppCapability): Boolean = accessAllowed(capability.packageName, capability.action.value)
    @Synchronized fun accessChanged() {
        mutable.value = mutable.value.copy(generationId = UUID.randomUUID().toString(), createdAt = Instant.now())
    }
    companion object {
        private val PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+")
        private val NAME = Regex("[A-Za-z_][A-Za-z0-9_]{0,63}")
        fun clean(text: String, limit: Int): String = text.filter { !it.isISOControl() || it == ' ' }.take(limit)
    }
}

/** Only Halo-owned, exact semantic aliases enable provider-independent resolution. */
object SemanticActionMapping {
    fun fromOfferedIdentifier(identifier: String): SemanticActionId = fromIdentifier(identifier)
        ?: SemanticActionId("external_direct_" + java.security.MessageDigest.getInstance("SHA-256")
            .digest(identifier.toByteArray()).take(8).joinToString("") { "%02x".format(it) })
    fun fromIdentifier(identifier: String): SemanticActionId? = when (identifier.substringAfterLast('/').substringAfterLast('#').substringAfterLast('.')
        .replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()) {
        "archive", "archive_current_thread", "archive_thread", "archive_current_message" -> SemanticActionId("archive_current")
        "mark_unread", "mark_current_unread" -> SemanticActionId("mark_unread")
        "pause", "pause_media" -> SemanticActionId("media_pause")
        "play", "resume", "play_media" -> SemanticActionId("media_play")
        "search" -> SemanticActionId("search")
        else -> null
    }
}
