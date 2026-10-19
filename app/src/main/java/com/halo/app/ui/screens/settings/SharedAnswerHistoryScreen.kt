package com.halo.app.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.language.uiText
import com.halo.app.privacy.SharedAnswerHistory
import com.halo.app.ui.design.HaloScreenHeader
import kotlinx.coroutines.launch

@Composable fun SharedAnswerHistoryScreen(history: SharedAnswerHistory, onBack: () -> Unit) {
    val entries by history.state.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    val dates = remember(locale) { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, locale) }
    var confirmClear by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
    val visible = entries.filter { it.recordedAt >= cutoff }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { HaloScreenHeader(uiText("What left this phone"), navigation = onBack) }
        item { Text(uiText("Online answer requests · last 30 days"), style = MaterialTheme.typography.titleSmall) }
        item { Text(uiText("Records start with this version. Message contents are not stored here. Other app actions and voice services are not included."),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (visible.isEmpty()) item {
            Text(uiText("No online answer requests recorded"), Modifier.padding(vertical = 24.dp), style = MaterialTheme.typography.titleMedium)
        }
        itemsIndexed(visible) { _, entry ->
            Surface(shape = com.halo.app.ui.design.HaloShapes.card, color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(entry.service, style = MaterialTheme.typography.titleSmall)
                    Text(uiText(if (entry.answered) "Answer request completed" else "Request attempted · delivery not confirmed"), style = MaterialTheme.typography.bodyMedium)
                    Text(dates.format(java.util.Date(entry.recordedAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (visible.isNotEmpty()) item { TextButton(onClick = { confirmClear = true }, enabled = !clearing) { Text(uiText("Clear this history")) } }
        if (failure) item { Text(uiText("Could not save your change. Try again."), color = MaterialTheme.colorScheme.error) }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { if (!clearing) confirmClear = false },
        title = { Text(uiText("Clear this history?")) },
        text = { Text(uiText("This removes these records from this phone. It does not remove anything held by an online service.")) },
        confirmButton = { TextButton(enabled = !clearing, onClick = {
            scope.launch {
                clearing = true
                try { history.clear(); confirmClear = false; failure = false }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { failure = true; confirmClear = false }
                finally { clearing = false }
            }
        }) { Text(uiText("Clear history")) } },
        dismissButton = { TextButton(enabled = !clearing, onClick = { confirmClear = false }) { Text(uiText("Cancel")) } })
}
