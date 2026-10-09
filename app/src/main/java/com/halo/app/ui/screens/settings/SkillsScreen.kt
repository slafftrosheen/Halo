package com.halo.app.ui.screens.settings

import com.halo.app.language.uiText

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.design.HaloSpacing
import com.halo.app.skills.ParsedSkillPackage
import com.halo.app.skills.SkillMarkdownPackageParser
import com.halo.runtime.cognition.skills.SkillRegistry
import com.halo.runtime.cognition.tools.CapabilityCatalogueSnapshot
import com.halo.runtime.cognition.tools.CapabilityCatalogueSource
import com.halo.runtime.cognition.tools.ToolApprovalPolicy
import com.halo.runtime.cognition.tools.ToolAvailability
import com.halo.runtime.cognition.tools.ToolAvailabilityResolver
import com.halo.runtime.cognition.tools.ToolDescriptor
import com.halo.runtime.cognition.tools.ToolRegistry
import com.halo.runtime.cognition.tools.ToolRiskClass
import com.halo.sdk.runtime.skills.HostPermission
import com.halo.sdk.runtime.skills.SkillAvailability
import com.halo.sdk.runtime.skills.SkillDescriptor
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * Skills is now a projection of the Runtime tool/capability fabric rather than
 * a second source of availability truth.
 */
