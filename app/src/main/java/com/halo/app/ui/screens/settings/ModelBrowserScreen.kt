package com.halo.app.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.design.HaloSpacing
import com.halo.app.ui.theme.HaloPastelMint
import com.halo.providers.litert.models.DownloadState
import com.halo.providers.litert.models.LiteRtCatalogModel
import com.halo.providers.litert.models.LocalModelDescriptor
import com.halo.providers.litert.models.LocalModelValidation
import com.halo.providers.litert.models.ModelVerificationResult
import com.halo.sdk.provider.LocalComputeBackend

@Composable
fun ModelBrowserScreen(viewModel: IntelligenceViewModel, onBack: () -> Unit) {
    val ui by viewModel.uiState.collectAsState()
    var query by remember { mutableStateOf("") }
    var detailsFor by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importModelUri(uri)
        }
    }

    val catalogModels = ui.catalogModels.filter { model ->
        query.isBlank() || model.displayName.contains(query, ignoreCase = true) || model.id.contains(query, ignoreCase = true)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = HaloSpacing.screen)) {
        HaloScreenHeader("Models", "Verified on-device LiteRT models", navigation = onBack)

        Spacer(Modifier.height(HaloSpacing.sm))

        OutlinedButton(
            onClick = { importLauncher.launch("*/*") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Import Local .litertlm File")
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

        if (catalogModels.isEmpty()) {
            Text(
                "No models match your search query.",
                modifier = Modifier.padding(top = 32.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(catalogModels, key = { it.id }) { model ->
                    val descriptor = ui.localModels.firstOrNull { it.id == model.id }
                    val downloadState = ui.downloadStates[model.id] ?: DownloadState.NotInstalled
                    val verification = ui.verifications[model.id]

                    ModelCard(
                        model = model,
                        descriptor = descriptor,
                        downloadState = downloadState,
                        verification = verification,
                        verifying = ui.verifyingModelId == model.id,
                        isActive = descriptor?.verified == true && model.id == ui.activeModelId,
                        detailsExpanded = detailsFor == model.id,
                        diagnosticsLine = ui.litertLoadDiagnostics
                            ?.takeIf { it.modelId == model.id }
                            ?.technicalSummary(),
                        onDownload = { viewModel.downloadCatalogModel(model) },
                        onCancel = { viewModel.cancelDownload(model.id) },
                        onSelect = { viewModel.selectLocalModel(model.id) },
                        onVerify = { viewModel.verifyModel(model.id) },
                        onToggleDetails = { detailsFor = if (detailsFor == model.id) null else model.id },
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
    descriptor: LocalModelDescriptor?,
    downloadState: DownloadState,
    verification: ModelVerificationResult?,
    verifying: Boolean,
    isActive: Boolean,
    detailsExpanded: Boolean,
    diagnosticsLine: String?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onSelect: () -> Unit,
    onVerify: () -> Unit,
    onToggleDetails: () -> Unit,
) {
    val installedAndVerified = descriptor?.validation == LocalModelValidation.VERIFIED_ARTIFACT
    val damaged = descriptor != null && !descriptor.valid
    val installFailed = downloadState is DownloadState.Failed
    val inFlight = downloadState is DownloadState.Downloading || downloadState is DownloadState.Verifying

    val statusText = when {
        verifying -> "Verifying on device…"
        verification is ModelVerificationResult.Verified ->
            "Verified on ${backendLabel(verification.backend)}"
        verification is ModelVerificationResult.Failed -> "Load failed"
        downloadState is DownloadState.Downloading -> "Downloading ${percent(downloadState)}"
        downloadState is DownloadState.Verifying -> "Verifying file"
        damaged -> "Corrupt/stale — re-download"
        installedAndVerified -> if (isActive) "Active" else "Installed — ready to select"
        descriptor != null -> "Installed (unverified)"
        else -> "Not installed"
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(model.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "LiteRT-LM · ${size(model.expectedBytes)} · Text · ${model.contextWindowTokens} context",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Requires LiteRT-LM ${model.minLiteRtLmVersion} · revision ${model.revision.take(12)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (damaged || installFailed || verification is ModelVerificationResult.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isActive) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = HaloPastelMint,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(
                                "Active ✓",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    when {
                        inFlight -> {
                            OutlinedButton(onClick = onCancel) { Text("Cancel") }
                        }
                        // Only a genuinely verified artifact may be selected as ready.
                        installedAndVerified -> {
                            if (!isActive) {
                                Button(onClick = onSelect) { Text("Select") }
                            }
                        }
                        damaged || installFailed -> {
                            Button(onClick = onDownload) { Text("Re-download") }
                        }
                        else -> {
                            Button(onClick = onDownload) { Text("Download") }
                        }
                    }
                }
            }

            when (downloadState) {
                is DownloadState.Downloading -> {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { downloadState.progress ?: 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "${size(downloadState.downloadedBytes)} of ${downloadState.totalBytes?.let(::size) ?: "…"}",
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

            if (installedAndVerified && !inFlight) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onVerify, enabled = !verifying) {
                        Text(if (verifying) "Verifying…" else "Verify")
                    }
                    TextButton(onClick = onToggleDetails) {
                        Text(if (detailsExpanded) "Hide details" else "Details")
                    }
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

            if (detailsExpanded) {
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        appendLine("Artifact: ${model.filename}")
                        appendLine("Expected bytes: ${model.expectedBytes}")
                        appendLine("Expected SHA-256: ${model.sha256}")
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
                    }.trim(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
    if (bytes >= 1_000_000_000L) "%.1f GB".format(bytes / 1_000_000_000.0)
    else "%d MB".format((bytes + 999_999L) / 1_000_000L)
