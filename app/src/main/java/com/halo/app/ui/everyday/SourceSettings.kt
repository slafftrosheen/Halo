package com.halo.app.ui.everyday

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.everyday.collectors.*
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloAlertDialog
import com.halo.app.R

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
    var child by remember(source.id, source.version) { mutableStateOf(source.childApproved) }
    var pendingKidsSave by remember(source.id, source.version) { mutableStateOf<CollectorSource?>(null) }
    var category by rememberSaveable(source.id, source.version) { mutableStateOf(source.category.name) }
    var cityName by rememberSaveable(source.id, source.version) { mutableStateOf(source.place?.name) }
    var latitude by rememberSaveable(source.id, source.version) { mutableStateOf(source.place?.latitude) }
    var longitude by rememberSaveable(source.id, source.version) { mutableStateOf(source.place?.longitude) }
    var cityZone by rememberSaveable(source.id, source.version) { mutableStateOf(source.place?.zoneId) }
    var picker by rememberSaveable { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val verifyKidsSave = com.halo.app.ui.screens.kids.rememberKidsGrownUpGate(onVerified = {
        val draft = pendingKidsSave
        pendingKidsSave = null
        if (draft != null) model.save(draft, state.draftNew, kidsVerified = true)
    }, onUnavailable = { pendingKidsSave = null; invalid = true })
    EverydaySheet({ if (!state.busy) discard = true }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(uiText("Source settings"), style = MaterialTheme.typography.headlineSmall)
            state.error?.let { EverydayMessage(it, true) }
            if (invalid) Text(stringResource(R.string.halo_source_check_public_url), color = MaterialTheme.colorScheme.error)
            OutlinedTextField(title, { title = it.take(200) }, enabled = !state.busy, label = { Text(uiText("Source name")) }, modifier = Modifier.fillMaxWidth())
            val air = source.kind == CollectorKind.AIR_QUALITY
            OutlinedTextField(publisher, { publisher = it.take(200) }, enabled = !state.busy && !air, label = { Text(uiText("Publisher")) }, modifier = Modifier.fillMaxWidth())
            if (source.external && !source.kind.usesCityCoordinates) OutlinedTextField(url, { url = it.take(4096) }, enabled = !state.busy,
                label = { Text(stringResource(R.string.halo_source_public_url)) }, modifier = Modifier.fillMaxWidth())
            if (source.kind.usesCityCoordinates) {
                Text(cityName.orEmpty(), style = MaterialTheme.typography.titleMedium)
                Text(cityZone.orEmpty())
                TextButton({ picker = true }, enabled = !state.busy) { Text(uiText(if (air) "Choose air & pollen city" else "Choose weather city")) }
                Text(uiText("City coordinates are sent to Open-Meteo only when this selected source refreshes in Power."), style = MaterialTheme.typography.bodySmall)
            }
            EverydayToggle(stringResource(R.string.sp13_use_answers), selected, !state.busy) { selected = it }
            EverydayToggle(stringResource(R.string.sp13_use_today), selected && !muted, !state.busy) {
                if (it) selected = true
                muted = !it
            }
            EverydayToggle(uiText("Enable refresh"), enabled, !state.busy) { enabled = it }
            if (source.external) {
                OutlinedTextField(language, { language = it.take(20) }, enabled = !state.busy, label = { Text(uiText("Source language · en, ru, lv or another code")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(cadence, { cadence = it.take(4) }, enabled = !state.busy, label = { Text(uiText("Refresh interval in minutes · 15–1440")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                EverydayToggle(stringResource(R.string.sp13_use_kids), child, !state.busy) { child = it }
                Text(stringResource(R.string.sp13_kids_pin), style = MaterialTheme.typography.bodySmall)
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
                    val place = if (source.kind.usesCityCoordinates) WeatherPlace(requireNotNull(cityName), requireNotNull(latitude), requireNotNull(longitude), requireNotNull(cityZone)) else null
                    val endpoint = when {
                        air && place != null -> OpenMeteoAirQuality.endpoint(place)
                        place != null -> OpenMeteoParser.endpoint(place)
                        else -> url.takeIf { source.external }
                    }
                    source.copy(title = title.trim(), publisher = publisher.trim(), endpoint = endpoint,
                        place = place, language = language.trim(), refreshMinutes = cadence.toInt(), selected = selected, enabled = enabled, muted = muted,
                        childApproved = child, category = SourceCategory.valueOf(category))
                }.getOrNull()
                invalid = value == null
                value?.let {
                    if (it.childApproved && (!source.childApproved || state.draftNew || it.endpoint != source.endpoint)) {
                        pendingKidsSave = it
                        verifyKidsSave()
                    } else model.save(it, state.draftNew)
                }
            }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(uiText("Save")) }
            TextButton({ discard = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(uiText("Cancel")) }
        }
    }
    if (picker) OfflineCityPicker(state.busy, onDismiss = { picker = false }, weather = true) { city ->
        if (source.kind == CollectorKind.WEATHER && title == cityName) title = city.name
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
