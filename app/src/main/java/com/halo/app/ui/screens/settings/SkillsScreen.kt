package com.halo.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.design.HaloSpacing
import com.halo.runtime.cognition.skills.SkillRegistry
import com.halo.runtime.cognition.tools.ToolApprovalPolicy
import com.halo.sdk.runtime.skills.SkillAvailability
import com.halo.sdk.runtime.skills.SkillDescriptor
import kotlinx.coroutines.launch

@Composable
fun SkillsScreen(skillRegistry: SkillRegistry, onBack: () -> Unit) {
    val skills by skillRegistry.skills.collectAsState()
    val scope = rememberCoroutineScope()
    var selectedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(skillRegistry) { skillRegistry.refreshAvailability() }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle, skillRegistry) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) skillRegistry.refreshAvailability()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    selectedId?.let { id ->
        skills.firstOrNull { it.id.value == id }?.let { skill ->
            var policy by remember(id) { mutableStateOf(skillRegistry.approvalPolicy(skill.id)) }
            var showPolicies by remember(id) { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = { selectedId = null },
                title = { Text(skill.name) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(HaloSpacing.sm)) {
                        Text(skill.description)
                        Text("Status · ${availabilityLabel(skill.availability)}")
                        Text("Access · ${skill.requiredPermissions.joinToString().ifBlank { "No special permission" }}")
                        Text("Actions · ${skill.tools.sorted().joinToString(", ")}")
                        Text("Risk · ${skill.risk.name.replace('_', ' ').lowercase()}")
                        TextButton(onClick = { showPolicies = true }) {
                            Text("Approval · ${policy.name.replace('_', ' ').lowercase()}")
                        }
                        DropdownMenu(expanded = showPolicies,
                            onDismissRequest = { showPolicies = false }) {
                            listOf(ToolApprovalPolicy.ASK_EVERY_TIME,
                                ToolApprovalPolicy.ALWAYS_ALLOW,
                                ToolApprovalPolicy.DENY).forEach { choice ->
                                DropdownMenuItem(
                                    text = { Text(choice.name.replace('_', ' ').lowercase()) },
                                    onClick = {
                                        showPolicies = false
                                        policy = choice
                                        scope.launch { skillRegistry.setApprovalPolicy(skill.id, choice) }
                                    }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch { skillRegistry.setEnabled(skill.id, !skill.enabled) }
                    }) { Text(if (skill.enabled) "Disable" else "Enable") }
                },
                dismissButton = { TextButton(onClick = { selectedId = null }) { Text("Close") } }
            )
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = HaloSpacing.screen)) {
        HaloScreenHeader("Skills", "Actions Halo can use", navigation = onBack)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(HaloSpacing.xs)) {
            skills.groupBy { it.category }.forEach { (category, entries) ->
                item(key = category.name) {
                    Text(category.name.lowercase().replaceFirstChar { it.uppercase() },
                        modifier = Modifier.padding(top = HaloSpacing.lg, bottom = HaloSpacing.sm),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(entries, key = { it.id.value }) { skill ->
                    Surface(onClick = { selectedId = skill.id.value },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.background) {
                        Row(Modifier.padding(vertical = HaloSpacing.md),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(skill.name, style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium)
                                Text(availabilityLabel(skill.availability),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = skill.enabled,
                                onCheckedChange = { enabled ->
                                    scope.launch { skillRegistry.setEnabled(skill.id, enabled) }
                                })
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

private fun availabilityLabel(value: SkillAvailability): String = when (value) {
    SkillAvailability.Available -> "Available"
    is SkillAvailability.PartiallyAvailable -> "Some actions unavailable: ${value.unavailableTools.joinToString()}"
    SkillAvailability.Disabled -> "Disabled"
    is SkillAvailability.MissingPermission -> "Needs ${value.permission.name.lowercase()} permission"
    is SkillAvailability.MissingDependency -> "Unavailable · ${value.dependency} is missing"
    SkillAvailability.UnsupportedDevice -> "Unsupported on this device"
}
