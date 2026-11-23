package com.halo.app.ui.adaptive

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.data.workspace.*
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.organizer.android.PhoneCalendarAgenda
import com.halo.app.everyday.presentation.route
import com.halo.app.language.uiText
import com.halo.app.nativeactions.AdaptiveWidgetReview
import com.halo.app.nativeactions.EverydayWidgetPicker
import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.ui.theme.HaloLookMotion
import com.halo.app.ui.theme.HaloViewMode
import com.halo.app.ui.theme.rememberVisualPreferences
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private data class AdaptiveDraft(
    val original: AdaptiveLayout = AdaptiveLayout(emptyList()),
    val layout: AdaptiveLayout = original,
    val page: AdaptivePageRef? = null,
    val title: String = "",
    val undo: List<AdaptiveLayout> = emptyList(),
    val checkpoint: AdaptiveLayout? = null,
) {
    val dirty: Boolean get() = checkpoint?.let { layout != it } ?: layout.slots.isNotEmpty()
    fun change(next: AdaptiveLayout): AdaptiveDraft =
        if (next == layout) this else copy(layout = next, undo = (undo + layout).takeLast(20))
    fun undoChange(): AdaptiveDraft = undo.lastOrNull()?.let { copy(layout = it, undo = undo.dropLast(1)) } ?: this
}

