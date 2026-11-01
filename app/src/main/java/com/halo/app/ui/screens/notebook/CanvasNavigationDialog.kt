package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloAlertDialog

/** Button alternatives to pan, pinch and drag; all writes stay with the current Canvas owner. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CanvasNavigationDialog(state: CanvasEditorUiState, onPan: (Float, Float) -> Unit,
    onZoom: (Float) -> Unit, onMove: (Float, Float) -> Unit, onFit: () -> Unit, onReset: () -> Unit, onDismiss: () -> Unit,
    kidsMode: Boolean = false) {
    val target = if (kidsMode) 56.dp else 48.dp
    var fine by remember { mutableStateOf(false) }
    val moveEnabled = state.canEditBoard() && (state.selectedNodeId != null || state.selectedNodeIds.isNotEmpty())
    HaloAlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.canvas_view_controls)) },
        text = { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.canvas_pan_view), style = MaterialTheme.typography.titleSmall)
            CanvasDirectionButtons(!state.loading && !state.missing, 48f, target, onPan)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onZoom(1.1f) }, enabled = state.viewportScale < 2.5f, modifier = Modifier.heightIn(min = target)) { Text(stringResource(R.string.canvas_zoom_in)) }
                TextButton(onClick = { onZoom(1f / 1.1f) }, enabled = state.viewportScale > .35f, modifier = Modifier.heightIn(min = target)) { Text(stringResource(R.string.canvas_zoom_out)) }
                TextButton(onClick = onFit, enabled = state.nodes.isNotEmpty(), modifier = Modifier.heightIn(min = target)) { Text(uiText("Fit all cards")) }
                TextButton(onClick = onReset, modifier = Modifier.heightIn(min = target)) { Text(uiText("Reset view")) }
            }
            HorizontalDivider()
            Text(stringResource(R.string.canvas_move_selected), style = MaterialTheme.typography.titleSmall)
            if (state.selectedNodeId == null && state.selectedNodeIds.isEmpty()) Text(stringResource(R.string.canvas_select_before_move))
            FilterChip(selected = fine, onClick = { fine = !fine }, label = { Text(stringResource(R.string.canvas_small_steps)) }, modifier = Modifier.heightIn(min = target))
            CanvasDirectionButtons(moveEnabled, if (fine) 1f else 12f, target, onMove)
        } }, confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = target)) { Text(uiText("Done")) } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CanvasDirectionButtons(enabled: Boolean, step: Float, target: androidx.compose.ui.unit.Dp, onMove: (Float, Float) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Triple(R.string.canvas_left, -step, 0f), Triple(R.string.canvas_right, step, 0f),
            Triple(R.string.canvas_up, 0f, -step), Triple(R.string.canvas_down, 0f, step)).forEach { (label, x, y) ->
            OutlinedButton(onClick = { onMove(x, y) }, enabled = enabled, modifier = Modifier.heightIn(min = target)) { Text(stringResource(label)) }
        }
    }
}
