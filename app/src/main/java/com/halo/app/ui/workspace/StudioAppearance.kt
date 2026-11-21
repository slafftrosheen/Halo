package com.halo.app.ui.workspace

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import org.json.JSONArray
import org.json.JSONObject

internal enum class StudioPaper(@StringRes val label: Int) {
    PLAIN(R.string.alive_studio_plain), DOTS(R.string.alive_studio_dots), GRID(R.string.alive_studio_grid)
}

internal enum class StudioFavorite(val action: String, @StringRes val label: Int) {
    DRAW("draw", R.string.studio_draw),
    WORDS("text", R.string.alive_studio_words),
    PICTURE("image", R.string.alive_studio_picture),
    UNDO("undo", R.string.studio_undo),
    ADD("add-layer", R.string.zone5_add_layer),
    LAYERS("layers", R.string.studio_layers),
    TRANSFORM("transform", R.string.studio_transforms),
}

internal data class StudioAppearance(
    val paper: StudioPaper = StudioPaper.PLAIN,
    val favorites: List<StudioFavorite> = listOf(StudioFavorite.ADD, StudioFavorite.DRAW, StudioFavorite.LAYERS, StudioFavorite.PICTURE),
    val leftHanded: Boolean = false,
    val snapZoom: Boolean = true,
    val speakTools: Boolean = false,
)

internal fun studioAppearanceOwner(context: Context): ToolPageAppearance<StudioAppearance> =
    ToolPageAppearance(context, "studio", StudioAppearance(), { json ->
        val favorites = json.optJSONArray("favorites")
        StudioAppearance(
            paper = StudioPaper.entries.firstOrNull { it.name == json.optString("paper") } ?: StudioPaper.PLAIN,
            favorites = if (favorites == null) StudioAppearance().favorites else
                (0 until minOf(16, favorites.length())).mapNotNull { index ->
                    StudioFavorite.entries.firstOrNull { it.name == favorites.optString(index) }
                }.distinct().take(4),
            leftHanded = json.optBoolean("leftHanded"),
            snapZoom = json.optBoolean("snapZoom", true),
            speakTools = json.optBoolean("speakTools"),
        )
    }, { value ->
        JSONObject().put("paper", value.paper.name)
            .put("favorites", JSONArray(value.favorites.map { it.name }))
            .put("leftHanded", value.leftHanded)
            .put("snapZoom", value.snapZoom)
            .put("speakTools", value.speakTools)
    })

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StudioAppearanceControls(appearance: StudioAppearance, onPreview: (StudioAppearance) -> Unit) {
    ToolLookSelections(R.string.alive_studio_artboard, StudioPaper.entries.map { it to it.label }, appearance.paper,
        onSelect = { onPreview(appearance.copy(paper = it)) })
    Text(stringResource(R.string.alive_studio_favourites), style = MaterialTheme.typography.labelLarge)
    Text(stringResource(R.string.alive_studio_favourites_limit), style = MaterialTheme.typography.bodySmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StudioFavorite.entries.forEach { favorite ->
            val selected = favorite in appearance.favorites
            FilterChip(selected, { onPreview(appearance.copy(favorites = if (selected) appearance.favorites - favorite
                else appearance.favorites + favorite)) },
                enabled = selected || appearance.favorites.size < 4, label = { Text(stringResource(favorite.label)) },
                modifier = Modifier.heightIn(min = 48.dp))
        }
    }
    HaloWorkspaceToggleRow(stringResource(R.string.alive_studio_left), appearance.leftHanded, onCheckedChange = {
        onPreview(appearance.copy(leftHanded = it))
    })
    HaloWorkspaceToggleRow(stringResource(R.string.alive_studio_snap), appearance.snapZoom, onCheckedChange = {
        onPreview(appearance.copy(snapZoom = it))
    })
    HaloWorkspaceToggleRow(stringResource(R.string.alive_studio_names), appearance.speakTools, onCheckedChange = {
        onPreview(appearance.copy(speakTools = it))
    })
}

@Composable
internal fun StudioAppearancePreview(paper: StudioPaper) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f)
    Surface(Modifier.fillMaxWidth().height(106.dp), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Canvas(Modifier.fillMaxSize()) { drawStudioPaper(paper, ink) }
    }
}

/** An editor guide. The exported document uses the original project pixels. */
internal fun DrawScope.drawStudioPaper(paper: StudioPaper, ink: Color) {
    if (paper == StudioPaper.PLAIN) return
    val spacing = 20.dp.toPx()
    val columns = (size.width / spacing).toInt().coerceAtMost(160)
    val rows = (size.height / spacing).toInt().coerceAtMost(160)
    when (paper) {
        StudioPaper.GRID -> {
            for (column in 1..columns) drawLine(ink, Offset(column * spacing, 0f),
                Offset(column * spacing, size.height), strokeWidth = 1.dp.toPx())
            for (row in 1..rows) drawLine(ink, Offset(0f, row * spacing),
                Offset(size.width, row * spacing), strokeWidth = 1.dp.toPx())
        }
        StudioPaper.DOTS -> for (column in 1..columns) for (row in 1..rows)
            drawCircle(ink, 1.dp.toPx(), Offset(column * spacing, row * spacing))
        StudioPaper.PLAIN -> Unit
    }
}