@Composable
fun SkillsScreen(
    skillRegistry: SkillRegistry,
    onBack: () -> Unit,
    onIntegrations: () -> Unit = {},
    toolRegistry: ToolRegistry? = null,
    availabilityResolver: ToolAvailabilityResolver? = null,
    capabilityCatalogue: CapabilityCatalogueSource? = null,
    onInstallCustomSkill: ((String, ByteArray) -> Result<Unit>)? = null,
    /** MINING-P1-001 capability overview. Optional: the screen works without it. */
    capabilityStatus: CapabilityStatusViewModel? = null,
    executionService: com.halo.runtime.cognition.skills.SkillExecutionService? = null,
    appCapabilities: com.halo.runtime.cognition.apps.AppFunctionCapabilityIndex? = null,
    refreshAppCapabilities: (suspend () -> Unit)? = null,
    screenControlAvailable: () -> Boolean = { false },
    providerRoleCheck: (suspend (SkillDescriptor) -> String?)? = null,
    onRequestSkillAccess: ((Set<HostPermission>, Set<String>) -> Unit)? = null,
    instructionSource: com.halo.runtime.cognition.skills.SkillInstructionSource? = null,
) {
    LaunchedEffect(appCapabilities) { refreshAppCapabilities?.let { refresh -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { refresh() } } }
    val appSnapshot by (appCapabilities?.state ?: kotlinx.coroutines.flow.MutableStateFlow(com.halo.runtime.cognition.apps.AppCapabilitySnapshot())).collectAsStateWithLifecycle()
    val skills by skillRegistry.skills.collectAsStateWithLifecycle()
    // Recompose whenever live host/device capability availability changes.
    val capabilitySnapshot by (
        capabilityCatalogue?.snapshot
            ?: flowOf(CapabilityCatalogueSnapshot(emptyList(), 0L))
        ).collectAsStateWithLifecycle(initialValue = CapabilityCatalogueSnapshot(emptyList(), 0L))
    capabilitySnapshot.revision // explicit observation; availabilityResolver reads the same source.

    var checks by remember { mutableStateOf<Map<String, SkillCheckSnapshot>>(emptyMap()) }
    var checkRevision by remember { mutableStateOf(0) }
    var savingGlobalPolicy by remember { mutableStateOf(false) }
    var globalPolicyMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(skills, capabilitySnapshot.revision, checkRevision) {
        checks = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            skills.associate { skill ->
                val descriptors = skill.tools.mapNotNull { toolRegistry?.descriptor(it) }
                val availability = descriptors.associate { it.name to (availabilityResolver?.checkAvailability(it) ?: ToolAvailability.Available) }
                val roleDetail = providerRoleCheck?.invoke(skill)
                val detail = listOfNotNull(roleDetail, executionService?.check(skill.id)?.entries?.joinToString("\n") { "${it.key}: ${it.value}" }).joinToString("\n")
                skill.id.value to SkillCheckSnapshot(descriptors, availability,
                    roleDetail ?: skillRuntimeAvailability(skill, descriptors, availabilityResolver), skillRegistry.approvalPolicy(skill.id), detail)
            }
        }
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var selectedId by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<PendingSkillImport?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching {
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "SKILL.md"
                val bytes = context.contentResolver.openInputStream(uri)?.use(::readBoundedSkillPackage)
                    ?: error("Could not read selected skill package")
                PendingSkillImport(name, bytes, SkillMarkdownPackageParser.parse("review:$name", bytes))
            } }.onSuccess { pendingImport = it }
                .onFailure { importError = it.message ?: "Invalid skill package" }
        }
    }

    LaunchedEffect(skillRegistry) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { skillRegistry.refreshAvailability() } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, skillRegistry) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) scope.launch(kotlinx.coroutines.Dispatchers.IO) { skillRegistry.refreshAvailability() }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    selectedId?.let { id ->
        skills.firstOrNull { it.id.value == id }?.let { skill ->
            var policy by remember(id, checks[id]?.policy) { mutableStateOf(checks[id]?.policy ?: ToolApprovalPolicy.ASK_EVERY_TIME) }
            var showPolicies by remember(id) { mutableStateOf(false) }
            var showInstructions by remember(id) { mutableStateOf(false) }
            var instructionBody by remember(id) { mutableStateOf<String?>(null) }
            LaunchedEffect(id,showInstructions,skill.provenance?.contentSha256) {
                if(showInstructions) instructionBody=instructionSource?.load(skill.id)?.body
            }
            val descriptors = checks[id]?.descriptors.orEmpty()
            val requiredAccess = declaredPermissions(skill)

            AlertDialog(
                onDismissRequest = { selectedId = null },
                title = { Text(skill.name) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(HaloSpacing.sm)) {
                        Text(skill.description)
                        if(skill.source == com.halo.sdk.runtime.skills.SkillSource.CUSTOM) {
                            TextButton({showInstructions=!showInstructions}) { Text(if(showInstructions) "Hide instructions" else "Read instructions") }
                            if(showInstructions) SelectionContainer { Text(instructionBody ?: "Instructions unavailable",modifier=Modifier.heightIn(max=240.dp).verticalScroll(rememberScrollState())) }
                        }
                        if (id == "halo.phone.screen") PhoneOperatorPanel(readingEnabled = skill.enabled && policy != ToolApprovalPolicy.DENY,
                            actionPolicy = screenPolicyCopy(id, policy))
                        Text("Status · ${checks[id]?.status ?: "Checking"}")
                        TextButton(onClick = { checkRevision++ }) { Text(uiText("Check")) }
                        checks[id]?.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        Text("Access · ${requiredAccess.joinToString().ifBlank { "No special permission" }}")
                        if (onRequestSkillAccess != null) {
                            TextButton(onClick = { onRequestSkillAccess.invoke(requiredAccess, skill.tools) }) {
                                Text(uiText("Grant Android access"))
                            }
                        }
                        Text("Source · ${skill.source.name.lowercase().replace('_', ' ')} · schema ${skill.schemaVersion}")
                        skill.provenance?.let { provenance ->
                            Text(
                                "Package · ${provenance.sourceId} · ${provenance.contentSha256.take(12)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        if (descriptors.isNotEmpty()) {
                            Text(
                                uiText("Actions"),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            descriptors.forEach { descriptor ->
                                val availability = checks[id]?.availability?.get(descriptor.name) ?: ToolAvailability.Disabled("Checking")
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
                            Text("Approval · ${screenPolicyCopy(id, policy)}")
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
                                    text = { Text(screenPolicyCopy(id, choice)) },
                                    onClick = {
                                        showPolicies = false
                                        policy = choice
                                        scope.launch {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                skillRegistry.setApprovalPolicy(skill.id, choice)
                                            }
                                            checkRevision++
                                        }
                                    },
                                )
                            }
                        }

                        if (descriptors.any {
                                it.security.confirmation == com.halo.runtime.cognition.tools.ConfirmationPolicy.ALWAYS
                            }) {
                            Text(
                                uiText("Some sensitive actions always require confirmation."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val enable = !skill.enabled
                        scope.launch {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                skillRegistry.setEnabled(skill.id, enable)
                            }
                            checkRevision++
                            if (enable) onRequestSkillAccess?.invoke(requiredAccess, skill.tools)
                        }
                    }) {
                        Text(if (skill.enabled) "Disable" else "Enable")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedId = null }) { Text(uiText("Close")) }
                },
            )
        }
    }

    pendingImport?.let { pending ->
        val skill = pending.parsed.descriptor
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Install ${skill.name}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(HaloSpacing.sm)) {
                    Text(skill.description)
                    Text("Source · Custom · schema ${skill.schemaVersion}")
                    Text("Risk · ${skill.risk.name.lowercase().replace('_', ' ')}")
                    Text("Actions · ${skill.tools.joinToString()}")
                    Text("Permissions · ${skill.requiredPermissions.joinToString().ifBlank { "None" }}")
                    Text(uiText("The skill installs disabled. You can review its actions before enabling it."))
                    Text(uiText("Instructions supplied by this package"),style=MaterialTheme.typography.labelMedium)
                    SelectionContainer { Text(pending.parsed.instruction.body,modifier=Modifier.heightIn(max=240.dp).verticalScroll(rememberScrollState())) }
                }
            },
            confirmButton = {
                TextButton(onClick = { scope.launch {
                    val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        onInstallCustomSkill?.invoke(pending.fileName, pending.bytes)
                            ?: Result.failure(IllegalStateException("Custom skill installation is unavailable"))
                    }
                    pendingImport = null
                    if (result.isFailure) importError = result.exceptionOrNull()?.message ?: "Could not install skill"
                } }) { Text(uiText("Install")) }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text(uiText("Cancel")) } },
        )
    }

    importError?.let { message ->
        AlertDialog(
            onDismissRequest = { importError = null },
            title = { Text(uiText("Skill package not installed")) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { importError = null }) { Text(uiText("OK")) } },
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = HaloSpacing.screen)) {
        HaloScreenHeader(uiText("Skills"), "Actions Halo can use", navigation = onBack)
        Button(
            enabled = !savingGlobalPolicy,
            onClick = {
                savingGlobalPolicy = true
                scope.launch {
                    try {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            skillRegistry.setApprovalPolicyForAll(ToolApprovalPolicy.ALWAYS_ALLOW)
                        }
                        checkRevision++
                        globalPolicyMessage = "Tool policies saved"
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        globalPolicyMessage = "Could not save tool policies. Try again."
                    } finally {
                        savingGlobalPolicy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = HaloSpacing.xs)
        ) {
            Text(uiText("Allow all tools without asking"))
        }
        globalPolicyMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(HaloSpacing.xs)) {
            if (appCapabilities != null) item(key = "app-capabilities") {
                Column(Modifier.padding(vertical = HaloSpacing.xs)) {
                    Text(uiText("App capabilities"), style = MaterialTheme.typography.titleMedium)
                    Text("App Functions · ${appSnapshot.availability.name.lowercase().replace('_', ' ')}")
                    Text("Apps visible · ${appSnapshot.capabilities.map { it.packageName }.distinct().size}")
                    Text("Functions discovered · ${appSnapshot.capabilities.count { it.route == com.halo.runtime.cognition.apps.AppOperationRoute.APP_FUNCTION }}")
                    Text("Current app actions · ${appCapabilities.currentCandidates().size}")
                    Text("Direct Actions · ${appSnapshot.capabilities.count { it.route == com.halo.runtime.cognition.apps.AppOperationRoute.DIRECT_ACTION }}")
                    Text("Screen control · ${if (screenControlAvailable()) "Available" else "Off"}")
                }
            }
            if (capabilityStatus != null) {
                item(key = "capability-status") {
                    CapabilityStatusCard(capabilityStatus, onIntegrations)
                }
            }
            if (onInstallCustomSkill != null) {
                item(key = "import-skill") {
                    TextButton(onClick = { importLauncher.launch(arrayOf("text/markdown", "text/plain")) }) {
                        Text(uiText("Import SKILL.md"))
                    }
                }
            }
            if (onRequestSkillAccess != null) {
                item(key = "global-skill-access") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = HaloSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(HaloSpacing.xs),
                    ) {
                        Text(
                            uiText("Skill authority"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Allow all without asking applies the stored skill policy globally. Android permission and special-access screens remain explicit, and actions marked Always confirm still require confirmation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = {
                            val enabled = skills.filter { it.enabled }
                            onRequestSkillAccess?.invoke(
                                enabled.flatMap(::declaredPermissions).toSet(),
                                enabled.flatMap { it.tools }.toSet(),
                            )
                        }) {
                            Text(uiText("Grant all needed Android access"))
                        }
                    }
                }
            }
            skills.filterNot { it.id.value in setOf("halo.phone.actions", "halo.phone", "halo.reminders") }.groupBy { skillLane(it) }.forEach { (category, entries) ->
                item(key = category) {
                    Text(
                        category,
                        modifier = Modifier.padding(top = HaloSpacing.lg, bottom = HaloSpacing.sm),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                items(entries, key = { it.id.value }) { skill ->
                    val descriptors = checks[skill.id.value]?.descriptors.orEmpty()
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
                                    checks[skill.id.value]?.status ?: "Checking",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = skill.enabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            skillRegistry.setEnabled(skill.id, enabled)
                                        }
                                        checkRevision++
                                        if (enabled) onRequestSkillAccess?.invoke(
                                            declaredPermissions(skill),
                                            skill.tools,
                                        )
                                    }
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

private fun declaredPermissions(skill: SkillDescriptor): Set<HostPermission> =
    (skill.requiredPermissions + skill.toolDefinitions.flatMap { it.requiredPermissions }).toSet()

/**
 * MINING-P1-001 capability overview card.
 *
 * Summary only: every row is a fact about what is installed or wired, never a probe result, and
 * deep configuration stays on the dedicated screens. Status words are pastel/semantic, not raw
 * enum names.
 */
@Composable
private fun CapabilityStatusCard(viewModel: CapabilityStatusViewModel, onIntegrations: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = HaloSpacing.sm),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(HaloSpacing.md), verticalArrangement = Arrangement.spacedBy(HaloSpacing.xs)) {
            Text(
                uiText("What Halo can do now"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            CapabilityStatusRow("Agent safety", "Run deadline", state.agentRunDeadline)
            CapabilityStatusRow("", "Tool loop guard", state.toolLoopGuard)
            CapabilityStatusRow("", "Thermal policy", state.thermalPolicy)
            CapabilityStatusRow("Memory", "Saved facts", state.durableFacts)
            CapabilityStatusRow("", "Search", state.memorySearch)
            CapabilityStatusRow("", "Semantic search", state.semanticSearch)
            CapabilityStatusRow("Extensions", "Learned procedures", state.learnedProcedures)
            TextButton(onClick = onIntegrations, modifier = Modifier.fillMaxWidth()) { Text("MCP servers · ${state.mcpServers}") }
            TextButton(onClick = onIntegrations, modifier = Modifier.fillMaxWidth()) { Text("MCP tools · ${state.mcpTools}") }
            CapabilityStatusRow("Voice", "Barge-in", state.bargeIn)
            CapabilityStatusRow("", "Semantic interrupt", state.semanticInterrupt)
            CapabilityStatusRow("", "Streaming speech", state.streamingSpeech)
            CapabilityStatusRow("Notifications", "Replyable now", state.replyableNotifications)
            if (state.capabilityModels.isNotEmpty()) {
                Text(
                    uiText("Capability models"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = HaloSpacing.sm),
                )
                state.capabilityModels.forEach { (name, stateLabel) ->
                    CapabilityStatusRow("", name, stateLabel)
                }
                Text(
                    "These are optional abilities, not chat models. Halo installs nothing without a " +
                        "verified artifact.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                uiText("Early speech is available for exact, tool-free streaming responses. Tool and approval work stays buffered."),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CapabilityStatusRow(section: String, label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (section.isBlank()) label else "$section · $label",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

private data class PendingSkillImport(
    val fileName: String,
    val bytes: ByteArray,
    val parsed: ParsedSkillPackage,
)

private fun readBoundedSkillPackage(input: java.io.InputStream): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (output.size() + count > com.halo.sdk.runtime.skills.SkillManifestPolicy.MAX_MANIFEST_BYTES) {
            error("Skill package exceeds ${com.halo.sdk.runtime.skills.SkillManifestPolicy.MAX_MANIFEST_BYTES} bytes")
        }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
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
    "host.screen.observe" -> "screen observation access"
    "host.screen.control" -> "screen control access"
    // RUNTIME-056 native abilities.
    "host.device.inspect" -> "a readable device profile"
    "host.battery.read" -> "battery state access"
    "host.network.read" -> "network state access"
    "host.clipboard.read" -> "clipboard read access"
    "host.clipboard.write" -> "clipboard write access"
    "host.web.fetch" -> "internet access"
    "host.timer.set" -> "a clock app that accepts timers"
    "host.ringer.control" -> "sound mode control"
    "host.screen.brightness" -> "permission to modify system settings"
    "host.settings.open" -> "Android settings access"
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

private fun screenPolicyCopy(skillId: String, policy: ToolApprovalPolicy): String = if (skillId == "halo.phone.screen") when (policy) {
    ToolApprovalPolicy.ASK_EVERY_TIME, ToolApprovalPolicy.ALLOW_WHILE_APP_OPEN -> "Ask before screen actions"
    ToolApprovalPolicy.ALWAYS_ALLOW -> "Allow reversible screen actions"
    ToolApprovalPolicy.DENY -> "Never allow screen actions"
} else policy.name.replace('_', ' ').lowercase()

private data class SkillCheckSnapshot(val descriptors: List<ToolDescriptor>, val availability: Map<String, ToolAvailability>,
    val status: String, val policy: ToolApprovalPolicy, val detail: String?)
private fun skillLane(skill: SkillDescriptor): String = when {
    skill.id.value == "halo.phone.screen" -> "Screen control"
    "vision" in skill.id.value -> "Vision"
    skill.tools.any { it.startsWith("halo_ui_") } -> "Screen control"
    skill.category == com.halo.sdk.runtime.skills.SkillCategory.WORKSPACE -> "Workspace"
    skill.tools.any { it.contains("note") || it.contains("working_set") || it.contains("file") || it.contains("memory") } -> "Workspace"
    skill.id.value in setOf("halo.presentation", "halo.discovery", "halo.web", "halo.research") -> "Agentic"
    else -> "Native"
}
