package com.halo.app.ui.everyday

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.collectors.*
import com.halo.app.language.uiText
import kotlinx.coroutines.*

@Composable
internal fun OfflineCityPicker(busy: Boolean, error: String? = null, onDismiss: () -> Unit, onChoose: (OfflineCity) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    var rows by remember { mutableStateOf<List<OfflineCity>>(emptyList()) }
    var looking by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(query, retry) {
        rows = emptyList(); looking = true; failed = false
        try { delay(200); rows = Graph.offlineCities.search(query, limit = 30); looking = false }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { looking = false; failed = true }
    }
    EverydaySheet({ if (!busy) onDismiss() }) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(uiText("Find a city"), style = MaterialTheme.typography.headlineSmall)
            Text(uiText("Search a city to see its time. No internet or location permission is needed."), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(query, { query = it.take(120) }, label = { Text(uiText("City or country code")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (looking || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { EverydayMessage(it, true) }
            if (failed) {
                EverydayMessage("City search could not be opened. Try again.", true)
                TextButton({ retry++ }, Modifier.heightIn(min = 56.dp)) { Text(uiText("Retry")) }
            } else if (!looking && rows.isEmpty()) Text(uiText("No matching city. Try another spelling or a larger nearby city."))
            LazyColumn(Modifier.weight(1f, false)) {
                items(rows, key = { it.id }) { city -> TextButton({ onChoose(city) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text("${city.name} · ${city.countryCode}\n${city.zoneId}", Modifier.fillMaxWidth())
                } }
            }
            Text(OfflineCityCatalogue.ATTRIBUTION, style = MaterialTheme.typography.bodySmall)
            Text(OfflineCityCatalogue.ATTRIBUTION_URL, style = MaterialTheme.typography.bodySmall)
            TextButton(onDismiss, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(uiText("Cancel")) }
        }
    }
}
