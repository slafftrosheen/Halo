package com.halo.app.context

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CurrentScreenSuite { HOME, WORKSPACE, OFFICE, STUDIO, STUDY, MEMORY, DEVICES, SETTINGS, ACTIVITY, OTHER }

/** Only machine identifiers. No title, document text, file bytes, arguments or execution callback. */
data class CurrentScreenMetadata(
    val routeTemplate: String,
    val parentSuite: CurrentScreenSuite,
    val activeTool: String,
    val objectId: String? = null,
    /** A canonical owner-supplied revision, never a UI hash, title or inferred timestamp. */
    val sourceVersion: String? = null,
    val selectionIds: List<String> = emptyList(),
    val uiActionIds: List<String> = emptyList(),
    /** Only explicitly mapped, actually registered canonical tools; not carousel callback names. */
    val canonicalToolIds: List<String> = emptyList(),
    val selectionCount: Int = selectionIds.size,
    val selectionTruncated: Boolean = selectionCount > selectionIds.size,
)

data class CurrentScreenSnapshot(val snapshot: String, val metadata: CurrentScreenMetadata, val schemaVersion: Int = 1)

/** App-side ephemeral projection, not an execution engine, context grant, or source reader. */
class CurrentScreenContext {
    class Lease internal constructor(internal val token: String, internal val entryId: String)
    private val session = UUID.randomUUID().toString()
    private var generation = 0L
    private var active: Lease? = null
    private var completeSelection: List<String> = emptyList()
    private val mutable = MutableStateFlow<CurrentScreenSnapshot?>(null)
    val state: StateFlow<CurrentScreenSnapshot?> = mutable.asStateFlow()

    @Synchronized fun attach(navigationEntryId: String): Lease {
        require(navigationEntryId.isNotBlank() && navigationEntryId.length <= 240)
        val lease = Lease(UUID.randomUUID().toString(), navigationEntryId)
        active = lease
        completeSelection = emptyList()
        mutable.value = null
        return lease
    }

    /** An outgoing page cannot overwrite the new page even if its disposal runs later. */
    @Synchronized fun publish(lease: Lease, metadata: CurrentScreenMetadata, fullSelectionIds: List<String> = metadata.selectionIds): Boolean {
        if (active !== lease) return false
        validate(metadata)
        require(fullSelectionIds.size == metadata.selectionCount && fullSelectionIds.distinct().size == fullSelectionIds.size)
        require(fullSelectionIds.take(100) == metadata.selectionIds)
        require(fullSelectionIds.all { it.length in 1..240 && ID.matches(it) })
        val copy = metadata.copy(selectionIds = metadata.selectionIds.toList(),
            uiActionIds = metadata.uiActionIds.toList(), canonicalToolIds = metadata.canonicalToolIds.toList())
        if (mutable.value?.metadata != copy || completeSelection != fullSelectionIds) {
            completeSelection = fullSelectionIds.toList()
            generation++
            mutable.value = CurrentScreenSnapshot("$session:$generation", copy)
        }
        return true
    }

    @Synchronized fun detach(lease: Lease) {
        if (active === lease) { active = null; mutable.value = null; completeSelection = emptyList() }
    }

    /** Null means detached or stale. Re-read metadata; never replay an action from an old snapshot. */
    @Synchronized fun read(expectedSnapshot: String? = null): CurrentScreenSnapshot? {
        val value = mutable.value ?: return null
        if (active == null || expectedSnapshot != null && expectedSnapshot != value.snapshot) return null
        return value.copy(metadata = value.metadata.copy(selectionIds = value.metadata.selectionIds.toList(),
            uiActionIds = value.metadata.uiActionIds.toList(), canonicalToolIds = value.metadata.canonicalToolIds.toList()))
    }

    private fun validate(value: CurrentScreenMetadata) {
        fun identifier(text: String, max: Int = 240) = text.length in 1..max && ID.matches(text)
        require(value.routeTemplate.length in 1..300 && ROUTE.matches(value.routeTemplate))
        require(identifier(value.activeTool, 100))
        require(value.objectId == null || identifier(value.objectId))
        require(value.sourceVersion == null || value.objectId != null && identifier(value.sourceVersion, 2000))
        require(value.selectionIds.size <= 100 && value.selectionIds.distinct().size == value.selectionIds.size && value.selectionIds.all { identifier(it) })
        require(value.selectionCount >= value.selectionIds.size && value.selectionTruncated == (value.selectionCount > value.selectionIds.size))
        require(value.selectionIds.size == minOf(value.selectionCount, 100))
        require(value.uiActionIds.size <= 64 && value.uiActionIds.distinct().size == value.uiActionIds.size && value.uiActionIds.all { identifier(it, 160) })
        require(value.canonicalToolIds.size <= 32 && value.canonicalToolIds.distinct().size == value.canonicalToolIds.size &&
            value.canonicalToolIds.all { it.length <= 100 && TOOL.matches(it) })
    }

    private companion object {
        val ID = Regex("[A-Za-z0-9_.:/+-]+")
        val ROUTE = Regex("[A-Za-z0-9_./?{}=&%:+-]+")
        val TOOL = Regex("halo_[a-z0-9_]+")
    }
}
