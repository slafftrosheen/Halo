package com.halo.app.ui.everyday

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.ai.PrivacyMode
import com.halo.app.data.knowledge.ReferenceImportJobEntity
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.knowledge.*
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.*
import org.json.JSONArray

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReferenceLibrariesScreen(model: ReferenceLibrariesViewModel, onBack: () -> Unit, onSource: (String) -> Unit,
    initialSourceId: String? = null, initialVersion: Long? = null, initialGeneration: String? = null, initialEntryId: String? = null) {
    LaunchedEffect(initialSourceId, initialVersion, initialGeneration, initialEntryId) {
        if (initialSourceId != null && initialVersion != null && initialGeneration != null && initialEntryId != null)
            model.openExact(initialSourceId, initialVersion, initialGeneration, initialEntryId)
    }
    val state by model.state.collectAsStateWithLifecycle()
    val mode by Graph.privacyModes.mode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf("Search") }
    var reselect by remember { mutableStateOf<ReferenceImportJobEntity?>(null) }
    var replacementId by rememberSaveable { mutableStateOf<String?>(null) }
    var replacementVersion by rememberSaveable { mutableStateOf<Long?>(null) }
    var download by remember { mutableStateOf<ReferenceStarterId?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val job = reselect; reselect = null
        if (job != null) model.resume(job, uri) else {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: uri.lastPathSegment.orEmpty().substringAfterLast('/').take(200)
            model.preview(uri, name, replacementId, replacementVersion)
            replacementId = null; replacementVersion = null
        }
    } else { reselect = null; replacementId = null; replacementVersion = null } }
    val owner = ShellPageOwner("reference_libraries", state.review?.id ?: state.entry?.entryId,
        sourceIdentity = "${state.kids}:${state.busy}:${state.review?.state}:${state.entry?.generation}",
        canonicalSourceVersion = state.entrySourceVersion?.toString(), canonicalToolIds = (com.halo.app.di.tools.ReferenceLibraryContracts.names + com.halo.app.di.tools.MaterialImageContracts.names).toList(),
        disabledActionReasons = mapOf("page.references.import" to uiText(if (state.kids) "A parent imports reference libraries" else "Please wait")))
    BindShellPageActions(owner, listOf(
        ShellPageAction("page.references.import", "Import library", HaloGlyph.FILE, !state.kids && !state.busy) { replacementId = null; replacementVersion = null; picker.launch(arrayOf("*/*")) },
        ShellPageAction("page.references.search", "Search local libraries", HaloGlyph.SEARCH, state.query.isNotBlank() && !state.busy) { model.search() },
    ))
    BackHandler(state.entry != null) { model.closeEntry() }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(uiText("Reference libraries"), style = MaterialTheme.typography.headlineSmall)
                TextButton(if (state.entry != null) model::closeEntry else onBack) { Text(uiText("Back")) }
            }
            if (state.entry == null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Search", "Libraries", "Imports").filter { !state.kids || it != "Imports" }.forEach { value ->
                    FilterChip(tab == value, { tab = value }, label = { Text(uiText(value)) }, modifier = Modifier.heightIn(min = if (state.kids) 56.dp else 48.dp))
                }
            }
        }
        if (state.loading || state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { error -> item { EverydayMessage(error, true)
            if (state.initializationFailed) TextButton(model::reload) { Text(uiText("Retry")) }
        } }
        val entry = state.entry
        if (entry != null) {
            item {
                Text(entry.title, style = MaterialTheme.typography.headlineMedium)
                state.entrySourceVersion?.let { version -> MaterialImagesPanel(MaterialImageTarget(entry.sourceId, version, entry.generation, entry.entryId, library = true), state.kids) }
                Text(entry.text, style = MaterialTheme.typography.bodyLarge)
                HorizontalDivider()
                Text(entry.publisher + " · " + entry.language)
                if (state.libraries.find { it.source.id == entry.sourceId }?.library?.origin == "USER_IMPORT")
                    Text(uiText("User-imported text; publisher and rights are not verified"), style = MaterialTheme.typography.bodySmall)
                entry.updatedAt?.let { Text(uiText("Source date: %1\$s", it)) }
                if (state.libraries.find { it.source.id == entry.sourceId }?.library?.origin == "MEDLINEPLUS")
                    entry.revision?.let { Text(uiText("Dataset generated: %1\$s", it)) }
                Text(entry.license, style = MaterialTheme.typography.bodySmall)
                if (entry.format == ReferenceTextFormat.WIKITEXT.name) Text(uiText("Original wiki source; server templates are not expanded"), style = MaterialTheme.typography.bodySmall)
                entry.url?.let { url -> TextButton({ runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure { model.reportError("The original source could not be opened") } }) { Text(uiText("Open original source")) } }
            }
        } else when (tab) {
            "Search" -> {
                item {
                    OutlinedTextField(state.query, model::query, enabled = !state.busy, label = { Text(uiText("Search selected libraries")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button({ model.search() }, enabled = !state.busy && state.query.isNotBlank()) { Text(uiText("Search")) }
                    if (state.libraries.none { it.library.generation != null && it.source.selected && it.source.enabled && !it.source.muted }) {
                        Text(uiText("Add a library to search saved reference text offline."))
                        if (!state.kids) TextButton({ tab = "Libraries" }) { Text(uiText("Add reference library")) }
                    }
                    state.results?.takeIf { it.entries.isEmpty() }?.let { Text(uiText("No matching entries. Try the source language or different words.")) }
                }
                items(state.results?.entries.orEmpty(), key = { "${it.sourceId}:${it.generation}:${it.entryId}" }) { hit ->
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(hit.title, style = MaterialTheme.typography.titleMedium); Text(hit.text.take(240))
                            TextButton({ state.results?.sources?.get(hit.sourceId)?.version?.let { model.open(hit, it) } }) { Text(uiText("Read entry")) }
                        }
                    }
                }
                if (state.results?.nextOffset != null) item { TextButton({ model.search(true) }, enabled = !state.busy) { Text(uiText("Next results")) } }
            }
            "Libraries" -> {
                if (!state.kids) item {
                    Button({ replacementId = null; replacementVersion = null; picker.launch(arrayOf("*/*")) }, enabled = !state.busy) { Text(uiText("Import library")) }
                    Text(uiText("XML, JSONL and CSV · compressed files supported"), style = MaterialTheme.typography.bodySmall)
                }
                items(state.libraries, key = { it.source.id }) { row ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(row.library.title, style = MaterialTheme.typography.titleMedium)
                        Text(if (row.library.generation == null) uiText("Not installed") else uiText("%1\$d indexed entries · %2\$s", row.library.entryCount, row.library.language))
                        if (!row.source.selected || !row.source.enabled || row.source.muted) Text(uiText("Excluded from search"))
                        if (!state.kids) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton({ onSource(row.source.id) }) { Text(uiText("Source settings")) }
                            TextButton({ replacementId = row.source.id; replacementVersion = row.source.version; picker.launch(arrayOf("*/*")) }, enabled = !state.busy) { Text(uiText("Update from file")) }
                        }
                    }
                }
                if (!state.kids) item {
                    HorizontalDivider(); Text(uiText("Optional compact downloads"), style = MaterialTheme.typography.titleMedium)
                    ReferenceStarterId.entries.forEach { id ->
                        TextButton({ download = id }, enabled = !state.busy) { Text(uiText(id.title)) }
                        Text(uiText(id.description), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            else -> items(state.imports.take(20), key = { it.id }) { job ->
                Column(Modifier.fillMaxWidth()) {
                    Text(job.title, style = MaterialTheme.typography.titleMedium)
                    Text(uiText(referenceStateLabel(job.state)) + " · " + uiText("%1\$d entries processed", job.processed))
                    TextButton({ model.review(job) }) { Text(uiText("Review progress")) }
                }
            }
        }
    }
    state.review?.takeIf { !state.kids }?.let { job -> EverydaySheet(model::closeReview) {
        Column(Modifier.fillMaxWidth().weight(1f, false).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(job.title, style = MaterialTheme.typography.titleLarge)
            Text(uiText(referenceStateLabel(job.state))); Text(uiText("%1\$d entries processed", job.processed))
            job.message?.let { Text(uiText(it)) }
            if (job.state == "REVIEWING") {
                Text(job.fileName + " · " + uiText("%1\$d KiB", (job.inputBytes + 1023) / 1024))
                Text(uiText(if (job.origin == "USER_IMPORT") "User-imported text; publisher and rights are not verified" else "Downloaded from the official source"), style = MaterialTheme.typography.bodySmall)
                val samples = remember(job.previewJson) { JSONArray(job.previewJson) }
                for (index in 0 until minOf(3, samples.length())) { val sample = samples.getJSONObject(index)
                    Text(sample.getString("title") + " · " + sample.optString("language"), style = MaterialTheme.typography.titleSmall)
                    Text(sample.optString("text"), maxLines = 3)
                }
                Text(uiText("The selected file is imported locally. Its source language, date and rights are retained."), style = MaterialTheme.typography.bodySmall)
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            if (job.state == "REVIEWING") Button({ model.confirm(job) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(uiText("Confirm import")) }
            if (job.state in setOf("INTERRUPTED", "FAILED", "CANCELLED")) {
                Button({ model.resume(job) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(uiText("Resume import")) }
                TextButton({ reselect = job; picker.launch(arrayOf("*/*")) }) { Text(uiText("Reselect original file")) }
            }
            if (job.state != "READY") TextButton({ model.cancel(job) }, enabled = !state.busy) { Text(uiText("Cancel import")) }
            TextButton(model::closeReview) { Text(uiText("Close")) }
        }
    } }
    download?.let { id -> EverydaySheet({ download = null }) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(uiText(id.title), style = MaterialTheme.typography.titleLarge); Text(uiText(id.description))
            Text(uiText("Downloads require Power. You will review the downloaded file separately before importing it."))
            if (id == ReferenceStarterId.PREPAREDNESS) Text(uiText("Source: CDC. Available free from CDC; use by Halo does not imply CDC or US government endorsement."), style = MaterialTheme.typography.bodySmall)
            Button({ download = null; model.download(id) }, enabled = mode == PrivacyMode.CLOUD && !state.busy, modifier = Modifier.fillMaxWidth()) { Text(uiText("Download for review")) }
            TextButton({ download = null }) { Text(uiText("Cancel")) }
        }
    } }
}

internal fun referenceStateLabel(value: String) = when (value) {
    "REVIEWING" -> "Review before import"; "QUEUED" -> "Queued"; "IMPORTING" -> "Importing"; "INDEXING" -> "Indexing"
    "READY" -> "Ready"; "CANCELLED" -> "Cancelled"; "INTERRUPTED" -> "Interrupted"; else -> "Failed"
}
