package com.halo.app.ui.screens.settings

import com.halo.app.language.uiText

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import com.halo.app.ui.design.HaloProgressPresentation
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
import androidx.compose.runtime.saveable.rememberSaveable
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
fun OnDeviceModelsScreen(viewModel: IntelligenceViewModel, onBack: () -> Unit, onConfigureRoles: () -> Unit = {}, onAudioLab: () -> Unit = {}, onVisionLab: () -> Unit = {}) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val hubStatus by (viewModel.hubSyncState ?: remember { kotlinx.coroutines.flow.MutableStateFlow("Offline catalog") }).collectAsStateWithLifecycle()
    val deviceContext = androidx.compose.ui.platform.LocalContext.current
    val memory = remember(ui.downloadStates, ui.localModels, ui.memoryPressure) { android.app.ActivityManager.MemoryInfo().also {
        (deviceContext.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(it)
    } }
    val freeStorage = remember(ui.catalogModels, ui.downloadStates) { deviceContext.noBackupFilesDir.usableSpace }
    var advanced by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var modelUse by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(LocalModelUse.ALL) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(query, modelUse) { listState.scrollToItem(0) }
    var licensedModel by remember { mutableStateOf<LiteRtCatalogModel?>(null) }
    var detailsFor by rememberSaveable { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importModelUri(uri)
        }
    }

    val purpose by viewModel.candidatePurpose.collectAsStateWithLifecycle()
    val catalogModels = browseLocalModels(ui.catalogModels, query, modelUse).filter { model ->
        purpose == null || purpose in model.evaluationPurposes
    }
    .sortedWith(compareBy<com.halo.providers.litert.models.LiteRtCatalogModel> {
        when {
            localGenerationIsCurrent(ui.localModels.firstOrNull { local -> local.id == it.id }, it.id in ui.generationProvenIds) -> -2
            ui.localModels.any { local -> local.id==it.id && local.valid } -> -1
            else -> com.halo.app.ai.LocalModelDeviceFit.assess(it.expectedBytes,freeStorage,memory.availMem,memory.totalMem,it.minDeviceRamGb).ordinal
        }
    }.thenBy { it.expectedBytes })
    val evidence by (viewModel.roleEvidence?.state ?: remember { kotlinx.coroutines.flow.MutableStateFlow(com.halo.app.ai.LocalRoleEvidenceState()) }).collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.roleEvidence?.invalidate() }
    val catalogIds = ui.catalogModels.mapTo(hashSetOf()) { it.id }
    val importedModels = ui.localModels.filter { local -> local.id !in catalogIds &&
        (modelUse != LocalModelUse.TEXT || local.supportsText) &&
        modelUse.matches(local.supportsVision, local.supportsAudio, local.supportsTools) &&
        (query.isBlank() || local.displayName.contains(query.trim(), true) || local.id.contains(query.trim(), true)) }

    licensedModel?.let { model ->
        var token by remember(model.id) { mutableStateOf("") }
        var accepted by remember(model.id) { mutableStateOf(false) }
        androidx.compose.material3.AlertDialog(onDismissRequest={licensedModel=null},
            title={Text(uiText("Download a licensed model"))},
            text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(uiText("Accept the publisher's license on Hugging Face, then use a read token with access. Halo cannot approve the license for you."))
                TextButton(onClick={deviceContext.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://huggingface.co/${model.repoId}")))}) {Text(uiText("Open publisher license"))}
                TextButton(onClick={deviceContext.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://huggingface.co/settings/tokens")))}) {Text(uiText("Get a Hugging Face read token"))}
                OutlinedTextField(token,{token=it.take(512)},label={Text(uiText("Hugging Face token"))},
                    visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),singleLine=true)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(accepted,{accepted=it})
                    Text(uiText("I accepted the publisher's license"))
                }
                Text(uiText("The token is used for this download only and is not saved."))
            }},
            confirmButton={TextButton(onClick={viewModel.downloadLicensedHubModel(model,token);token="";licensedModel=null},
                enabled=accepted && com.halo.providers.litert.models.isHuggingFaceReadToken(token)) {Text(uiText("Download"))}},
            dismissButton={TextButton(onClick={licensedModel=null}) {Text(uiText("Cancel"))}})
    }
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(HaloSpacing.screen), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "browse_controls") { Column {
        HaloScreenHeader(uiText("On-device models"), uiText("Download models and choose how Halo uses them."), navigation = onBack)

        if (purpose != null) Row {
            Text("Candidates for ${purpose?.name?.lowercase()?.replace('_', ' ')} testing")
            TextButton(onClick = { viewModel.candidatePurpose.value = null }) { Text(uiText("Show all")) }
        }
        Spacer(Modifier.height(HaloSpacing.sm))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { viewModel.syncHubModels(query) }, enabled = hubStatus != "Syncing models…") { Text(uiText("Sync Hugging Face")) }
            TextButton(onClick = onConfigureRoles) { Text(uiText("Model roles")) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(onClick = onAudioLab) { Text(uiText("Local audio test")) }
            TextButton(onClick = onVisionLab) { Text(uiText("Vision Lab")) }
        }
        Text(uiText(hubStatus), style = MaterialTheme.typography.bodyMedium)
        Text(uiText("Sorted for this phone. Memory estimates are guidance; test a model before relying on it."), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { advanced = !advanced }) { Text(uiText("Advanced")) }
        if (advanced) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { importLauncher.launch("*/*") },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(uiText("Import .litertlm"))
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
                Text(uiText("Run installed role matrix"))
            }
            if (ui.runningRoleMatrix) TextButton(onClick = viewModel::cancelRoleMatrix) { Text(uiText("Cancel")) }
        }
        if (evidence.history.isNotEmpty()) {
            Text(uiText("Current device · Durable role matrix"), style = MaterialTheme.typography.titleSmall)
            Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                evidence.history.distinctBy { it.artifactId }.forEach { history ->
                    val row = evidence.current.firstOrNull { it.artifactId == history.artifactId }
                    Text("${history.artifactId} · ${row?.backend ?: history.backend} · " + if (row == null) "Needs retest" else
                        "FAST ${row.warmMedianMs ?: "—"} ms · CHAT ${row.chatPassed}/${row.chatTotal} · REASONING ${row.reasoningPassed}/${row.reasoningTotal} · AGENTIC ${row.toolProtocol} ${row.agenticVerdict} · ${if (row.stable) "Stable" else "Measured but unstable"} · Tested ${row.testedAtMs}", style = MaterialTheme.typography.bodySmall)
                    val suggested = evidence.recommendations.filterValues { it.recommended?.modelId == history.artifactId }.keys
                    if (suggested.isNotEmpty()) Text("Suggested roles: ${suggested.joinToString()}", style = MaterialTheme.typography.labelSmall)
                    TextButton(onClick = { viewModel.runRoleSuite(history.artifactId) }, enabled = !ui.runningRoleMatrix && !ui.runningMatrix && ui.busyModelId == null) { Text(uiText("Retest")) }
                }
            }
            TextButton(onClick = { viewModel.roleEvidence?.report(com.halo.app.BuildConfig.HALO_SOURCE_ID)?.let { clipboard.setText(AnnotatedString(it)) } }) { Text(uiText("Copy current device results")) }
        }
        if (ui.roleRows.isNotEmpty()) {
            val report = LocalModelRoleQualification.report(ui.roleRows, com.halo.app.BuildConfig.HALO_SOURCE_ID)
            Text(report.trim(), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()))
            Row {
                TextButton(onClick = { clipboard.setText(AnnotatedString(report)) }) { Text(uiText("Copy role matrix")) }
                TextButton(onClick = onConfigureRoles) { Text(uiText("Configure model roles")) }
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

        }
        Spacer(Modifier.height(HaloSpacing.md))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(uiText("Search catalog models")) }
        )

        Spacer(Modifier.height(HaloSpacing.md))

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LocalModelUse.entries.forEach { use -> FilterChip(modelUse == use, onClick = { modelUse = use }, label = { Text(uiText(use.label)) }) }
        }
        if (modelUse == LocalModelUse.TOOLS) Text(uiText("Tool candidates still need a proven tool loop before Halo can use them for actions."), style = MaterialTheme.typography.bodySmall)
        } }
            if (catalogModels.isEmpty() && importedModels.isEmpty()) item {
                Text(uiText("No local models match these filters. Try another capability or clear the search."), Modifier.padding(vertical = 12.dp))
                TextButton(onClick = { query = ""; modelUse = LocalModelUse.ALL; viewModel.candidatePurpose.value = null }) { Text(uiText("Clear filters")) }
            }
            items(catalogModels, key = { it.id }) { model ->
                val descriptor = ui.localModels.firstOrNull { it.id == model.id }
                val downloadState = ui.downloadStates[model.id] ?: DownloadState.NotInstalled
                val verification = ui.verifications[model.id]
                val requestedBackend = ui.requestedBackends[model.id] ?: model.runtimeProfile.preferredBackend
                val resident = ui.residentModelId == model.id
                val selected = ui.activeModelId == model.id

                val measured = evidence.current.firstOrNull { it.artifactId == model.id }
                Text(uiText(if(ui.localModels.any { it.id==model.id && it.valid }) "Installed" else com.halo.app.ai.LocalModelDeviceFit.assess(model.expectedBytes,freeStorage,memory.availMem,memory.totalMem,model.minDeviceRamGb).label), style = MaterialTheme.typography.labelMedium)
                ModelCard(
                    model = model,
                    measuredRoles = measured,
                    needsRetest = evidence.history.any { it.artifactId == model.id } && measured == null,
                    descriptor = descriptor,
                    downloadState = downloadState,
                    verification = verification,
                    generationProven = localGenerationIsCurrent(descriptor, model.id in ui.generationProvenIds),
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
                    diagnosticsLine = listOfNotNull(
                        ui.litertLoadDiagnostics
                        ?.takeIf { it.modelId == model.id }
                        ?.technicalSummary()
                        ?: ui.litertAcquisitionDiagnostics
                            ?.takeIf { it.modelId == null || it.modelId == model.id }
                            ?.technicalSummary(),
                        ui.conversationDiagnostics?.takeIf { ui.residentModelId == model.id }?.summaryLine(),
                    ).joinToString("\n").takeIf { it.isNotEmpty() },
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
                    onImport = { if(model.access == LiteRtCatalogAccess.GATED_IMPORT_ONLY) licensedModel=model else importLauncher.launch("*/*") },
                    onBackend = { backend -> viewModel.setDeveloperBackend(model.id, backend) },
                    onToggleDetails = { detailsFor = if (detailsFor == model.id) null else model.id },
                )
                HorizontalDivider()
            }

            if (importedModels.isNotEmpty()) {
                item {
                    Text(
                        uiText("Imported models"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = HaloSpacing.md),
                    )
                }
                items(importedModels, key = { "imported-${it.id}" }) { descriptor ->
                    ImportedModelCard(
                        descriptor = descriptor,
                        measuredRoles = evidence.current.firstOrNull { it.artifactId == descriptor.id },
                        generationProven = localGenerationIsCurrent(descriptor, descriptor.id in ui.generationProvenIds),
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
            Text(size(model.expectedBytes), style = MaterialTheme.typography.bodyMedium)
            Text(model.repoId, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOfNotNull("Text", "Vision".takeIf { model.supportsVision }, "Audio".takeIf { model.supportsAudio },
                    "Tool candidates".takeIf { model.supportsTools }).forEach { capability ->
                    Text(uiText(capability), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (detailsExpanded) {
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

            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = uiText(statusText),
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
                    inFlight -> OutlinedButton(onClick = onCancel) { Text(uiText("Cancel")) }
                    importOnly && !installedAndVerified -> Button(onClick = onImport) { Text(uiText(if(model.access == LiteRtCatalogAccess.GATED_IMPORT_ONLY) "Download with Hugging Face" else "Import")) }
                    paused -> {
                        Button(onClick = onResume) { Text(uiText("Resume")) }
                        OutlinedButton(onClick = onDiscardPartial) { Text(uiText("Discard partial")) }
                    }
                    damaged || downloadFailed -> {
                        Button(onClick = onDownload) { Text(uiText("Re-download")) }
                        if (downloadState is DownloadState.Failed && downloadState.resumable) {
                            OutlinedButton(onClick = onResume) { Text(uiText("Resume")) }
                            OutlinedButton(onClick = onDiscardPartial) { Text(uiText("Discard partial")) }
                        }
                    }
                    installedAndVerified -> {
                        if (!selected) Button(onClick = onSelect) { Text(uiText("Use for conversation")) }
                        if (resident) OutlinedButton(onClick = onUnload) { Text(uiText("Unload")) }
                        OutlinedButton(onClick = onDelete) { Text(uiText("Delete")) }
                    }
                    else -> Button(onClick = onDownload) { Text(uiText("Download")) }
                }
            }

            if (installedAndVerified) {
                OutlinedButton(onClick = onSmokeTest, enabled = !busy) { Text(uiText("Test an answer")) }
                var developerTests by remember(model.id) { mutableStateOf(false) }
                TextButton(onClick = { developerTests = !developerTests }) { Text(if (developerTests) "Developer tests ▴" else "Developer tests ▾") }
                if (developerTests) {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Three deliberately distinct levels: raw SDK probe, provider verification,
                        // native tool channel. Never presented as one action.
                        OutlinedButton(onClick = onControlProbe, enabled = !busy) {
                            Text(uiText("Direct runtime probe"))
                        }
                        OutlinedButton(onClick = onVerify, enabled = !busy) { Text(uiText("Verify model")) }
                        OutlinedButton(onClick = onSmokeTest, enabled = !busy) { Text(uiText("Smoke test")) }
                        OutlinedButton(onClick = onBenchmark, enabled = !busy) { Text(uiText("Benchmark")) }
                        OutlinedButton(onClick = onProbeTools, enabled = !busy) {
                            Text(uiText("Probe tool channel"))
                        }
                        if (model.supportsVision) OutlinedButton(onClick = onProbeVision, enabled = !busy) {
                            Text(uiText("Run synthetic vision probe"))
                        }
                        OutlinedButton(onClick = onRoleSuite, enabled = !busy) { Text(uiText("Run role suite")) }
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
                    HaloProgressPresentation(uiText("Downloading"), completed = downloadState.downloadedBytes, total = downloadState.totalBytes)
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

            if (detailsExpanded) {
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
                    Text(uiText(if (detailsExpanded) "Hide details" else "Details"))
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
                ?: Text(uiText("Not measured on this device"), style = MaterialTheme.typography.bodySmall)
            Text(localInteractionProfilePresentation(measuredProfile, memoryPressure, descriptor.contextWindowTokens.toLong(), liveLocalHealth(descriptor.id)), style = MaterialTheme.typography.labelSmall)
            var developerTests by remember(descriptor.id) { mutableStateOf(false) }
            TextButton(onClick = { developerTests = !developerTests }) { Text(uiText("Developer tests")) }
            if (developerTests) {
                TextButton(onClick = onBenchmark) { Text(uiText("Benchmark")) }
                TextButton(onClick = onRoleSuite) { Text(uiText("Run role suite")) }
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
                if (descriptor.valid && !selected) OutlinedButton(onClick = onSelect) { Text(uiText("Use model")) }
                if (resident) Text(uiText("Resident"), style = MaterialTheme.typography.labelMedium)
                OutlinedButton(onClick = onDelete) { Text(uiText("Delete")) }
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
