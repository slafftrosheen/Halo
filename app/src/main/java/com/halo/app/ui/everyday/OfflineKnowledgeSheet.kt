package com.halo.app.ui.everyday

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.collectors.KnowledgeHit
import com.halo.app.everyday.collectors.KnowledgePackState
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.ui.zone6.Zone6ButtonSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun OfflineKnowledgeSheet(power: Boolean, dismiss: () -> Unit, openPublisher: (String) -> Boolean) {
    val knowledge = Graph.offlineKnowledge
    val revision by knowledge.changes.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<KnowledgeHit?>(null) }
    val downloadError = stringResource(R.string.knowledge_failed)
    val linkError = stringResource(R.string.knowledge_link_failed)
    fun showLink(url: String) { problem = if (openPublisher(url)) null else linkError }
    val packs by produceState<List<KnowledgePackState>?>(null, revision) { value = knowledge.catalogue() }
    val search = query.trim()
    val hits by produceState<List<KnowledgeHit>?>(null, search, revision) {
        value = null
        if (search.isNotBlank()) value = knowledge.search(search)
    }
    Zone6ButtonSheet(stringResource(R.string.knowledge_title), dismiss) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                HaloTextField(value = query, onValueChange = { query = it.take(160); selected = null },
                    label = { Text(stringResource(R.string.knowledge_query)) }, modifier = Modifier.fillMaxWidth(),
                    kind = VoiceFieldKind.SEARCH, singleLine = true, maxChars = 160)
                if (!power) Text(stringResource(R.string.knowledge_power), style = MaterialTheme.typography.bodySmall)
            }
            problem?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            if (search.isBlank()) {
                if (packs == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                items(packs.orEmpty(), key = { it.pack.id }) { record ->
                    val pack = record.pack
                    val snapshot = record.snapshot
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(uiText(pack.title), style = MaterialTheme.typography.titleSmall)
                        Text(pack.publisher, style = MaterialTheme.typography.labelMedium)
                        if (snapshot != null) {
                            Text(stringResource(R.string.knowledge_count, snapshot.entries.size, snapshot.retrievedAt.take(10)),
                                style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text(stringResource(if (pack.searchable) R.string.knowledge_available else R.string.knowledge_external),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (pack.searchable) {
                            TextButton(enabled = power && busy == null, onClick = {
                                busy = pack.id
                                problem = null
                                scope.launch {
                                    try {
                                        knowledge.download(pack.id)
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        problem = downloadError
                                    } finally { busy = null }
                                }
                            }) {
                                Text(stringResource(when {
                                    busy == pack.id -> R.string.knowledge_loading
                                    snapshot != null -> R.string.knowledge_update
                                    else -> R.string.knowledge_download
                                }))
                            }
                        } else {
                            TextButton(enabled = power, onClick = { showLink(pack.sourceUrl) }) {
                                Text(stringResource(if (pack.sourceUrl.endsWith(".zim")) R.string.knowledge_browser_download
                                    else R.string.knowledge_open_download))
                            }
                        }
                    }
                }
            } else {
                val result = hits
                if (result == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                else if (result.isEmpty()) item { Text(stringResource(R.string.knowledge_empty)) }
                else items(result) { hit ->
                    Column {
                        TextButton(onClick = { selected = if (selected == hit) null else hit }) { Text(hit.entry.title) }
                        Text(stringResource(R.string.knowledge_attribution, hit.pack.publisher, hit.retrievedAt.take(10)),
                            style = MaterialTheme.typography.labelMedium)
                        if (selected == hit) {
                            Text(hit.entry.text, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { showLink(hit.entry.url) }, enabled = power) {
                                Text(stringResource(R.string.knowledge_publisher))
                            }
                        }
                    }
                }
            }
        }
    }
}
