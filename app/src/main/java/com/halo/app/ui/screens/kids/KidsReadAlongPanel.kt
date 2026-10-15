package com.halo.app.ui.screens.kids

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.screens.study.ReaderState
import com.halo.app.ui.screens.study.StudyViewModel

/** Highlight only ranges delivered by the current narration generation. */
@Composable
internal fun KidsReadAlongPanel(reader: ReaderState, model: StudyViewModel) {
    Surface(shape = HaloShapes.card, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val highlight = reader.wordRange?.takeIf {
                reader.speaking && it.first >= 0 && it.last < reader.current.length
            }
            Text(buildAnnotatedString {
                append(reader.current)
                highlight?.let { range -> addStyle(SpanStyle(
                    background = MaterialTheme.colorScheme.primaryContainer,
                    color = MaterialTheme.colorScheme.onPrimaryContainer), range.first, range.last + 1) }
            }, style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = model::toggleReaderPlayback, shape = HaloShapes.pill,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                    Text(uiText(if (reader.speaking) "Pause reading" else "Resume reading"))
                }
                OutlinedButton(onClick = model::closeReader, shape = HaloShapes.pill,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(uiText("Stop reading")) }
            }
        }
    }
}
