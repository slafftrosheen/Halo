package com.halo.app.ui.screens.study

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.components.*

/** Chapters are actual saved tutor turns. The shared reader owns focus, consent and media controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudyListenMode(state: StudyUiState, model: StudyViewModel, onDismiss: () -> Unit) {
    val chapters = state.turns.filter { it.failure == null && it.text.isNotBlank() }
    var selected by rememberSaveable(state.session?.id) { mutableIntStateOf(0) }
    var question by rememberSaveable(state.session?.id) { mutableStateOf("") }
    val index = selected.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))
    val chapter = chapters.getOrNull(index)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.94f)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(uiText("Listen to this lesson"), style = MaterialTheme.typography.titleLarge)
                Text(uiText("Chapters use saved lesson steps. You can always read their full text here."),
                    style = MaterialTheme.typography.bodyMedium)
                if (chapter != null) {
                    Text("${index + 1}/${chapters.size}", style = MaterialTheme.typography.labelLarge)
                    Text(uiText(chapter.stage.guidance), style = MaterialTheme.typography.titleMedium)
                    Text(chapter.text, style = MaterialTheme.typography.bodyLarge)
                    HaloReadAloud(chapter.text, label = "Read chapter aloud")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { VoiceFieldCancellation.stop(); selected = index - 1 }, enabled = index > 0,
                            modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Previous chapter")) }
                        OutlinedButton(onClick = { VoiceFieldCancellation.stop(); selected = index + 1 }, enabled = index < chapters.lastIndex,
                            modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Next chapter")) }
                    }
                    HaloTextField(value = question, onValueChange = { question = it.take(2000) },
                        kind = VoiceFieldKind.TEXT, label = { Text(uiText("Ask about this step")) },
                        enabled = !state.isGenerating && !state.isFinished, maxChars = 2000)
                    Button(onClick = { model.askAboutSavedTurn(chapter.id, question); onDismiss() },
                        enabled = question.isNotBlank() && !state.isGenerating && !state.isFinished,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(uiText("Ask halo")) }
                } else Text(uiText("There is nothing to read"))
            }
            HaloReadAloudBar(allowVoiceSettings = false, onVoiceSettings = {})
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).navigationBarsPadding()) {
                Text(uiText("Back to lesson"))
            }
        }
    }
}
