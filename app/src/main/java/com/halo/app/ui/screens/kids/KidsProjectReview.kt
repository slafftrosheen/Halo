package com.halo.app.ui.screens.kids

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.glass.components.HaloMarkdownText

/** K04: preview first; only the existing Keep owner commits the reviewed text. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KidsProjectReview(state: KidsProjectState, saved: Boolean,
    onTextChange: (String) -> Unit, onKeep: () -> Unit, onDiscard: () -> Unit,
    onChangeIdea: () -> Unit) {
    var editing by rememberSaveable(state.noteId) { mutableStateOf(state.draft.isBlank()) }
    var discard by remember { mutableStateOf(false) }
    if (discard) AlertDialog(onDismissRequest = { discard = false },
        title = { Text(uiText("Discard unkept changes?")) },
        text = { Text(uiText("Only unkept changes are discarded. Saved projects stay in Notebook.")) },
        confirmButton = { TextButton(onClick = { discard = false; editing = false; onDiscard() },
            enabled = !state.busy) { Text(uiText("Discard")) } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text(uiText("Keep editing")) } })
    val ink = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(Modifier.fillMaxWidth().then(if (saved) Modifier else Modifier.drawWithContent {
            drawContent()
            val inset = 1.dp.toPx()
            drawRoundRect(ink, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset),
                CornerRadius(20.dp.toPx()), style = Stroke(2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))))
        }), shape = HaloShapes.card,
            color = if (saved) MaterialTheme.colorScheme.surface else
                androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surface,
                    MaterialTheme.colorScheme.primaryContainer, .08f),
            border = if (saved) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = HaloShapes.pill, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(uiText(if (saved) "Saved project" else "Not saved yet"),
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Text(state.idea.ifBlank { uiText("My project") },
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp,
                        lineHeight = 28.sp, fontWeight = FontWeight.SemiBold))
                if (editing) HaloTextField(state.draft, onTextChange, kind = VoiceFieldKind.TEXT,
                    label = { Text(uiText("My draft")) }, modifier = Modifier.fillMaxWidth(),
                    enabled = !state.busy, minLines = 4, maxLines = 12, maxChars = 12000)
                else HaloMarkdownText(state.draft)
            }
        }
        if (!saved) Text(uiText("Review it, change anything you like, then choose Keep to save it."),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!saved) KidsSecondaryButton(onClick = { discard = true }, enabled = !state.busy,
                modifier = Modifier.heightIn(min = 56.dp)) { Text(uiText("Discard")) }
            KidsSecondaryButton(onClick = { editing = !editing }, enabled = !state.busy,
                modifier = Modifier.heightIn(min = 56.dp)) { Text(uiText(if (editing) "Preview" else "Change")) }
            if (!saved) KidsPrimaryButton(onClick = onKeep, enabled = !state.busy && state.draft.isNotBlank(),
                modifier = Modifier.heightIn(min = 56.dp)) { Text(uiText("Keep")) }
        }
        if (!saved) TextButton(onClick = onChangeIdea, enabled = !state.busy,
            modifier = Modifier.heightIn(min = 56.dp)) { Text(uiText("Change the idea")) }
    }
}
