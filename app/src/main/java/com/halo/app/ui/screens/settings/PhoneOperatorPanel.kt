package com.halo.app.ui.screens.settings

import com.halo.app.language.uiText

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.host.android.accessibility.HaloAccessibilityService
import com.halo.runtime.cognition.ui.UiOperatorDiagnostics
import com.halo.runtime.cognition.ui.safeTargetLabel
import kotlinx.coroutines.launch

/** Passive until the user explicitly requests a capture. Never executes an action. */
@Composable
fun PhoneOperatorPanel(observationControls: Boolean = false, onOpenSandbox: (() -> Unit)? = null,
    readingEnabled: Boolean = true, actionPolicy: String = "Ask before screen actions",
    technical: Boolean = false) {
    val metrics by UiOperatorDiagnostics.metrics.collectAsStateWithLifecycle()
    val connected by UiOperatorDiagnostics.accessibilityConnected.collectAsStateWithLifecycle()
    val receipt by UiOperatorDiagnostics.lastReceipt.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<String?>(null) }
    var previewVisible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val service = HaloAccessibilityService.instance
    val snapshot = service?.latestSemanticSnapshot
    val current = snapshot?.takeIf { it.uiRevision == service?.currentActionableRevision && System.currentTimeMillis() - it.capturedAtMs <= 30_000 }
    val appLabel = snapshot?.packageName?.let { pkg -> runCatching {
        service?.packageManager?.let { manager -> manager.getApplicationLabel(manager.getApplicationInfo(pkg, 0)).toString().take(80) }
    }.getOrNull() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!technical) {
            Text(uiText("Act in other apps"), style = MaterialTheme.typography.titleMedium)
            Text(uiText(when {
                !readingEnabled -> "Screen access is off for this skill"
                !connected || service == null -> "Turn on accessibility access in Android settings"
                else -> "halo reads the screen only when you ask"
            }))
            Text(uiText(actionPolicy))
            Text(uiText("Passwords and permission dialogs stay under your control."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
        Text(uiText("Phone operator"), style = MaterialTheme.typography.titleMedium)
        Text("Accessibility · ${if (connected && service != null) "Connected" else "Enable in Android settings"}")
        Text("Screen reading · ${if (!readingEnabled) "Disabled by skill policy" else if (service == null) "Unavailable" else "On demand"}")
        Text("Screen actions · $actionPolicy")
        Text(uiText("Sensitive packages · Blocked · Passwords and permission dialogs · Manual completion"))
        Text(uiText("Coordinate input and privileged control · Unsupported"))
        Text("Last observed app · ${appLabel ?: "No observation"}")
        Text("Inspectability · ${current?.inspectability?.name?.lowercase()?.replace('_', ' ') ?: "Fresh observation required"}")
        Text("Last transaction · ${receipt?.let { "${it.operation.name.lowercase().replace('_', ' ')} · ${it.actionTruth.name.lowercase()} · ${it.changeKind.replace('_', ' ')}" } ?: "None"}")
        Text("Reads ${metrics.screenReadCount} · Dispatched ${metrics.actionDispatchCount} · Verified ${metrics.confirmedCount}", style = MaterialTheme.typography.bodySmall)
        if (observationControls) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = service != null && !busy && readingEnabled, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val captured = service?.captureSnapshot()
                            preview = if (captured == null) "No readable screen. Complete blocked or unsupported steps yourself." else
                                "Revision ${captured.uiRevision} · ${captured.nodes.size} nodes · ${captured.inspectability.name.lowercase()}\n" +
                                    captured.nodes.take(30).filter { !it.sensitive }.joinToString("\n") {
                                        "${it.semanticType.name.lowercase()} · ${it.safeTargetLabel().orEmpty()} · enabled=${it.enabled}" }
                            previewVisible = false
                        } finally { busy = false }
                    }
                }) { Text(uiText("Read current screen")) }
                TextButton(enabled = preview != null, onClick = { previewVisible = !previewVisible }) { Text(uiText("Preview snapshot")) }
            }
            preview?.let { if (previewVisible) Text(it, style = MaterialTheme.typography.bodySmall) else Text(uiText("Observation ready. Tap Preview snapshot."), style = MaterialTheme.typography.bodySmall) }
        }
        onOpenSandbox?.let { open -> TextButton(onClick = open) { Text(uiText("Open phone-control sandbox")) } }
        }
    }
}
