package com.halo.app.nativeactions

import android.content.Context
import android.provider.Settings
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.collectors.CollectorKind
import com.halo.app.everyday.presentation.EverydayCardKind
import com.halo.app.everyday.presentation.EverydaySnapshot
import com.halo.app.everyday.presentation.WidgetFocus
import com.halo.app.everyday.presentation.WidgetLearning
import com.halo.app.everyday.presentation.readEverydaySnapshot
import com.halo.app.ui.screens.study.LearningStage
import com.halo.app.ui.screens.study.learnPathStep
import com.halo.app.ui.toolkit.HaloFocusOwner
import com.halo.app.ui.toolkit.HaloLifeOwner
import com.halo.app.ui.toolkit.FamilyKind
import com.halo.runtime.cognition.routine.RoutineRunState
import kotlinx.coroutines.flow.MutableStateFlow
import com.halo.sdk.runtime.study.StudyLearnerLevel
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal fun everydayWidgetMainSourceKind(kind: EverydayCardKind): CollectorKind = when (kind) {
    EverydayCardKind.HEADLINES -> CollectorKind.FEED
    EverydayCardKind.AIR_POLLEN -> CollectorKind.AIR_QUALITY
    else -> CollectorKind.WEATHER
}

internal object EverydayWidgetRecording { val active = MutableStateFlow(false) }

/** Outside hosts get aggregate stage/attempt counts only. No lesson, goal, answer or profile identity. */
internal suspend fun readEverydayWidgetSnapshot(context: Context): EverydaySnapshot = withContext(Dispatchers.IO) {
    val snapshot = readEverydaySnapshot(context, includePastWeek = true)
    val sessions = Graph.studyRepository.observeActiveSessions().first()
    val latest = sessions.firstOrNull { session ->
        if (snapshot.kids) session.learnerLevel in setOf(StudyLearnerLevel.PRIMARY, StudyLearnerLevel.EARLY_PRIMARY)
        else session.learnerLevel == StudyLearnerLevel.ADULT
    }
    val learning = latest?.let { session ->
        val stage = Graph.studyLessonStore.loadJourney(session.id)?.stage ?: LearningStage.START
        WidgetLearning(if (stage == LearningStage.CONTINUE) 4 else learnPathStep(stage).ordinal,
            Instant.ofEpochMilli(session.updatedAtEpochMs))
    }
    val focus = if (snapshot.kids) null else {
        val session = HaloFocusOwner.get(context).state.value
        val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        session?.takeIf { it.boot == boot }?.let {
            val remaining = it.left()
            if (remaining > 0) WidgetFocus(it.durationMinutes * 60_000L, remaining, it.deadlineElapsed) else null
        }
    }
    val familyList = if (snapshot.kids) emptyList() else HaloLifeOwner.get(context).family.value.filter { it.kind == FamilyKind.LIST }
    val routineHistory = if (snapshot.kids) emptyList() else Graph.routineRepository.observeRecentRuns().first()
        .filter { it.state == RoutineRunState.COMPLETED && it.source == "MANUAL" }
    val routineIds = if (snapshot.kids || routineHistory.isEmpty()) emptySet() else Graph.routineRepository.observeRoutinesReadOnly()
        .first().filter { it.enabled }.mapTo(hashSetOf()) { it.id }
    val today = snapshot.now.atZone(snapshot.zone).toLocalDate()
    val attempts = if (!snapshot.kids) 0 else Graph.studyRepository.observeChildAttemptCount(
        today.atStartOfDay(snapshot.zone).toInstant().toEpochMilli(),
        today.plusDays(1).atStartOfDay(snapshot.zone).toInstant().toEpochMilli()).first().coerceAtMost(5)
    val histories = routineHistory.filter { it.routineId in routineIds }.groupBy { it.routineId }.values
        .sortedByDescending { runs -> runs.maxOf { it.triggerAt } }.take(2)
    val dots = histories.map { runs ->
        val runDays = runs.mapTo(hashSetOf()) { it.triggerAt.atZone(snapshot.zone).toLocalDate() }
        (6L downTo 0L).map { today.minusDays(it) in runDays }
    }
    val hour = snapshot.now.atZone(snapshot.zone).hour
    val habitualHour = histories.any { runs ->
        val observations = runs.map { it.triggerAt.atZone(snapshot.zone) }.filter { it.hour == hour }
        observations.map { it.toLocalDate() }.distinct().size >= 2
    }
    Graph.kidsPreferences.requirePresentation(snapshot.familyRevision)
    snapshot.copy(widgetLearning = learning, widgetFocus = focus, widgetHabits = dots, widgetChildAttempts = attempts,
        widgetHabitNow = habitualHour, widgetHomeCount = familyList.size, widgetHomeDone = familyList.count { it.done },
        widgetRecording = !snapshot.kids && EverydayWidgetRecording.active.value)
}
