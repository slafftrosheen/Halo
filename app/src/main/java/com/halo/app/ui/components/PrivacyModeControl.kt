package com.halo.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.ai.PrivacyMode
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.language.uiText
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shared, persistent control; every app screen observes the same durable mode. */
@Composable fun PrivacyModeControl(modifier: Modifier = Modifier, detail: Boolean = false) {
    val mode by Graph.privacyModes.mode.collectAsStateWithLifecycle()
    val kids by Graph.kidsPreferences.enabled.collectAsStateWithLifecycle()
    val kidsCloud by Graph.kidsPreferences.cloudAllowed.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val modeDescription = uiText("AI privacy mode")
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center) {
                Text(uiText("Private"), style = MaterialTheme.typography.labelLarge,
                    color = if (mode == PrivacyMode.PRIVATE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Switch(mode == PrivacyMode.CLOUD, onCheckedChange = { cloud ->
                    if (!busy) scope.launch {
                        busy = true
                        try { withContext(Dispatchers.IO) { Graph.setPrivacyMode(if (cloud) PrivacyMode.CLOUD else PrivacyMode.PRIVATE) }; failed = false }
                        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (_: Exception) { failed = true }
                        finally { busy = false }
                    }
                }, enabled = !busy && (!kids || kidsCloud || mode == PrivacyMode.CLOUD),
                    modifier = Modifier.padding(horizontal = 12.dp).semantics { contentDescription = modeDescription })
                Text(uiText("Power"), style = MaterialTheme.typography.labelLarge,
                    color = if (mode == PrivacyMode.CLOUD) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (detail) Text(uiText(if (mode == PrivacyMode.PRIVATE)
                "AI processing stays on this phone. Power features are off."
                else "Power mode can send your conversation, images, memories, and workspace to the selected provider. Provider charges may apply."),
                style = MaterialTheme.typography.bodySmall)
            if (failed) Text(uiText("Could not change privacy mode. Try again."), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable fun CloudModeResolution(required: Boolean = true) {
    val mode by Graph.privacyModes.mode.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    if (mode == PrivacyMode.PRIVATE) {
        Text(uiText(if (required) "This action needs Power mode. Use the switch at the top of Halo." else "Private mode"), style = MaterialTheme.typography.bodySmall)
        TextButton(enabled = !busy, onClick = { scope.launch {
            busy = true
            try { withContext(Dispatchers.IO) { Graph.setPrivacyMode(PrivacyMode.CLOUD) }; error = false }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { busy = false }
        } }) { Text(uiText("Switch to Power")) }
        if (error) Text(uiText("Power mode could not be enabled. Check Kids settings or try again."), color = MaterialTheme.colorScheme.error)
    }
}
