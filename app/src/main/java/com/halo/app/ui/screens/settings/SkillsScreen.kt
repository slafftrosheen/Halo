package com.halo.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.design.HaloSpacing
import com.halo.runtime.cognition.skills.SkillRegistry
import com.halo.runtime.cognition.tools.CapabilityCatalogueSnapshot
import com.halo.runtime.cognition.tools.CapabilityCatalogueSource
import com.halo.runtime.cognition.tools.ToolApprovalPolicy
import com.halo.runtime.cognition.tools.ToolAvailability
import com.halo.runtime.cognition.tools.ToolAvailabilityResolver
import com.halo.runtime.cognition.tools.ToolDescriptor
import com.halo.runtime.cognition.tools.ToolRegistry
import com.halo.runtime.cognition.tools.ToolRiskClass
import com.halo.sdk.runtime.skills.SkillAvailability
import com.halo.sdk.runtime.skills.SkillDescriptor
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Skills is now a projection of the Runtime tool/capability fabric rather than
 * a second source of availability truth.
 */
@Composable
fun SkillsScreen(
    skillRegistry: SkillRegistry,
    onBack: () -> Unit,
    toolRegistry: ToolRegistry? = null,
    availabilityResolver: ToolAvailabilityResolver? = null,
    capabilityCatalogue: CapabilityCatalogueSource? = null,
) {
    val skills by skillRegistry.skills.collectAsState()
    // Recompose whenever live host/device capability availability changes.
    val capabilitySnapshot by (
        capabilityCatalogue?.snapshot
            ?: flowOf(CapabilityCatalogueSnapshot(emptyList(), 0L))
        ).collectAsState(initial = CapabilityCatalogueSnapshot(emptyList(), 0L))
    capabilitySnapshot.revision // explicit observation; availabilityResolver reads the same source.

    val scope = rememberCoroutineScope()
    var selectedId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(skillRegistry) { skillRegistry.refreshAvailability() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, skillRegistry) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) skillRegistry.refreshAvailability()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    selectedId?.let { id ->
        skills.firstOrNull { it.id.value == id }?.let { skill ->
            var policy by remember(id) { mutableStateOf(skillRegistry.approvalPolicy(skill.id)) }
            var showPolicies by remember(id) { mutableStateOf(false) }
            val descriptors = skill.tools.mapNotNull { toolRegistry?.descriptor(it) }

            AlertDialog(
                onDismissRequest = { selectedId = null },
                title = { Text(skill.name) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(HaloSpacing.sm)) {
                        Text(skill.description)
                        Text("Status · ${skillRuntimeAvailability(skill, descriptors, availabilityResolver)}")
                        Text("Access · ${skill.requiredPermissions.joinToString().ifBlank { "No special permission" }}")

                        if (descriptors.isNotEmpty()) {
                            Text(
                                "Actions",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            descriptors.forEach { descriptor ->
                                val availability = availabilityResolver?.checkAvailability(descriptor)
                                    ?: ToolAvailability.Available
                                Column(Modifier.padding(vertical = HaloSpacing.xs)) {
                                    Text(
                                        descriptor.presentation?.title ?: descriptor.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        toolAvailabilityCopy(availability),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (availability is ToolAvailability.Available) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        },
                                    )
                                    Text(
                                        riskCopy(descriptor),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        } else {
                            Text("Actions · ${skill.tools.size}")
                        }

                        TextButton(onClick = { showPolicies = true }) {
                            Text("Approval · ${policy.name.replace('_', ' ').lowercase()}")
                        }
                        DropdownMenu(
                            expanded = showPolicies,
                            onDismissRequest = { showPolicies = false },
                        ) {
                            listOf(
                                ToolApprovalPolicy.ASK_EVERY_TIME,
                                ToolApprovalPolicy.ALWAYS_ALLOW,
                                ToolApprovalPolicy.DENY,
                            ).forEach { choice ->
                                DropdownMenuItem(
                                    text = { Text(choice.name.replace('_', ' ').lowercase()) },
                                    onClick = {
                                        showPolicies = false
                                        policy = choice
                                        scope.launch { skillRegistry.setApprovalPolicy(skill.id, choice) }
                                    },
                                )
                            }
                        }

                        if (descriptors.any {
                                it.security.confirmation == com.halo.runtime.cognition.tools.ConfirmationPolicy.ALWAYS
                            }) {
                            Text(
                                "Some sensitive actions always require confirmation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch { skillRegistry.setEnabled(skill.id, !skill.enabled) }
                    }) {
                        Text(if (skill.enabled) "Disable" else "Enable")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedId = null }) { Text("Close") }
                },
            )
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = HaloSpacing.screen)) {
        HaloScreenHeader("Skills", "Actions Halo can use", navigation = onBack)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(HaloSpacing.xs)) {
            skills.groupBy { it.category }.forEach { (category, entries) ->
                item(key = category.name) {
                    Text(
                        category.name.lowercase().replaceFirstChar { it.uppercase() },
                        modifier = Modifier.padding(top = HaloSpacing.lg, bottom = HaloSpacing.sm),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                items(entries, key = { it.id.value }) { skill ->
                    val descriptors = skill.tools.mapNotNull { toolRegistry?.descriptor(it) }
                    Surface(
                        onClick = { selectedId = skill.id.value },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        Row(
                            Modifier.padding(vertical = HaloSpacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    skill.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    skillRuntimeAvailability(skill, descriptors, availabilityResolver),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = skill.enabled,
                                onCheckedChange = { enabled ->
                                    scope.launch { skillRegistry.setEnabled(skill.id, enabled) }
                                },
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

private fun skillRuntimeAvailability(
    skill: SkillDescriptor,
    descriptors: List<ToolDescriptor>,
    resolver: ToolAvailabilityResolver?,
): String {
    if (!skill.enabled) return "Disabled"
    if (descriptors.isNotEmpty() && resolver != null) {
        val available = descriptors.count { resolver.isAvailable(it) }
        return when {
            available == descriptors.size -> "$available of ${descriptors.size} actions available"
            available == 0 -> descriptors.firstNotNullOfOrNull {
                (resolver.checkAvailability(it) as? ToolAvailability.MissingCapabilities)
                    ?.missing?.joinToString { id -> capabilityLabel(id.value) }
            }?.let { "Needs $it" } ?: "No actions currently available"
            else -> "$available of ${descriptors.size} actions available"
        }
    }
    return availabilityLabel(skill.availability)
}

private fun toolAvailabilityCopy(value: ToolAvailability): String = when (value) {
    ToolAvailability.Available -> "Available"
    is ToolAvailability.Disabled -> value.reason
    is ToolAvailability.Degraded -> value.reason
    is ToolAvailability.MissingCapabilities ->
        "Needs " + value.missing.joinToString { capabilityLabel(it.value) }
}

private fun riskCopy(descriptor: ToolDescriptor): String = when (descriptor.security.risk) {
    ToolRiskClass.READ_ONLY -> "Reads information"
    ToolRiskClass.REVERSIBLE -> "Can change state and is intended to be reversible"
    ToolRiskClass.EXTERNAL_SIDE_EFFECT -> "Affects another app, service, or device"
    ToolRiskClass.SENSITIVE -> "Uses sensitive information"
    ToolRiskClass.DESTRUCTIVE -> "Can make a non-reversible change"
}

private fun capabilityLabel(id: String): String = when (id) {
    "host.contacts.read" -> "contacts access"
    "host.notification.read" -> "notification access"
    "host.camera.still", "device.camera.still" -> "a camera"
    "host.audio.input", "device.audio.input" -> "a microphone"
    "host.audio.output", "device.audio.output" -> "audio output"
    "device.media.transfer" -> "a connected media-transfer device"
    "device.mobility" -> "a connected mobility device"
    else -> id.substringAfterLast('.').replace('_', ' ')
}

private fun availabilityLabel(value: SkillAvailability): String = when (value) {
    SkillAvailability.Available -> "Available"
    is SkillAvailability.PartiallyAvailable -> "Some actions unavailable: ${value.unavailableTools.joinToString()}"
    SkillAvailability.Disabled -> "Disabled"
    is SkillAvailability.MissingPermission -> "Needs ${value.permission.name.lowercase()} permission"
    is SkillAvailability.MissingDependency -> "Unavailable · ${value.dependency} is missing"
    SkillAvailability.UnsupportedDevice -> "Unsupported on this device"
}
