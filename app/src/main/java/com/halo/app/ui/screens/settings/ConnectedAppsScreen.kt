package com.halo.app.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.language.uiText
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.design.HaloShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android-owned app actions, without raw server addresses or credentials. */
@Composable
fun ConnectedAppsScreen(onBack: () -> Unit, onActions: () -> Unit) {
    val snapshot by ManualDependencyGraph.appFunctionCapabilityIndex.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val packages = remember(snapshot.capabilities) { snapshot.capabilities.groupBy { it.packageName } }
    val labels by produceState<Map<String, String>>(emptyMap(), packages.keys) {
        value = withContext(Dispatchers.IO) {
            packages.keys.associateWith { name ->
                runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(name, 0)).toString() }
                    .getOrDefault("An app on this phone")
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { HaloScreenHeader(uiText("Apps & services"), uiText("Actions your apps make available to halo."), navigation = onBack) }
        if (packages.isEmpty()) item {
            Text(uiText("No app actions are available yet. You can still ask halo questions and work in Notebook."),
                style = MaterialTheme.typography.bodyLarge)
        }
        packages.forEach { (name, capabilities) -> item(key = name) {
            Surface(shape = HaloShapes.card, color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(labels[name] ?: uiText("An app on this phone"), style = MaterialTheme.typography.titleMedium)
                    Text(uiText("%1\$d available actions", capabilities.size), style = MaterialTheme.typography.bodyMedium)
                }
            }
        } }
        if (packages.isNotEmpty()) item {
            Button(onClick = onActions, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(uiText("See available actions")) }
        }
    }
}
