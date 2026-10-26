package com.halo.app.ui.screens.settings

import com.halo.app.language.uiText
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.halo.app.ui.design.HaloScreenHeader
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.ui.online.OnlineCostControls
import com.halo.runtime.observer.InferenceLedger

@Composable
fun IntelligenceUsageScreen(ledger: InferenceLedger, onBack: () -> Unit, onDiagnostics: () -> Unit) {
    SettingsChildZone("Online costs", onBack, listOf(settingsChildLink("onDiagnostics", "Check-up", onDiagnostics)))
    val inference by ledger.aggregate.collectAsStateWithLifecycle()
    val tools by ledger.toolAggregate.collectAsStateWithLifecycle()
    var details by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(com.halo.app.ui.design.HaloSpacing.screen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!com.halo.app.ui.presence.LocalShellHaloOrb.current) HaloScreenHeader(stringResource(R.string.online_costs), navigation = onBack)
        OnlineCostControls()
        TextButton(onClick = { details = !details }) { Text(uiText("Usage and diagnostics")) }
        if (details) {
            Text(uiText("AI requests: %1\$d · Failures: %2\$d", inference.requests, inference.failures))
            Text(uiText("Reported tokens: %1\$d input · %2\$d output", inference.inputTokens, inference.outputTokens))
            Text(uiText("Tool outcomes: %1\$d · Failures: %2\$d · Unknown: %3\$d", tools.outcomes, tools.failures, tools.unknownOutcomes))
            Text(uiText("Provider billing is account dependent; token counts are not an invoice."))
            TextButton(onClick = onDiagnostics) { Text(uiText("Open System Check")) }
        }
    }
}
