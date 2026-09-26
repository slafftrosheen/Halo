package com.halo.app.ui.presence

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon

@Composable
fun HaloWorkspaceMark(kind: String, modifier: Modifier = Modifier) {
    val glyph = when (kind.lowercase()) {
        "glasses" -> HaloGlyph.GLASSES
        "robot" -> HaloGlyph.ROBOT
        "activity" -> HaloGlyph.ACTIVITY
        "notebook" -> HaloGlyph.NOTE
        "devices" -> HaloGlyph.DEVICE
        else -> HaloGlyph.ACTION
    }
    HaloIcon(glyph = glyph, contentDescription = kind, modifier = modifier.size(28.dp))
}
