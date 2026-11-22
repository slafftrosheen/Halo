package com.halo.app.ui.screens.notebook

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.data.notebook.CanvasNode
import com.halo.app.data.notebook.CanvasNodeType
import com.halo.app.ui.theme.rememberVisualPreferences
import com.halo.app.ui.workspace.HaloWorkspaceToggleRow
import com.halo.app.ui.workspace.ToolLookSelections
import com.halo.app.ui.workspace.ToolPageAppearance
import org.json.JSONObject

internal enum class CanvasColours(@StringRes val label: Int) {
    BY_KIND(R.string.alive_canvas_by_kind), BY_ME(R.string.alive_canvas_by_me), ONE(R.string.alive_canvas_one)
}

internal enum class CanvasLinkStyle(@StringRes val label: Int) {
    CURVED(R.string.alive_canvas_curved), STRAIGHT(R.string.alive_canvas_straight), DOTTED(R.string.alive_canvas_dotted)
}

internal enum class CanvasCardPaint(@StringRes val label: Int, val color: Color) {
    NEUTRAL(R.string.alive_canvas_neutral, Color.Transparent),
    MINT(R.string.alive_canvas_mint, Color(0xFF31C9A1)),
    LILAC(R.string.alive_canvas_lilac, Color(0xFFB69BF0)),
    PINK(R.string.alive_canvas_pink, Color(0xFFE894BF)),
    APRICOT(R.string.alive_canvas_apricot, Color(0xFFFBB089))
}

internal data class CanvasAppearance(
    val colours: CanvasColours = CanvasColours.BY_KIND,
    val oneColour: CanvasCardPaint = CanvasCardPaint.MINT,
    val cardColours: Map<String, CanvasCardPaint> = emptyMap(),
    val links: CanvasLinkStyle = CanvasLinkStyle.CURVED,
    val snap: Boolean = false,
    val openAsList: Boolean = false,
)

internal fun canvasAppearanceOwner(context: Context, boardId: String): ToolPageAppearance<CanvasAppearance> =
    ToolPageAppearance(context, "canvas:$boardId", CanvasAppearance(), { json ->
        val values = json.optJSONObject("cardColours")
        CanvasAppearance(
            colours = CanvasColours.entries.firstOrNull { it.name == json.optString("colours") } ?: CanvasColours.BY_KIND,
            oneColour = CanvasCardPaint.entries.firstOrNull { it.name == json.optString("oneColour") } ?: CanvasCardPaint.MINT,
            cardColours = values?.let { entries ->
                entries.keys().asSequence().take(512).mapNotNull { id ->
                    CanvasCardPaint.entries.firstOrNull { it.name == entries.optString(id) }?.let { id to it }
                }.toMap()
            }.orEmpty(),
            links = CanvasLinkStyle.entries.firstOrNull { it.name == json.optString("links") } ?: CanvasLinkStyle.CURVED,
            snap = json.optBoolean("snap"),
            openAsList = json.optBoolean("openAsList"),
        )
    }, { value ->
        JSONObject().put("colours", value.colours.name).put("oneColour", value.oneColour.name)
            .put("cardColours", JSONObject(value.cardColours.mapValues { it.value.name }))
            .put("links", value.links.name).put("snap", value.snap).put("openAsList", value.openAsList)
    })

