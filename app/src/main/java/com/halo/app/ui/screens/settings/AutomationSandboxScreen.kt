package com.halo.app.ui.screens.settings

import com.halo.app.language.uiText
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.ui.workspace.*
import com.halo.runtime.cognition.routine.*
import com.halo.runtime.cognition.watch.ContinuationWatchMatcher
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/** Synthetic events are matcher previews only; never submitted to source flows or executor. */
@Composable fun AutomationSandboxScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<String>>(emptyList()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HaloWorkspaceHeader("Automation sandbox", "Internal preview · no actions or delivery", onBack)
        OutlinedTextField(target, { target = it.take(256) }, label = { Text(uiText("Device ID or package (developer preview)")) })
        listOf("Battery crosses 50%" to AutomationSignalKind.BATTERY_CHANGED,
            "Network available" to AutomationSignalKind.NETWORK_AVAILABLE, "Device connected" to AutomationSignalKind.DEVICE_CONNECTED,
            "App foregrounded" to AutomationSignalKind.APP_FOREGROUNDED, "Notification posted" to AutomationSignalKind.NOTIFICATION_POSTED).forEach { (label, kind) ->
            HaloWorkspaceButton(label, onClick = { scope.launch {
                val attributes = when(kind) {
                    AutomationSignalKind.BATTERY_CHANGED -> mapOf("previous" to AutomationValue.Integer(49), "percent" to AutomationValue.Integer(51))
                    AutomationSignalKind.DEVICE_CONNECTED -> mapOf("device" to AutomationValue.Text(target))
                    AutomationSignalKind.APP_FOREGROUNDED, AutomationSignalKind.NOTIFICATION_POSTED -> mapOf("package" to AutomationValue.Text(target))
                    else -> emptyMap()
                }
                val signal = AutomationSignal(UUID.randomUUID().toString(), kind, Instant.now(), "sandbox", AutomationTrust.INTERNAL_TEST, attributes)
                val routineResults = ManualDependencyGraph.automationRuntime.preview(signal)
                val activeWatches = ManualDependencyGraph.followUpRepository.getAllActiveWatches()
                val watchResults = activeWatches.filter { ContinuationWatchMatcher.matches(it.condition, signal) }.map {
                    "Follow-up watch ${it.id.value}: matches condition ${it.condition::class.simpleName}"
                }
                results = routineResults + watchResults
            } })
        }
        results.forEach { Text(it) }
    }
}
