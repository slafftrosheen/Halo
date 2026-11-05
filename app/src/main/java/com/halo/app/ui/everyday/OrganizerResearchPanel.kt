package com.halo.app.ui.everyday

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.collectors.*
import com.halo.app.language.uiText
import kotlinx.coroutines.*

@Composable
internal fun OrganizerResearchPanel(model: OrganizerViewModel, state: OrganizerPageState, onOpen: (String) -> Unit) {
    TextButton({ onOpen(EverydayRoutes.LIBRARIES) }, Modifier.fillMaxWidth()) { Text(uiText("Reference libraries")) }
    val sources by remember { Graph.collectorRepository.observe() }.collectAsStateWithLifecycle(emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var sourceQuery by rememberSaveable { mutableStateOf("") }
    var provider by rememberSaveable { mutableStateOf("wikipedia") }
    val language = LocalConfiguration.current.locales[0].language.takeIf { it in setOf("en", "ru", "lv", "pl") } ?: "en"
    var results by remember { mutableStateOf<List<ResearchEvidence>>(emptyList()) }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(query, sources) {
        results = emptyList(); error = false
        if (query.isNotBlank()) {
            delay(200)
            try {
                val family = Graph.kidsPreferences.adultActionRevision()
                val evidence = withContext(Dispatchers.IO) { Graph.organizerResearch.search(query) }
                Graph.kidsPreferences.requireAdultAction(family)
                results = evidence
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(uiText("Search / Research"), style = MaterialTheme.typography.headlineSmall)
        Text(uiText("Search retained evidence offline. To retrieve more, save an explicit research source and refresh it in Power."))
        Text(uiText("Free reference and document providers have different coverage. Archived material is not guaranteed current."))
        if (provider == "public_page") Text(uiText("Enter one public HTTPS page address. Halo checks robots policy and retains readable text without following links or page redirects."))
        OutlinedTextField(query, { query = it.take(400) }, label = { Text(uiText("Search retained evidence")) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
        OrganizerChoice("Research provider", provider, ResearchProviderRegistry.public.list().map { it.id to it.title }, !state.busy) { provider = it }
        OutlinedTextField(sourceQuery, { sourceQuery = it.take(400) }, label = { Text(uiText(if (provider == "public_page") "Public HTTPS page address" else "Topic or search query")) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
        Button({ model.saveResearch(sourceQuery, provider, language) {} }, enabled = !state.busy && sourceQuery.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(uiText("Save selected research source")) }
        sources.filter { it.source.kind == CollectorKind.RESEARCH }.forEach { source ->
            HorizontalDivider()
            Text(source.source.title, style = MaterialTheme.typography.titleMedium)
            Text(source.source.publisher)
            Text(if (source.source.selected) uiText("Selected") else uiText("Not selected"))
            source.snapshot?.let { Text(uiText("Retrieved %1\$s", it.retrievedAt.toString())) }
            TextButton({ model.refreshResearch(source.source) }, enabled = !state.busy && source.source.selected && source.source.enabled) { Text(uiText("Refresh selected source")) }
            TextButton({ onOpen(EverydayRoutes.source(source.source.id)) }) { Text(uiText("Open source")) }
        }
        if (error) EverydayMessage("Retained evidence changed or could not be read. Search again after reviewing your selected sources.", true)
        if (query.isNotBlank() && results.isEmpty() && !error) Text(uiText("No matching retained evidence. This search made no network request."))
        results.filter { evidence -> sources.any { it.source.id == evidence.sourceId && it.source.version == evidence.sourceVersion && it.source.selected && it.source.enabled && !it.source.muted } }.forEach { evidence ->
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(evidence.title, style = MaterialTheme.typography.titleMedium)
                    Text(evidence.passage)
                    Text(evidence.provider + " · " + evidence.retrievedAt)
                    evidence.publishedAt?.let { Text(uiText("Published %1\$s", it.toString())) }
                    evidence.updatedAt?.let { Text(uiText("Updated %1\$s", it.toString())) }
                    if (evidence.stale) Text(uiText("Cached / stale"))
                    evidence.canonicalUrl?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    TextButton({
                        if (evidence.sourceType == "LOCAL_LIBRARY" && evidence.libraryGeneration != null)
                            onOpen(EverydayRoutes.libraryEntry(evidence.sourceId, evidence.sourceVersion, evidence.libraryGeneration, evidence.id))
                        else onOpen(EverydayRoutes.source(evidence.sourceId, evidence.id))
                    }) { Text(uiText("Open evidence")) }
                }
            }
        }
    }
}
