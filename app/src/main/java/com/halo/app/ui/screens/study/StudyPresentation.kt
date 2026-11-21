package com.halo.app.ui.screens.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.language.uiText
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.theme.HaloAccent
import com.halo.app.ui.theme.HaloNightInk
import com.halo.app.ui.theme.HaloPastelYellow
import com.halo.app.ui.theme.brandAccent
import com.halo.app.ui.workspace.HaloWorkspaceDestination
import com.halo.app.ui.workspace.HaloWorkspaceToggleRow
import com.halo.sdk.runtime.Storage

internal enum class StudyPresentationSize(val label: String, val preview: String) {
    PICTURES("Pictures", "See one part at a time and hear it aloud."),
    SIMPLE("Simple", "Read the main idea first, with more detail available."),
    FULL("Full", "Keep the full text and lesson controls visible."),
}

internal enum class StudyReadingHighlight(val label: String) {
    MINT("Mint"), LILAC("Lilac"), SUN("Sun"), UNDERLINE("Underline");

    fun style(): SpanStyle = when (this) {
        MINT -> brandAccent(false, HaloAccent.MINT).let { SpanStyle(color = it.onFill, background = it.fill) }
        LILAC -> brandAccent(false, HaloAccent.LILAC).let { SpanStyle(color = it.onFill, background = it.fill) }
        SUN -> SpanStyle(color = HaloNightInk, background = HaloPastelYellow)
        UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
    }
}

internal data class StudyPresentation(
    val size: StudyPresentationSize = StudyPresentationSize.FULL,
    val highlight: StudyReadingHighlight = StudyReadingHighlight.MINT,
    val easySpacing: Boolean = false,
    val practiceReminder: Boolean = false,
) {
    fun save(storage: Storage) {
        storage.putString(KEY, listOf(size.name, highlight.name, easySpacing, practiceReminder).joinToString("|"))
    }

    companion object {
        private const val KEY = "study_presentation_v1"

        fun load(storage: Storage?): StudyPresentation {
            val saved = runCatching { storage?.getString(KEY) }.getOrNull()?.split('|').orEmpty()
            return StudyPresentation(
                StudyPresentationSize.entries.find { it.name == saved.getOrNull(0) } ?: StudyPresentationSize.FULL,
                StudyReadingHighlight.entries.find { it.name == saved.getOrNull(1) } ?: StudyReadingHighlight.MINT,
                saved.getOrNull(2) == "true",
                saved.getOrNull(3) == "true",
            )
        }
    }
}

@Composable
internal fun StudyPresentation.readingTextStyle(): TextStyle = (
    if (size == StudyPresentationSize.PICTURES) MaterialTheme.typography.bodyLarge
    else MaterialTheme.typography.bodyMedium
).let { if (easySpacing) it.copy(letterSpacing = 0.6.sp, lineHeight = 28.sp) else it }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun StudyPresentationSheet(
    saved: StudyPresentation,
    onApply: (StudyPresentation) -> Boolean,
    onDismiss: () -> Unit,
    onConfigureReadingVoice: (() -> Unit)?,
) {
    var draft by remember(saved) { mutableStateOf(saved) }
    var saveFailed by remember { mutableStateOf(false) }
    val preview = uiText(draft.size.preview)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(uiText("How you learn"), style = MaterialTheme.typography.titleLarge)
            Text(uiText("Learning size"), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StudyPresentationSize.entries.forEach { size ->
                    FilterChip(selected = draft.size == size, onClick = { draft = draft.copy(size = size) },
                        label = { Text(uiText(size.label)) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            Text(uiText("Highlight while reading"), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StudyReadingHighlight.entries.forEach { highlight ->
                    FilterChip(selected = draft.highlight == highlight, onClick = { draft = draft.copy(highlight = highlight) },
                        label = { Text(uiText(highlight.label)) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            HaloWorkspaceToggleRow(title = uiText("Easy-read spacing"), checked = draft.easySpacing,
                onCheckedChange = { draft = draft.copy(easySpacing = it) })
            HaloWorkspaceToggleRow(
                title = androidx.compose.ui.res.stringResource(com.halo.app.R.string.coach_practice_reminder),
                checked = draft.practiceReminder,
                onCheckedChange = { draft = draft.copy(practiceReminder = it) },
            )
            Text(androidx.compose.ui.res.stringResource(com.halo.app.R.string.coach_practice_reminder_body),
                style = MaterialTheme.typography.bodySmall)
            Surface(shape = HaloShapes.card, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Text(buildAnnotatedString {
                    append(preview)
                    addStyle(draft.highlight.style(), 0, preview.indexOf(' ').let { if (it < 0) preview.length else it })
                }, Modifier.fillMaxWidth().padding(16.dp), style = draft.readingTextStyle())
            }
            if (onConfigureReadingVoice != null && draft == saved) {
                HaloWorkspaceDestination("Reading voice", "Choose halo’s voice", onConfigureReadingVoice)
            }
            if (saveFailed) Text(uiText("Could not save your change. Try again."), color = MaterialTheme.colorScheme.error)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { draft = saved; saveFailed = false }, enabled = saved != draft,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(uiText("Undo")) }
                Button(onClick = {
                    if (onApply(draft)) onDismiss() else saveFailed = true
                }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(uiText("Apply")) }
            }
        }
    }
}
