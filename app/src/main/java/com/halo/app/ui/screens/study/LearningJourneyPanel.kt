package com.halo.app.ui.screens.study

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind

/** One state owner and one set of actions, with larger child targets. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LearningJourneyPanel(state: StudyUiState, model: StudyViewModel, kids: Boolean = false, nextAction: LearningNextAction = LearningNextAction.NONE) {
    val journey = state.journey
    val question = uiText("What do you already know?")
    val context = androidx.compose.ui.platform.LocalContext.current
    val accessibility = context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
    LaunchedEffect(kids, state.session?.id, journey.stage) {
        if (kids && state.session != null && journey.stage == LearningStage.DISCOVER &&
            accessibility?.isTouchExplorationEnabled != true) model.readLearningPrompt(question)
    }
    DisposableEffect(accessibility, model) {
        val listener = android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener { active ->
            if (active) model.stopReading("Screen reader takes audio focus")
        }
        accessibility?.addTouchExplorationStateChangeListener(listener)
        onDispose { accessibility?.removeTouchExplorationStateChangeListener(listener) }
    }
    val ready = !state.loading && !state.isGenerating && !state.isSubmitting && !state.isFinished
    var exploring by rememberSaveable(state.session?.id) { mutableStateOf(false) }
    var branch by rememberSaveable(state.session?.id) { mutableStateOf("") }
    var reflection by rememberSaveable(state.session?.id) { mutableStateOf(journey.reflection) }
    val target = if (kids) 56.dp else 48.dp
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (journey.stage == LearningStage.DISCOVER && state.session != null)
            key(state.session.id) { com.halo.app.ui.fx.HaloLearningOpenFx(Modifier.size(48.dp)) }
        Text(uiText(when (journey.stage) {
            LearningStage.START -> "Start"
            LearningStage.DISCOVER -> "What do you already know?"
            LearningStage.EXPLAIN -> "One idea at a time"
            LearningStage.EXPLORE -> "Explore deeper"
            LearningStage.PRACTICE -> "Try it yourself"
            LearningStage.REFLECT -> "What I learned"
            LearningStage.CONTINUE -> "Continue learning"
        }), style = MaterialTheme.typography.titleMedium)
        }
        if (!state.isFinished) HomeworkReviewPanel(state, model, kids)
        journey.currentBranch?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (journey.stage == LearningStage.DISCOVER && !state.isFinished) {
            Text(uiText("Tell halo what you know, or choose where to begin."))
            TextButton(onClick = { model.readLearningPrompt(question) }, enabled = ready,
                modifier = Modifier.heightIn(min = target)) { Text(uiText("Read to me")) }
            HaloTextField(value = state.draft, onValueChange = model::setDraft,
                kind = VoiceFieldKind.TEXT, label = { Text(uiText("My thinking")) }, enabled = ready,
                guideDictation = nextAction == LearningNextAction.MIC)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("I know a lot", "A little", "Not yet").forEach { label ->
                    val answer = uiText(label)
                    OutlinedButton(onClick = { model.discoverPriorKnowledge(answer) },
                        enabled = ready, modifier = Modifier.heightIn(min = target)) { Text(answer) }
                }
            }
            if (state.draft.isNotBlank()) com.halo.app.ui.components.HaloActionButton(onClick = { model.discoverPriorKnowledge(state.draft) },
                enabled = ready, modifier = Modifier.heightIn(min = target)) { Text(uiText("Use my thinking")) }
        } else if (!state.isFinished && journey.stage != LearningStage.REFLECT) {
            TextButton(onClick = { exploring = !exploring }, enabled = ready,
                modifier = Modifier.heightIn(min = target)) { Text(uiText("Explore deeper")) }
            if (exploring) {
            LearningTopicMap(state, model, kids, ready)
            HaloTextField(value = branch, onValueChange = { branch = it.take(300) },
                kind = VoiceFieldKind.SHORT, label = { Text(uiText("What would you like to explore?")) },
                enabled = ready, maxChars = 300)
            com.halo.app.ui.components.HaloActionButton(onClick = { model.exploreBranch(branch) }, enabled = ready && branch.isNotBlank(),
                modifier = Modifier.heightIn(min = target)) { Text(uiText("Explore deeper")) }
            if (journey.branches.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                journey.branches.takeLast(3).forEach { topic ->
                    AssistChip(onClick = { model.exploreBranch(topic) }, enabled = ready,
                        label = { Text(topic) })
                }
            }
            }
            TextButton(onClick = { model.reflectOnLearning(reflection) }, enabled = ready,
                modifier = Modifier.heightIn(min = target)) { Text(uiText("Done for now")) }
        }
        if (journey.stage == LearningStage.REFLECT) {
            HaloTextField(value = reflection, onValueChange = { reflection = it.take(4000) },
                kind = VoiceFieldKind.TEXT, label = { Text(uiText("What I learned")) }, enabled = ready,
                maxChars = 4000)
            OutlinedButton(onClick = { model.reviewExplainBack(reflection) }, enabled = ready && reflection.isNotBlank(),
                modifier = Modifier.heightIn(min = target)) { Text(uiText("Help me reflect on my explanation")) }
            journey.explainBack?.takeIf { it.transcript == reflection.trim() }?.let { ExplainBackFeedback(it) }
            com.halo.app.ui.components.HaloActionButton(onClick = { model.reflectOnLearning(reflection) }, enabled = ready,
                modifier = Modifier.heightIn(min = target)) { Text(uiText("Save checkpoint")) }
            OutlinedButton(onClick = model::saveSummaryToNotebook, enabled = ready && !state.savingSummary,
                modifier = Modifier.heightIn(min = target)) { Text(uiText("Save study notes")) }
            TextButton(onClick = model::finishSession, enabled = ready,
                modifier = Modifier.heightIn(min = target)) { Text(uiText("Finish lesson")) }
        }
    }
}
