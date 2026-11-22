package com.halo.app.ui.instruments

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.language.uiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class InstrumentExportReceipt(val versions: List<String>, val payload: String, val revision: Long)

/** Exports the reviewed data to a document chosen by the person, using the current adult authority. */
@Composable
internal fun HaloInstrumentTextExport(
    mimeType: String,
    suggestedName: String,
    buttonTitle: String,
    records: List<String>,
    content: () -> String,
    request: Boolean,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<InstrumentExportReceipt?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    val latestRecords by rememberUpdatedState(records)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType)) { destination ->
        val receipt = pending
        pending = null
        if (destination != null && receipt != null) scope.launch {
            try {
                check(latestRecords == receipt.versions)
                withContext(Dispatchers.IO) {
                    Graph.kidsPreferences.requireAdultAction(receipt.revision)
                    requireNotNull(context.contentResolver.openOutputStream(destination, "wt")).use { stream ->
                        stream.write(receipt.payload.encodeToByteArray())
                    }
                }
                result = "File saved."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { result = "Could not save this export. Review the current items and try again." }
        }
    }
    if (request) AlertDialog(onDismissRequest = onClose, title = { Text(uiText(buttonTitle)) },
        text = { Text(uiText("Save a copy of %1\$d items to a location you choose.", records.size)) },
        confirmButton = { TextButton(enabled = records.isNotEmpty(), onClick = {
            try {
                val payload = content()
                check(payload.length <= 524_288)
                pending = InstrumentExportReceipt(records.toList(), payload, Graph.kidsPreferences.adultActionRevision())
                picker.launch(suggestedName)
            } catch (_: Exception) { pending = null; result = "Could not prepare this export." }
            onClose()
        }) { Text(uiText("Save a copy")) } },
        dismissButton = { TextButton(onClick = onClose) { Text(uiText("Cancel")) } })
    result?.let { message -> AlertDialog(onDismissRequest = { result = null }, title = { Text(uiText(message)) },
        confirmButton = { TextButton(onClick = { result = null }) { Text(uiText("Done")) } }) }
}

internal fun String.asInstrumentCsvCell(): String {
    // Spreadsheets can execute formula-looking text imported from an otherwise plain CSV.
    val text = if (trimStart().firstOrNull() in setOf('=', '+', '-', '@')) "'$this" else this
    return "\"${text.replace("\"", "\"\"")}\""
}
