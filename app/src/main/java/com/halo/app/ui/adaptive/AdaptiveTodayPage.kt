package com.halo.app.ui.adaptive

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.ui.screens.home.HaloHomeBlock
import com.halo.app.ui.theme.HaloLookMotion
import com.halo.app.ui.theme.HaloViewMode
import com.halo.app.ui.theme.rememberVisualPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.halo.app.data.workspace.AdaptivePlanner
import com.halo.app.data.workspace.AdaptiveSlot
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private fun HaloHomeBlock.adaptiveSlot(): AdaptiveSlot? = when {
    id.startsWith("event:") || id.startsWith("phone-calendar/") -> AdaptiveSlot.PLANNER
    id == "weather" -> AdaptiveSlot.WEATHER
    id == "study" -> AdaptiveSlot.STUDY
    id == "reads" -> AdaptiveSlot.READS
    id == "library" -> AdaptiveSlot.LIBRARY
    id == "focus" -> AdaptiveSlot.FOCUS
    id == "winddown" -> AdaptiveSlot.WIND_DOWN
    else -> null
}

private data class SavedAdaptivePage(val id: String, val title: String, val slots: List<AdaptiveSlot>,
    val big: Set<AdaptiveSlot>, val revision: Long)
private data class AdaptiveEdit(val slots: List<AdaptiveSlot>, val big: Set<AdaptiveSlot>)

private class AdaptivePagePreferences(context: Context) {
    private val disk = context.applicationContext.getSharedPreferences("halo_adaptive_pages_v1", Context.MODE_PRIVATE)

    private fun slots(value: String) = value.split(',')
        .mapNotNull { key -> AdaptiveSlot.entries.firstOrNull { it.name == key } }.distinct()

    fun pages(): List<SavedAdaptivePage> {
        val source = disk.getString("pages", null)
        if (source == null) {
            val legacySlots = slots(disk.getString("pinned", null).orEmpty())
            return if (legacySlots.isEmpty()) emptyList() else listOf(SavedAdaptivePage("legacy", "", legacySlots, emptySet(), 1))
        }
        val rows = runCatching { JSONArray(source) }.getOrElse { JSONArray() }
        return List(minOf(rows.length(), 24)) { rows.optJSONObject(it) }.mapNotNull { row ->
            val item = row ?: return@mapNotNull null
            val id = item.optString("id")
            val revision = item.optLong("revision")
            val selected = slots(item.optString("slots"))
            if (!id.matches(Regex("[a-z0-9-]{1,36}")) || selected.isEmpty() || revision <= 0) return@mapNotNull null
            SavedAdaptivePage(id, item.optString("title").take(80), selected, slots(item.optString("large")).toSet().intersect(selected.toSet()), revision)
        }.distinctBy { it.id }
    }

    fun hidden(): Set<AdaptiveSlot> = disk.getStringSet("hidden", null).orEmpty()
        .mapNotNullTo(mutableSetOf()) { key -> AdaptiveSlot.entries.firstOrNull { it.name == key } }

    suspend fun pin(previous: SavedAdaptivePage?, title: String, edit: AdaptiveEdit, verify: () -> Unit): SavedAdaptivePage =
        withContext(Dispatchers.IO) {
            require(edit.slots.isNotEmpty() && edit.slots.size <= 9 && edit.slots.distinct() == edit.slots && title.length in 1..80)
            require(edit.big.all { it in edit.slots })
            synchronized(disk) {
                val current = pages()
                if (previous == null) require(current.size < 24)
                else check(current.any { it.id == previous.id && it.revision == previous.revision })
                val saved = SavedAdaptivePage(previous?.id ?: UUID.randomUUID().toString(), title,
                    edit.slots, edit.big, (previous?.revision ?: 0L) + 1)
                val update = listOf(saved) + current.filterNot { it.id == saved.id }
                val rows = JSONArray(update.map { item ->
                    JSONObject().put("id", item.id).put("title", item.title).put("revision", item.revision)
                        .put("slots", item.slots.joinToString(",") { it.name }).put("large", item.big.joinToString(",") { it.name })
                })
                verify()
                check(disk.edit().putString("pages", rows.toString()).remove("pinned").commit())
                saved
            }
        }

    suspend fun hide(slot: AdaptiveSlot, verify: () -> Unit) = withContext(Dispatchers.IO) {
        synchronized(disk) {
            val next = hidden().mapTo(mutableSetOf()) { it.name } + slot.name
            verify()
            check(disk.edit().putStringSet("hidden", next).commit())
        }
    }

