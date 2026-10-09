package com.halo.app.ui.screens.study

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import com.halo.app.language.uiText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloGlyphRenderer

/** The words remain the accessible action name; the symbol is a visual signpost. */
@Composable
fun StudyActionLabel(glyph: HaloGlyph, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HaloGlyphRenderer(glyph, Modifier.size(20.dp))
        Text(uiText(text))
    }
}
