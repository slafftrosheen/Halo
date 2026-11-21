package com.halo.app.nativeactions

import android.animation.ValueAnimator
import android.content.Context
import android.os.PowerManager
import android.widget.RemoteViews
import com.halo.app.R
import com.halo.app.everyday.organizer.OrganizerStatus
import com.halo.app.everyday.presentation.EverydayCardConfig
import com.halo.app.everyday.presentation.EverydayCardFace
import com.halo.app.everyday.presentation.EverydayCardKind
import com.halo.app.everyday.presentation.EverydaySnapshot
import com.halo.app.everyday.presentation.EverydayTarget
import com.halo.app.ui.theme.HaloLookMotion


internal data class WidgetRenderedState(
    val version: Long,
    val familyRevision: Long,
    val modeRevision: Long,
    val locale: String,
    val theme: String,
    val title: String,
    val value: String,
    val records: Map<String, String>,
    val sourceRevisions: List<String>,
    val briefId: String?,
    val completedOccurrences: Set<Pair<String, java.time.Instant>>,
)

internal enum class WidgetUpdateMotion { TASK_COMPLETE, NEW_ITEM, SOURCE_UPDATE }
internal data class WidgetTransition(val motion: WidgetUpdateMotion, val previous: WidgetRenderedState, val affectedRow: String? = null)

/** Keeps in-memory, privacy-filtered visual reviews only. Each one is replaced after a successful launcher publish. */
internal object EverydayWidgetMotion {
    private val published = mutableMapOf<Int, WidgetRenderedState>()

    fun level(context: Context, enabled: Boolean = true, kids: Boolean = false): HaloLookMotion {
        if (!enabled || !ValueAnimator.areAnimatorsEnabled() ||
            context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true) return HaloLookMotion.STILL
        val prefs = context.getSharedPreferences("halo_visual_options", Context.MODE_PRIVATE)
        val configured = HaloLookMotion.entries.find { it.name == prefs.getString("motionLevel", null) }
            ?: if (prefs.getBoolean("motion", false)) HaloLookMotion.STILL else HaloLookMotion.GENTLE
        return if (kids && configured == HaloLookMotion.FULL) HaloLookMotion.GENTLE else configured
    }

    fun frame(instance: EverydayWidgetInstance, snapshot: EverydaySnapshot, face: EverydayCardFace, locale: String): WidgetRenderedState {
        val config = instance.config
        val sources = snapshot.sources.filter { entry ->
            entry.source.enabled && entry.source.selected && !entry.source.muted && (!snapshot.kids || entry.source.childApproved) &&
                when (config.kind) {
                    EverydayCardKind.CLOCK_WEATHER, EverydayCardKind.WEATHER, EverydayCardKind.LOCK_RAIN ->
                        entry.source.kind == com.halo.app.everyday.collectors.CollectorKind.WEATHER &&
                            (config.sourceId == null || config.sourceId == entry.source.id)
                    EverydayCardKind.AIR_POLLEN ->
                        entry.source.kind == com.halo.app.everyday.collectors.CollectorKind.AIR_QUALITY &&
                            (config.sourceId == null || config.sourceId == entry.source.id)
                    EverydayCardKind.HEADLINES ->
                        entry.source.kind == com.halo.app.everyday.collectors.CollectorKind.FEED &&
                            (config.sourceId == null || config.sourceId == entry.source.id)
                    else -> false
                }
        }
        return WidgetRenderedState(
            instance.version, snapshot.familyRevision, snapshot.modeRevision, locale, config.theme,
            face.title, face.value,
            face.rows.mapNotNull { row ->
                val record = row.target as? EverydayTarget.Record
                if (record != null) record.id to if (row.personal) face.title else row.title else null
            }.toMap(),
            sources.mapNotNull { entry -> entry.snapshot?.takeIf { it.sourceVersion == entry.source.version }
                ?.let { entry.source.id + ":" + it.contentRevision } }.sorted(),
            (face.target as? EverydayTarget.Brief)?.id,
            snapshot.occurrences.filter { it.completed }.mapTo(mutableSetOf()) {
                it.record.id to it.originalStart
            },
        )
    }

