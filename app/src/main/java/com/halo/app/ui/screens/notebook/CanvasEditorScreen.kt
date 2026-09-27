package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun CanvasEditorScreen(viewModel: CanvasEditorViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val density = LocalDensity.current
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text(state.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${(state.viewportScale * 100).roundToInt()}% · ${state.nodes.size} nodes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = viewModel::resetViewport) {
                Icon(Icons.Rounded.CenterFocusStrong, contentDescription = "Reset canvas view")
            }
            IconButton(onClick = viewModel::deleteSelectedNode, enabled = state.selectedNodeId != null) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete selected node")
            }
            Surface(
                onClick = viewModel::addTextNode,
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Text", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ -> viewModel.transform(pan.x, pan.y, zoom) }
            },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val spacing = 32.dp.toPx() * state.viewportScale
                if (spacing > 8f) {
                    var x = state.viewportX % spacing
                    while (x < size.width) {
                        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                        x += spacing
                    }
                    var y = state.viewportY % spacing
                    while (y < size.height) {
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                        y += spacing
                    }
                }
            }

            if (state.loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else if (state.missing) {
                Text("Canvas unavailable", modifier = Modifier.align(Alignment.Center))
            }

            state.nodes.forEach { node ->
                val xPx = with(density) { (node.x.dp * state.viewportScale).toPx() } + state.viewportX
                val yPx = with(density) { (node.y.dp * state.viewportScale).toPx() } + state.viewportY
                val width = (node.width * state.viewportScale).coerceAtLeast(140f).dp
                Surface(
                    modifier = Modifier
                        .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
                        .width(width)
                        .pointerInput(node.id, state.viewportScale) {
                            detectDragGestures(
                                onDragStart = { viewModel.selectNode(node.id) },
                                onDrag = { change, amount ->
                                    change.consume()
                                    viewModel.moveNode(
                                        node.id,
                                        with(density) { amount.x.toDp().value },
                                        with(density) { amount.y.toDp().value },
                                    )
                                },
                            )
                        },
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        if (state.selectedNodeId == node.id) 2.dp else 1.dp,
                        if (state.selectedNodeId == node.id) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                    ),
                    shadowElevation = if (state.selectedNodeId == node.id) 8.dp else 2.dp,
                ) {
                    BasicTextField(
                        value = node.text,
                        onValueChange = { viewModel.updateNodeText(node.id, it) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.padding(16.dp),
                        decorationBox = { inner ->
                            if (node.text.isEmpty()) {
                                Text("Write on the canvas…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            inner()
                        },
                    )
                }
            }
        }
    }
}
