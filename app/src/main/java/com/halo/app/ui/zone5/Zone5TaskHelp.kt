package com.halo.app.ui.zone5

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.presence.ShellPageProjection

internal val LocalZone5HelpReadAloud = staticCompositionLocalOf<(@Composable (String) -> Unit)?> { null }

/** Explanations and guidance use live page metadata. Only an explicit action invokes its existing lease. */
@Composable
internal fun Zone5TaskHelp(projection: ShellPageProjection, kids: Boolean, onAction: (String) -> Unit, onShowControl: () -> Unit) {
    val owner = projection.owner
    val session = LocalZone5GuideSession.current
    val target = if (kids) 56.dp else 48.dp
    val description = owner.helpDescription ?: return
    Text(description, style = MaterialTheme.typography.bodyLarge)
    val nextIndex = owner.helpSteps.indexOfFirst { !it.complete }
    val step = owner.helpSteps.getOrNull(nextIndex)
    if (owner.helpSteps.isNotEmpty()) {
        Text(stringResource(if (step == null) R.string.zone5_help_done else R.string.zone5_help_steps), style = MaterialTheme.typography.titleMedium)
        owner.helpSteps.forEachIndexed { index, item ->
            Text("${if (item.complete) "✓" else "${index + 1}."} ${item.message}", style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (step != null) {
        val action = projection.actions.find { it.id == step.actionId }
        if (action != null) Button(onClick = { onAction(action.id) }, enabled = action.enabled && !owner.guidanceBlocked,
            modifier = Modifier.fillMaxWidth().heightIn(min = target)) { Text(uiText(action.title)) }
        else Text(stringResource(R.string.zone5_help_continue), style = MaterialTheme.typography.bodyMedium)
        val canShowControl = action?.enabled == true && !owner.guidanceBlocked
        if (!canShowControl) Text(owner.disabledReasonFor(step.actionId)?.let { uiText(it) }
            ?: stringResource(R.string.zone5_help_continue), style = MaterialTheme.typography.bodyMedium)
        if (session?.applies(owner) == true) Text(stringResource(R.string.zone5_help_active), style = MaterialTheme.typography.bodyMedium)
        else if (session != null) TextButton(onClick = { session.start(owner); if (canShowControl) onShowControl() },
            modifier = Modifier.heightIn(min = target)) { Text(stringResource(
                if (session.paused && session.guidedFamily == owner.routeFamily) R.string.zone5_help_resume else R.string.zone5_help_guide)) }
    }
    if (session?.guidedFamily == owner.routeFamily) {
        TextButton(onClick = { session.pause() }, enabled = !session.paused, modifier = Modifier.heightIn(min = target)) {
            Text(stringResource(if (session.paused) R.string.zone5_help_paused else R.string.zone5_help_pause))
        }
        TextButton(onClick = session::exit, modifier = Modifier.heightIn(min = target)) { Text(stringResource(R.string.zone5_help_exit)) }
    }
    if (session?.preferenceFailure == true) Text(stringResource(R.string.zone5_help_preference_failed), color = MaterialTheme.colorScheme.error)
    val accessibility = LocalContext.current.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
    var touchExploration by remember(accessibility) { mutableStateOf(accessibility?.isTouchExplorationEnabled == true) }
    DisposableEffect(accessibility) {
        val listener = android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener { touchExploration = it }
        accessibility?.addTouchExplorationStateChangeListener(listener)
        onDispose { accessibility?.removeTouchExplorationStateChangeListener(listener) }
    }
    if (!touchExploration) LocalZone5HelpReadAloud.current?.invoke(
        listOfNotNull(description, step?.message).joinToString("\n"))
}
