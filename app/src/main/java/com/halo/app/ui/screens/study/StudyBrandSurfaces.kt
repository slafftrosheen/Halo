package com.halo.app.ui.screens.study

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.language.uiText
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.fx.haloTrace
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon

internal enum class LearnPathStep(val title: String, val glyph: HaloGlyph) {
    CHOOSE("Choose", HaloGlyph.QUESTION),
    LOOK("Look", HaloGlyph.READ_MODE),
    TRY("Try", HaloGlyph.NOTE),
    HELP("Help", HaloGlyph.THINK),
    KEEP("Keep", HaloGlyph.SUCCESS),
}

internal fun learnPathStep(stage: LearningStage, helpActive: Boolean = false, finished: Boolean = false): LearnPathStep =
    when {
        finished || stage == LearningStage.REFLECT || stage == LearningStage.CONTINUE -> LearnPathStep.KEEP
        helpActive -> LearnPathStep.HELP
        stage == LearningStage.START -> LearnPathStep.CHOOSE
        stage == LearningStage.PRACTICE -> LearnPathStep.TRY
        else -> LearnPathStep.LOOK
    }

@Composable
internal fun learnPathLabel(stage: LearningStage, finished: Boolean = false): String {
    val step = learnPathStep(stage, finished = finished)
    return uiText("Step %1\$d of %2\$d · %3\$s", step.ordinal + 1, LearnPathStep.entries.size, uiText(step.title))
}

/** Navigation through the saved journey. Help is optional and never stands for a grade. */
@Composable
internal fun StudyJourneyProgress(
    stage: LearningStage,
    kids: Boolean = false,
    helpActive: Boolean = false,
    finished: Boolean = false,
) {
    val step = learnPathStep(stage, helpActive, finished)
    val steps = if (kids) LearnPathStep.entries.drop(1) else LearnPathStep.entries
    val position = steps.indexOf(step).coerceAtLeast(0)
    val activeTitle = uiText(if (kids && step == LearnPathStep.KEEP) "Done" else steps[position].title)
    val label = uiText("Step %1\$d of %2\$d · %3\$s", position + 1, steps.size, activeTitle)
    val colors = MaterialTheme.colorScheme
    val mint = com.halo.app.ui.theme.HaloMint
    val showLabels = LocalDensity.current.fontScale < 1.5f
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics(mergeDescendants = true) {
        progressBarRangeInfo = ProgressBarRangeInfo(position.toFloat(), 0f..steps.lastIndex.toFloat(), steps.size - 2)
    }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.primary)
        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            steps.forEachIndexed { index, marker ->
                val isCurrent = index == position
                val visited = index < position && marker != LearnPathStep.HELP
                val color = when {
                    visited || finished && isCurrent -> mint
                    kids && isCurrent -> colors.primary
                    isCurrent && marker == LearnPathStep.HELP ->
                        com.halo.app.ui.theme.brandAccent(false, com.halo.app.ui.theme.HaloAccent.LILAC).fill
                    isCurrent -> colors.primary
                    else -> colors.outlineVariant
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.fillMaxWidth().height(if (kids) 24.dp else 4.dp)
                        .background(color, HaloShapes.pill), contentAlignment = Alignment.Center) {
                        if (kids) HaloIcon(marker.glyph, null, Modifier.size(16.dp),
                            tint = when {
                                visited || finished && isCurrent ->
                                    com.halo.app.ui.theme.brandAccent(false, com.halo.app.ui.theme.HaloAccent.MINT).onFill
                                isCurrent -> colors.onPrimary
                                else -> colors.onSurfaceVariant
                            })
                    }
                    if (showLabels) {
                        val title = uiText(if (kids && marker == LearnPathStep.KEEP) "Done" else marker.title)
                        Text(title, style = MaterialTheme.typography.labelSmall,
                            color = if (isCurrent) colors.onSurface else colors.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
    }
}

/** S01 reusable card; text and callbacks always come from the stored session. */
@Composable
internal fun StudyResumeCard(title: String, subtitle: String?, onContinue: () -> Unit) {
    val large = LocalDensity.current.fontScale >= 1.5f
    Surface(onClick = onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        shape = HaloShapes.card, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            StudyCardIcon(HaloGlyph.READ_MODE)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.5.sp,
                    lineHeight = 21.sp, fontWeight = FontWeight.SemiBold))
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (large) Text(uiText("Continue"), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
            }
            if (!large) Surface(shape = HaloShapes.pill, color = MaterialTheme.colorScheme.surfaceContainer) {
                Text(uiText("Continue"), Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
internal fun StudyReviewEntry(count: Int, enabled: Boolean, onStart: () -> Unit) {
    val large = LocalDensity.current.fontScale >= 1.5f
    Surface(Modifier.fillMaxWidth().haloTrace(enabled, MaterialTheme.colorScheme.primary, corner = 20.dp, lapOnAppear = true),
        shape = HaloShapes.card, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StudyCardIcon(HaloGlyph.MEMORY)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(uiText("Keep practising"), style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                Text(uiText("Ready to review: %1\$d", count), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (large) Button(onStart, enabled = enabled,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) { Text(uiText("Start")) }
            }
            if (!large) Button(onStart, enabled = enabled,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) { Text(uiText("Start")) }
        }
    }
}

@Composable
private fun StudyCardIcon(glyph: HaloGlyph) {
    Surface(shape = HaloShapes.control, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(painterResource(if (glyph == HaloGlyph.MEMORY) com.halo.app.R.drawable.halo_ic_practise
                else com.halo.app.R.drawable.halo_ic_study), null, Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun StudyChooseLesson(enabled: Boolean, onTopic: () -> Unit, onMaterial: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(uiText("Choose what to learn"), style = MaterialTheme.typography.titleMedium)
        StudyFocusAction("Learn a topic", HaloGlyph.QUESTION, enabled, onTopic)
        OutlinedButton(onClick = onMaterial, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            StudyActionLabel(HaloGlyph.FILE, "My homework or notes")
        }
    }
}
