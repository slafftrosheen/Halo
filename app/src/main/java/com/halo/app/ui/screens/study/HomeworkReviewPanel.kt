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

/** One learner-confirmed exercise at a time, shared by Kids and Study. */
@Composable
internal fun HomeworkReviewPanel(state: StudyUiState, model: StudyViewModel, kids: Boolean) {
    var expanded by rememberSaveable(state.session?.id) { mutableStateOf(false) }
    var reviewId by rememberSaveable(state.session?.id) { mutableStateOf<String?>(null) }
    var wording by rememberSaveable(state.session?.id) { mutableStateOf("") }
    LaunchedEffect(state.requestedHomeworkReviewId) {
        val requested = state.journey.exercises.firstOrNull { it.id == state.requestedHomeworkReviewId }
        if (requested != null) { expanded = true; reviewId = requested.id; wording = requested.text }
    }
    val ready = !state.loading && !state.isGenerating && !state.isSubmitting && !state.isFinished
    val target = if (kids) 56.dp else 48.dp
    val current = state.journey.exercises.firstOrNull { it.id == state.session?.currentExerciseId }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        current?.let {
            Text(uiText("Current exercise"), style = MaterialTheme.typography.titleSmall)
            Text(it.text, style = MaterialTheme.typography.bodyLarge)
            if (it.finished) {
                Text(uiText("Marked done by you · not an assessment"), style = MaterialTheme.typography.labelMedium)
                Button(onClick = model::nextHomeworkExercise, enabled = ready,
                    modifier = Modifier.fillMaxWidth().heightIn(min = target)) { Text(uiText("Next exercise")) }
            } else OutlinedButton(onClick = { model.finishHomeworkExercise() }, enabled = ready,
                modifier = Modifier.fillMaxWidth().heightIn(min = target)) { Text(uiText("Done with this exercise")) }
        }
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = target)) {
            Text(uiText(if (expanded) "Close homework review" else "Homework exercises"))
        }
        if (expanded) {
            Text(uiText("Check the wording against your material. halo may misread symbols or miss exercises."))
            if (state.session?.sourceObjectIds?.isNotEmpty() == true)
                OutlinedButton(onClick = model::extractHomework, enabled = ready,
                    modifier = Modifier.fillMaxWidth().heightIn(min = target)) {
                    Text(uiText("Find exercises in this material"))
                }
            state.journey.exercises.forEachIndexed { index, exercise ->
                OutlinedButton(onClick = { reviewId = exercise.id; wording = exercise.text },
                    enabled = ready, modifier = Modifier.fillMaxWidth().heightIn(min = target)) {
                    Column(Modifier.fillMaxWidth()) {
                        Text("${index + 1}. ${exercise.text.take(180)}")
                        Text(uiText(if (exercise.reviewed) "Wording checked by you" else "Review wording first"),
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            state.journey.exercises.firstOrNull { it.id == reviewId }?.let { exercise ->
                val source = state.sources.firstOrNull { it.objectId == exercise.sourceObjectId }
                source?.let {
                    Text(listOfNotNull(it.title, exercise.locator).joinToString(" · "),
                        style = MaterialTheme.typography.titleSmall)
                    it.resolved?.let { resolved -> HomeworkSourcePreview(resolved, exercise.locator, kids) { page ->
                        model.selectPdfPage(resolved.objectId, page)
                    } }
                    it.excerpt?.takeIf(String::isNotBlank)?.let { excerpt ->
                        Text(uiText("Source excerpt"), style = MaterialTheme.typography.labelMedium)
                        Text(excerpt.take(1500))
                    }
                }
            }
            TextButton(onClick = { reviewId = null; wording = "" }, enabled = ready,
                modifier = Modifier.heightIn(min = target)) { Text(uiText("Type another exercise")) }
            key(reviewId) {
                HomeworkWordingReview(wording,
                    state.journey.exercises.firstOrNull { it.id == reviewId }?.uncertainty,
                    kids, ready, onTextChange = { wording = it.take(4000) },
                    onConfirm = { model.confirmHomework(reviewId, wording); expanded = false })
            }
        }
    }
}