    suspend fun clearLearning(verify: () -> Unit) = withContext(Dispatchers.IO) {
        synchronized(disk) {
            verify()
            check(disk.edit().remove("hidden").commit())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AdaptiveTodayPage(available: List<HaloHomeBlock>, open: (String) -> Unit, showSaved: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { AdaptivePagePreferences(context) }
    val scope = rememberCoroutineScope()
    val visual = rememberVisualPreferences()
    val limit = when (visual.viewMode) {
        HaloViewMode.EASY -> 4
        HaloViewMode.STANDARD -> 7
        HaloViewMode.COMPACT -> 9
    }
    val bySlot = available.distinctBy { it.adaptiveSlot() }.mapNotNull { item -> item.adaptiveSlot()?.let { it to item } }.toMap()
    var hidden by remember { mutableStateOf(store.hidden()) }
    var request by rememberSaveable { mutableStateOf("") }
    var savedPages by remember { mutableStateOf(if (showSaved) store.pages() else emptyList()) }
    var selectedPage by remember { mutableStateOf<SavedAdaptivePage?>(null) }
    var original by remember { mutableStateOf(AdaptiveEdit(emptyList(), emptySet())) }
    var layout by remember { mutableStateOf(original.slots) }
    var bigger by remember { mutableStateOf(original.big) }
    var undo by remember { mutableStateOf(emptyList<AdaptiveEdit>()) }
    var askName by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var explanation by remember { mutableStateOf<AdaptiveSlot?>(null) }
    var swap by remember { mutableStateOf<AdaptiveSlot?>(null) }
    var alternates by remember { mutableStateOf(false) }
    var edited by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var receipt by remember { mutableStateOf<String?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    val savedText = uiText("Page saved")
    val failedText = uiText("The change could not be saved")
    val notReady = uiText("There is no current source for this block")

    fun change(next: List<AdaptiveSlot>, large: Set<AdaptiveSlot> = bigger) {
        if (next != layout || large != bigger) undo = (undo + AdaptiveEdit(layout, bigger)).takeLast(20)
        layout = next
        bigger = large
        receipt = null
    }
    fun write(operation: suspend (() -> Unit) -> Unit, committed: () -> Unit) {
        if (busy) return
        val family = Graph.kidsPreferences
        val lease = runCatching { family.adultActionRevision() }.getOrNull() ?: return
        busy = true
        scope.launch {
            try {
                operation {
                    family.requireAdultAction(lease)
                    Graph.haloPause.requireActive()
                }
                family.requireAdultAction(lease)
                committed()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { receipt = failedText }
            finally { busy = false }
        }
    }
    fun showPlan(slots: List<AdaptiveSlot>) {
        selectedPage = null
        original = AdaptiveEdit(slots, emptySet())
        change(slots, emptySet())
        alternates = false
        edited = false
        generation++
    }
    fun assemble() {
        val matches = AdaptivePlanner.select(request.trim(), bySlot.keys - hidden, limit)
        if (matches.isEmpty()) alternates = true else showPlan(matches)
    }

    com.halo.app.ui.zone6.Zone6ButtonSheet(stringResource(
        if (showSaved) R.string.adaptive_pages_you_made else R.string.adaptive_made_for_you), onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (showSaved && savedPages.isNotEmpty()) {
                Text(stringResource(R.string.adaptive_pages_you_made), style = MaterialTheme.typography.titleMedium)
                savedPages.forEach { page ->
                    TextButton(onClick = {
                        selectedPage = page
                        title = page.title
                        original = AdaptiveEdit(page.slots, page.big)
                        layout = page.slots
                        bigger = page.big
                        undo = emptyList()
                        edited = false
                        receipt = null
                        generation++
                    }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(page.title.ifBlank { stringResource(R.string.adaptive_made_for_you) })
                    }
                }
            }
            HaloTextField(request, { request = it.take(280); alternates = false }, VoiceFieldKind.TEXT,
                label = { Text(uiText("What are you doing?")) }, modifier = Modifier.fillMaxWidth(), maxChars = 280, singleLine = true)
            Button(onClick = ::assemble, enabled = request.isNotBlank() && !busy && bySlot.isNotEmpty(),
                modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Make a page")) }
            if (alternates) {
                Text(stringResource(R.string.adaptive_which_helps), style = MaterialTheme.typography.titleMedium)
                val availableSlots = bySlot.keys.filterNot(hidden::contains)
                val ordinary = availableSlots.take(limit)
                val different = availableSlots.filterNot { it == ordinary.firstOrNull() }.take(limit)
                listOf(ordinary, different).filter { it.isNotEmpty() }.distinct().forEach { option ->
                    OutlinedButton(onClick = { showPlan(option) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(option.joinToString(" · ") { bySlot.getValue(it).source })
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.adaptive_standard_layout)) }
            }
            layout.forEachIndexed { index, slot ->
                key(generation, slot) {
                    val still = visual.motionLevel == HaloLookMotion.STILL
                    var visible by remember(still) { mutableStateOf(still || showSaved || edited) }
                    LaunchedEffect(still) { if (!visible) { delay(70L * index); visible = true } }
                    AnimatedVisibility(visible, enter = fadeIn(tween(if (still) 0 else 220))) {
                        val item = bySlot[slot]
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (item == null) Text(notReady)
                                else {
                                    Text(stringResource(item.heading), style = MaterialTheme.typography.labelLarge)
                                    Text(item.title, style = if (slot in bigger) MaterialTheme.typography.headlineSmall
                                        else MaterialTheme.typography.titleMedium)
                                    item.supporting?.let { Text(it) }
                                    Text(item.source, style = MaterialTheme.typography.bodyMedium)
                                    if (explanation == slot) Text(item.explanation ?: stringResource(item.why))
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (item != null) {
                                        TextButton(onClick = { onDismiss(); open(item.route) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                            Text(uiText("Open"))
                                        }
                                        TextButton(onClick = { explanation = slot.takeUnless { it == explanation } },
                                            modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.adaptive_why)) }
                                    }
                                    if (edited) {
                                        TextButton(onClick = {
                                            change(layout, if (slot in bigger) bigger - slot else bigger + slot)
                                        }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.adaptive_bigger)) }
                                        TextButton(onClick = { swap = slot }, enabled = bySlot.keys.any { it !in layout },
                                            modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.adaptive_swap)) }
                                        TextButton(onClick = {
                                            write({ store.hide(slot, it) }) {
                                                hidden = store.hidden()
                                                change(layout - slot, bigger - slot)
                                            }
                                        }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                                            Text(stringResource(R.string.adaptive_less_like_this))
                                        }
                                        TextButton(onClick = {
                                            change(layout.toMutableList().also { it.removeAt(index); it.add(index - 1, slot) })
                                        }, enabled = index > 0, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Move up")) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (layout.isNotEmpty() || undo.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { edited = !edited }, enabled = layout.isNotEmpty() && !busy) {
                    Text(stringResource(if (edited) R.string.adaptive_done else R.string.adaptive_edit))
                }
                TextButton(onClick = { undo.lastOrNull()?.let { step -> layout = step.slots; bigger = step.big }; undo = undo.dropLast(1) },
                    enabled = undo.isNotEmpty() && !busy) { Text(stringResource(R.string.adaptive_undo)) }
                TextButton(onClick = { change(original.slots, original.big) },
                    enabled = !busy && AdaptiveEdit(layout, bigger) != original) {
                    Text(stringResource(R.string.adaptive_reset_page))
                }
                Button(onClick = { title = selectedPage?.title.orEmpty(); askName = true },
                    enabled = layout.isNotEmpty() && !busy) { Text(stringResource(R.string.adaptive_pin_page)) }
            }
            receipt?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
    if (askName) AlertDialog(onDismissRequest = { askName = false },
        title = { Text(stringResource(R.string.adaptive_pin_page)) },
        text = {
            HaloTextField(title, { title = it.take(80) }, VoiceFieldKind.SHORT,
                label = { Text(uiText("Page name")) }, maxChars = 80, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = {
                askName = false
                val name = title.trim()
                val base = selectedPage
                val newLayout = AdaptiveEdit(layout.toList(), bigger.toSet())
                var result: SavedAdaptivePage? = null
                write({ check -> result = store.pin(base, name, newLayout, check) }) {
                    selectedPage = result
                    if (showSaved) savedPages = store.pages()
                    receipt = savedText
                }
            }, enabled = title.isNotBlank() && !busy) { Text(uiText("Save")) }
        }, dismissButton = { TextButton(onClick = { askName = false }) { Text(uiText("Cancel")) } })
    swap?.let { old ->
        AlertDialog(onDismissRequest = { swap = null }, title = { Text(stringResource(R.string.adaptive_swap)) }, text = {
            Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                bySlot.filterKeys { it !in layout }.forEach { (slot, item) ->
                    TextButton(onClick = { change(layout.map { if (it == old) slot else it }, bigger - old); swap = null },
                        modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(item.heading)) }
                }
            }
        }, confirmButton = { TextButton(onClick = { swap = null }) { Text(uiText("Cancel")) } })
    }
}

@Composable
internal fun AdaptiveLearningSettings() {
    val context = LocalContext.current
    val store = remember(context) { AdaptivePagePreferences(context) }
    val scope = rememberCoroutineScope()
    var count by remember { mutableIntStateOf(store.hidden().size) }
    var ask by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    Text(uiText("Block preferences saved on this phone") + " · " + count)
    TextButton(onClick = { ask = true }, enabled = count > 0, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(R.string.adaptive_privacy_clear))
    }
    if (error) Text(uiText("The change could not be saved"), color = MaterialTheme.colorScheme.error)
    if (ask) AlertDialog(onDismissRequest = { ask = false },
        title = { Text(stringResource(R.string.adaptive_privacy_clear)) },
        confirmButton = { TextButton(onClick = {
            ask = false
            scope.launch {
                val family = Graph.kidsPreferences
                try {
                    val revision = family.adultActionRevision()
                    store.clearLearning { family.requireAdultAction(revision); Graph.haloPause.requireActive() }
                    count = store.hidden().size
                    error = false
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = true }
            }
        }) { Text(uiText("Clear")) } },
        dismissButton = { TextButton(onClick = { ask = false }) { Text(uiText("Cancel")) } })
}
