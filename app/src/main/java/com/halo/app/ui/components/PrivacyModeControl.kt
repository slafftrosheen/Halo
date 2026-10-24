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
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon

/** Compact shell projection; uses exactly the same durable profiles and sharing disclosure. */
@Composable fun CompactPrivacyModeControl(modifier: Modifier = Modifier) {
    val mode by Graph.privacyModes.mode.collectAsStateWithLifecycle()
    val kids by Graph.kidsPreferences.enabled.collectAsStateWithLifecycle()
    val cloudAllowed by Graph.kidsPreferences.cloudAllowed.collectAsStateWithLifecycle()
    val loaded by Graph.kidsPreferences.loadedState.collectAsStateWithLifecycle()
    val changeMode = rememberModeChangeAction()
    val label = uiText(if (mode == PrivacyMode.PRIVATE) "Private" else "Power")
    val description = uiText("AI privacy mode") + ": " + label
    CompactPrivacyModeButton(mode, description, loaded && (!kids || cloudAllowed || mode == PrivacyMode.CLOUD),
        { changeMode(if (mode == PrivacyMode.PRIVATE) PrivacyMode.CLOUD else PrivacyMode.PRIVATE) }, modifier)
}

/** Shared, persistent control; every app screen observes the same durable mode. */
@Composable fun PrivacyModeControl(modifier: Modifier = Modifier, detail: Boolean = false) {
    val mode by Graph.privacyModes.mode.collectAsStateWithLifecycle()
    val kids by Graph.kidsPreferences.enabled.collectAsStateWithLifecycle()
    val kidsCloud by Graph.kidsPreferences.cloudAllowed.collectAsStateWithLifecycle()
    val kidsLoaded by Graph.kidsPreferences.loadedState.collectAsStateWithLifecycle()
    val modeDescription = uiText("AI privacy mode")
    val changeMode = rememberModeChangeAction()
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center) {
                Text(uiText("Private"), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (mode == PrivacyMode.PRIVATE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Switch(mode == PrivacyMode.CLOUD, onCheckedChange = { cloud ->
                    changeMode(if (cloud) PrivacyMode.CLOUD else PrivacyMode.PRIVATE)
                }, enabled = kidsLoaded && (!kids || kidsCloud || mode == PrivacyMode.CLOUD),
                    modifier = Modifier.padding(horizontal = 12.dp).semantics { contentDescription = modeDescription })
                Text(androidx.compose.ui.res.stringResource(com.halo.app.R.string.mode47_full_power), modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (mode == PrivacyMode.CLOUD) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (detail) Text(uiText(if (mode == PrivacyMode.PRIVATE)
                "AI processing stays on this phone. Full Power features are off."
                else "Full Power mode can send your conversation, images, memories, and workspace to the selected provider. Provider charges may apply."),
                style = MaterialTheme.typography.bodySmall)
            val revision by Graph.privacyModes.profileChanges.collectAsStateWithLifecycle()
            val profile = remember(mode, revision) { Graph.privacyModes.profile(mode) }
            if (mode == PrivacyMode.CLOUD && !Graph.privacyModes.sharingAcknowledged) {
                TextButton(onClick = { changeMode(PrivacyMode.CLOUD) }) {
                    Text(androidx.compose.ui.res.stringResource(com.halo.app.R.string.mode47_enable_power))
                }
            }
            Text(if (mode == PrivacyMode.PRIVATE) androidx.compose.ui.res.stringResource(com.halo.app.R.string.mode47_local_route)
                else androidx.compose.ui.res.stringResource(com.halo.app.R.string.mode47_route,
                    com.halo.app.ui.screens.settings.modeProfileProviderName(profile.providerId)),
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable fun CloudModeResolution(required: Boolean = true) {
    val mode by Graph.privacyModes.mode.collectAsStateWithLifecycle()
    val changeMode = rememberModeChangeAction()
    if (mode == PrivacyMode.PRIVATE) {
        Text(uiText(if (required) "This action needs Full Power mode. Use the control beside the halo Orb." else "Private mode"), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { changeMode(PrivacyMode.CLOUD) }) {
            Text(androidx.compose.ui.res.stringResource(com.halo.app.R.string.mode47_enable_power)) }
    }
}
