package com.halo.app.ui.screens.study

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.study.StudyExplanationDepth

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StudyExplanationOptions(state: StudyUiState, model: StudyViewModel, kids: Boolean = false) {
    var expanded by rememberSaveable(state.session?.id) { mutableStateOf(false) }
    val ready = !state.loading && !state.isGenerating && !state.isSubmitting && !state.isFinished &&
        !state.savingSummary && state.comparingCards.isEmpty()
    val target = if (kids) 56.dp else 48.dp
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { expanded = !expanded }, enabled = state.session != null && !state.loading,
            modifier = Modifier.heightIn(min = target)) {
            Column {
                Text(uiText("How much detail?"))
                Text(uiText(state.journey.explanationDepth.label()), style = MaterialTheme.typography.labelSmall)
            }
        }
        if (expanded) {
            Text(uiText("This choice changes future explanations, not your level or results."), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StudyExplanationDepth.entries.forEach { depth ->
                    FilterChip(selected = state.journey.explanationDepth == depth, enabled = ready,
                        onClick = { model.setExplanationDepth(depth) }, modifier = Modifier.heightIn(min = target),
                        label = { Text(uiText(depth.label())) })
                }
            }
        }
    }
}

private fun StudyExplanationDepth.label(): String = when (this) {
    StudyExplanationDepth.SIMPLER -> "Simpler explanation"
    StudyExplanationDepth.BALANCED -> "Usual detail"
    StudyExplanationDepth.DEEPER -> "Deeper explanation"
}
