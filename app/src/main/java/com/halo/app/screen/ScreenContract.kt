// halo Screen — illustrative presentation contract (DevKit 2.0). Names are proposals, not a refactor mandate.
// Rule: cards render from data + a registered widget type. No generated UI, no executable markup, no secrets in layouts.
package com.halo.app.screen

import java.time.Instant

enum class ScreenSourceType { CLOCK, WEATHER, TASK, CALENDAR, AUTOMATION, RESEARCH, NOTEBOOK, HALO_ACTIVITY }
enum class ScreenContentType { CLOCK, FORECAST, LIST, EVENT, SUMMARY, ARTICLES, NOTE_REF, STATUS }
enum class ScreenItemState { READY, LOADING, REFRESHING, EMPTY, NEEDS_SETUP, PERMISSION_DENIED, OFFLINE, STALE, ERROR }
enum class ScreenSensitivity { PUBLIC, PERSONAL, PRIVATE }   // PERSONAL/PRIVATE are masked in full screen when "Hide private details" is on
enum class CardSize { COMPACT, STANDARD, EXPANDED }

/** App-owned presentation facts, distinct from the unchanged source title/summary. */
sealed interface ScreenAppSummary {
    data class RoutineActionsFinished(val completed: Int, val total: Int) : ScreenAppSummary {
        val valid: Boolean get() = total >= 0 && completed in 0..total
    }
}

sealed interface ScreenAction {            // action ids are stable strings for analytics + tests
    val id: String
    data object ReadAloud : ScreenAction { override val id = "screen.read_aloud" }
    data object OpenSource : ScreenAction { override val id = "screen.open_source" }      // routine, note, calendar event, article
    data object SaveToNotebook : ScreenAction { override val id = "screen.save_notebook" }
    data object Pin : ScreenAction { override val id = "screen.pin" }
    data object HideFromScreen : ScreenAction { override val id = "screen.hide" }         // never deletes or disables the source
    data object Setup : ScreenAction { override val id = "screen.setup" }
    data object Retry : ScreenAction { override val id = "screen.retry" }
    data object FewerLikeThis : ScreenAction { override val id = "screen.fewer_like_this" }
}

data class ScreenItem(
    val id: String,                    // stable across updates (e.g. "automation:<routineId>")
    val sourceType: ScreenSourceType,
    val sourceId: String?,             // authoritative record; Screen never owns the data
    val title: String,
    val summary: String?,
    val contentType: ScreenContentType,
    val updatedAt: Instant?,
    val lastSuccessAt: Instant?,       // shown when the latest run failed
    val expiresAt: Instant?,           // after this → STALE
    val state: ScreenItemState,
    val sensitivity: ScreenSensitivity,
    val attribution: String?,          // publisher / provider shown on the card
    val availableActions: List<ScreenAction>,
    val sourceLinks: List<ScreenSourceLink> = emptyList(),
    val sourceRevision: String? = null, // exact producing owner revision; absent on legacy publications
    val appSummary: ScreenAppSummary? = null,
)

data class ScreenSourceLink(val title: String, val url: String)

data class ScreenCard(val widgetType: String, val itemId: String?, val size: CardSize, val pinned: Boolean,
    val config: Map<String, String> = emptyMap(), val archived: Boolean = false,
    val placement: ScreenPlacement? = null, val typography: ScreenCardTypography = ScreenCardTypography(),
    val presentationId: String = "$widgetType:${itemId.orEmpty()}")
data class ScreenLayout(val id: String, val name: String, val cards: List<ScreenCard>, val version: Int,
    val appearance: ScreenAppearance = ScreenAppearance())

/** Publication is idempotent: same id → update in place, never append a duplicate card. */
interface ScreenItemStore {
    suspend fun publish(item: ScreenItem)          // upsert by id
    suspend fun markFailed(id: String, at: Instant) // keeps last good summary, state = STALE + lastSuccessAt
    fun observe(ids: Set<String>): kotlinx.coroutines.flow.Flow<List<ScreenItem>>
}

/** halo may PROPOSE a change; it is validated against the registry before preview/apply. */
sealed interface LayoutChange {
    data class Add(val widgetType: String, val config: Map<String, String>) : LayoutChange
    data class Remove(val widgetType: String) : LayoutChange
    data class Move(val widgetType: String, val toIndex: Int) : LayoutChange
    data class Resize(val widgetType: String, val size: CardSize) : LayoutChange
}
data class LayoutProposal(val changes: List<LayoutChange>, val summary: String, val reversible: Boolean, val unsupported: List<String>)
