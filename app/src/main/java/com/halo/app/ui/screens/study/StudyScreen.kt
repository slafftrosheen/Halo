package com.halo.app.ui.screens.study

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.halo.app.data.study.StudyCitation
import com.halo.app.data.study.StudyPracticeItem
import com.halo.app.data.study.StudyVerdict
import com.halo.app.study.MasteryCardCurve
import com.halo.app.study.SourceCoverage
import com.halo.app.study.StudyMasterySnapshot
import com.halo.app.study.StudyReviewIntensity
import com.halo.app.study.StudySourceReference
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.design.HaloSpacing
import com.halo.app.ui.presence.HaloSignalGlyph
import com.halo.app.ui.presence.HaloSignalKind
import com.halo.sdk.runtime.study.TutorStage

@Composable
fun StudyScreen(viewModel: StudyViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var cardQuestion by remember { mutableStateOf("") }

    // Leaving the screen must never leave the phone talking to an empty room.
    BackHandler(enabled = true) {
        viewModel.stopReading("Left the Study screen")
        onBack()
    }

    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = HaloSpacing.screen)
    ) {
        HaloScreenHeader(
            title = "Study",
            subtitle = state.session?.goal ?: state.stage.guidance,
            navigation = {
                viewModel.stopReading("Left the Study screen")
                onBack()
            },
            primaryAction = null,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                TextButton(onClick = { showMenu = true }) { Text("Session") }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (state.isFinished) "Already completed" else "Finish session") },
                        enabled = !state.isFinished,
                        onClick = {
                            showMenu = false
                            viewModel.finishSession()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete this session's data") },
                        onClick = {
                            showMenu = false
                            viewModel.deleteAllSessionData()
                        },
                    )
                }
            }
            state.measuredMasteryPercent?.let { percent ->
                Spacer(Modifier.width(8.dp))
                MasteryChip(percent)
            }
            Spacer(Modifier.weight(1f))
            if (state.isGenerating) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { viewModel.cancelTurn("Learner cancelled") }) { Text("Stop") }
            }
        }

        state.reader?.let { reader ->
            Spacer(Modifier.height(4.dp))
            ReaderPanel(reader, viewModel)
        }

        state.notice?.let { notice ->
            Surface(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        notice,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    TextButton(onClick = viewModel::clearNotice) { Text("Dismiss") }
                }
            }
        }

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }
        state.unavailable?.let { message ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(message, style = MaterialTheme.typography.titleMedium)
            }
            return@Column
        }

        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(4.dp))
            StageStrip(state)
            Spacer(Modifier.height(10.dp))
            StageControls(state, viewModel)
            Spacer(Modifier.height(14.dp))
            state.sources.forEach { source -> SourceCard(source, state, viewModel) }
            if (state.hasPartialCoverage) {
                Text(
                    "Halo sent only part of this source to the model. Ask about a narrower passage for a " +
                        "complete answer.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            if (state.turns.isEmpty()) {
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Start with what you know", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Ask Halo for the first step, or write your attempt and submit it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            state.turns.forEach { turn -> TutorTurnCard(turn, state, viewModel) }
            Spacer(Modifier.height(12.dp))
            AttemptEditor(state, viewModel)
            Spacer(Modifier.height(14.dp))
            if (state.turns.isNotEmpty()) {
                SaveAsCardRow(cardQuestion, { cardQuestion = it }) {
                    viewModel.saveLatestTurnAsCard(cardQuestion)
                    cardQuestion = ""
                }
                Spacer(Modifier.height(10.dp))
            }
            PracticeSection(state, viewModel)
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun MasteryChip(percent: Int) {
    val color = when {
        percent >= 80 -> MaterialTheme.colorScheme.primaryContainer
        percent >= 50 -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    Surface(shape = RoundedCornerShape(10.dp), color = color) {
        Text(
            "Mastery $percent%",
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Sentence-level read-along. Word timing is unavailable, so the unit is honestly a sentence. */
@Composable
private fun ReaderPanel(reader: ReaderState, viewModel: StudyViewModel) {
    Surface(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HaloSignalGlyph(kind = HaloSignalKind.HALO, size = 20.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${reader.sourceLabel} · ${reader.progressLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = viewModel::readerPreviousSentence, enabled = reader.hasPrevious) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Previous sentence")
                }
                IconButton(onClick = viewModel::toggleReaderPlayback) {
                    Icon(
                        if (reader.speaking) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (reader.speaking) "Pause reading" else "Resume reading",
                    )
                }
                IconButton(onClick = viewModel::readerNextSentence, enabled = reader.hasNext) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Next sentence")
                }
                IconButton(onClick = { viewModel.closeReader() }) {
                    Icon(Icons.Rounded.Stop, contentDescription = "Stop reading")
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                reader.current,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (reader.hasPrevious) {
                Spacer(Modifier.height(4.dp))
                Text(
                    reader.sentences[reader.index - 1],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (reader.hasNext) {
                Spacer(Modifier.height(4.dp))
                Text(
                    reader.sentences[reader.index + 1],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = reader.autoAdvance,
                    onCheckedChange = viewModel::setReaderAutoAdvance,
                )
                Spacer(Modifier.width(8.dp))
                Text("Continue to the next sentence automatically", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun StageStrip(state: StudyUiState) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TutorStage.entries.forEach { stage ->
            val active = stage == state.stage
            val reached = stage.ordinal <= state.stage.ordinal
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = when {
                    active -> MaterialTheme.colorScheme.primaryContainer
                    reached -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.surface
                },
            ) {
                Text(
                    stage.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = when {
                        active -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun StageControls(state: StudyUiState, viewModel: StudyViewModel) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = viewModel::goBack,
            enabled = state.stage.previous() != null && !state.isFinished,
        ) { Text("Back") }
        TextButton(onClick = viewModel::repeatStage, enabled = !state.isGenerating && !state.isFinished) {
            Text("Repeat")
        }
        Spacer(Modifier.weight(1f))
        if (!state.isFinished && !state.stage.isSolution) {
            TextButton(onClick = viewModel::askTutor, enabled = !state.isGenerating) { Text("Ask") }
            TextButton(onClick = viewModel::advance, enabled = !state.isGenerating) { Text("Next step") }
            TextButton(onClick = viewModel::askForSolution, enabled = !state.isGenerating) {
                Text("Full solution")
            }
        }
    }
}

@Composable
private fun SourceCard(
    source: StudySourceReference,
    state: StudyUiState,
    viewModel: StudyViewModel,
) {
    Surface(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HaloSignalGlyph(kind = HaloSignalKind.NOTE, size = 22.dp)
                Spacer(Modifier.width(8.dp))
                Text(source.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(
                    onClick = viewModel::readSourceAloud,
                    enabled = state.narrationAvailable && source.coverage != SourceCoverage.MISSING,
                ) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = "Read source aloud")
                }
            }
            source.excerpt?.let { excerpt ->
                Spacer(Modifier.height(4.dp))
                Text(excerpt, style = MaterialTheme.typography.bodySmall, maxLines = 4)
            }
            when (source.coverage) {
                SourceCoverage.MISSING ->
                    Text(
                        "No readable text is available for this source.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                SourceCoverage.PARTIAL ->
                    Text(
                        "Coverage: partial — ${source.omittedCharacters} of ${source.totalCharacters} " +
                            "characters are not sent to the model.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                SourceCoverage.FULL -> Unit
            }
            if (source.stale) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "This source changed since the session started. Citations refer to revision " +
                        "${state.session?.sourceRevision ?: 0}.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun TutorTurnCard(turn: StudyTurn, state: StudyUiState, viewModel: StudyViewModel) {
    val reading = state.reader?.passageId == "${state.session?.id}:turn:${turn.id}"
    Surface(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HaloSignalGlyph(kind = HaloSignalKind.HALO, size = 20.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    turn.stage.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                turn.verdict?.let { VerdictChip(it) }
                if (turn.text.isNotBlank()) {
                    IconButton(
                        onClick = {
                            if (reading) viewModel.stopReading("Stopped by learner")
                            else viewModel.readTurnAloud(turn)
                        },
                        enabled = state.narrationAvailable,
                    ) {
                        Icon(
                            if (reading) Icons.Rounded.Stop else Icons.Rounded.GraphicEq,
                            contentDescription = if (reading) "Stop reading" else "Read response aloud",
                        )
                    }
                }
            }
            if (turn.failure != null) {
                Text(turn.failure, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            } else {
                Text(turn.text, style = MaterialTheme.typography.bodyMedium)
            }
            turn.nextStep?.takeIf { it.isNotBlank() }?.let { next ->
                Spacer(Modifier.height(8.dp))
                Text("Next: $next", style = MaterialTheme.typography.labelMedium)
            }
            turn.uncertainty?.takeIf { it.isNotBlank() }?.let { uncertainty ->
                Spacer(Modifier.height(6.dp))
                Text(
                    uncertainty,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (turn.citations.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Source", style = MaterialTheme.typography.labelMedium)
                turn.citations.forEach { citation -> CitationRow(citation) }
            }
        }
    }
}

@Composable
private fun CitationRow(citation: StudyCitation) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.Top) {
        HaloSignalGlyph(kind = HaloSignalKind.FILE, size = 16.dp)
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                "[${citation.sourceObjectId}]${citation.locator?.let { " $it" }.orEmpty()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(citation.excerpt, style = MaterialTheme.typography.bodySmall, maxLines = 3)
        }
    }
}

@Composable
private fun VerdictChip(verdict: StudyVerdict) {
    val color = when (verdict) {
        StudyVerdict.CHECKS_OUT -> MaterialTheme.colorScheme.primaryContainer
        StudyVerdict.NEEDS_WORK -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(shape = RoundedCornerShape(8.dp), color = color) {
        Text(
            verdict.label,
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AttemptEditor(state: StudyUiState, viewModel: StudyViewModel) {
    Column(Modifier.fillMaxWidth()) {
        Text("Your attempt", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = state.draft,
            onValueChange = viewModel::setDraft,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isFinished,
            minLines = 3,
            placeholder = { Text("Write what you have so far — you can submit at any stage") },
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = viewModel::submitAttempt, enabled = state.canSubmitAttempt) {
                Text(if (state.isSubmitting) "Saving…" else "Submit attempt")
            }
            TextButton(onClick = viewModel::askTutor, enabled = !state.isGenerating && !state.isFinished) {
                Text("Ask for a hint")
            }
        }
        val attempts = state.attempts
        if (attempts.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            val unaided = attempts.count { it.helpRevealed.isEmpty() }
            Text(
                "${attempts.size} attempt(s) saved locally · $unaided without help",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SaveAsCardRow(question: String, onQuestionChange: (String) -> Unit, onSave: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Save this response as a practice card", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = question,
                onValueChange = onQuestionChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Question") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onSave, enabled = question.isNotBlank()) { Text("Save draft card") }
                Text(
                    "Drafts stay out of the queue until you accept them",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PracticeSection(state: StudyUiState, viewModel: StudyViewModel) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Practice", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(8.dp))
            Text(
                "${state.practiceItems.count { it.accepted }} in queue · ${state.masteredCount} mastered",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        if (state.practiceItems.isEmpty()) {
            Text(
                "Accepted cards appear here with a due date. Nothing enters the queue automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }
        ReviewIntensityRow(state.reviewIntensity, viewModel)
        state.curveFocusItemId?.let { focusId ->
            val curve = state.curves[focusId]
            val item = state.practiceItems.firstOrNull { it.id == focusId }
            if (curve != null && item != null) {
                Spacer(Modifier.height(8.dp))
                MemoryCard(
                    question = item.question,
                    curve = curve,
                    snapshot = state.mastery[focusId],
                    now = remember { System.currentTimeMillis() },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        state.practiceItems.forEach { item ->
            PracticeItemRow(item, state, viewModel)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PracticeItemRow(
    item: StudyPracticeItem,
    state: StudyUiState,
    viewModel: StudyViewModel,
) {
    val mastery = state.mastery[item.id]
    val result = state.cardResults[item.id]
    val revealed = item.id in state.revealedAnswers
    val answer = state.cardAnswers[item.id].orEmpty()
    val now = remember(System.currentTimeMillis()) { System.currentTimeMillis() }

    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HaloSignalGlyph(kind = HaloSignalKind.ACTION, size = 20.dp)
                Spacer(Modifier.width(8.dp))
                Text(item.question, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                mastery?.let { snapshot -> MasteryBadge(snapshot, now) }
            }
            if (item.mastered) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Mastered — kept in a long-interval maintenance queue",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (!item.accepted) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Draft — not in the review queue",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.userEdited) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "You edited this card; Halo will not overwrite it.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (item.accepted) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = answer,
                    onValueChange = { viewModel.setCardAnswer(item.id, it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Your answer") },
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { viewModel.checkCardAnswer(item.id) },
                        enabled = answer.isNotBlank(),
                    ) { Text("Check answer") }
                    if (!revealed) {
                        TextButton(onClick = { viewModel.revealAnswer(item.id) }) { Text("Reveal") }
                    }
                }
            }

            result?.let { outcome ->
                Spacer(Modifier.height(6.dp))
                if (outcome.diff != null) {
                    AnswerComparison(
                        prefix = outcome.diff.prefix,
                        learner = outcome.diff.learnerMiddle,
                        expected = outcome.diff.expectedMiddle,
                        suffix = outcome.diff.suffix,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    outcome.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (outcome.verdict) {
                        true -> MaterialTheme.colorScheme.primary
                        false -> MaterialTheme.colorScheme.error
                        null -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (outcome.verdict == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { viewModel.selfAssessCard(item.id, known = true) }) { Text("I had it") }
                        TextButton(onClick = { viewModel.selfAssessCard(item.id, known = false) }) { Text("Show again soon") }
                    }
                }
            }

            if (revealed && result == null) {
                Spacer(Modifier.height(6.dp))
                Text(item.answer, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!item.accepted) {
                    IconButton(onClick = { viewModel.setCardAccepted(item.id, true) }) {
                        Icon(Icons.Rounded.Check, contentDescription = "Accept card")
                    }
                    IconButton(onClick = { viewModel.deleteCard(item.id) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Discard draft")
                    }
                } else {
                    TextButton(onClick = { viewModel.selfAssessCard(item.id, known = false) }) {
                        Text("Show again soon")
                    }
                    IconButton(onClick = { viewModel.deleteCard(item.id) }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete card")
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "reviewed ${item.reviewCount}×",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Review intensity. The target retention is spelled out because it is the trade-off being chosen:
 * a stricter target means shorter gaps and more reviews.
 */
@Composable
private fun ReviewIntensityRow(intensity: StudyReviewIntensity, viewModel: StudyViewModel) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Review intensity",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StudyReviewIntensity.entries.forEach { option ->
                FilterChip(
                    selected = option == intensity,
                    onClick = { viewModel.setReviewIntensity(option) },
                    label = { Text("${option.label} · ${option.retentionPercent}%") },
                )
            }
        }
        Text(
            "${intensity.description}. Applies to how soon a card comes back; the mistake ladder is unaffected.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Memory decay for one card, drawn from the same closed-form model the scheduler uses.
 * Probabilities are sampled per pixel-width, so nothing is stored per day and the chart cannot drift
 * away from the schedule it explains.
 */
@Composable
private fun MemoryCard(
    question: String,
    curve: MasteryCardCurve,
    snapshot: StudyMasterySnapshot?,
    now: Long,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val dueColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Memory decay", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                snapshot?.let { state ->
                    Text(
                        "recall now ${state.retentionPercent}% · target ${(state.desiredRetention * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                question,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Canvas(Modifier.fillMaxWidth().height(48.dp)) {
                if (size.width <= 0f || curve.points.size < 2) return@Canvas
                // The 50% line is the horizon the due date is allowed to cross.
                drawLine(
                    color = gridColor,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 1f,
                )
                val path = Path()
                curve.points.forEachIndexed { index, point ->
                    val x = size.width * index / (curve.points.size - 1f)
                    val y = (size.height * (1f - point.retrievability.toFloat())).coerceIn(0f, size.height)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color = lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                curve.dueFraction?.let { fraction ->
                    val x = size.width * fraction
                    drawLine(
                        color = dueColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(curve.spanLabel(), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(
                    snapshot?.statusLabel(now) ?: "Not measured yet",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (snapshot != null && now >= snapshot.dueAtEpochMs) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                "${curve.reviewCount} review(s) · the line is read at 50% recall probability",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Learner answer in red, stored answer in green, shared prefix/suffix kept neutral. */
@Composable
private fun AnswerComparison(prefix: String, learner: String, expected: String, suffix: String) {
    Column(Modifier.fillMaxWidth()) {
        Text("Your answer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = prefix + learner + suffix,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text("Stored answer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(expected, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun MasteryBadge(snapshot: StudyMasterySnapshot, now: Long) {
    val percent = if (snapshot.isMeasured) "${snapshot.masteryPercent}%" else "—"
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
            Text(
                "Mastery $percent",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${snapshot.statusLabel(now)} · memory ${snapshot.stabilityLabel()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