    @Synchronized fun transition(instance: EverydayWidgetInstance, snapshot: EverydaySnapshot, latest: WidgetRenderedState): WidgetTransition? {
        val prior = published[instance.id] ?: return null
        return compare(instance.config, prior, latest, snapshot.records
            .asSequence().filter { it.status == OrganizerStatus.COMPLETED }.map { it.id }.toSet())
    }

    internal fun compare(config: EverydayCardConfig, before: WidgetRenderedState, after: WidgetRenderedState,
        completed: Set<String>): WidgetTransition? {
        if (before.version != after.version || before.familyRevision != after.familyRevision ||
            before.modeRevision != after.modeRevision || before.locale != after.locale || before.theme != after.theme) return null
        val newCompletedOccurrences = after.completedOccurrences - before.completedOccurrences
        val finishedTask = before.records.entries.firstOrNull { (id, _) ->
            id in completed || newCompletedOccurrences.any { it.first == id }
        }
        return when {
            config.kind == EverydayCardKind.TASKS && finishedTask != null ->
                WidgetTransition(WidgetUpdateMotion.TASK_COMPLETE, before, finishedTask.value)
            config.kind in setOf(EverydayCardKind.UPCOMING, EverydayCardKind.DASHBOARD) && (after.records.keys - before.records.keys).isNotEmpty() ->
                WidgetTransition(WidgetUpdateMotion.NEW_ITEM, before)
            config.kind in setOf(EverydayCardKind.PLANNER_WEEK, EverydayCardKind.LEARN, EverydayCardKind.LITTLE_HABITS) &&
                after.value != before.value -> WidgetTransition(WidgetUpdateMotion.NEW_ITEM, before)
            config.kind == EverydayCardKind.BRIEF && after.briefId != null && after.briefId != before.briefId ->
                WidgetTransition(WidgetUpdateMotion.NEW_ITEM, before)
            after.sourceRevisions.isNotEmpty() && after.sourceRevisions != before.sourceRevisions ->
                WidgetTransition(WidgetUpdateMotion.SOURCE_UPDATE, before)
            else -> null
        }
    }

    @Synchronized fun remember(id: Int, rendered: WidgetRenderedState) { published[id] = rendered }
    @Synchronized fun forget(id: Int) { published.remove(id) }

    fun wrap(context: Context, current: RemoteViews, transition: WidgetTransition, level: HaloLookMotion,
        textColor: Int, accent: Int): RemoteViews {
        if (level == HaloLookMotion.STILL) return current
        val outer = when {
            level == HaloLookMotion.GENTLE -> R.layout.halo_widget_transition_gentle
            transition.motion == WidgetUpdateMotion.SOURCE_UPDATE -> R.layout.halo_widget_transition_fade
            transition.motion == WidgetUpdateMotion.TASK_COMPLETE -> R.layout.halo_widget_transition_task
            else -> R.layout.halo_widget_transition_slide
        }
        val previous = RemoteViews(context.packageName, R.layout.halo_widget_transition_previous).apply {
            setTextViewText(R.id.widget_previous_title, transition.previous.title)
            setTextColor(R.id.widget_previous_title, textColor)
            val item = if (transition.affectedRow != null) {
                android.text.SpannableString("✓  " + transition.affectedRow).apply {
                    setSpan(android.text.style.StrikethroughSpan(), 3, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            } else transition.previous.value
            setTextViewText(R.id.widget_previous_value, item)
            setTextColor(R.id.widget_previous_value, if (transition.affectedRow == null) textColor else accent)
        }
        return RemoteViews(context.packageName, outer).apply {
            removeAllViews(R.id.widget_transition_flipper)
            addView(R.id.widget_transition_flipper, previous)
            addView(R.id.widget_transition_flipper, current)
            setDisplayedChild(R.id.widget_transition_flipper, 1)
        }
    }
}
