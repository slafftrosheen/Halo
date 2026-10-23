package com.halo.app.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** A discovery sheet. Only a deliberate Use action can open an editor or prepare intent. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun HomeToolsSheet(snapshot: HomeToolCatalogSnapshot, voiceModeActive: Boolean,
    onRefresh: () -> Unit, resolve: suspend (String) -> HomeToolAction?,
    onAction: (HomeToolAction) -> Unit, onRecovery: (HomeToolRecovery) -> Unit,
    onDismiss: () -> Unit, initialGroup: HomeToolGroup = HomeToolGroup.CREATE, initialEntry: String? = null) {
    var groupName by rememberSaveable(initialGroup, initialEntry) { mutableStateOf(initialGroup.name) }
    var selectedId by rememberSaveable(initialGroup, initialEntry) { mutableStateOf(initialEntry) }
    var using by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val group = HomeToolGroup.entries.firstOrNull { it.name == groupName } ?: HomeToolGroup.CREATE
    val selected = snapshot.entries.firstOrNull { it.descriptor.id == selectedId }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var useJob by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) useJob?.cancel()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); useJob?.cancel() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        // A native dialog has its own Compose owner. Preserve the app's enlarged text preference.
        CompositionLocalProvider(LocalDensity provides density) {
        Column(Modifier.fillMaxWidth().heightIn(max = 680.dp)
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.home_tools_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onRefresh, enabled = !snapshot.loading && !using, modifier = Modifier.size(48.dp)) {
                    Icon(androidx.compose.ui.res.painterResource(R.drawable.halo_ic_retry), stringResource(R.string.home_tools_refresh))
                }
            }
            if (snapshot.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (selected == null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HomeToolGroup.entries.forEach { item ->
                        FilterChip(selected = item == group, onClick = { groupName = item.name },
                            label = { Text(stringResource(item.title)) }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
                if (snapshot.entries.isEmpty() && !snapshot.loading) Text(stringResource(R.string.home_tools_unavailable))
                snapshot.entries.filter { it.descriptor.group == group }.forEach { entry ->
                    ListItem(
                        headlineContent = { Text(stringResource(entry.descriptor.title)) },
                        supportingContent = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(entry.descriptor.description), maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(stringResource(entry.status.label), style = MaterialTheme.typography.labelLarge,
                                color = if (entry.status == HomeToolStatus.READY) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                        } },
                        leadingContent = { HaloIcon(entry.descriptor.icon, null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable {
                            selectedId = entry.descriptor.id; failed = false
                        },
                    )
                    HorizontalDivider()
                }
            } else {
                TextButton(onClick = { selectedId = null; failed = false }, enabled = !using,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.home_tools_back)) }
                Text(stringResource(selected.descriptor.title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(selected.status.label), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(selected.descriptor.description))
                if (selected.reason.text != selected.descriptor.description)
                    Text(stringResource(selected.reason.text), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (selected.descriptor.needsAnswers) snapshot.answerProviderName?.let { provider ->
                    Text(stringResource(if (snapshot.answerOffDevice) R.string.home_tools_remote_route else R.string.home_tools_local_route,
                        provider), style = MaterialTheme.typography.bodySmall)
                }
                if (selected.descriptor.action == HomeToolAction.WEB)
                    Text(stringResource(R.string.home_tools_web_route), style = MaterialTheme.typography.bodySmall)
                if (selected.canUse) {
                    Text(stringResource(R.string.home_tools_intent_notice), style = MaterialTheme.typography.bodySmall)
                    Button(enabled = !using && !snapshot.loading, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        onClick = {
                            failed = false
                            val requestedAction = if (selected.descriptor.action == HomeToolAction.VOICE_MODE) {
                                if (voiceModeActive) HomeToolAction.END_VOICE else HomeToolAction.START_VOICE
                            } else selected.descriptor.action
                            useJob = scope.launch {
                                using = true
                                try {
                                    val action = resolve(selected.descriptor.id)
                                    coroutineContext.ensureActive()
                                    if (action == selected.descriptor.action && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
                                        onAction(requestedAction) else failed = true
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) { failed = true }
                                finally { using = false }
                            }
                        }) {
                        Text(stringResource(homeToolActionLabel(selected.descriptor.action, voiceModeActive)))
                    }
                } else if (selected.recovery != HomeToolRecovery.NONE) {
                    OutlinedButton(onClick = { onRecovery(selected.recovery) }, enabled = !using,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.home_tools_setup)) }
                }
                if (failed) Text(stringResource(R.string.home_tools_changed), color = MaterialTheme.colorScheme.error)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.home_tools_close))
            }
            Spacer(Modifier.height(12.dp))
        }
        }
    }
}

internal fun homeToolActionLabel(action: HomeToolAction, voiceModeActive: Boolean): Int = when (action) {
    HomeToolAction.NEW_NOTE -> R.string.home_tools_create_note
    HomeToolAction.ATTACH -> R.string.home_tools_attach
    HomeToolAction.WEB -> R.string.home_tools_open_search
    HomeToolAction.RESEARCH_DRAFT, HomeToolAction.MAPS_DRAFT -> R.string.home_tools_prepare
    HomeToolAction.FILE_TOOLS -> R.string.file52_title
    HomeToolAction.STUDIO -> R.string.studio_open
    HomeToolAction.NOTEBOOK -> R.string.home_tools_open_notebook
    HomeToolAction.STUDY -> R.string.home_tools_open_study
    HomeToolAction.CONNECTIONS -> R.string.home_tools_open_connections
    HomeToolAction.TIMER -> R.string.home_tools_timer
    HomeToolAction.OPEN_APP -> R.string.home_tools_app
    HomeToolAction.VOICE_MODE -> if (voiceModeActive) R.string.home_tools_end_voice else R.string.home_tools_start_voice
    HomeToolAction.START_VOICE -> R.string.home_tools_start_voice
    HomeToolAction.END_VOICE -> R.string.home_tools_end_voice
    HomeToolAction.INBOX -> R.string.home_tools_open_inbox
    HomeToolAction.REPEAT_VOICE -> R.string.home_tool_repeat
    HomeToolAction.PLANNED -> R.string.home_tools_close
}