private val draftSaver = Saver<AdaptiveDraft, String>(
    save = { draft -> JSONObject().put("original", draft.original.json()).put("layout", draft.layout.json())
        .put("id", draft.page?.id).put("revision", draft.page?.revision).put("title", draft.title)
        .put("checkpoint", draft.checkpoint?.json())
        .put("undo", JSONArray(draft.undo.map { it.json() })).toString() },
    restore = { raw -> runCatching {
        val value = JSONObject(raw)
        val page = value.optString("id", "").takeIf(String::isNotEmpty)?.let {
            AdaptivePageRef(it, value.getLong("revision"))
        }
        val undo = value.getJSONArray("undo")
        AdaptiveDraft(AdaptiveLayout.read(value.getJSONObject("original")), AdaptiveLayout.read(value.getJSONObject("layout")),
            page, value.optString("title").take(80),
            List(undo.length().coerceAtMost(20)) { AdaptiveLayout.read(undo.getJSONObject(it)) },
            value.optJSONObject("checkpoint")?.let { AdaptiveLayout.read(it) }
                ?: page?.let { AdaptiveLayout.read(value.getJSONObject("original")) })
    }.getOrNull() },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AdaptiveTodayPage(
    blocks: Map<AdaptiveSlot, AdaptiveBlock>,
    open: (String) -> Unit,
    showSaved: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    initialPage: SavedAdaptivePage? = null,
) {
    val context = LocalContext.current
    val store = remember(context) { AdaptivePageStore(context) }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val visual = rememberVisualPreferences()
    val limit = when (visual.viewMode) {
        HaloViewMode.EASY -> 4
        HaloViewMode.STANDARD -> 7
        HaloViewMode.COMPACT -> 9
    }
    val savedTitle = stringResource(R.string.adaptive_pages_you_made)
    val newTitle = stringResource(R.string.adaptive_made_for_you)
    val changedText = stringResource(R.string.adaptive_page_changed)
    val sourceChanged = stringResource(R.string.adaptive_page_source_changed)
    val failedText = stringResource(R.string.adaptive_page_save_failed)
    val savedText = stringResource(R.string.adaptive_page_saved)
    val removedText = stringResource(R.string.adaptive_page_removed)
    val hiddenText = stringResource(R.string.adaptive_page_suggest_less_receipt)
    val deniedText = stringResource(R.string.adaptive_page_adult_only)
    var draft by rememberSaveable(stateSaver = draftSaver) {
        mutableStateOf(initialPage?.let {
            AdaptiveDraft(it.layout, page = it.ref, title = it.title, checkpoint = it.layout)
        } ?: AdaptiveDraft())
    }
    var editing by rememberSaveable { mutableStateOf(false) }
    var showLibrary by rememberSaveable { mutableStateOf(showSaved && initialPage == null) }
    var backToLibrary by rememberSaveable { mutableStateOf(initialPage != null) }
    var request by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(emptyList<SavedAdaptivePage>()) }
    var hidden by remember { mutableStateOf(emptySet<AdaptiveSlot>()) }
    var loaded by remember { mutableStateOf(false) }
    var readFailed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var receipt by remember { mutableStateOf<String?>(null) }
    var alternates by rememberSaveable { mutableStateOf(false) }
    var explanation by rememberSaveable { mutableStateOf<String?>(null) }
    var chooseBlock by remember { mutableStateOf(false) }
    var replaceBlock by remember { mutableStateOf<AdaptiveSlot?>(null) }
    var askName by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var copyPage by rememberSaveable { mutableStateOf(false) }
    var showWidgetReview by rememberSaveable { mutableStateOf(false) }
    var routineAuthority by rememberSaveable { mutableStateOf<Long?>(null) }
    var discardAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var confirmDelete by remember { mutableStateOf<SavedAdaptivePage?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    var animatePlan by remember { mutableStateOf(false) }
    val layout = draft.layout
    val pageScroll = rememberScrollState()
    val routineExport = remember(layout) { AdaptiveRoutineExport.from(layout) }
    val noRoutineSections = stringResource(R.string.adaptive_routine_no_sections)
    routineAuthority?.let { revision ->
        AdaptiveRoutineReview(routineExport, draft.title, revision, open, onBack = { routineAuthority = null })
        return
    }
    val widgetExport = remember(layout) { AdaptiveWidgetExport.from(layout) }
    val noWidgetSections = stringResource(R.string.adaptive_widget_no_sections)
    val suggestions = AdaptivePlanner.suggestions(blocks.keys, hidden, limit)

    suspend fun reload() {
        try {
            val family = Graph.kidsPreferences
            val revision = family.adultActionRevision()
            val result = withContext(Dispatchers.IO) {
                family.requireAdultAction(revision)
                store.pages() to store.hidden()
            }
            family.requireAdultAction(revision)
            saved = result.first
            hidden = result.second
            readFailed = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            saved = emptyList()
            hidden = emptySet()
            readFailed = true
        } finally {
            loaded = true
        }
    }

    LaunchedEffect(store) { store.changes().collect { reload() } }
    fun leaveDraft(next: () -> Unit) {
        if (draft.dirty) discardAction = next else next()
    }

    fun back() {
        if (busy) return
        if (!showLibrary && backToLibrary) showLibrary = true else leaveDraft(onBack)
    }

    BackHandler(onBack = ::back)

    fun update(next: AdaptiveLayout) {
        draft = draft.change(next)
        receipt = null
    }

    fun <T> operate(onError: String = failedText, operation: suspend (() -> Unit) -> T, finish: (T) -> Unit) {
        if (busy) return
        val family = Graph.kidsPreferences
        val lease = runCatching { family.adultActionRevision() }.getOrNull()
        if (lease == null) {
            receipt = deniedText
            return
        }
        busy = true
        receipt = null
        scope.launch {
            try {
                val verify: () -> Unit = {
                    family.requireAdultAction(lease)
                    Graph.haloPause.requireActive()
                }
                verify()
                val result = operation(verify)
                verify()
                finish(result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: AdaptivePageChanged) {
                reload()
                receipt = changedText
            } catch (_: Exception) {
                receipt = onError
            } finally {
                busy = false
            }
        }
    }

    fun openBlock(target: AdaptiveBlockTarget) {
        operate(sourceChanged, operation = { verify ->
            when (target) {
                is AdaptiveBlockTarget.Owner -> withContext(Dispatchers.IO) { verify(); target.target.route() }
                is AdaptiveBlockTarget.LibraryRoute -> target.route
                is AdaptiveBlockTarget.PhoneEvent -> {
                    check(PhoneCalendarAgenda.permitted(context))
                    null
                }
            }
        }, finish = { route ->
            if (target is AdaptiveBlockTarget.PhoneEvent) {
                if (!PhoneCalendarAgenda.permitted(context) ||
                    runCatching { context.startActivity(PhoneCalendarAgenda.intent(target.event)) }.isFailure) receipt = sourceChanged
            } else if (route != null) open(route)
        })
    }

    fun loadPage(page: SavedAdaptivePage) {
        draft = AdaptiveDraft(page.layout, page = page.ref, title = page.title, checkpoint = page.layout)
        backToLibrary = true
        editing = false
        showLibrary = false
        alternates = false
        explanation = null
        receipt = null
        animatePlan = false
        generation++
    }

    fun showPlan(slots: List<AdaptiveSlot>) {
        draft = AdaptiveDraft(original = AdaptiveLayout(slots))
        backToLibrary = false
        editing = false
        showLibrary = false
        alternates = false
        explanation = null
        receipt = null
        animatePlan = true
        generation++
    }

    fun assemble(text: String) {
        if (busy) return
        val matches = AdaptivePlanner.select(text.take(280).trim(), blocks.keys, limit)
        if (matches.isEmpty()) alternates = true else showPlan(matches)
    }

    fun startSave(asCopy: Boolean) {
        name = draft.title
        copyPage = asCopy
        askName = true
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 680.dp
        Column(Modifier.fillMaxSize().verticalScroll(pageScroll)
            .padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = ::back, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(uiText("Back"))
                }
                TextButton(onClick = { showLibrary = !showLibrary }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (showLibrary) stringResource(R.string.adaptive_page_editor) else savedTitle)
                }
            }
            Text(if (showLibrary) savedTitle else draft.title.ifBlank { newTitle },
                Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)

            if (showLibrary) {
                TextButton(onClick = { leaveDraft { showPlan(emptyList()); request = "" } }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.adaptive_page_new)) }
                if (!loaded) Text(uiText("Loading"))
                else if (readFailed) Text(stringResource(R.string.adaptive_page_read_failed), color = MaterialTheme.colorScheme.error)
                else if (saved.isEmpty()) Text(stringResource(R.string.adaptive_page_empty))
                saved.forEach { page ->
                    key(page.ref.id) {
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                                TextButton(onClick = { leaveDraft { loadPage(page) } }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text(page.title.ifBlank { newTitle }, style = MaterialTheme.typography.titleMedium)
                                }
                                Text(page.layout.slots.joinToString(" · ") { blocks[it]?.heading ?: it.localizedLabel(resources) },
                                    style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { confirmDelete = page }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text(stringResource(R.string.adaptive_page_remove_saved))
                                }
                            }
                        }
                    }
                }
            } else {
                if (layout.slots.isEmpty() || alternates) {
                    HaloTextField(request, { request = it.take(280); alternates = false }, VoiceFieldKind.TEXT,
                        label = { Text(stringResource(R.string.adaptive_page_request)) }, modifier = Modifier.fillMaxWidth(), maxChars = 280,
                        singleLine = true, enabled = !busy, onFinalSpeech = {
                            request = it.take(280)
                            assemble(request)
                        })
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = { assemble(request) }, enabled = request.isNotBlank() && !busy,
                            modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.adaptive_page_make)) }
                        if (layout.slots.isEmpty() && draft.undo.isEmpty())
                            TextButton(onClick = { chooseBlock = true }, enabled = !busy,
                                modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Add block")) }
                    }
                }
                if (alternates) {
                    Text(stringResource(R.string.adaptive_which_helps), style = MaterialTheme.typography.titleMedium)
                    if (suggestions.isEmpty()) Text(stringResource(R.string.adaptive_page_request_hint))
                    suggestions.forEach { option ->
                        OutlinedButton(onClick = { showPlan(option) }, enabled = !busy,
                            modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(option.joinToString(" · ") { blocks.getValue(it).heading })
                        }
                    }
                    TextButton(onClick = ::back, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.adaptive_standard_layout))
                    }
                }
                if (layout.slots.isNotEmpty() || draft.undo.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { editing = !editing }, enabled = layout.slots.isNotEmpty() && !busy,
                            modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(if (editing) R.string.adaptive_done else R.string.adaptive_edit))
                        }
                        TextButton(onClick = { draft = draft.undoChange(); receipt = null }, enabled = draft.undo.isNotEmpty() && !busy,
                            modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.adaptive_undo)) }
                        TextButton(onClick = onRefresh, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.adaptive_page_refresh))
                        }
                        Button(onClick = { startSave(false) }, enabled = layout.slots.isNotEmpty() && !busy,
                            modifier = Modifier.heightIn(min = 48.dp)) {
                            val label = when {
                                draft.page == null -> R.string.adaptive_pin_page
                                draft.dirty -> R.string.adaptive_page_save_changes
                                else -> R.string.adaptive_page_rename
                            }
                            Text(stringResource(label))
                        }
                        TextButton(onClick = {
                            if (widgetExport.available.isEmpty()) receipt = noWidgetSections
                            else showWidgetReview = true
                        }, enabled = layout.slots.isNotEmpty() && !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.adaptive_widget_open))
                        }
                        TextButton(onClick = {
                            if (routineExport.sections.isEmpty()) receipt = noRoutineSections
                            else runCatching { Graph.kidsPreferences.adultActionRevision() }
                                .onSuccess { routineAuthority = it }.onFailure { receipt = deniedText }
                        }, enabled = layout.slots.isNotEmpty() && !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.adaptive_routine_open))
                        }
                    }
                    if (editing || layout.slots.isEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { chooseBlock = true }, enabled = layout.slots.size < limit && !busy,
                            modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Add block")) }
                        TextButton(onClick = { update(draft.original) }, enabled = layout != draft.original && !busy,
                            modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.adaptive_reset_page)) }
                        if (draft.page != null) TextButton(onClick = { startSave(true) }, enabled = layout.slots.isNotEmpty() && !busy,
                            modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.adaptive_page_save_copy)) }
                    }
                }
                adaptiveRows(layout, wide).forEach { row ->
                    key(row.first()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { slot ->
                                key(generation, slot) {
                                    val still = visual.motionLevel == HaloLookMotion.STILL
                                    var visible by remember(still) { mutableStateOf(still || !animatePlan || editing) }
                                    LaunchedEffect(still) {
                                        if (!visible) { delay(70L * layout.slots.indexOf(slot).coerceAtLeast(0)); visible = true }
                                    }
                                    AnimatedVisibility(visible, modifier = Modifier.weight(1f),
                                        enter = fadeIn(tween(if (still) 0 else 220))) {
                                        val block = blocks[slot]
                                        if (block != null) AdaptiveBlockCard(
                                            block, slot in layout.large, explanation == slot.name, editing, busy,
                                            canMoveUp = layout.slots.firstOrNull() != slot, canMoveDown = layout.slots.lastOrNull() != slot,
                                            canSwap = blocks.keys.any { it !in layout.slots },
                                            onEdit = { editing = true },
                                            onRowOpen = ::openBlock,
                                            onOpen = block.target?.let { target -> { openBlock(target) } },
                                            onWhy = { explanation = slot.name.takeUnless { it == explanation } },
                                            onResize = { update(layout.copy(large = if (slot in layout.large) layout.large - slot else layout.large + slot)) },
                                            onMove = { direction -> update(layout.move(slot, direction)) },
                                            onSwap = { replaceBlock = slot },
                                            onRemove = { update(layout.remove(slot)) },
                                            onSuggestLess = { operate(operation = { store.hide(slot, it) }, finish = {
                                                hidden = hidden + slot
                                                receipt = hiddenText
                                            }) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
            receipt?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium) }
        }
    }

    if (showWidgetReview && widgetExport.available.isNotEmpty()) {
        EverydayWidgetPicker(adaptiveReview = AdaptiveWidgetReview(widgetExport,
            widgetExport.unsupported.map { it.localizedLabel(resources) })) { showWidgetReview = false }
    }
    if (askName) AlertDialog(onDismissRequest = { askName = false },
        title = { Text(stringResource(when {
            copyPage -> R.string.adaptive_page_save_copy
            draft.page == null -> R.string.adaptive_pin_page
            draft.dirty -> R.string.adaptive_page_save_changes
            else -> R.string.adaptive_page_rename
        })) },
        text = {
            HaloTextField(name, { name = it.take(80) }, VoiceFieldKind.SHORT, label = { Text(stringResource(R.string.adaptive_page_name)) },
                maxChars = 80, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = {
                askName = false
                val previous = draft.page.takeUnless { copyPage }
                val savedLayout = layout
                val title = name.trim()
                operate(operation = { store.save(previous, title, savedLayout, it) }, finish = {
                    val page = it
                    saved = if (previous == null) listOf(page) + saved else saved.map { old ->
                        if (old.ref.id == page.ref.id) page else old
                    }
                    draft = draft.copy(page = page.ref, title = page.title, checkpoint = page.layout)
                    receipt = savedText
                })
            }, enabled = name.isNotBlank() && !busy) { Text(uiText("Save")) }
        }, dismissButton = { TextButton(onClick = { askName = false }) { Text(uiText("Cancel")) } })
    if (chooseBlock || replaceBlock != null) {
        val old = replaceBlock
        fun close() { chooseBlock = false; replaceBlock = null }
        AlertDialog(onDismissRequest = ::close,
            title = { Text(if (old == null) uiText("Add block") else stringResource(R.string.adaptive_swap)) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    blocks.filterKeys { it !in layout.slots }.forEach { (slot, block) ->
                        TextButton(onClick = {
                            if (old != null) update(layout.swap(old, slot))
                            else if (layout.slots.size < limit) {
                                update(layout.copy(slots = layout.slots + slot))
                                editing = true
                                alternates = false
                            }
                            close()
                        }, modifier = Modifier.heightIn(min = 48.dp)) { Text(block.heading) }
                    }
                }
            }, confirmButton = { TextButton(onClick = ::close) { Text(uiText("Cancel")) } })
    }
    discardAction?.let { next ->
        AlertDialog(onDismissRequest = { discardAction = null },
            title = { Text(stringResource(R.string.adaptive_page_discard)) },
            text = { Text(stringResource(R.string.adaptive_page_discard_detail)) },
            confirmButton = { TextButton(onClick = { discardAction = null; next() }) { Text(uiText("Discard")) } },
            dismissButton = { TextButton(onClick = { discardAction = null }) { Text(uiText("Keep editing")) } })
    }
    confirmDelete?.let { page ->
        AlertDialog(onDismissRequest = { confirmDelete = null }, title = { Text(stringResource(R.string.adaptive_page_remove_saved)) },
            text = { Text(page.title.ifBlank { newTitle }) }, confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    operate(operation = { store.delete(page.ref, it) }, finish = {
                        saved = saved.filterNot { it.ref == page.ref }
                        if (draft.page == page.ref) draft = draft.copy(page = null, checkpoint = null)
                        receipt = removedText
                    })
                }, enabled = !busy) { Text(uiText("Remove")) }
            }, dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(uiText("Cancel")) } })
    }
}

