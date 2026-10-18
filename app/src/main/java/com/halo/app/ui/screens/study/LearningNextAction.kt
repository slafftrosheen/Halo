package com.halo.app.ui.screens.study

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.data.study.StudyVerdict
import com.halo.app.ui.components.VoiceFieldCancellation
import kotlinx.coroutines.delay

enum class LearningNextAction { NONE, MIC, HINT, NEXT }

/** A clock can suggest help only after an actual narration-completed event. */
@Composable
internal fun rememberLearningNextAction(state: StudyUiState, kids: Boolean, blocking: Boolean = false): LearningNextAction {
    val activeFields by VoiceFieldCancellation.activeFields.collectAsStateWithLifecycle()
    var hesitant by remember(state.awaitingAnswerSince) { mutableStateOf(false) }
    val busy = state.loading || state.isGenerating || state.isSubmitting ||
        state.reader?.let { it.speaking || it.preparing } == true || activeFields.isNotEmpty() || blocking
    LaunchedEffect(state.awaitingAnswerSince, busy) {
        hesitant = false
        val since = state.awaitingAnswerSince
        if (!busy && since != null) {
            val threshold = if (kids) 12_000L else 20_000L
            delay((threshold - (System.currentTimeMillis() - since).coerceAtLeast(0)).coerceAtLeast(0))
            hesitant = true
        }
    }
    if (busy || state.isFinished) return LearningNextAction.NONE
    val feedback = state.feedback.lastOrNull()
    val attempt = state.attempts.firstOrNull { it.id == feedback?.attemptId }
    return when {
        feedback?.verdict == StudyVerdict.CHECKS_OUT && attempt != null && attempt.helpRevealed.isEmpty() &&
            attempt.exerciseId == state.session?.currentExerciseId && feedback.generationId == state.turns.lastOrNull()?.id -> LearningNextAction.NEXT
        state.awaitingAnswerSince != null && hesitant -> LearningNextAction.HINT
        state.awaitingAnswerSince != null -> LearningNextAction.MIC
        else -> LearningNextAction.NONE
    }
}
