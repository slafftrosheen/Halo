package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.halo.app.data.notebook.CanvasNode
import com.halo.app.data.notebook.isCanvasContainer
import com.halo.app.language.uiText
import com.halo.app.ui.zone6.Zone6ButtonSheet

@Composable
internal fun CanvasMinimapSheet(nodes: List<CanvasNode>, onJump: (String) -> Unit, onFit: () -> Unit, onDismiss: () -> Unit) {
    val sheetNodes = nodes.sortedBy { it.zIndex }
    Zone6ButtonSheet(uiText("Minimap"), onDismiss) {
        val cardColor = MaterialTheme.colorScheme.primary
        val groupColor = MaterialTheme.colorScheme.secondaryContainer
        fun project(size: Size): List<Pair<CanvasNode, Rect>> {
            val left = sheetNodes.minOfOrNull { it.x } ?: return emptyList()
            val right = sheetNodes.maxOf { it.x + it.width }
            val top = sheetNodes.minOf { it.y }
            val bottom = sheetNodes.maxOf { it.y + it.height }
            val margin = 12f
            val width = (right - left).coerceAtLeast(1f)
            val height = (bottom - top).coerceAtLeast(1f)
            val scale = minOf((size.width - margin * 2) / width, (size.height - margin * 2) / height).coerceAtLeast(.0001f)
            val origin = Offset((size.width - width * scale) / 2, (size.height - height * scale) / 2)
            return sheetNodes.map { node -> node to Rect(origin + Offset((node.x - left) * scale, (node.y - top) * scale),
                Size((node.width * scale).coerceAtLeast(3f), (node.height * scale).coerceAtLeast(3f))) }
        }
        Canvas(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(180.dp).background(MaterialTheme.colorScheme.surfaceContainer)
            .pointerInput(sheetNodes) {
                detectTapGestures { point ->
                    val cards = project(Size(size.width.toFloat(), size.height.toFloat()))
                    val target = cards.lastOrNull { (_, box) -> box.contains(point) }
                        ?: cards.minByOrNull { (_, box) ->
                            val distance = box.center - point
                            distance.x * distance.x + distance.y * distance.y
                        }
                    target?.first?.id?.let(onJump)
                }
            }) {
            project(size).forEach { (node, rect) ->
                val group = node.type.isCanvasContainer()
                drawRect(if (group) groupColor else cardColor.copy(alpha = .72f), rect.topLeft, rect.size)
            }
        }
        TextButton(onClick = onFit) { Text(uiText("Fit all cards")) }
        Column(Modifier.fillMaxWidth().heightIn(max = 164.dp).verticalScroll(rememberScrollState())) {
            sheetNodes.forEach { node ->
                TextButton(onClick = { onJump(node.id) }) {
                    Text(node.readableText().lineSequence().firstOrNull().orEmpty().take(60).ifBlank { uiText("Untitled card") })
                }
            }
        }
    }
}
