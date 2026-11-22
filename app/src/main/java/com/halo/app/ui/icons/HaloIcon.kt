package com.halo.app.ui.icons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun HaloIcon(
    glyph: HaloGlyph,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Box(
        modifier = modifier.size(24.dp).semantics {
            if (contentDescription != null) {
                this.contentDescription = contentDescription
            }
        },
        contentAlignment = Alignment.Center
    ) {
        HaloIconTreatment(glyph, Modifier.matchParentSize(), tint) { styleModifier, ink ->
            HaloGlyphRenderer(glyph = glyph, modifier = styleModifier, tint = ink)
        }
    }
}


@Composable
internal fun HaloIconTreatment(glyph: HaloGlyph, modifier: Modifier, tint: Color,
                               content: @Composable (Modifier, Color) -> Unit) {
    val visual = com.halo.app.ui.theme.rememberVisualPreferences()
    val colors = androidx.compose.material3.MaterialTheme.colorScheme
    val semantic = glyph in setOf(HaloGlyph.APPROVAL, HaloGlyph.ERROR, HaloGlyph.WARNING, HaloGlyph.HALO, HaloGlyph.SUCCESS)
    val style = if (visual.higherContrast || semantic) com.halo.app.ui.theme.HaloIconStyle.LINE else visual.iconStyle
    val contentInk = LocalContentColor.current.copy(alpha = tint.alpha)
    if (style == com.halo.app.ui.theme.HaloIconStyle.LINE) {
        content(modifier, if (visual.higherContrast && !semantic) contentInk else tint)
        return
    }
    Box(modifier.then(if (style == com.halo.app.ui.theme.HaloIconStyle.TILE)
        Modifier.background(colors.primary.copy(alpha = .15f), RoundedCornerShape(6.dp)) else Modifier),
        contentAlignment = Alignment.Center) {
        if (style == com.halo.app.ui.theme.HaloIconStyle.SPOT) {
            Box(Modifier.align(Alignment.TopEnd).size(8.dp)
                .background(colors.primary.copy(alpha = .52f), CircleShape))
        }
        content(Modifier.fillMaxSize(if (style == com.halo.app.ui.theme.HaloIconStyle.TILE) .72f else .86f),
            if (style == com.halo.app.ui.theme.HaloIconStyle.SPOT) contentInk else tint)
    }
}
