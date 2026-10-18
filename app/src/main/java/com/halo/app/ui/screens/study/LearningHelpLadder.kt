package com.halo.app.ui.screens.study

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.fx.haloTrace
import com.halo.sdk.runtime.study.TutorStage

/** Help is always available; neither points nor elapsed time gate any rung. */
@Composable
internal fun LearningHelpLadder(state: StudyUiState, model: StudyViewModel, kids: Boolean = false, guideHint: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val enabled = !state.loading && !state.isGenerating && !state.isSubmitting && !state.isFinished
    val height = if (kids) 56.dp else 48.dp
    TextButton(onClick = { model.requestGuidance(TutorStage.HINT) }, enabled = enabled,
        modifier = Modifier.heightIn(min = height).haloTrace(guideHint, MaterialTheme.colorScheme.primary, lapOnAppear = true)) { Text(uiText("Hint")) }
    TextButton(onClick = { open = true }, enabled = enabled,
        modifier = Modifier.heightIn(min = height)) { Text(uiText("Help me think")) }
    if (open) AlertDialog(onDismissRequest = { open = false },
        title = { Text(uiText("Choose how much help")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Remember" to TutorStage.ASK, "Hint" to TutorStage.HINT,
                "Worked step" to TutorStage.PARTIAL_SOLUTION, "Another way" to TutorStage.ANALOGY).forEach { (label, stage) ->
                OutlinedButton(onClick = { open = false; model.requestGuidance(stage) }, enabled = enabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = height)) { Text(uiText(label)) }
            }
            TextButton(onClick = { open = false; confirm = true }, enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = height)) { Text(uiText("Full solution")) }
        } }, confirmButton = { TextButton(onClick = { open = false }) { Text(uiText("Not now")) } })
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text(uiText("Show every step?")) },
        text = { Text(uiText("You can still try first. Help is always available.")) },
        confirmButton = { TextButton(onClick = { confirm = false; if (enabled) model.askForSolution() },
            enabled = enabled) { Text(uiText("Full solution")) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(uiText("Not now")) } })
}