internal fun adaptiveRows(layout: AdaptiveLayout, wide: Boolean): List<List<AdaptiveSlot>> = buildList {
    var waiting: AdaptiveSlot? = null
    for (slot in layout.slots) {
        val first = waiting
        when {
            !wide || slot in layout.large -> {
                if (first != null) add(listOf(first))
                add(listOf(slot))
                waiting = null
            }
            first != null -> {
                add(listOf(first, slot))
                waiting = null
            }
            else -> waiting = slot
        }
    }
    waiting?.let { add(listOf(it)) }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun AdaptiveBlockCard(block: AdaptiveBlock, large: Boolean, explained: Boolean, editing: Boolean, busy: Boolean,
    canMoveUp: Boolean, canMoveDown: Boolean, canSwap: Boolean, onEdit: () -> Unit, onOpen: (() -> Unit)?, onWhy: () -> Unit,
    onRowOpen: (AdaptiveBlockTarget) -> Unit, onResize: () -> Unit, onMove: (Int) -> Unit, onSwap: () -> Unit, onRemove: () -> Unit, onSuggestLess: () -> Unit) {
    val locale = LocalResources.current.configuration.locales[0]
    val editLabel = stringResource(R.string.adaptive_edit)
    val openLabel = uiText("Open")
    OutlinedCard(Modifier.fillMaxWidth().then(
        if (!editing && !busy) Modifier.combinedClickable(onClick = onOpen ?: onEdit, onClickLabel = if (onOpen == null) editLabel else openLabel,
            onLongClick = onEdit, onLongClickLabel = editLabel) else Modifier)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(block.heading, Modifier.semantics { heading() }, style = MaterialTheme.typography.labelLarge)
            if (block.state == AdaptiveBlockState.LOADING) {
                Surface(Modifier.fillMaxWidth(.65f).height(18.dp), shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant) {}
                Text(uiText("Loading"), style = MaterialTheme.typography.bodySmall)
            } else {
                Text(block.value, style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                    color = if (block.state == AdaptiveBlockState.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                if (block.source.isNotBlank()) Text(block.source, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                block.rows.take(if (large) 4 else 2).forEach { row ->
                    if (row.target != null && !editing) {
                        TextButton(onClick = { onRowOpen(row.target) }, enabled = !busy,
                            modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                            Column {
                                Text(row.title, style = MaterialTheme.typography.bodyMedium)
                                if (row.detail.isNotBlank()) Text(row.detail, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        if (row.title != block.value) Text(row.title, style = MaterialTheme.typography.bodyMedium)
                        if (row.detail.isNotBlank()) Text(row.detail, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (block.state == AdaptiveBlockState.OFFLINE) block.observedAt?.let { timestamp ->
                    val time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale)
                        .withZone(ZoneId.systemDefault()).format(timestamp)
                    Text(stringResource(R.string.adaptive_last_checked, time), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (explained) Text(block.reason, style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onOpen != null) TextButton(onClick = onOpen, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(openLabel)
                }
                TextButton(onClick = onWhy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.adaptive_why))
                }
            }
            if (editing) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onResize, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (large) stringResource(R.string.adaptive_page_smaller) else stringResource(R.string.adaptive_bigger))
                }
                TextButton(onClick = { onMove(-1) }, enabled = canMoveUp && !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(uiText("Move up"))
                }
                TextButton(onClick = { onMove(1) }, enabled = canMoveDown && !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(uiText("Move down"))
                }
                TextButton(onClick = onSwap, enabled = canSwap && !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.adaptive_swap))
                }
                TextButton(onClick = onRemove, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.adaptive_page_remove_block)) }
                TextButton(onClick = onSuggestLess, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.adaptive_less_like_this))
                }
            }
        }
    }
}

