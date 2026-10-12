package com.halo.app.ui.screens.study

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon

/** This describes the next useful learner action, not a fictional mastery percentage. */
internal enum class StudyLearningPhase { EXPLORE, TRY, REVIEW, COMPLETE }

internal fun studyLearningPhase(state: StudyUiState, reviewing: Boolean = false): StudyLearningPhase =
    when {
        reviewing -> StudyLearningPhase.REVIEW
        state.isFinished -> StudyLearningPhase.COMPLETE
        state.turns.isEmpty() && state.attempts.isEmpty() -> StudyLearningPhase.EXPLORE
        else -> StudyLearningPhase.TRY
    }

@Composable
internal fun StudySessionGuide(state: StudyUiState, reviewing: Boolean = false) {
    val phase = studyLearningPhase(state, reviewing)
    val message = when {
        state.isGenerating -> "Halo is preparing the next step. Your work stays saved."
        phase == StudyLearningPhase.REVIEW -> "Recall the answer first, then reveal and rate it."
        phase == StudyLearningPhase.COMPLETE -> "Lesson complete. You can still review your saved cards."
        state.turns.isEmpty() && state.draft.isNotBlank() -> "You can submit your idea now, before asking for help."
        state.turns.isEmpty() -> "Start with your own thoughts, or ask Halo for the first explanation."
        state.draft.isNotBlank() -> "Check your attempt for feedback, or ask a question instead."
        else -> "Your turn: try an answer, ask a question, or request another hint."
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(uiText("Your learning path"), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LearningPathMarker("Understand", HaloGlyph.THINK,
                    phase == StudyLearningPhase.EXPLORE, Modifier.weight(1f))
                LearningPathMarker("Try", HaloGlyph.NOTE,
                    phase == StudyLearningPhase.TRY, Modifier.weight(1f))
                LearningPathMarker("Review", HaloGlyph.MEMORY,
                    phase == StudyLearningPhase.REVIEW || phase == StudyLearningPhase.COMPLETE, Modifier.weight(1f))
            }
            Text(uiText(message), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface)
            if (!reviewing && !state.isFinished) {
                Text(uiText("Guidance level: %1\$s", uiText(state.stage.guidance)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LearningPathMarker(label: String, glyph: HaloGlyph, current: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 48.dp),
        color = if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(horizontal = 7.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            HaloIcon(glyph, contentDescription = null, modifier = Modifier.size(17.dp),
                tint = if (current) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(uiText(label), style = MaterialTheme.typography.labelMedium,
                color = if (current) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2)
        }
    }
}
