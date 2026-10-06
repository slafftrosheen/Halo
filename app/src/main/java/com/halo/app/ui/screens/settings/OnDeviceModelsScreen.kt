package com.halo.app.ui.screens.settings

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.halo.providers.litert.runtime.LocalModelRoleQualification
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.design.HaloSpacing
import com.halo.app.ui.theme.HaloPastelMint
import com.halo.providers.litert.LiteRtToolProbeResult
import com.halo.providers.litert.LiteRtToolQualification
import com.halo.providers.litert.runtime.LiteRtControlProbeResult
import com.halo.providers.litert.models.DownloadState
import com.halo.providers.litert.models.LiteRtCatalogAccess
import com.halo.providers.litert.models.LiteRtCatalogModel
import com.halo.providers.litert.models.LiteRtModelFailureRecord
import com.halo.providers.litert.models.LocalModelDescriptor
import com.halo.providers.litert.models.LocalModelValidation
import com.halo.providers.litert.models.ModelVerificationResult
import com.halo.providers.litert.runtime.LocalModelBenchmarkResult
import com.halo.providers.litert.runtime.LocalModelDevelopmentRunner
import com.halo.providers.litert.runtime.LocalModelSmokeTestResult
import com.halo.sdk.provider.LocalComputeBackend

@Composable
fun OnDeviceModelsScreen(viewModel: IntelligenceViewModel, onBack: () -> Unit, onConfigureRoles: () -> Unit = {}) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var query by remember { mutableStateOf("") }
    var detailsFor by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importModelUri(uri)
        }
    }

    val purpose by viewModel.candidatePurpose.collectAsStateWithLifecycle()
    val catalogModels = ui.catalogModels.filter { model ->
        (purpose == null || purpose in model.evaluationPurposes) && (query.isBlank() ||
            model.displayName.contains(query, ignoreCase = true) ||
            model.id.contains(query, ignoreCase = true))
    }
    val evidence by (viewModel.roleEvidence?.state ?: remember { kotlinx.coroutines.flow.MutableStateFlow(com.halo.app.ai.LocalRoleEvidenceState()) }).collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.roleEvidence?.invalidate() }
    val catalogIds = ui.catalogModels.mapTo(hashSetOf()) { it.id }
    val importedModels = ui.localModels.filterNot { it.id in catalogIds }

    Column(Modifier.fillMaxSize().padding(horizontal = HaloSpacing.screen)) {
        HaloScreenHeader("Models", "LiteRT development matrix · LiteRT-LM 0.17.1", navigation = onBack)

        if (purpose != null) Row {
            Text("Candidates for ${purpose?.name?.lowercase()?.replace('_', ' ')} testing")
            TextButton(onClick = { viewModel.candidatePurpose.value = null }) { Text("Show all") }
        }
        Spacer(Modifier.height(HaloSpacing.sm))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { importLauncher.launch("*/*") },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Import .litertlm")
            }
            OutlinedButton(
                onClick = { viewModel.runModelMatrix() },
                enabled = !ui.runningMatrix && !ui.runningRoleMatrix,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (ui.runningMatrix) "Running matrix…" else "Run smoke matrix")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { viewModel.runRoleSuite() }, enabled = !ui.runningRoleMatrix && !ui.runningMatrix && ui.busyModelId == null) {
                Text("Run installed role matrix")
            }
            if (ui.runningRoleMatrix) TextButton(onClick = viewModel::cancelRoleMatrix) { Text("Cancel") }
        }
        if (evidence.history.isNotEmpty()) {
            Text("Current device · Durable role matrix", style = MaterialTheme.typography.titleSmall)
            Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                evidence.history.distinctBy { it.artifactId }.forEach { history ->
                    val row = evidence.current.firstOrNull { it.artifactId == history.artifactId }
                    Text("${history.artifactId} · ${row?.backend ?: history.backend} · " + if (row == null) "Needs retest" else
                        "FAST ${row.warmMedianMs ?: "—"} ms · CHAT ${row.chatPassed}/${row.chatTotal} · REASONING ${row.reasoningPassed}/${row.reasoningTotal} · AGENTIC ${row.toolProtocol} ${row.agenticVerdict} · ${if (row.stable) "Stable" else "Measured but unstable"} · Tested ${row.testedAtMs}", style = MaterialTheme.typography.bodySmall)
                    val suggested = evidence.recommendations.filterValues { it.recommended?.modelId == history.artifactId }.keys
                    if (suggested.isNotEmpty()) Text("Suggested roles: ${suggested.joinToString()}", style = MaterialTheme.typography.labelSmall)
                    TextButton(onClick = { viewModel.runRoleSuite(history.artifactId) }, enabled = !ui.runningRoleMatrix && !ui.runningMatrix && ui.busyModelId == null) { Text("Retest") }
                }
            }
            TextButton(onClick = { viewModel.roleEvidence?.report(com.halo.app.BuildConfig.HALO_SOURCE_ID)?.let { clipboard.setText(AnnotatedString(it)) } }) { Text("Copy current device results") }
        }
        if (ui.roleRows.isNotEmpty()) {
            val report = LocalModelRoleQualification.report(ui.roleRows, com.halo.app.BuildConfig.HALO_SOURCE_ID)
            Text(report.trim(), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()))
            Row {
                TextButton(onClick = { clipboard.setText(AnnotatedString(report)) }) { Text("Copy role matrix") }
                TextButton(onClick = onConfigureRoles) { Text("Configure model roles") }
            }
            if (ui.roleRows.any { it.agentic?.verdict == com.halo.providers.litert.runtime.LocalRoleProbeVerdict.PASS && it.agentic?.provenanceRetained == false })
                Text(if (viewModel.roleEvidence != null) "Role evidence is separate from Benchmark calibration provenance" else "Session pass · run Benchmark to retain device/runtime provenance", style = MaterialTheme.typography.bodySmall)
        }

        if (ui.matrixRows.isNotEmpty()) {
            Spacer(Modifier.height(HaloSpacing.sm))
            Text(
                LocalModelDevelopmentRunner.formatTable(ui.matrixRows).trim(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }

        Spacer(Modifier.height(HaloSpacing.md))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search catalog models") }
        )

        Spacer(Modifier.height(HaloSpacing.md))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(catalogModels, key = { it.id }) { model ->
                val descriptor = ui.localModels.firstOrNull { it.id == model.id }
                val downloadState = ui.downloadStates[model.id] ?: DownloadState.NotInstalled
                val verification = ui.verifications[model.id]
                val requestedBackend = ui.requestedBackends[model.id] ?: model.runtimeProfile.preferredBackend
                val resident = ui.residentModelId == model.id
                val selected = ui.activeModelId == model.id

                val measured = evidence.current.firstOrNull { it.artifactId == model.id }
                ModelCard(
                    model = model,
                    measuredRoles = measured,
                    needsRetest = evidence.history.any { it.artifactId == model.id } && measured == null,
                    descriptor = descriptor,
                    downloadState = downloadState,
                    verification = verification,
                    generationProven = model.id in ui.generationProvenIds,
                    verifying = ui.verifyingModelId == model.id,
                    selected = selected,
                    resident = resident,
                    effectiveBackend = if (resident) ui.residentBackend else null,
                    effectiveContextTokens = if (resident) ui.residentContextTokens else null,
                    requestedBackend = requestedBackend,
                    failureRecord = ui.modelFailureRecords[model.id],
                    smokeTest = ui.smokeTests[model.id],
                    benchmark = ui.benchmarkResults[model.id],
                    measuredProfile = ui.measuredProfiles[model.id],
                    memoryPressure = ui.memoryPressure,
                    toolProbe = ui.toolProbes[model.id],
                    controlProbe = ui.controlProbes[model.id],
                    toolQualification = ui.toolQualification[model.id]
                        ?: LiteRtToolQualification.NOT_APPLICABLE,
                    executionState = ui.nativeResources
                        .firstOrNull {
                            it.kind == com.halo.sdk.runtime.NativeResourceKind.HEAVY_LLM &&
                                it.ownerId == "litert_local"
                        }
                        ?.let { snapshot ->
                            when {
                                snapshot.executing -> "In use"
                                snapshot.state == com.halo.sdk.runtime.NativeResourceState.RESIDENT -> "Idle (resident)"
                                else -> snapshot.state.name.lowercase().replace('_', ' ')
                            }
                        },
                    busy = ui.busyModelId != null || ui.runningMatrix || ui.runningRoleMatrix,
                    detailsExpanded = detailsFor == model.id,
                    diagnosticsLine = ui.litertLoadDiagnostics
                        ?.takeIf { it.modelId == model.id }
                        ?.technicalSummary()
                        ?: ui.litertAcquisitionDiagnostics
                            ?.takeIf { it.modelId == null || it.modelId == model.id }
                            ?.technicalSummary(),
                    storagePreflightLine = ui.storagePreflightLines[model.id],
                    onDownload = { viewModel.downloadCatalogModel(model) },
                    onResume = { viewModel.resumeCatalogDownload(model) },
                    onCancel = { viewModel.cancelDownload(model.id) },
                    onDiscardPartial = { viewModel.discardPartialDownload(model.id) },
                    onSelect = { viewModel.selectLocalModel(model.id) },
                    onVerify = { viewModel.verifyModel(model.id) },
                    onSmokeTest = { viewModel.smokeTestModel(model.id) },
                    onRoleSuite = { viewModel.runRoleSuite(model.id) },
                    onBenchmark = { viewModel.benchmarkModel(model.id) },
                    onProbeTools = { viewModel.probeModelTools(model.id) },
                    onProbeVision = { viewModel.probeModelVision(model.id) },
                    visionQualification = ui.visionQualification[model.id],
                    onControlProbe = { viewModel.runControlProbe(model.id) },
                    onUnload = { viewModel.unloadLocalModel() },
                    onDelete = { viewModel.deleteModel(model.id) },
                    onImport = { importLauncher.launch("*/*") },
                    onBackend = { backend -> viewModel.setDeveloperBackend(model.id, backend) },
                    onToggleDetails = { detailsFor = if (detailsFor == model.id) null else model.id },
                )
                HorizontalDivider()
            }

            if (importedModels.isNotEmpty()) {
                item {
                    Text(
                        "Imported models",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = HaloSpacing.md),
                    )
                }
                items(importedModels, key = { "imported-${it.id}" }) { descriptor ->
                    ImportedModelCard(
                        descriptor = descriptor,
                        measuredRoles = evidence.current.firstOrNull { it.artifactId == descriptor.id },
                        generationProven = descriptor.id in ui.generationProvenIds,
                        selected = ui.activeModelId == descriptor.id,
                        resident = ui.residentModelId == descriptor.id,
                        failureRecord = ui.modelFailureRecords[descriptor.id],
                        measuredProfile = ui.measuredProfiles[descriptor.id],
                        memoryPressure = ui.memoryPressure,
                        onBenchmark = { viewModel.benchmarkModel(descriptor.id) },
                        onRoleSuite = { viewModel.runRoleSuite(descriptor.id) },
                        onSelect = { viewModel.selectLocalModel(descriptor.id) },
                        onDelete = { viewModel.deleteModel(descriptor.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: LiteRtCatalogModel,
    measuredRoles: com.halo.app.data.models.LocalRoleQualificationEntity?,
    needsRetest: Boolean,
    descriptor: LocalModelDescriptor?,
    downloadState: DownloadState,
    verification: ModelVerificationResult?,
    generationProven: Boolean,
    verifying: Boolean,
    selected: Boolean,
    resident: Boolean,
    effectiveBackend: LocalComputeBackend?,
    effectiveContextTokens: Int?,
    requestedBackend: LocalComputeBackend,
    failureRecord: LiteRtModelFailureRecord?,
    smokeTest: LocalModelSmokeTestResult?,
    benchmark: LocalModelBenchmarkResult?,
    measuredProfile: com.halo.runtime.cognition.context.LocalInferenceRuntimeProfile?,
    memoryPressure: com.halo.sdk.runtime.NativeMemoryPressure,
    toolProbe: LiteRtToolProbeResult?,
    controlProbe: LiteRtControlProbeResult?,
    toolQualification: LiteRtToolQualification,
    executionState: String?,
    busy: Boolean,
    detailsExpanded: Boolean,
    diagnosticsLine: String?,
    storagePreflightLine: String?,
    onDownload: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDiscardPartial: () -> Unit,
    onSelect: () -> Unit,
    onVerify: () -> Unit,
    onSmokeTest: () -> Unit,
    onRoleSuite: () -> Unit,
    onBenchmark: () -> Unit,
    onProbeTools: () -> Unit,
    onProbeVision: () -> Unit,
    visionQualification: com.halo.providers.litert.VisionQualification?,
    onControlProbe: () -> Unit,
    onUnload: () -> Unit,
    onDelete: () -> Unit,
    onImport: () -> Unit,
    onBackend: (LocalComputeBackend) -> Unit,
    onToggleDetails: () -> Unit,
) {
    val installedAndVerified = descriptor?.validation == LocalModelValidation.VERIFIED_ARTIFACT
    val damaged = descriptor != null && !descriptor.valid
    val downloadFailed = downloadState is DownloadState.Failed
    val inFlight = downloadState is DownloadState.Queued ||
        downloadState is DownloadState.Preparing ||
        downloadState is DownloadState.Downloading ||
        downloadState is DownloadState.Verifying
    val paused = downloadState is DownloadState.Paused
    val importOnly = model.access != LiteRtCatalogAccess.OPEN_DIRECT

    val statusText = when {
        busy || verifying -> "Busy on device…"
        resident && generationProven -> "Resident on ${backendLabel(effectiveBackend ?: requestedBackend)} · generation proven"
        resident -> "Resident on ${backendLabel(effectiveBackend ?: requestedBackend)} · generation not yet proven"
        verification is ModelVerificationResult.Failed -> "Load failed (${verification.stage})"
        downloadState is DownloadState.Queued -> "Queued"
        downloadState is DownloadState.Preparing -> "Preparing…"
        downloadState is DownloadState.Downloading -> "Downloading ${percent(downloadState)}"
        downloadState is DownloadState.Verifying -> "Verifying…"
        downloadState is DownloadState.Paused -> "Partial ${size(downloadState.downloadedBytes)} — resumable"
        damaged -> "Corrupt/stale — re-download"
        installedAndVerified && selected -> "Selected · cold (will load on request)"
        installedAndVerified -> "Installed · verified · cold"
        descriptor != null -> "Installed (unverified)"
        importOnly -> "Not installed · import a licensed file"
        else -> "Not installed"
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(model.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            measuredRoles?.let { row -> Text("Measured roles · FAST ${row.warmMedianMs ?: "—"} ms · CHAT ${row.chatPassed}/${row.chatTotal} · REASONING ${row.reasoningPassed}/${row.reasoningTotal} · AGENTIC ${row.toolProtocol} ${row.agenticVerdict} · VISION ${row.visionVerdict}", style = MaterialTheme.typography.bodySmall) }
                ?: Text(if (needsRetest) "Needs retest" else "Not measured on this device", style = MaterialTheme.typography.bodySmall)
            if (model.evaluationPurposes.isNotEmpty()) Text("Candidate for: ${model.evaluationPurposes.joinToString { it.name.lowercase().replace('_', ' ') }} testing", style = MaterialTheme.typography.labelSmall)
            Text(
                "${model.family} · ${model.quantizationLabel} · ${size(model.expectedBytes)} · " +
                    "${model.contextWindowTokens} ctx · ${model.tier.name.lowercase()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOfNotNull("Text", "Vision".takeIf { model.supportsVision }, "Audio".takeIf { model.supportsAudio },
                    "Tools".takeIf { model.supportsTools }, "Thinking".takeIf { model.supportsThinking }).forEach { capability ->
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(capability, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // MINING-P0-001 compact truth indicators. Each is a separate fact: artifact,
            // selection, residency, execution lease, backend, generation proof, tool channel.
            Text(
                buildString {
                    append("Artifact: ")
                    append(
                        when (descriptor?.validation) {
                            LocalModelValidation.VERIFIED_ARTIFACT -> "Verified"
                            LocalModelValidation.UNVERIFIED -> "Unverified"
                            LocalModelValidation.CORRUPT -> "Corrupt"
                            LocalModelValidation.INCOMPATIBLE -> "Incompatible"
                            null -> "Not installed"
                        }
                    )
                    append(" · Selected: "); append(if (selected) "Yes" else "No")
                    append(" · Resident: "); append(if (resident) "Yes" else "No")
                    append(" · Execution: "); append(executionState ?: "Idle")
                    append(" · Backend: "); append(effectiveBackend?.let(::backendLabel) ?: "-")
                    append(" · Generation: "); append(if (generationProven) "Proven" else "Not proven")
                    append(" · Tools: "); append(toolQualification.label)
                    append(" · Vision: "); append(when (visionQualification) {
                        com.halo.providers.litert.VisionQualification.PROVEN -> "Proven"
                        com.halo.providers.litert.VisionQualification.FAILED -> "Last probe failed"
                        else -> if (model.supportsVision) "Probe required" else "Not supported"
                    })
                    append(" · Audio: "); append(if (model.supportsAudio) "Probe required" else "Not supported")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "requested=${backendLabel(requestedBackend)} · " +
                    "effective=${effectiveBackend?.let(::backendLabel) ?: "cold"} · " +
                    "context=${effectiveContextTokens ?: (if (descriptor?.useModelDefaultContext == true) "model default" else model.contextWindowTokens.toString())}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LocalComputeBackend.entries.forEach { backend ->
                    FilterChip(
                        selected = requestedBackend == backend,
                        onClick = { onBackend(backend) },
                        label = { Text(backendLabel(backend)) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (verification is ModelVerificationResult.Verified &&
                verification.loadBackend != verification.generationBackend
            ) {
                Text(
                    "${backendLabel(verification.loadBackend)} generation failed; " +
                        "${backendLabel(verification.generationBackend)} fallback succeeded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (damaged || downloadFailed || verification is ModelVerificationResult.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                if (selected || resident) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = HaloPastelMint,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            if (resident) "Resident ✓" else "Selected ✓",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    inFlight -> OutlinedButton(onClick = onCancel) { Text("Cancel") }
                    importOnly -> Button(onClick = onImport) { Text("Import") }
                    paused -> {
                        Button(onClick = onResume) { Text("Resume") }
                        OutlinedButton(onClick = onDiscardPartial) { Text("Discard partial") }
                    }
                    damaged || downloadFailed -> {
                        Button(onClick = onDownload) { Text("Re-download") }
                        if (downloadState is DownloadState.Failed && downloadState.resumable) {
                            OutlinedButton(onClick = onResume) { Text("Resume") }
                            OutlinedButton(onClick = onDiscardPartial) { Text("Discard partial") }
                        }
                    }
                    installedAndVerified -> {
                        if (!selected) Button(onClick = onSelect) { Text("Select") }
                        if (resident) OutlinedButton(onClick = onUnload) { Text("Unload") }
                        OutlinedButton(onClick = onDelete) { Text("Delete") }
                    }
                    else -> Button(onClick = onDownload) { Text("Download") }
                }
            }

            if (installedAndVerified) {
                var developerTests by remember(model.id) { mutableStateOf(false) }
                TextButton(onClick = { developerTests = !developerTests }) { Text(if (developerTests) "Developer tests ▴" else "Developer tests ▾") }
                if (developerTests) {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Three deliberately distinct levels: raw SDK probe, provider verification,
                        // native tool channel. Never presented as one action.
                        OutlinedButton(onClick = onControlProbe, enabled = !busy) {
                            Text("Direct runtime probe")
                        }
                        OutlinedButton(onClick = onVerify, enabled = !busy) { Text("Verify model") }
                        OutlinedButton(onClick = onSmokeTest, enabled = !busy) { Text("Smoke test") }
                        OutlinedButton(onClick = onBenchmark, enabled = !busy) { Text("Benchmark") }
                        OutlinedButton(onClick = onProbeTools, enabled = !busy) {
                            Text("Probe tool channel")
                        }
                        if (model.supportsVision) OutlinedButton(onClick = onProbeVision, enabled = !busy) {
                            Text("Run synthetic vision probe")
                        }
                        OutlinedButton(onClick = onRoleSuite, enabled = !busy) { Text("Run role suite") }
                    }
                }
            }

            if (importOnly) {
                Spacer(Modifier.height(4.dp))
                Text(
                    model.importGuidance ?: "Import a licensed artifact from this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when (downloadState) {
                is DownloadState.Downloading -> {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { downloadState.progress ?: 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        buildString {
                            if (downloadState.resumedFrom > 0L) {
                                append("resumed at ${size(downloadState.resumedFrom)} · ")
                            }
                            append("${size(downloadState.downloadedBytes)} of ")
                            append(downloadState.totalBytes?.let(::size) ?: "…")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is DownloadState.Verifying -> {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                is DownloadState.Failed -> {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        downloadState.userMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> Unit
            }

            failureRecord?.let { record ->
                Spacer(Modifier.height(4.dp))
                Text(
                    record.summaryLine(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            smokeTest?.let { result ->
                Spacer(Modifier.height(4.dp))
                Text(
                    "Smoke: ${result.summaryLine()} · first=\"${result.firstStep?.responseText?.take(40) ?: "∅"}\"",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            benchmark?.let { result ->
                Spacer(Modifier.height(4.dp))
                Text(
                    result.summaryLine(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            toolProbe?.let { result ->
                Spacer(Modifier.height(4.dp))
                Text(
                    result.summaryLine(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            controlProbe?.let { result ->
                Spacer(Modifier.height(4.dp))
                Text(
                    result.summaryLine(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = if (result.controlProbePassed) HaloPastelMint else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val validationDetail = descriptor?.validationDetail
            if (damaged && validationDetail != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    validationDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onToggleDetails) {
                    Text(if (detailsExpanded) "Hide details" else "Details")
                }
            }

            if (detailsExpanded) {
                val liveHealth = liveLocalHealth(model.id)
                Text(
                    buildString {
                        appendLine("Artifact: ${model.filename}")
                        appendLine(localInteractionProfilePresentation(measuredProfile, memoryPressure, (descriptor?.contextWindowTokens ?: model.contextWindowTokens).toLong(), liveHealth))
                        appendLine()
                        appendLine("Access: ${model.access} · maturity: ${model.maturity}")
                        appendLine("Pinned revision: ${model.revision}")
                        appendLine("Declared vision: ${model.supportsVision} · audio: ${model.supportsAudio} · tools: ${model.supportsTools}")
                        appendLine("Expected bytes: ${model.expectedBytes}")
                        appendLine("Expected SHA-256: ${model.sha256}")
                        appendLine("Runtime profile: preferred=${model.runtimeProfile.preferredBackend} " +
                            "cpuFallback=${model.runtimeProfile.allowCpuFallback} " +
                            "maxOutput=${model.runtimeProfile.defaultMaxOutputTokens} " +
                            "topK=${model.runtimeProfile.defaultTopK ?: "default"} " +
                            "topP=${model.runtimeProfile.defaultTopP ?: "default"} " +
                            "temp=${model.runtimeProfile.defaultTemperature ?: "default"}")
                        storagePreflightLine?.let { appendLine(it) }
                        descriptor?.let {
                            appendLine("On disk: ${it.bytes} bytes")
                            appendLine("On disk SHA-256: ${it.sha256 ?: "unknown"}")
                            appendLine("Validation: ${it.validation}")
                            appendLine(
                                "Context: " +
                                    if (it.useModelDefaultContext) {
                                        "model default"
                                    } else {
                                        "${it.contextWindowTokens} tokens"
                                    }
                            )
                        }
                        if (verification is ModelVerificationResult.Failed) {
                            appendLine("Verify failure (${verification.stage}): ${verification.reason}")
                            verification.technicalDetail?.let { appendLine("  $it") }
                        }
                        diagnosticsLine?.let { appendLine(it) }
                        controlProbe?.let { probe ->
                            appendLine("Direct runtime probe: ${probe.summaryLine()}")
                            probe.stages.forEach { stage ->
                                appendLine(
                                    "  ${stage.stage}: ${stage.status}" +
                                        (stage.durationMs?.let { " ${it}ms" } ?: "") +
                                        (stage.boundedDetail?.let { " — $it" } ?: "") +
                                        (stage.exceptionClass?.let { " ($it)" } ?: "")
                                )
                            }
                        }
                    }.trim(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ImportedModelCard(
    descriptor: LocalModelDescriptor,
    measuredRoles: com.halo.app.data.models.LocalRoleQualificationEntity?,
    measuredProfile: com.halo.runtime.cognition.context.LocalInferenceRuntimeProfile?,
    memoryPressure: com.halo.sdk.runtime.NativeMemoryPressure,
    onBenchmark: () -> Unit,
    onRoleSuite: () -> Unit,
    generationProven: Boolean,
    selected: Boolean,
    resident: Boolean,
    failureRecord: LiteRtModelFailureRecord?,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(descriptor.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            measuredRoles?.let { row -> Text("Measured roles · FAST ${row.warmMedianMs ?: "—"} ms · CHAT ${row.chatPassed}/${row.chatTotal} · REASONING ${row.reasoningPassed}/${row.reasoningTotal} · AGENTIC ${row.toolProtocol} ${row.agenticVerdict} · VISION ${row.visionVerdict}", style = MaterialTheme.typography.bodySmall) }
                ?: Text("Not measured on this device", style = MaterialTheme.typography.bodySmall)
            Text(localInteractionProfilePresentation(measuredProfile, memoryPressure, descriptor.contextWindowTokens.toLong(), liveLocalHealth(descriptor.id)), style = MaterialTheme.typography.labelSmall)
            var developerTests by remember(descriptor.id) { mutableStateOf(false) }
            TextButton(onClick = { developerTests = !developerTests }) { Text("Developer tests") }
            if (developerTests) {
                TextButton(onClick = onBenchmark) { Text("Benchmark") }
                TextButton(onClick = onRoleSuite) { Text("Run role suite") }
            }
            Text(
                "${descriptor.bytes / 1_000_000} MB · imported · ${descriptor.validation.name}" +
                    if (generationProven) " · generation proven" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            descriptor.validationDetail?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            failureRecord?.let {
                Text(it.summaryLine(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (descriptor.valid && !selected) OutlinedButton(onClick = onSelect) { Text("Use model") }
                if (resident) Text("Resident", style = MaterialTheme.typography.labelMedium)
                OutlinedButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

private fun percent(state: DownloadState.Downloading): String =
    state.progress?.let { "${(it * 100).toInt()}%" } ?: "${state.downloadedBytes / 1_000_000} MB"

private fun backendLabel(backend: LocalComputeBackend): String = when (backend) {
    LocalComputeBackend.GPU -> "GPU"
    LocalComputeBackend.CPU -> "CPU"
    LocalComputeBackend.AUTO -> "AUTO"
}

private fun size(bytes: Long): String =
    if (bytes >= 1_000_000_000L) "%.2f GB".format(bytes / 1_000_000_000.0)
    else "%d MB".format((bytes + 999_999L) / 1_000_000L)

@Composable
private fun liveLocalHealth(modelId: String): com.halo.runtime.cognition.context.LocalInferenceRuntimeHealth? {
    val samples by com.halo.app.di.ManualDependencyGraph.localInferenceRuntimeHealthTracker.snapshots.collectAsStateWithLifecycle()
    return if (samples.any { it.modelId == modelId })
        com.halo.app.di.ManualDependencyGraph.localInferenceProfileSource.runtimeHealth(com.halo.providers.litert.LiteRtLocalProvider.ProviderIds.LITERT, modelId)
    else null
}
