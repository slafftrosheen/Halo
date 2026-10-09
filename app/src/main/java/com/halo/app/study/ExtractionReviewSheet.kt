package com.halo.app.study

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ExtractionImportButton(controller: ExtractionReviewController) {
    var choose by remember { mutableStateOf(false) }
    var first by remember { mutableStateOf("1") }
    var last by remember { mutableStateOf("1") }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if(uris.isNotEmpty()) controller.import(uris,first.toIntOrNull() ?: 1,last.toIntOrNull() ?: 1)
    }
    OutlinedButton({choose=true}) { Text("Read text from a photo or file") }
    if(choose) AlertDialog(onDismissRequest={choose=false}, title={Text("Add reading material")}, text={Column {
        Text("Choose up to four photos or files. Halo reads their text on this phone. For a PDF, choose up to eight pages.")
        OutlinedTextField(first,{first=it.filter(Char::isDigit).take(5)},label={Text("Start at PDF page")})
        OutlinedTextField(last,{last=it.filter(Char::isDigit).take(5)},label={Text("End at PDF page")})
    }},confirmButton={TextButton({choose=false;picker.launch(arrayOf("image/*","application/pdf",StudyTextExtractor.DOCX,"text/*"))}) {Text("Choose files")}},dismissButton={TextButton({choose=false}) {Text("Cancel")}})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractionReviewSheet(controller: ExtractionReviewController) {
    val visible by controller.visible.collectAsStateWithLifecycle()
    val state by controller.state.collectAsStateWithLifecycle()
    var confirmDiscard by remember { mutableStateOf(false) }
    if(!visible) return
    if(confirmDiscard) AlertDialog(onDismissRequest={confirmDiscard=false},title={Text("Remove this draft?")},text={Text("Your original photos and files stay saved. Changes to this text draft will be removed.")},confirmButton={TextButton({confirmDiscard=false;controller.discard()}) {Text("Remove draft")}},dismissButton={TextButton({confirmDiscard=false}) {Text("Keep draft")}})
    ModalBottomSheet(onDismissRequest=controller::dismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Check your text",style=MaterialTheme.typography.headlineSmall)
            Text(state.progress)
            Text("1. Check the words against your original.\n2. Fix any mistakes below.\n3. Choose Save, Ask or Study.\n\nYour photos and files stay saved. This text stays on your phone unless you change its sharing settings. Latin-script text is supported; Cyrillic text is not.",style=MaterialTheme.typography.bodySmall)
            if(state.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); if(controller.isExtracting()) TextButton(controller::cancel) { Text("Stop reading text") } }
            state.parts.forEachIndexed { index,part ->
                Text("${index+1}. ${part.label} · ${part.locator}")
                part.warning?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
                OutlinedTextField(part.text,{controller.edit(index,it)},enabled=!state.busy,modifier=Modifier.fillMaxWidth(),minLines=3,maxLines=8,label={Text("Fix the words here")})
                Row {
                    TextButton({controller.move(index,-1)},enabled=!state.busy && index>0) {Text("Move up")}
                    TextButton({controller.move(index,1)},enabled=!state.busy && index<state.parts.lastIndex) {Text("Move down")}
                }
            }
            state.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            val usable=!state.busy && state.parts.any { it.text.isNotBlank() }
            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Button({controller.use("save")},enabled=usable,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Save my text")}
                OutlinedButton({controller.use("ask")},enabled=usable,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Ask about this text")}
                OutlinedButton({controller.use("study")},enabled=usable,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Learn with this text")}
            }
            TextButton({confirmDiscard=true},enabled=!state.busy) {Text("Remove this draft")}
            TextButton(controller::dismiss) {Text("Keep draft for later")}
        }
    }
}
