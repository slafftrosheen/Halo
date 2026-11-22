package com.halo.app.ui.workspace

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.data.studio.StudioPixelSelection
import com.halo.app.data.studio.StudioProject
import com.halo.app.language.uiText

@Composable
internal fun StudioPixelInstrumentDialog(
    viewModel: StudioViewModel, snapshot: StudioProject, tool: StudioPixelTool, selection: StudioPixelSelection?,
    ready: Boolean, onDismiss: () -> Unit,
) {
    val document = snapshot.session.current
    val current = selection?.takeIf { it.projectId == snapshot.id && it.projectRevision == snapshot.revision }
    val mask = current?.mask
    if (tool == StudioPixelTool.SMUDGE) {
        val layer = document.layers.firstOrNull { it.id == document.selectedLayerId && !it.locked && it.visible && it.opacity > 0 }
            ?: return
        StudioSmudgeDialog(viewModel.service, document, layer, mask, ready, onApply = { request ->
            viewModel.applyPixels(snapshot.revision, request, current)
            onDismiss()
        }, onDismiss = onDismiss)
    } else {
        StudioSelectionDialog(viewModel.service, document, tool, mask, ready, onApply = { request ->
            viewModel.selectPixels(snapshot.revision, request, current)
            onDismiss()
        }, onDismiss = onDismiss)
    }
}

@Composable
internal fun StudioSelectedPixelActions(
    ready: Boolean, canCopy: Boolean, canErase: Boolean, onCopy: () -> Unit, onErase: () -> Unit, onClear: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onCopy, enabled = ready && canCopy) { Text(uiText("Copy selection to layer")) }
        TextButton(onClick = onErase, enabled = ready && canErase) { Text(uiText("Erase selected pixels")) }
        TextButton(onClick = onClear, enabled = ready) { Text(uiText("Deselect")) }
    }
}

@Composable
internal fun StudioErasePixelConfirmation(enabled: Boolean, onErase: () -> Unit, onDismiss: () -> Unit) {
    StudioAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText("Erase selected pixels?")) },
        text = { Text(uiText("This removes pixels from the current layer. Undo restores the original layer.")) },
        confirmButton = { TextButton(onClick = onErase, enabled = enabled) { Text(uiText("Erase selected pixels")) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
