package com.halo.app.ui.screens.study

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.fx.haloTrace
import com.halo.sdk.runtime.study.TutorStage
import com.halo.app.ui.theme.v13.Halo13Animation
import com.halo.app.ui.theme.v13.Halo13Motion

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
        modifier = Modifier.heightIn(min = height)) { Text(stringResource(R.string.ln13_help_title)) }
    if (open) AlertDialog(onDismissRequest = { open = false },
        title = { Text(stringResource(R.string.ln13_help_title)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Halo13Animation(Halo13Motion.HELP_LADDER, Modifier.fillMaxWidth().height(64.dp))
            Text(stringResource(R.string.ln13_help_sub))
            listOf(R.string.ln13_help_remind to TutorStage.ASK, R.string.ln13_help_hint to TutorStage.HINT,
                R.string.ln13_help_explain to TutorStage.EXPLAIN, R.string.ln13_help_another_way to TutorStage.ANALOGY,
                R.string.ln13_help_one_step to TutorStage.PARTIAL_SOLUTION).forEach { (label, stage) ->
                OutlinedButton(onClick = { open = false; model.requestGuidance(stage) }, enabled = enabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = height)) { Text(stringResource(label)) }
            }
            OutlinedButton(onClick = { open = false; model.submitAttempt() }, enabled = enabled && state.canSubmitAttempt,
                modifier = Modifier.fillMaxWidth().heightIn(min = height)) { Text(stringResource(R.string.ln13_help_check_try)) }
            TextButton(onClick = { open = false; confirm = true }, enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = height)) { Text(stringResource(R.string.ln13_help_show_all)) }
        } }, confirmButton = { TextButton(onClick = { open = false }) { Text(uiText("Not now")) } })
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text(uiText("Show every step?")) },
        text = { Text(stringResource(R.string.ln13_help_show_all_ask)) },
        confirmButton = { TextButton(onClick = { confirm = false; if (enabled) model.askForSolution() },
            enabled = enabled) { Text(stringResource(R.string.ln13_help_show_all)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(uiText("Not now")) } })
}
