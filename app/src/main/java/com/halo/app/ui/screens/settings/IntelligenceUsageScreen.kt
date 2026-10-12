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
import androidx.compose.ui.unit.dp
import com.halo.runtime.observer.InferenceLedger

@Composable
fun IntelligenceUsageScreen(ledger: InferenceLedger, onBack: () -> Unit, onDiagnostics: () -> Unit) {
    val inference by ledger.aggregate.collectAsStateWithLifecycle()
    val tools by ledger.toolAggregate.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HaloScreenHeader(uiText("Usage and diagnostics"), navigation = onBack)
        Text(uiText("AI requests: %1\$d · Failures: %2\$d", inference.requests, inference.failures))
        Text(uiText("Reported tokens: %1\$d input · %2\$d output", inference.inputTokens, inference.outputTokens))
        Text(uiText("Tool outcomes: %1\$d · Failures: %2\$d · Unknown: %3\$d", tools.outcomes, tools.failures, tools.unknownOutcomes))
        Text(uiText("Provider billing is account dependent; token counts are not an invoice."))
        TextButton(onClick = onDiagnostics) { Text(uiText("Open System Check")) }
    }
}
