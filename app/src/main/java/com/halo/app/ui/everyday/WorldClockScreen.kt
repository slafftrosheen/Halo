package com.halo.app.ui.everyday

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloAlertDialog
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.*
import com.halo.app.ui.zone5.*
import com.halo.app.ui.screens.notebook.ToolZoneHeader
import kotlinx.coroutines.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorldClockScreen(model: WorldClockViewModel, onBack: () -> Unit, initialZone: String? = null, initialLabel: String? = null) {
    val state by model.state.collectAsStateWithLifecycle()
    val familyReady by Graph.kidsPreferences.loadedState.collectAsStateWithLifecycle()
    val familyEnabled by Graph.kidsPreferences.enabled.collectAsStateWithLifecycle()
    val kids = !familyReady || familyEnabled
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val now by produceState(Instant.now(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { while (isActive) { value = Instant.now(); delay(1000) } }
    }
    var picker by rememberSaveable { mutableStateOf(false) }
    var previewName by rememberSaveable { mutableStateOf(initialLabel ?: initialZone) }
    var previewZone by rememberSaveable { mutableStateOf(initialZone) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var editVersion by rememberSaveable { mutableStateOf(0L) }
    var label by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(kids) { if (kids) { editId = null; model.clearUndo() } }
    val owner = ShellPageOwner("everyday_clocks", sourceIdentity = "${state.clocks.map { it.id to it.version }}:$picker:$editId:$kids:${state.busy}",
        helpDescription = uiText("Choose cities from the offline catalogue. Clock times follow their time zones and daylight-saving rules."),
        guidanceBlocked = state.busy || picker || editId != null,
        defaultDisabledActionReason = uiText(if (state.clocks.size >= 24) "Remove a clock before adding another." else "Wait for the current action to finish."))
    val canAdd = !state.busy && (kids || state.clocks.size < 24)
    BindShellPageActions(owner, listOf(
        ShellPageAction("page.clocks.add", uiText(if (kids) "Find a city" else "Add clock"), HaloGlyph.WATCH, canAdd) { picker = true },
        ShellPageAction("page.clocks.undo", uiText("Undo removal"), HaloGlyph.PREVIOUS, state.undoId != null && !kids && !state.busy) { model.undo() },
    ))
    if (LocalShellHaloOrb.current) BindShellUtilityBand(owner) { ToolZoneHeader(uiText("World clocks"), onBack) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Zone5PageHeading(uiText("World clocks"), uiText("Local time and the places you choose."))
            ClockTime(uiText("On this phone"), ZoneId.systemDefault(), now)
            if (!LocalShellHaloOrb.current) TextButton(onBack, Modifier.heightIn(min = 56.dp)) { Text(uiText("Back")) }
        }
        if (!kids && (state.loading || state.busy)) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.takeIf { !kids }?.let { item {
            EverydayMessage(it, true)
            if (state.readFailed) TextButton(model::retry, Modifier.heightIn(min = 56.dp)) { Text(uiText("Retry")) }
        } }
        if (state.undoId != null && !kids) item {
            EverydayMessage("Clock removed. You can undo this change.")
            TextButton(model::undo, enabled = !state.busy) { Text(uiText("Undo")) }
        }
        previewZone?.let { selectedZone -> item { ClockTime(previewName.orEmpty(), ZoneId.of(selectedZone), now) } }
        if (!kids) items(state.clocks, key = { it.id }) { clock ->
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    ClockTime(clock.label, ZoneId.of(clock.zoneId), now)
                    FlowRow {
                        TextButton({ editId = clock.id; editVersion = clock.version; label = clock.label }, enabled = !state.busy) { Text(uiText("Rename")) }
                        TextButton({ model.remove(clock) }, enabled = !state.busy) { Text(uiText("Remove")) }
                    }
                    FlowRow {
                        val position = state.clocks.indexOfFirst { it.id == clock.id }
                        listOf(-1 to "Move up", 1 to "Move down").forEach { (delta, text) ->
                            val actionLabel = uiText(text)
                            TextButton({ model.move(clock.id, delta) }, enabled = !state.busy && position + delta in state.clocks.indices,
                                modifier = Modifier.semantics { contentDescription = "$actionLabel: ${clock.label}" }) { Text(actionLabel) }
                        }
                    }
                }
            }
        }
        item {
            if (!state.loading && !state.readFailed && state.clocks.isEmpty() || kids) Text(uiText("Search a city to see its time. No internet or location permission is needed."))
            Text(uiText("Up to 24 saved clocks. City data: GeoNames, CC BY 4.0."), style = MaterialTheme.typography.bodySmall)
            if (state.clocks.size >= 24 && !kids) Text(uiText("Remove a clock before adding another."))
            if (!LocalShellHaloOrb.current || kids) TextButton({ picker = true }, enabled = canAdd, modifier = Modifier.heightIn(min = 56.dp)) { Text(uiText(if (kids) "Find a city" else "Add clock")) }
        }
    }
    if (picker) OfflineCityPicker(state.busy, state.error.takeIf { !kids }, { picker = false }) { city ->
        if (kids) { previewName = "${city.name} · ${city.countryCode}"; previewZone = city.zoneId; picker = false }
        else model.add(city) { picker = false }
    }
    editId?.takeIf { !kids }?.let { id -> HaloAlertDialog(onDismissRequest = { if (!state.busy) editId = null },
        title = { Text(uiText("Rename clock")) }, text = { Column {
            OutlinedTextField(label, { label = it.take(120) }, label = { Text(uiText("Clock name")) }, singleLine = true)
            state.error?.let { EverydayMessage(it, true) }
        } }, confirmButton = { TextButton({ model.rename(id, editVersion, label) { editId = null } }, enabled = label.isNotBlank() && !state.busy) { Text(uiText("Save")) } },
        dismissButton = { TextButton({ editId = null }, enabled = !state.busy) { Text(uiText("Cancel")) } }) }
}

@Composable
private fun ClockTime(label: String, zone: ZoneId, now: Instant) {
    val locale = LocalConfiguration.current.locales[0]
    Text(label, style = MaterialTheme.typography.titleMedium)
    Text(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone).format(now), style = MaterialTheme.typography.displaySmall)
    Text(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).withZone(zone).format(now), style = MaterialTheme.typography.bodyMedium)
    val offset = now.atZone(zone).offset.id.let { if (it == "Z") "+00:00" else it }
    Text("${zone.id} · UTC$offset", style = MaterialTheme.typography.bodySmall)
}
