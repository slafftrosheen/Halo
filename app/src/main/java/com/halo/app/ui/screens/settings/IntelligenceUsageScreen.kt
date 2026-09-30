package com.halo.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.runtime.observer.InferenceLedger

@Composable
fun IntelligenceUsageScreen(ledger: InferenceLedger, onBack: () -> Unit, onDiagnostics: () -> Unit) {
    val inference by ledger.aggregate.collectAsState()
    val tools by ledger.toolAggregate.collectAsState()
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Usage and diagnostics", style = MaterialTheme.typography.headlineMedium)
        Text("AI requests: ${inference.requests} · Failures: ${inference.failures}")
        Text("Reported tokens: ${inference.inputTokens} input · ${inference.outputTokens} output")
        Text("Tool outcomes: ${tools.outcomes} · Failures: ${tools.failures} · Unknown: ${tools.unknownOutcomes}")
        Text("Provider billing is account dependent; token counts are not an invoice.")
        TextButton(onClick = onDiagnostics) { Text("Open System Check") }
    }
}
