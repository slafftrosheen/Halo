package com.halo.app.ui.screens.activity

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.halo.app.context.ContextSourceReceiptFrame
import com.halo.app.context.ContextSourceReceipts
import com.halo.app.context.SourceReceiptStatus
import com.halo.app.language.uiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Explicit inspection only. A recorded source revision is never replaced by today's version. */
@Composable
internal fun ContextSourcesView(runId: String, receipts: ContextSourceReceipts, onOpenObject: (String) -> Unit) {
    var open by remember(runId) { mutableStateOf(false) }
    var refresh by remember(runId) { mutableIntStateOf(0) }
    var frame by remember(runId) { mutableStateOf<ContextSourceReceiptFrame?>(null) }
    var failed by remember(runId) { mutableStateOf(false) }
    fun closeInspector() { open = false; frame = null; failed = false }
    LaunchedEffect(open, runId, refresh) {
        if (open) {
            frame = null; failed = false
            try {
                frame = withContext(Dispatchers.IO) { receipts.read(runId) }
                failed = frame == null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
        }
    }
    TextButton(onClick = { frame = null; failed = false; open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(uiText("Sources for this request"))
    }
    if (open) AlertDialog(onDismissRequest = ::closeInspector, title = { Text(uiText("Sources for this request")) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val snapshot = frame
                when {
                    failed -> Text(uiText("Source history is unavailable right now."))
                    snapshot == null -> Text(uiText("Checking saved source references…"))
                    !snapshot.recorded -> Text(uiText("No source receipt was recorded for this request."))
                    else -> {
                        if (snapshot.sources.isEmpty() && snapshot.memoryIds.isEmpty()) Text(uiText("No source links were stored in this receipt."))
                        snapshot.sources.forEachIndexed { index, source ->
                            TextButton(onClick = { closeInspector(); onOpenObject(source.objectId) },
                                enabled = source.currentVersion != null,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(source.title?.takeIf(String::isNotBlank) ?: uiText("Source %1\$d", index + 1),
                                        style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        color = if (source.currentVersion != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                    Text(uiText(when (source.status) {
                                        SourceReceiptStatus.UNCHANGED -> "Matches the recorded source version."
                                        SourceReceiptStatus.CHANGED -> "Source changed after this was recorded."
                                        SourceReceiptStatus.UNKNOWN -> "This receipt has no saved source version."
                                        SourceReceiptStatus.UNAVAILABLE -> "Source is currently unavailable."
                                    }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (source.textAnchor != null) Text(uiText("Selected passage"), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        if (snapshot.memoryIds.isNotEmpty()) Text(uiText("Memories recorded for this request: %1\$d", snapshot.memoryIds.size))
                        if (snapshot.truncated) Text(uiText("Only part of the saved source history is shown."))
                        Text(uiText("Opening a source shows its current contents."), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = ::closeInspector, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Done")) } },
        dismissButton = { TextButton(onClick = { frame = null; failed = false; refresh++ }, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Refresh")) } })
}
