package com.halo.app.ui.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.data.search.WorkspaceSearchHit
import com.halo.app.data.search.ReceiptTotals
import com.halo.app.data.search.parseReceiptQuery
import com.halo.app.data.search.readLibraryReceiptTotals
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.organizer.PlannerCalendarPreferences
import com.halo.app.language.uiText
import com.halo.app.nativeactions.EverydayWidgetPicker
import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.ui.toolkit.HaloToolkit
import com.halo.app.ui.toolkit.HaloToolkitShelf
import com.halo.app.ui.everyday.EverydayRoutes
import com.halo.app.ui.screens.notebook.HaloLibraryKind
import com.halo.app.ui.screens.notebook.parseLibrarySearchPrompt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

internal enum class HaloHomeTab(val route: String, val title: Int) {
    TODAY("home/today", R.string.halo11_home_today),
    LIBRARY("notebook", R.string.halo11_home_library),
    TOOLS("home/tools", R.string.halo11_home_tools),
    READS("home/reads", R.string.halo11_home_reads);

    companion object {
        fun at(route: String?): HaloHomeTab? = entries.firstOrNull { it.route == route?.substringBefore('?') }
    }
}

@Composable
internal fun HaloHomeTabs(active: HaloHomeTab, onOpen: (String) -> Unit) {
    val easy = com.halo.app.ui.theme.rememberVisualPreferences().viewMode == com.halo.app.ui.theme.HaloViewMode.EASY
    val ink = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(17.dp), color = ink.surfaceContainerLow,
        border = BorderStroke(1.dp, ink.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (tab in HaloHomeTab.entries) {
                val selected = active == tab
                Surface(
                    onClick = { if (!selected) onOpen(tab.route) },
                    modifier = Modifier.weight(1f).heightIn(min = if (easy) 56.dp else 48.dp).semantics {
                        role = Role.Tab
                        this.selected = selected
                    },
                    color = if (selected) ink.primary.copy(alpha = .10f) else ink.surfaceContainerLow,
                    shape = RoundedCornerShape(13.dp),
                    border = if (selected) BorderStroke(1.dp, ink.primary.copy(alpha = .65f)) else null,
                ) {
                    Box(Modifier.padding(horizontal = 2.dp, vertical = 4.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text(stringResource(tab.title), style = MaterialTheme.typography.labelLarge,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                            color = if (selected) ink.primary else ink.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HaloHomeTools(onOpen: (String) -> Unit) {
    val visual = com.halo.app.ui.theme.rememberVisualPreferences()
    var showWidgets by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { showWidgets = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.alive_widget_title))
            }
            TextButton(onClick = { onOpen("merchant_checkout") }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.halo11_checkout_title))
            }
        }
        HaloToolkitShelf(visual.viewMode == com.halo.app.ui.theme.HaloViewMode.EASY, onEdit = null, onOpen = onOpen)
        val recent = com.halo.app.ui.screens.home.recentLibraryBlock("continue")
        com.halo.app.ui.screens.home.HaloHomeBlockDeck(listOfNotNull(recent),
            com.halo.app.ui.screens.home.rememberHaloBlockChoices("tools"), onOpen)
    }
    if (showWidgets) EverydayWidgetPicker(onDismiss = { showWidgets = false })
}

internal data class LibrarySearchRow(
    val kind: HaloLibraryKind,
    val id: String,
    val title: String,
    val snippet: String?,
    val hit: WorkspaceSearchHit? = null,
    val route: String? = null,
)

internal suspend fun searchEveryLibraryKind(query: String): List<LibrarySearchRow> {
    val family = Graph.kidsPreferences.adultActionRevision()
    val sources = Graph.notebookViewModel.librarySourcesSnapshot()
    val visibility = PlannerCalendarPreferences.read(Graph.storage)
    val prompt = parseLibrarySearchPrompt(query)
    val words = prompt.terms.split(Regex("\\s+")).filter(String::isNotBlank)
    fun matches(text: String) = words.all { text.contains(it, ignoreCase = true) }
    val workspace = Graph.workspaceUseCases.searchLibrary(prompt.terms).hits
        .distinctBy { it.objectId.value to it.nodeId }.mapNotNull { hit ->
            val item = Graph.workspaceUseCases.getObject(hit.objectId.value)?.takeIf { prompt.includes(it.createdAt) } ?: return@mapNotNull null
            val kind = sources.kind(item) ?: return@mapNotNull null
            val route = if (kind == HaloLibraryKind.PLACE) "maps?placeId=${android.net.Uri.encode(hit.objectId.value)}" else null
            LibrarySearchRow(kind, "${hit.objectId.value}:${hit.nodeId}", hit.title, hit.snippet, hit, route)
        }
    val existingIds = workspace.mapNotNullTo(hashSetOf()) { it.hit?.objectId?.value }
    val aliases = Graph.notebookViewModel.libraryAliasIds(prompt.terms).asSequence().filterNot(existingIds::contains).take(20).toList()
        .mapNotNull { id ->
            val item = Graph.workspaceUseCases.getObject(id)?.takeIf { prompt.includes(it.createdAt) } ?: return@mapNotNull null
            val kind = sources.kind(item) ?: return@mapNotNull null
            val op = "library/item/$id"
            LibrarySearchRow(kind, "$id:alias", item.title, null, route = HaloDeepLinks.PREFIX + op)
        }
    val cards = sources.cards.asSequence().filter { prompt.includes(it.createdAtEpochMs) && matches(it.question) }.take(20).map { card ->
        val session = android.net.Uri.encode(card.sessionId)
        val id = android.net.Uri.encode(card.id)
        LibrarySearchRow(HaloLibraryKind.CARD, card.id, card.question, null,
            route = "notebook/study/$session?review=true&cardId=$id")
    }.toList()
    val plans = Graph.organizerRepository.records().asSequence()
        .filter { prompt.includes(it.createdAt.toEpochMilli()) && it.deletedAt == null && visibility.appearance(it.calendar).visible &&
            matches("${it.title} ${it.details} ${it.location}") }
        .take(20).map { plan ->
            LibrarySearchRow(HaloLibraryKind.PLAN, plan.id, plan.title, plan.location.takeIf(String::isNotBlank),
                route = EverydayRoutes.organizer(plan.id))
        }.toList()
    val people = sources.people.asSequence().filter { matches(it.name) }.take(20).toList().mapNotNull { person ->
        val photo = Graph.workspaceUseCases.getObject(person.photoId)?.takeIf {
            prompt.includes(it.createdAt) && Graph.workspaceUseCases.getFile(person.photoId)?.version == person.photoVersion
        } ?: return@mapNotNull null
        LibrarySearchRow(HaloLibraryKind.PERSON, person.id, person.name, photo.title,
            route = HaloDeepLinks.PREFIX + "library/item/" + android.net.Uri.encode(person.photoId))
    }
    Graph.kidsPreferences.requireAdultAction(family)
    return workspace + aliases + cards + plans + people
}

private data class SearchPanel(
    val query: String = "",
    val hits: List<LibrarySearchRow> = emptyList(),
    val loading: Boolean = false,
    val failed: Boolean = false,
    val receipts: ReceiptTotals? = null,
)

private val searchableSettings = mapOf(
    "settings/intelligence/providers" to R.string.halo11_search_connections,
    "settings/intelligence/context" to R.string.halo11_search_privacy,
    "permissions" to R.string.halo11_search_permissions,
    "settings/voice/input" to R.string.halo11_search_speech_input,
    "settings/voice/output" to R.string.halo11_search_speech_output,
    "notification_settings" to R.string.halo11_search_notifications,
    "storage_backup" to R.string.halo11_search_backup,
    "devices" to R.string.halo11_search_devices,
    "guided_setup" to R.string.halo11_search_setup,
    "family" to R.string.halo11_search_family,
)

@Composable
internal fun HaloLibrarySearch(
    initialQuery: String,
    search: suspend (String) -> List<LibrarySearchRow>,
    onOpenHit: (WorkspaceSearchHit) -> Unit,
    onOpenRoute: (String) -> Unit,
) {
    var query by rememberSaveable(initialQuery) { mutableStateOf(initialQuery.take(200)) }
    var retry by remember { mutableIntStateOf(0) }
    val normalized = query.trim()
    val latestSearch by rememberUpdatedState(search)
    val envelopes = remember { Graph.appDatabase.privateLibraryDao().observeEnvelopes().distinctUntilChanged() }
    val results by produceState(SearchPanel(), normalized, retry) {
        if (normalized.isBlank()) {
            value = SearchPanel()
            return@produceState
        }
        try {
            envelopes.collectLatest {
                value = SearchPanel(query = normalized, loading = true)
                delay(180)
                try {
                    val receiptQuery = parseReceiptQuery(normalized)
                    value = if (receiptQuery != null) SearchPanel(query = normalized, receipts = readLibraryReceiptTotals(receiptQuery))
                        else SearchPanel(normalized, latestSearch(normalized))
                }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { value = SearchPanel(query = normalized, failed = true) }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { value = SearchPanel(query = normalized, failed = true) }
    }
    val toolNames = HaloToolkit.tools.associateWith { stringResource(it.title) }
    val settingNames = searchableSettings.mapValues { stringResource(it.value) }
    val tools = if (normalized.isEmpty()) emptyList() else HaloToolkit.tools.filter {
        it.route.contains(normalized, ignoreCase = true) || toolNames.getValue(it).contains(normalized, ignoreCase = true)
    }.take(12)
    val settings = if (normalized.isEmpty()) emptyList() else settingNames.entries.filter {
        it.key.contains(normalized, ignoreCase = true) || it.value.contains(normalized, ignoreCase = true)
    }
    val current = results.takeIf { it.query == normalized } ?: SearchPanel(query = normalized, loading = normalized.isNotEmpty())
    val excerpt = current.hits.singleOrNull()
        ?.takeIf { normalized.endsWith('?') && it.hit != null && !it.snippet.isNullOrBlank() }
    fun open(row: LibrarySearchRow) {
        if (row.route != null) onOpenRoute(row.route) else row.hit?.let(onOpenHit)
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(stringResource(R.string.halo11_search_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            HaloTextField(query, { query = it.take(200) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                kind = VoiceFieldKind.SEARCH, label = { Text(stringResource(R.string.halo11_search_hint)) })
        }
        if (parseLibrarySearchPrompt(normalized).monthSaved != null) item {
            Text(stringResource(R.string.halo11_search_previous_month_saved), style = MaterialTheme.typography.bodySmall)
        }
        if (excerpt != null) item("library-excerpt") {
            SearchRow(stringResource(R.string.halo11_search_saved_excerpt, excerpt.title), excerpt.snippet) { open(excerpt) }
        }
        current.receipts?.let { totals ->
            item("library-receipts-summary") {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(stringResource(R.string.halo_receipt_totals), style = MaterialTheme.typography.titleMedium)
                    if (totals.receipts.isEmpty()) Text(stringResource(R.string.halo_receipt_no_totals))
                    else totals.byCurrency.forEach { (currency, amount) ->
                        val count = totals.receipts.count { it.currency == currency }
                        Text(stringResource(R.string.halo_receipt_total_amount, currency, amount.toPlainString(), count))
                    }
                    if (totals.unreadable > 0 || totals.limited) Text(stringResource(R.string.halo_receipt_limited),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            items(totals.receipts, key = { "receipt:${it.objectId}" }) { receipt ->
                SearchRow(receipt.title, "${receipt.date} · ${receipt.currency} ${receipt.amount.toPlainString()}") {
                    onOpenRoute(HaloDeepLinks.PREFIX + "library/item/" + android.net.Uri.encode(receipt.objectId))
                }
            }
        }
        if (tools.isNotEmpty()) item { Text(stringResource(R.string.halo11_home_tools), style = MaterialTheme.typography.titleSmall) }
        items(tools, key = { "tool:${it.route}" }) { tool ->
            SearchRow(toolNames.getValue(tool), null) { onOpenRoute(tool.route) }
        }
        if (settings.isNotEmpty()) item { Text(stringResource(R.string.halo11_search_settings), style = MaterialTheme.typography.titleSmall) }
        items(settings, key = { "setting:${it.key}" }) { setting ->
            SearchRow(setting.value, null) { onOpenRoute(setting.key) }
        }
        if (current.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (current.failed) item { TextButton(onClick = { retry++ }) { Text(uiText("Try again")) } }
        if (!current.loading && !current.failed && normalized.isNotEmpty() && current.hits.isEmpty() && current.receipts == null && tools.isEmpty() && settings.isEmpty())
            item { Text(stringResource(R.string.halo11_search_empty)) }
        for ((kind, group) in current.hits.groupBy(LibrarySearchRow::kind)) {
            item("type:${kind.name}") { Text(stringResource(kind.group), style = MaterialTheme.typography.titleSmall) }
            items(group, key = { row -> "hit:${row.kind.name}:${row.id}" }) { row ->
                SearchRow(row.title, row.snippet.takeUnless { row.id == excerpt?.id }) { open(row) }
            }
        }
    }
}

@Composable
private fun SearchRow(title: String, description: String?, onOpen: () -> Unit) {
    Surface(onClick = onOpen, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!description.isNullOrBlank()) Text(description, style = MaterialTheme.typography.bodySmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
