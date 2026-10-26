package com.halo.app.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.language.uiText
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.components.HaloActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.halo.app.data.settings.AppActionAccessStore

/** Android-owned app actions, without raw server addresses or credentials. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectedAppsScreen(onBack: () -> Unit, onActions: () -> Unit) {
    SettingsChildZone("Apps & services", onBack, listOf(settingsChildLink("onActions", "Skills", onActions)))
    val snapshot by ManualDependencyGraph.appFunctionCapabilityIndex.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val access = ManualDependencyGraph.appActionAccess
    val disabled by access.disabled.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf<Set<String>>(emptySet()) }
    var accessError by remember { mutableStateOf(false) }
    fun changeAccess(packageName: String, action: String, allowed: Boolean) {
        val key = AppActionAccessStore.key(packageName, action)
        if (key in saving) return
        saving = saving + key
        accessError = false
        scope.launch {
            try { access.setAllowed(packageName, action, allowed) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { accessError = true }
            finally { saving = saving - key }
        }
    }
    var expandedPackage by rememberSaveable { mutableStateOf<String?>(null) }
    val packages = remember(snapshot.capabilities) { snapshot.capabilities.groupBy { it.packageName } }
    val labels by produceState<Map<String, String>>(emptyMap(), packages.keys) {
        value = withContext(Dispatchers.IO) {
            packages.keys.associateWith { name ->
                runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(name, 0)).toString() }
                    .getOrDefault("")
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { if (!com.halo.app.ui.presence.LocalShellHaloOrb.current) HaloScreenHeader(uiText("Apps & services"), uiText("Actions your apps make available to halo."), navigation = onBack) }
        item { Text(uiText("Available actions still need permission. Connecting an app does not approve its actions."),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (accessError) item { Text(uiText("Could not save that change. Try again."), color = MaterialTheme.colorScheme.error) }
        if (packages.isEmpty()) item {
            Text(uiText(when (snapshot.availability) {
                com.halo.runtime.cognition.apps.AppFunctionAvailability.PERMISSION_DENIED -> "App action access is blocked. Review access to continue."
                com.halo.runtime.cognition.apps.AppFunctionAvailability.ERROR -> "App actions could not be read. Open the app and try again."
                else -> "No app actions are available yet. You can still ask halo questions and work in Notebook."
            }),
                style = MaterialTheme.typography.bodyLarge)
        }
        packages.forEach { (name, capabilities) -> item(key = name) {
            Surface(onClick = { expandedPackage = if (expandedPackage == name) null else name },
                shape = HaloShapes.card, color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(labels[name]?.takeIf { it.isNotBlank() } ?: uiText("An app on this phone"), style = MaterialTheme.typography.titleMedium)
                    Text(uiText("%1\$d available actions", capabilities.filter { it.enabled &&
                        AppActionAccessStore.key(name, it.action.value) !in disabled && AppActionAccessStore.key(name, "*") !in disabled
                    }.distinctBy { it.action }.size), style = MaterialTheme.typography.bodyMedium)
                    Text(uiText("Review actions"),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

                }
            }
        } }
        item {
            HaloActionButton(onClick = onActions, modifier = Modifier.fillMaxWidth()) { Text(uiText("Manage action access")) }
        }
    }
    expandedPackage?.let { name ->
        val actions = packages[name].orEmpty().groupBy { it.action }
        ModalBottomSheet(onDismissRequest = { expandedPackage = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { com.halo.app.ui.glass.components.HaloGlassSheetHandle(Modifier.padding(top = 10.dp, bottom = 16.dp)) }) {
            LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Text(labels[name]?.takeIf { it.isNotBlank() } ?: uiText("An app on this phone"),
                        style = MaterialTheme.typography.headlineSmall)
                }
                if (actions.isEmpty()) item { Text(uiText("Not available right now")) }
                if (accessError) item { Text(uiText("Could not save that change. Try again."), color = MaterialTheme.colorScheme.error) }
                item {
                    val key = AppActionAccessStore.key(name, "*")
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(uiText("Allow actions from this app"), Modifier.weight(1f))
                        Switch(checked = key !in disabled, enabled = key !in saving,
                            onCheckedChange = { changeAccess(name, "*", it) },
                            modifier = Modifier.semantics { contentDescription = labels[name].orEmpty() })
                    }
                }
                actions.entries.forEachIndexed { actionIndex, (action, routes) -> item(key = action.value) {
                    Surface(shape = HaloShapes.card, color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val actionTitle = when (action.value) {
                                "archive_current" -> "Archive the current item"
                                "mark_unread" -> "Mark as unread"
                                "media_pause" -> "Pause playback"
                                "media_play" -> "Start playback"
                                "search" -> "Search in this app"
                                else -> null
                            }
                            Text(actionTitle?.let { uiText(it) } ?: uiText("App action %1\$d", actionIndex + 1),
                                style = MaterialTheme.typography.titleSmall)
                            Text(uiText(if (routes.any { it.enabled }) "Always asks you first" else "Not available right now"),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val key = AppActionAccessStore.key(name, action.value)
                            val label = uiText("Allow this action")
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(label, Modifier.weight(1f))
                                Switch(checked = key !in disabled,
                                    enabled = key !in saving && AppActionAccessStore.key(name, "*") !in disabled && routes.any { it.enabled },
                                    onCheckedChange = { changeAccess(name, action.value, it) },
                                    modifier = Modifier.semantics { contentDescription = label })
                            }
                        }
                    }
                } }
                item {
                    HaloActionButton(onClick = { expandedPackage = null; onActions() }, modifier = Modifier.fillMaxWidth()) {
                        Text(uiText("Manage action access"))
                    }
                }
            }
        }
    }

}
