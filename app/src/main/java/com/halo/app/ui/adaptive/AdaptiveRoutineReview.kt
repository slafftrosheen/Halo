package com.halo.app.ui.adaptive

import androidx.activity.compose.BackHandler
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.automation.RoutineSnapshotSection
import com.halo.app.data.workspace.AdaptiveRoutineExport
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.briefing.routineSnapshotLabel
import com.halo.app.language.uiText
import com.halo.app.ui.screens.settings.AutomationPage
import com.halo.app.ui.screens.settings.EverydayRoutineEditor
import com.halo.app.ui.screens.settings.RoutineDraft
import com.halo.app.ui.screens.settings.nullableRoutineDraftStateSaver
import com.halo.app.ui.screens.settings.rememberAutomationReadiness
import com.halo.runtime.cognition.routine.Routine

@Composable
internal fun AdaptiveRoutineReview(plan: AdaptiveRoutineExport, title: String, familyRevision: Long,
    open: (String) -> Unit, onBack: () -> Unit) {
    val model = Graph.routinesViewModel
    val state by model.uiState.collectAsStateWithLifecycle()
    val trial by model.trial.collectAsStateWithLifecycle()
    val pause by Graph.haloPause.state.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val heading = stringResource(R.string.adaptive_routine_review)
    val defaultName = stringResource(R.string.adaptive_routine_name)
    var draft by rememberSaveable(familyRevision, stateSaver = nullableRoutineDraftStateSaver) {
        mutableStateOf<RoutineDraft?>(null)
    }
    var savedId by rememberSaveable { mutableStateOf<String?>(null) }
    var outcome by rememberSaveable { mutableStateOf<String?>(null) }

    fun verify() {
        Graph.kidsPreferences.requireAdultAction(familyRevision)
        check(!Graph.haloPause.isPaused())
    }
    if (pause.paused || runCatching(::verify).isFailure) {
        BackHandler(onBack = onBack)
        AutomationPage(heading, onBack, titleIsSource = true) {
            Text(stringResource(R.string.adaptive_routine_unavailable))
        }
        return
    }

    val id = savedId
    if (id != null) {
        BackHandler(onBack = onBack)
        val message = when (outcome) {
            "OFF" -> R.string.adaptive_routine_saved_off
            "SCHEDULED" -> R.string.adaptive_routine_scheduled
            "EVENT_DRIVEN" -> R.string.adaptive_routine_event_driven
            else -> R.string.adaptive_routine_saved_check
        }
        AutomationPage(heading, onBack, titleIsSource = true) {
            Text(stringResource(message))
            OutlinedButton(onClick = { open("automation?routineId=" + android.net.Uri.encode(id)) }) {
                Text(stringResource(R.string.adaptive_routine_open_saved))
            }
        }
        return
    }

    val current = draft
    if (current != null) {
        val readiness = rememberAutomationReadiness(listOf(current.routine), emptyList())
        fun save(approved: Routine) {
            model.saveReviewed(approved, verify = ::verify) { receipt ->
                savedId = receipt.routineId.value
                outcome = if (approved.enabled) receipt.scheduling else "OFF"
                draft = null
            }
        }
        EverydayRoutineEditor(current, state.error, model.actionDescriptors, readiness, onDismiss = { draft = null },
            review = { require(runCatching(::verify).isSuccess); model.review(it) }, onSave = ::save,
            onNotifications = { open("notification_settings") }, onInbox = { open("notifications") },
            saving = state.saving, trial = trial,
            onTryOnce = { candidate -> if (runCatching(::verify).isSuccess) model.tryOnce(candidate) },
            onStopTrial = model::stopTrial)
        return
    }

    BackHandler(onBack = onBack)
    AutomationPage(heading, onBack, titleIsSource = true) {
        if (plan.sections.isEmpty()) {
            Text(stringResource(R.string.adaptive_routine_no_sections))
        } else {
            Text(stringResource(R.string.adaptive_routine_explain))
            plan.sections.forEach { section -> Text(routineSnapshotLabel(resources, section)) }
            if (plan.unsupported.isNotEmpty()) {
                Text(stringResource(R.string.adaptive_widget_omitted, plan.unsupported.joinToString(", ") { it.localizedLabel(resources) }))
            }
            if (RoutineSnapshotSection.AGENDA in plan.sections) {
                Text(stringResource(R.string.adaptive_routine_agenda_scope))
            }
            OutlinedButton(onClick = {
                if (runCatching(::verify).isSuccess) draft = RoutineDraft(plan.draft(title.ifBlank { defaultName }))
            }) { Text(uiText("Continue")) }
        }
    }
}