@Composable
internal fun AdaptiveLearningSettings() {
    val context = LocalContext.current
    val store = remember(context) { AdaptivePageStore(context) }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    var preferences by remember { mutableStateOf(emptySet<AdaptiveSlot>()) }
    LaunchedEffect(store) {
        store.changes().collect { preferences = withContext(Dispatchers.IO) { store.hidden() } }
    }
    var ask by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    Text(stringResource(R.string.adaptive_page_stored_preferences) + " · " + preferences.size)
    if (preferences.isNotEmpty()) Text(preferences.sorted().joinToString(" · ") { it.localizedLabel(resources) })
    TextButton(onClick = { ask = true }, enabled = preferences.isNotEmpty(), modifier = Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(R.string.adaptive_privacy_clear))
    }
    if (error) Text(stringResource(R.string.adaptive_page_save_failed), color = MaterialTheme.colorScheme.error)
    if (ask) AlertDialog(onDismissRequest = { ask = false },
        title = { Text(stringResource(R.string.adaptive_privacy_clear)) },
        confirmButton = { TextButton(onClick = {
            ask = false
            scope.launch {
                val family = Graph.kidsPreferences
                try {
                    val revision = family.adultActionRevision()
                    store.clearLearning { family.requireAdultAction(revision); Graph.haloPause.requireActive() }
                    preferences = store.hidden()
                    error = false
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = true }
            }
        }) { Text(uiText("Clear")) } },
        dismissButton = { TextButton(onClick = { ask = false }) { Text(uiText("Cancel")) } })
}
