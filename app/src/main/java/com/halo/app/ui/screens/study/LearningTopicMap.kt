package com.halo.app.ui.screens.study

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloReadAloud

/** Parent edges are stored explicitly; an explored node is never an assessed skill. */
@Composable
internal fun LearningTopicMap(state: StudyUiState, model: StudyViewModel, kids: Boolean, enabled: Boolean) {
    var page by rememberSaveable(state.session?.id) { mutableIntStateOf(0) }
    val nodes = state.journey.concepts
    val pages = (nodes.size + 2) / 3
    val currentPage = page.coerceIn(0, (pages - 1).coerceAtLeast(0))
    val target = if (kids) 56.dp else 48.dp
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (nodes.isNotEmpty() && !state.isGenerating) key(nodes.map { it.id }, state.journey.currentBranch) {
                com.halo.app.ui.fx.HaloLearningBranchFx(state.journey.currentBranch != null, Modifier.size(48.dp))
            }
            Text(uiText("My topic map"), style = MaterialTheme.typography.titleMedium)
        }
        Text(state.session?.goal ?: uiText("My lesson"), style = MaterialTheme.typography.bodyLarge)
        Text(uiText("Explored means we opened an explanation. It does not mean you have mastered it."),
            style = MaterialTheme.typography.bodySmall)
        nodes.drop(currentPage * 3).take(3).forEach { node ->
            OutlinedButton(onClick = { model.exploreBranch(node.title) }, enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = target)) {
                Column(Modifier.fillMaxWidth()) {
                    nodes.firstOrNull { it.id == node.parentId }?.let { parent ->
                        Text(parent.title, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(node.title, style = MaterialTheme.typography.titleSmall)
                    Text(uiText(if (node.explored) "Explored" else "Idea to explore"),
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (pages > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { page = currentPage - 1 }, enabled = currentPage > 0,
                modifier = Modifier.heightIn(min = target)) { Text(uiText("Previous")) }
            Text("${currentPage + 1}/$pages")
            TextButton(onClick = { page = currentPage + 1 }, enabled = currentPage + 1 < pages,
                modifier = Modifier.heightIn(min = target)) { Text(uiText("Next")) }
        }
        OutlinedButton(onClick = model::suggestLearningBranches, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = target)) { Text(uiText("Suggest connected ideas")) }
    }
}

@Composable
internal fun ExplainBackFeedback(review: ExplainBackReview) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            key(review) { com.halo.app.ui.fx.HaloExplainBackFx(Modifier.size(48.dp)) }
            Text(uiText("Suggested feedback · not a grade"), style = MaterialTheme.typography.titleMedium)
        }
        Text(uiText("halo may be wrong. Use these questions to think again; this does not change your mastery."),
            style = MaterialTheme.typography.bodySmall)
        review.observations.forEach { observation ->
            Text(observation.quote, style = MaterialTheme.typography.titleSmall)
            Text(observation.suggestion)
        }
        review.questions.forEach { Text(it) }
        HaloReadAloud((review.observations.map { it.quote + ". " + it.suggestion } + review.questions).joinToString("\n"))
    }
}
