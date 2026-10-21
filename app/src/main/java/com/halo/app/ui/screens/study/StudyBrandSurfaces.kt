package com.halo.app.ui.screens.study

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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

internal fun LearningStage.displayLabel(): String = when (this) {
    LearningStage.START -> "Start"
    LearningStage.DISCOVER -> "What do you already know?"
    LearningStage.EXPLAIN -> "One idea at a time"
    LearningStage.EXPLORE -> "Explore"
    LearningStage.PRACTICE -> "Try it yourself"
    LearningStage.REFLECT -> "What I learned"
    LearningStage.CONTINUE -> "Continue learning"
}

/** Position in a saved journey, not a score or a measure of mastery. */
@Composable
internal fun StudyJourneyProgress(stage: LearningStage, kids: Boolean = false) {
    // K02 groups the persisted journey into five child-facing phases.
    // This is navigation progress, never a knowledge or completion score.
    val position = if (kids) when (stage) {
        LearningStage.START, LearningStage.DISCOVER -> 0
        LearningStage.EXPLAIN -> 1
        LearningStage.EXPLORE -> 2
        LearningStage.PRACTICE -> 3
        LearningStage.REFLECT, LearningStage.CONTINUE -> 4
    } else stage.ordinal
    val total = if (kids) 5 else LearningStage.entries.size
    val label = uiText("Step %1\$d of %2\$d · %3\$s", position + 1,
        total, uiText(stage.displayLabel()))
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = label
            progressBarRangeInfo = ProgressBarRangeInfo(position.toFloat(), 0f..(total - 1).toFloat(), total - 2)
        }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(total) { index ->
                Box(Modifier.weight(1f).height(if (kids) 8.dp else 4.dp).background(
                    when {
                        index == position -> MaterialTheme.colorScheme.primary
                        index < position -> MaterialTheme.colorScheme.primary.copy(alpha = .6f)
                        else -> MaterialTheme.colorScheme.outlineVariant
                    }, HaloShapes.pill))
            }
        }
        if (!kids) Text(label, style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary)
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
                Text(uiText("Ready to remember"), style = MaterialTheme.typography.titleSmall,
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
internal fun StudyEmptyHome(onStart: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        com.halo.app.ui.presence.HaloSignalFace(
            com.halo.app.ui.presence.HaloSignalPresentation(), size = 84.dp,
            contentDescription = uiText("halo"))
        Text(uiText("Learn anything, at your pace"), style = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(uiText("Choose a topic, add a photo, a PDF or a note, and halo guides you step by step."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        StudyFocusAction("Start a new lesson", HaloGlyph.NOTE, true, onStart, guide = true)
    }
}