@Composable
internal fun CanvasAppearanceControls(appearance: CanvasAppearance, selectedId: String?, onPreview: (CanvasAppearance) -> Unit) {
    ToolLookSelections(R.string.alive_canvas_colors, CanvasColours.entries.map { it to it.label }, appearance.colours,
        onSelect = { onPreview(appearance.copy(colours = it)) })
    if (appearance.colours == CanvasColours.ONE) {
        ToolLookSelections(R.string.alive_canvas_one_color, CanvasCardPaint.entries.map { it to it.label },
            appearance.oneColour, onSelect = { onPreview(appearance.copy(oneColour = it)) })
    }
    if (appearance.colours == CanvasColours.BY_ME && selectedId != null &&
        (selectedId in appearance.cardColours || appearance.cardColours.size < 512)) {
        ToolLookSelections(R.string.alive_canvas_card_color, CanvasCardPaint.entries.map { it to it.label },
            appearance.cardColours[selectedId] ?: CanvasCardPaint.NEUTRAL, onSelect = { colour ->
                onPreview(appearance.copy(cardColours = if (colour == CanvasCardPaint.NEUTRAL)
                    appearance.cardColours - selectedId else appearance.cardColours + (selectedId to colour)))
            })
    }
    ToolLookSelections(R.string.alive_canvas_links, CanvasLinkStyle.entries.map { it to it.label }, appearance.links,
        onSelect = { onPreview(appearance.copy(links = it)) })
    HaloWorkspaceToggleRow(stringResource(R.string.alive_canvas_snap), appearance.snap,
        onCheckedChange = { onPreview(appearance.copy(snap = it)) })
    HaloWorkspaceToggleRow(stringResource(R.string.alive_canvas_list), appearance.openAsList,
        onCheckedChange = { onPreview(appearance.copy(openAsList = it)) })
}

@Composable
internal fun canvasCardColor(appearance: CanvasAppearance, node: CanvasNode, image: Boolean): Color {
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    if (rememberVisualPreferences().higherContrast) return surface
    val tint = when (appearance.colours) {
        CanvasColours.ONE -> appearance.oneColour
        CanvasColours.BY_ME -> appearance.cardColours[node.id] ?: CanvasCardPaint.NEUTRAL
        CanvasColours.BY_KIND -> when {
            image || node.type == CanvasNodeType.IMAGE -> CanvasCardPaint.PINK
            node.type == CanvasNodeType.NOTE || node.type == CanvasNodeType.STICKY -> CanvasCardPaint.APRICOT
            node.type == CanvasNodeType.TEXT || node.type == CanvasNodeType.SKETCH -> CanvasCardPaint.LILAC
            else -> CanvasCardPaint.NEUTRAL
        }
    }
    return if (tint == CanvasCardPaint.NEUTRAL) when (node.type) {
        CanvasNodeType.GROUP, CanvasNodeType.FRAME -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .30f).compositeOver(surface)
        CanvasNodeType.TAG -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .50f).compositeOver(surface)
        CanvasNodeType.STICKY -> CanvasCardPaint.APRICOT.color.copy(alpha = .24f).compositeOver(surface)
        else -> surface
    } else tint.color.copy(alpha = .14f).compositeOver(surface)
}

internal fun DrawScope.drawCanvasLink(start: Offset, end: Offset, color: Color, kind: CanvasLinkStyle) {
    when (kind) {
        CanvasLinkStyle.CURVED -> {
            val bend = (end.x - start.x) * .5f
            val path = Path().apply {
                moveTo(start.x, start.y)
                cubicTo(start.x + bend, start.y, end.x - bend, end.y, end.x, end.y)
            }
            drawPath(path, color, style = Stroke(2.dp.toPx()))
        }
        CanvasLinkStyle.STRAIGHT -> drawLine(color, start, end, 2.dp.toPx())
        CanvasLinkStyle.DOTTED -> drawLine(color, start, end, 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx())))
    }
}

@Composable
internal fun CanvasAppearancePreview(appearance: CanvasAppearance) {
    val tone = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceContainerHighest
    Surface(Modifier.fillMaxWidth().height(94.dp), shape = MaterialTheme.shapes.medium, color = surface) {
        Canvas(Modifier.fillMaxSize()) {
            val start = Offset(size.width * .27f, size.height * .32f)
            val end = Offset(size.width * .73f, size.height * .68f)
            val left = if (appearance.colours == CanvasColours.ONE) appearance.oneColour else CanvasCardPaint.LILAC
            val right = if (appearance.colours == CanvasColours.ONE) appearance.oneColour else CanvasCardPaint.MINT
            drawCanvasLink(start, end, tone, appearance.links)
            drawCircle(left.color.copy(alpha = .8f), 10.dp.toPx(), start)
            drawCircle(right.color.copy(alpha = .8f), 10.dp.toPx(), end)
        }
    }
}
