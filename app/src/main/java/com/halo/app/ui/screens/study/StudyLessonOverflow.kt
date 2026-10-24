package com.halo.app.ui.screens.study

import androidx.compose.runtime.Composable
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloOverflowAction
import com.halo.app.ui.components.HaloScreenOverflow

@Composable
internal fun StudyLessonOverflow(enabled: Boolean, expanded: Boolean, onExpandedChange: (Boolean) -> Unit,
    canListen: Boolean, canSave: Boolean, completed: Boolean, onDetails: () -> Unit,
    onListen: () -> Unit, onSaveNotes: () -> Unit, onFinish: () -> Unit, onDelete: () -> Unit) {
    HaloScreenOverflow(listOf(
        HaloOverflowAction(uiText("Sources and progress"), onClick = onDetails),
        HaloOverflowAction(uiText("Listen, then explain"), canListen, onListen),
        HaloOverflowAction(uiText("Save study notes to Workspace"), canSave, onSaveNotes),
        HaloOverflowAction(uiText(if (completed) "Already completed" else "Finish session"), !completed, onFinish),
        HaloOverflowAction(uiText("Delete this session's data"), onClick = onDelete),
    ), enabled, expanded, onExpandedChange)
}

@Composable
internal fun StudySetupOverflow(optionsExpanded: Boolean, enabled: Boolean, onToggleOptions: () -> Unit) {
    HaloScreenOverflow(listOf(HaloOverflowAction(
        uiText(if (optionsExpanded) "Hide learning options" else "Age, language & learning options"),
        onClick = onToggleOptions)), enabled)
}
