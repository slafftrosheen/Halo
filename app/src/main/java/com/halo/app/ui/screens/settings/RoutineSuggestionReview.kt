package com.halo.app.ui.screens.settings

import com.halo.app.ui.presence.BindShellChildPage
import com.halo.app.ui.presence.ShellPageAction
import com.halo.app.ui.icons.HaloGlyph
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.data.memory.PersonalMemoryCodec
import com.halo.app.data.memory.PersonalSuggestionEntity
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloActionButton
import com.halo.app.ui.components.HaloActionStyle
import com.halo.runtime.cognition.memory.PersonalSuggestionKind
import com.halo.runtime.cognition.memory.PersonalSuggestionReason
import com.halo.runtime.cognition.routine.Routine

internal data class RoutineSuggestion(val id: String, val routineId: String, val completedCount: Int)

internal fun routineSuggestion(row: PersonalSuggestionEntity): RoutineSuggestion? {
    if (row.state != "PENDING" || row.kind != "AUTOMATION") return null
    val value = runCatching { PersonalMemoryCodec.decodeSuggestion(row.json) }.getOrNull() ?: return null
    if (value.kind != PersonalSuggestionKind.AUTOMATION || value.reason != PersonalSuggestionReason.REPEATED_MANUAL_ROUTINE) return null
    return value.targetId?.takeIf { it.isNotBlank() }?.let { RoutineSuggestion(row.id, it, value.evidenceCount) }
}

@Composable
internal fun RoutineSuggestionReview(suggestion: RoutineSuggestion?, routine: Routine?, readiness: AutomationReadiness,
    onBack: () -> Unit, onTurnOn: () -> Unit, onEdit: () -> Unit, onDismiss: () -> Unit) {
    AutomationPage("Suggested routine", onBack, objectId = routine?.id?.value, sourceIdentity = "${suggestion?.id}:${routine?.id?.value}",
        actions = listOf(ShellPageAction("routine.suggestion.edit", "Edit", HaloGlyph.NOTE,
            suggestion != null && routine != null, onEdit))) {
        if (suggestion == null) {
            Text(uiText("Suggestion no longer available"))
        } else {
            Text(routine?.name ?: uiText("Removed automation"), style = MaterialTheme.typography.titleLarge)
            DetailField("Why it was suggested", uiText("You completed this routine manually %1\$d times recently.", suggestion.completedCount))
            Text(uiText("Nothing changes until you review and save. Turning it on schedules future runs; it does not run it now."))
            if (routine != null) {
                RoutineReviewFields(routine, readiness.labels)
            } else Text(uiText("This routine is no longer available. You can dismiss this suggestion."))
            RoutineSuggestionActions(routine != null, onTurnOn, onEdit, onDismiss)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RoutineSuggestionActions(available: Boolean, onTurnOn: () -> Unit, onEdit: () -> Unit,
    onDismiss: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HaloActionButton(onClick = onDismiss, style = HaloActionStyle.UTILITY) { Text(uiText("No thanks")) }
        if (available) {
            HaloActionButton(onClick = onEdit, style = HaloActionStyle.SECONDARY) { Text(uiText("Edit")) }
            HaloActionButton(onClick = onTurnOn) { Text(uiText("Turn on")) }
        }
    }
}
