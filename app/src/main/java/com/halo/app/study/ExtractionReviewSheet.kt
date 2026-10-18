package com.halo.app.study

import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.language.uiText

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.screens.study.StudyActionLabel
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ExtractionImportButton(
    controller: ExtractionReviewController,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val review by controller.state.collectAsStateWithLifecycle()
    var choose by remember { mutableStateOf(false) }
    var first by remember { mutableStateOf("1") }
    var last by remember { mutableStateOf("1") }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if(uris.isNotEmpty()) controller.import(uris,first.toIntOrNull() ?: 1,last.toIntOrNull() ?: 1)
    }
    OutlinedButton(
        onClick = { if (review.parts.isNotEmpty() && review.savedNoteId == null) controller.resume() else choose = true },
        modifier = modifier,
        enabled = enabled && !review.busy,
        shape = com.halo.app.ui.design.HaloShapes.card,
    ) {
        if (title != null && (review.parts.isEmpty() || review.savedNoteId != null)) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                StudyActionLabel(HaloGlyph.CAMERA, title)
                if (subtitle != null) Text(uiText(subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else StudyActionLabel(HaloGlyph.CAMERA,
            if (review.parts.isNotEmpty() && review.savedNoteId == null)
                "Continue checking my text" else "Read text from a photo or file")
    }
    if(choose) AlertDialog(onDismissRequest={choose=false}, title={Text(uiText("Add reading material"))}, text={Column {
        Text(uiText("Choose up to four photos or files. Halo reads their text on this phone. For a PDF, choose up to eight pages."))
        HaloTextField(first,{first=it.filter(Char::isDigit).take(5)},label={Text(uiText("Start at PDF page"))}, kind = VoiceFieldKind.NUMBER)
        HaloTextField(last,{last=it.filter(Char::isDigit).take(5)},label={Text(uiText("End at PDF page"))}, kind = VoiceFieldKind.NUMBER)
    }},confirmButton={TextButton({choose=false;picker.launch(arrayOf("image/*","application/pdf",StudyTextExtractor.DOCX,"text/*"))}) {StudyActionLabel(HaloGlyph.FILE, "Choose files")}},dismissButton={TextButton({choose=false}) {Text(uiText("Cancel"))}})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractionReviewSheet(controller: ExtractionReviewController) {
    val visible by controller.visible.collectAsStateWithLifecycle()
    val state by controller.state.collectAsStateWithLifecycle()
    var confirmDiscard by remember { mutableStateOf(false) }
    if(!visible) return
    if(confirmDiscard) AlertDialog(onDismissRequest={confirmDiscard=false},title={Text(uiText("Remove this draft?"))},text={Text(uiText("Your original photos and files stay saved. Changes to this text draft will be removed."))},confirmButton={TextButton({confirmDiscard=false;controller.discard()}) {Text(uiText("Remove draft"))}},dismissButton={TextButton({confirmDiscard=false}) {Text(uiText("Keep draft"))}})
    ModalBottomSheet(onDismissRequest=controller::dismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(uiText("Check your text"),style=MaterialTheme.typography.headlineSmall)
            Text(uiText(state.progress))
            Text(uiText("1. Check the words against your original.\n2. Fix any mistakes below.\n3. Choose Save, Ask or Study.\n\nYour photos and files stay saved. This text stays on your phone unless you change its sharing settings. Reading photo text supports Latin letters, such as English and Latvian. Cyrillic photo text is not supported."),style=MaterialTheme.typography.bodySmall)
            if(!state.busy) Text(uiText(if(state.draftSaved) "Text draft kept on this phone" else "Keeping your latest edits..."),style=MaterialTheme.typography.bodySmall)
            if(state.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); if(controller.isExtracting()) TextButton(controller::cancel) { Text(uiText("Stop reading text")) } }
            state.parts.forEachIndexed { index,part ->
                Text("${index+1}. ${part.label} · ${part.locator}")
                part.warning?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
                HaloTextField(part.text,{controller.edit(index,it)},enabled=!state.busy,modifier=Modifier.fillMaxWidth(),minLines=3,maxLines=8,label={Text(uiText("Fix the words here"))}, kind = VoiceFieldKind.TEXT)
                Row {
                    TextButton({controller.move(index,-1)},enabled=!state.busy && index>0) {StudyActionLabel(HaloGlyph.PREVIOUS, "Move up")}
                    TextButton({controller.move(index,1)},enabled=!state.busy && index<state.parts.lastIndex) {StudyActionLabel(HaloGlyph.NEXT, "Move down")}
                }
            }
            state.error?.let { Text(uiText(it),color=MaterialTheme.colorScheme.error) }
            val usable=!state.busy && state.parts.any { it.text.isNotBlank() }
            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Button({controller.use("save")},enabled=usable,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {StudyActionLabel(HaloGlyph.NOTE, "Save my text")}
                OutlinedButton({controller.use("ask")},enabled=usable,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {StudyActionLabel(HaloGlyph.QUESTION, "Ask about this text")}
                OutlinedButton({controller.use("study")},enabled=usable,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {StudyActionLabel(HaloGlyph.THINK, "Learn with this text")}
            }
            TextButton({confirmDiscard=true},enabled=!state.busy) {StudyActionLabel(HaloGlyph.ERROR, "Remove this draft")}
            TextButton(controller::dismiss) {StudyActionLabel(HaloGlyph.STORAGE, "Keep draft for later")}
        }
    }
}
