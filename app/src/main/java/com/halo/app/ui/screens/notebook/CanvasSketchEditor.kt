package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.canvas.v13.InkSheets
import com.halo.app.ui.theme.v13.Halo13Animation
import com.halo.app.ui.theme.v13.Halo13Motion
import com.halo.sdk.runtime.ink.InkCodec
import com.halo.sdk.runtime.ink.InkSelectionContext

@Composable
internal fun CanvasSketchPreview(encoded: String, modifier: Modifier = Modifier,
    selection: InkSelectionContext? = null,
    ephemeralStroke: com.halo.sdk.runtime.ink.InkStroke? = null, mirrorCopies: Int = 1,
    strokeWidthFraction: Float? = null) {
    val document = remember(encoded, selection) {
        val source = InkCodec.decode(encoded) ?: return@remember null
        if (selection == null) source else {
            val selectedIds = selection.selectedStrokeIds.toSet()
            source.copy(strokes = source.strokes.filter { it.strokeId in selectedIds })
        }
    }
    val ink = if (selection == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        document?.let {
            if (selection == null) {
                val rect = inkBounds(size.width, size.height, it.aspectMilli)
                drawInkCopies(rect, mirrorCopies) {
                    drawInk(it, ink, rect, strokeWidthFraction?.let { fraction -> rect.width * fraction })
                }
            }
            else {
                val frame = inkSelectionFrame(size.width, size.height, it.aspectMilli, selection.bounds)
                val source = inkSelectionSourceRect(frame, selection.bounds)
                clipRect(frame.left, frame.top, frame.right, frame.bottom) { drawInk(it, ink, source) }
            }
            if (selection == null && ephemeralStroke != null && it.strokes.none { saved -> saved.strokeId == ephemeralStroke.strokeId }) {
                drawInk(com.halo.sdk.runtime.ink.InkDocument(listOf(ephemeralStroke), it.aspectMilli), ink.copy(alpha = 0.55f),
                    inkBounds(size.width, size.height, it.aspectMilli))
            }
        }
    }
}

@Composable
internal fun CanvasSketchPadDialog(
    initial: String = "",
    recovery: String? = null,
    selectionOwnerId: String? = null,
    selectionPageId: String? = null,
    onSelectionChanged: (com.halo.sdk.runtime.ink.InkSelectionContext?) -> Unit = {},
    onSaveSelection: (com.halo.sdk.runtime.ink.InkSelectionContext) -> Unit = {},
    onDraft: (String) -> Unit = {},
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    editingExisting: Boolean = false,
    secure: Boolean = false,
    sheetLimit: Int = 1,
    sheetNumber: Int = 1,
    recoveredSheets: List<String> = emptyList(),
    onSheetDraft: (List<String>) -> Unit = {},
    onSaveSheets: ((List<String>) -> Unit)? = null,
    saving: Boolean = false,
    saveFailed: Boolean = false,
) {
    // Keep the source stable while the parent receives drafts. Unchanged v1 is saved byte-for-byte.
    val source = remember(initial) { recovery ?: initial }
    var sheets by remember(source) { mutableStateOf(listOf(source) + recoveredSheets) }
    var sheet by remember(source) { mutableIntStateOf(sheets.lastIndex) }
    var rollover by remember(source) { mutableIntStateOf(0) }
    var overview by remember { mutableStateOf(false) }
    val multiSheet = onSaveSheets != null
    fun publish(next: List<String>) {
        sheets = next
        if (multiSheet) onSheetDraft(next) else onDraft(next.first())
    }
    LaunchedEffect(Unit) { onSelectionChanged(null) }
    AlertDialog(
        modifier = Modifier.widthIn(max = 880.dp).fillMaxWidth().padding(horizontal = 16.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false,
            securePolicy = if (secure) androidx.compose.ui.window.SecureFlagPolicy.SecureOn else androidx.compose.ui.window.SecureFlagPolicy.Inherit),
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(uiText("Sketch")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (multiSheet) {
                    Text(stringResource(R.string.mk13_sheet_n, sheetNumber + sheet),
                        style = MaterialTheme.typography.labelLarge)
                    if (sheets.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { sheet--; onSelectionChanged(null) }, enabled = sheet > 0) { Text(uiText("Previous")) }
                        TextButton(onClick = { sheet++; onSelectionChanged(null) }, enabled = sheet < sheets.lastIndex) { Text(uiText("Next")) }
                        TextButton(onClick = { overview = !overview }) { Text(stringResource(R.string.mk13_see_all)) }
                    }
                    if (sheetNumber + sheets.size - 1 >= InkSheets.CALM_NOTE_AT)
                        Text(stringResource(R.string.mk13_sheets_calm), style = MaterialTheme.typography.bodySmall)
                    if (rollover > 0) key(rollover) {
                        Halo13Animation(Halo13Motion.SHEET_NEXT, Modifier.fillMaxWidth().height(54.dp))
                    }
                }
                if (overview) {
                    for (encoded in sheets) CanvasSketchPreview(encoded, Modifier.fillMaxWidth().height(128.dp))
                } else key(sheet) {
                    val sheetSource = remember { sheets[sheet] }
                    CanvasInkSurface(sheetSource, onChanged = { encoded ->
                        publish(sheets.toMutableList().apply { set(sheet, encoded) })
                    }, selectionOwnerId = selectionOwnerId.takeIf { sheet == 0 },
                        selectionPageId = selectionPageId.takeIf { sheet == 0 }, showBrushes = true,
                        onSelectionChanged = onSelectionChanged, onSaveSelection = onSaveSelection,
                        continueOnNewSheet = if (multiSheet) ({ encoded ->
                            if (sheet != sheets.lastIndex || sheets.size >= sheetLimit) false
                            else {
                                publish(sheets + encoded)
                                sheet = sheets.lastIndex
                                rollover++
                                true
                            }
                        }) else null)
                }
                Text(stringResource(R.string.ink_draft_help), style = MaterialTheme.typography.bodySmall)
                if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (saveFailed) Text(stringResource(R.string.ink_draft_rejected), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            val documents = remember(sheets) { sheets.map(InkCodec::decode) }
            com.halo.app.ui.components.HaloActionButton(onClick = {
                if (onSaveSheets != null) onSaveSheets(sheets.filterIndexed { index, _ ->
                    index == 0 && editingExisting || documents[index]?.strokes?.isNotEmpty() == true
                }) else onSave(sheets.first())
            }, enabled = !saving && documents.all { it != null } &&
                (editingExisting || documents.any { it?.strokes?.isNotEmpty() == true })) { Text(uiText("Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(uiText("Cancel")) } },
    )
}
