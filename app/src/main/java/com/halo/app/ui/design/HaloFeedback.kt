package com.halo.app.ui.design

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.background

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloActionButton
import com.halo.app.ui.components.HaloActionStyle
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon

/** Presentation vocabulary only: never infers execution outcomes or readiness. */
enum class HaloStatus(val label: String, val glyph: HaloGlyph) {
    READY("Ready", HaloGlyph.SUCCESS), PREPARING("Getting ready…", HaloGlyph.MODEL),
    RUNNING("In progress", HaloGlyph.ACTIVITY), WAITING("Waiting", HaloGlyph.PAUSE),
    UNAVAILABLE("Unavailable", HaloGlyph.OFFLINE), PERMISSION_REQUIRED("Access required", HaloGlyph.SECURITY),
    COMPLETED("Completed", HaloGlyph.SUCCESS), FAILED("Couldn’t finish", HaloGlyph.ERROR),
}

@Composable
fun HaloStatusPresentation(status: HaloStatus, modifier: Modifier = Modifier, label: String = uiText(status.label)) {
    Row(modifier.semantics(mergeDescendants = true) { stateDescription = label },
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        HaloIcon(status.glyph, contentDescription = null, modifier = Modifier.size(20.dp),
            tint = if (status == HaloStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

enum class HaloFeedbackKind { INFORMATION, SUCCESS, WARNING, FAILURE }

@Composable
fun HaloFeedbackSurface(title: String, message: String, modifier: Modifier = Modifier,
    kind: HaloFeedbackKind = HaloFeedbackKind.INFORMATION, action: HaloHeaderAction? = null) {
    Surface(modifier.fillMaxWidth(), shape = HaloShapes.card,
        color = if (kind == HaloFeedbackKind.FAILURE) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (kind == HaloFeedbackKind.FAILURE) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp,
            if (kind == HaloFeedbackKind.FAILURE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val glyph = when (kind) {
                HaloFeedbackKind.INFORMATION -> HaloGlyph.HALO
                HaloFeedbackKind.SUCCESS -> HaloGlyph.SUCCESS
                HaloFeedbackKind.WARNING -> HaloGlyph.WARNING
                HaloFeedbackKind.FAILURE -> HaloGlyph.ERROR
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                HaloIcon(glyph, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            }
            Text(message, style = MaterialTheme.typography.bodyMedium)
            action?.let { HaloActionButton(it.onClick, style = HaloActionStyle.SECONDARY) { Text(it.label) } }
        }
    }
}

@Composable
fun HaloEmptyState(title: String, message: String, glyph: HaloGlyph, modifier: Modifier = Modifier,
    action: HaloHeaderAction? = null, enabled: Boolean = true) {
    Column(modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val state = when (glyph) {
            HaloGlyph.ERROR -> com.halo.app.ui.presence.HaloSignalState.ERROR
            HaloGlyph.WARNING -> com.halo.app.ui.presence.HaloSignalState.WARNING
            else -> com.halo.app.ui.presence.HaloSignalState.IDLE
        }
        com.halo.app.ui.presence.HaloSignalFace(
            com.halo.app.ui.presence.HaloSignalPresentation(state = state, label = title,
                contextGlyph = glyph, reducedMotion = true),
            size = if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f) 64.dp else 96.dp,
            contentDescription = title)
        Text(title, style = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(message, Modifier.widthIn(max = 520.dp), style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.let {
            HaloActionButton(onClick = it.onClick, modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                enabled = enabled, style = HaloActionStyle.SECONDARY) { Text(it.label) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HaloSectionHeader(title: String, modifier: Modifier = Modifier, action: HaloHeaderAction? = null) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.CenterVertically))
        action?.let { TextButton(it.onClick) { Text(it.label) } }
    }
}

@Composable
fun HaloActionRow(title: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true) {
    HaloActionButton(onClick, modifier.fillMaxWidth(), enabled, HaloActionStyle.SECONDARY) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.width(8.dp))
        HaloIcon(HaloGlyph.NEXT, contentDescription = null, modifier = Modifier.size(20.dp))
    }
}

internal fun haloMeasuredProgress(completed: Long?, total: Long?): Float? =
    if (completed == null || total == null || total <= 0 || completed < 0 || completed > total) null
    else (completed.toDouble() / total).toFloat()

/** Unknown or invalid measurements use an indeterminate indicator, never invented percentages. */
@Composable
fun HaloProgressPresentation(label: String, modifier: Modifier = Modifier, completed: Long? = null, total: Long? = null) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        val fraction = haloMeasuredProgress(completed, total)
        if (fraction == null && com.halo.app.ui.fx.rememberHaloReducedMotion()) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(4.dp)
                .background(MaterialTheme.colorScheme.outlineVariant, HaloShapes.pill))
        } else if (fraction == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        else LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun HaloGuidedStep(title: String, instruction: String, action: HaloHeaderAction, glyph: HaloGlyph,
    modifier: Modifier = Modifier, enabled: Boolean = true, completedSteps: Long? = null, totalSteps: Long? = null) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(instruction, style = MaterialTheme.typography.bodyMedium)
        if (completedSteps != null && totalSteps != null && haloMeasuredProgress(completedSteps, totalSteps) != null)
            HaloProgressPresentation(uiText("Steps completed: %1\$d of %2\$d", completedSteps, totalSteps),
                completed = completedSteps, total = totalSteps)
        HaloFocusAction(action.label, glyph, enabled, action.onClick)
    }
}
