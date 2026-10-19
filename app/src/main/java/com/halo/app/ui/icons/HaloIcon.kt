package com.halo.app.ui.icons

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
        HaloGlyphRenderer(glyph = glyph, modifier = Modifier.matchParentSize(), tint = tint)
    }
}
