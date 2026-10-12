package com.halo.app.ui.screens.settings

import com.halo.app.language.uiText

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.state.RuntimeUiFormatters
import com.halo.app.ui.state.RuntimeUiProjection
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.runtime.resource.*
import kotlinx.coroutines.flow.flowOf

@Composable
fun RuntimeScreen(
    projection: RuntimeUiProjection,
    catalogueSource: CapabilityCatalogueSource?,
    onBack: () -> Unit,
    onOpenSystemCheck: (() -> Unit)? = null,
) {
    val taskSummary by projection.taskSummary.collectAsStateWithLifecycle()
    val resourceSummary by projection.resourceSummary.collectAsStateWithLifecycle()
    val capabilitySummary by projection.capabilitySummary.collectAsStateWithLifecycle()
    val capabilitySnapshot by (catalogueSource?.snapshot ?: remember { flowOf(CapabilityCatalogueSnapshot(emptyList(), 0L)) }).collectAsStateWithLifecycle(initialValue = CapabilityCatalogueSnapshot(emptyList(), 0L))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        HaloScreenHeader(
            title = uiText("Runtime Status"),
            subtitle = uiText("Tasks, capabilities & resources"),
            navigation = onBack
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Section 1: Overview
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(uiText("System Overview"), style = MaterialTheme.typography.titleMedium)
                            if (onOpenSystemCheck != null) {
                                Button(
                                    onClick = onOpenSystemCheck,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text(uiText("System Check"))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Active Tasks: ${taskSummary.activeCount}")
                            Text("Waiting: ${taskSummary.waitingCount}")
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Capabilities: ${capabilitySummary.availableCount} available")
                            Text("Setup needed: ${capabilitySummary.needSetupCount}")
                        }
                        if (taskSummary.statusText != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Status: ${taskSummary.statusText}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // Section 2: Active Resources
            item {
                Text(uiText("Active Resources"), style = MaterialTheme.typography.titleMedium)
            }
            if (resourceSummary.activeLeases.isEmpty()) {
                item {
                    Text(uiText("No active resource leases"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(resourceSummary.activeLeases) { lease ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                lease.claims.joinToString { RuntimeUiFormatters.formatResource(it.key.resourceId) },
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Endpoint: ${lease.claims.firstOrNull()?.key?.endpointId?.value ?: "unknown"} · Priority: ${lease.priority.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Section 3: Capabilities by Category
            item {
                Text(uiText("Capabilities"), style = MaterialTheme.typography.titleMedium)
            }
            items(capabilitySnapshot.capabilities) { cap ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(RuntimeUiFormatters.formatCapability(cap.id), style = MaterialTheme.typography.bodyMedium)
                        Text(cap.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Badge(
                        containerColor = if (cap.availability == CapabilityAvailability.AVAILABLE)
                            MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            if (cap.availability == CapabilityAvailability.AVAILABLE) "Available" else "Setup Needed",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
