package com.halo.app.ui.everyday

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.everyday.collectors.*
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloAlertDialog

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SourceSettings(model: SourcesViewModel, state: SourcesPageState, source: CollectorSource) {
    var title by rememberSaveable(source.id, source.version) { mutableStateOf(source.title) }
    var publisher by rememberSaveable(source.id, source.version) { mutableStateOf(source.publisher) }
    var url by rememberSaveable(source.id, source.version) { mutableStateOf(source.endpoint.orEmpty()) }
    var language by rememberSaveable(source.id, source.version) { mutableStateOf(source.language) }
    var cadence by rememberSaveable(source.id, source.version) { mutableStateOf(source.refreshMinutes.toString()) }
    var selected by rememberSaveable(source.id, source.version) { mutableStateOf(source.selected) }
    var enabled by rememberSaveable(source.id, source.version) { mutableStateOf(source.enabled) }
    var muted by rememberSaveable(source.id, source.version) { mutableStateOf(source.muted) }
    var child by rememberSaveable(source.id, source.version) { mutableStateOf(source.childApproved) }
    var category by rememberSaveable(source.id, source.version) { mutableStateOf(source.category.name) }
    var cityName by rememberSaveable(source.id, source.version) { mutableStateOf(source.place?.name) }
    var latitude by rememberSaveable(source.id, source.version) { mutableStateOf(source.place?.latitude) }
    var longitude by rememberSaveable(source.id, source.version) { mutableStateOf(source.place?.longitude) }
    var cityZone by rememberSaveable(source.id, source.version) { mutableStateOf(source.place?.zoneId) }
    var picker by rememberSaveable { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    EverydaySheet({ if (!state.busy) discard = true }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(uiText("Source settings"), style = MaterialTheme.typography.headlineSmall)
            state.error?.let { EverydayMessage(it, true) }
            if (invalid) EverydayMessage("Check the source name, public HTTPS address, language and refresh interval.", true)
            OutlinedTextField(title, { title = it.take(200) }, enabled = !state.busy, label = { Text(uiText("Source name")) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(publisher, { publisher = it.take(200) }, enabled = !state.busy, label = { Text(uiText("Publisher")) }, modifier = Modifier.fillMaxWidth())
            if (source.external && source.kind != CollectorKind.WEATHER) OutlinedTextField(url, { url = it.take(4096) }, enabled = !state.busy,
                label = { Text(uiText("Public HTTPS feed address")) }, modifier = Modifier.fillMaxWidth())
            if (source.kind == CollectorKind.WEATHER) {
                Text(cityName.orEmpty(), style = MaterialTheme.typography.titleMedium)
                Text(cityZone.orEmpty())
                TextButton({ picker = true }, enabled = !state.busy) { Text(uiText("Choose weather city")) }
                Text(uiText("City coordinates are sent to Open-Meteo only when this selected source refreshes in Power."), style = MaterialTheme.typography.bodySmall)
            }
            EverydayToggle(uiText("Include in selected sources"), selected, !state.busy) { selected = it }
            EverydayToggle(uiText("Enable refresh"), enabled, !state.busy) { enabled = it }
            EverydayToggle(uiText("Mute in briefs and proactive suggestions"), muted, !state.busy) { muted = it }
            Text(uiText("Muted sources may still refresh when selected. Disable refresh to stop updates."), style = MaterialTheme.typography.bodySmall)
            if (source.external) {
                OutlinedTextField(language, { language = it.take(20) }, enabled = !state.busy, label = { Text(uiText("Source language · en, ru, lv or another code")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(cadence, { cadence = it.take(4) }, enabled = !state.busy, label = { Text(uiText("Refresh interval in minutes · 15–1440")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                EverydayToggle(uiText("Approve this public source for Kids"), child, !state.busy) { child = it }
                Text(uiText("Approval covers this public source. It does not share your private tasks, notes or activity."), style = MaterialTheme.typography.bodySmall)
            }
            Text(uiText("Category"), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SourceCategory.entries.forEach { value -> FilterChip(category == value.name, { category = value.name }, enabled = !state.busy,
                    label = { Text(uiText(sourceCategoryLabel(value))) }) }
            }
            Text(uiText("Local keeps saved information without internet requests. Android may delay background refreshes to save battery."), style = MaterialTheme.typography.bodySmall)
            Button({
                val value = runCatching {
                    val place = if (source.kind == CollectorKind.WEATHER) WeatherPlace(requireNotNull(cityName), requireNotNull(latitude), requireNotNull(longitude), requireNotNull(cityZone)) else null
                    source.copy(title = title.trim(), publisher = publisher.trim(), endpoint = if (place != null) OpenMeteoParser.endpoint(place) else url.takeIf { source.external },
                        place = place, language = language.trim(), refreshMinutes = cadence.toInt(), selected = selected, enabled = enabled, muted = muted,
                        childApproved = child, category = SourceCategory.valueOf(category))
                }.getOrNull()
                invalid = value == null
                value?.let { model.save(it, state.draftNew) }
            }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(uiText("Save")) }
            TextButton({ discard = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(uiText("Cancel")) }
        }
    }
    if (picker) OfflineCityPicker(state.busy, onDismiss = { picker = false }) { city ->
        cityName = city.name; latitude = city.latitude; longitude = city.longitude; cityZone = city.zoneId; picker = false
    }
    if (discard) HaloAlertDialog({ discard = false }, title = { Text(uiText("Discard this edit?")) },
        text = { Text(uiText("Your saved item is kept. The changes in this form will be discarded.")) },
        confirmButton = { TextButton({ discard = false; model.edit(null) }) { Text(uiText("Discard")) } },
        dismissButton = { TextButton({ discard = false }) { Text(uiText("Keep editing")) } })
}

internal fun sourceCategoryLabel(category: SourceCategory) = when (category) {
    SourceCategory.PERSONAL -> "On this phone"; SourceCategory.LATVIA -> "Latvia"; SourceCategory.WORLD -> "World"
    SourceCategory.SCIENCE -> "Science and space"; SourceCategory.TECHNOLOGY -> "Technology"; SourceCategory.WEATHER -> "Weather"
    SourceCategory.OFFICIAL -> "Official notices"; SourceCategory.CALENDAR -> "Calendar"; SourceCategory.CUSTOM -> "Custom sources"
}
