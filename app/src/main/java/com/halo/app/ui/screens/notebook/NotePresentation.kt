package com.halo.app.ui.screens.notebook

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.theme.HaloBrandFonts
import com.halo.app.ui.workspace.HaloWorkspaceToggleRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class NotePaper(@StringRes val label: Int) {
    PLAIN(R.string.note_look_plain), LINED(R.string.note_look_lined), GRID(R.string.note_look_grid),
}

internal enum class NoteLetters(@StringRes val label: Int) {
    EASY_READ(R.string.note_look_easy_read), BOOK(R.string.note_look_book), ROUND(R.string.note_look_round),
}

internal enum class NoteSpacing(@StringRes val label: Int, val factor: Float) {
    NORMAL(R.string.note_look_normal, 1f), ROOMY(R.string.note_look_roomy, 1.3f), EXTRA(R.string.note_look_extra, 1.6f),
}

internal data class NotePresentation(
    val paper: NotePaper = NotePaper.PLAIN,
    val letters: NoteLetters = NoteLetters.EASY_READ,
    val spacing: NoteSpacing = NoteSpacing.NORMAL,
    val easyView: Boolean = false,
    val dictation: Boolean = true,
    val readBack: Boolean = true,
)

internal val LocalNotePresentation = staticCompositionLocalOf { NotePresentation() }

/** Presentation only. Note content and versioning stay with its editor. */
internal class NotePresentationStore(context: Context) {
    private val app = context.applicationContext
    private fun disk() = app.getSharedPreferences("halo_note_presentation", Context.MODE_PRIVATE)

    suspend fun load(): NotePresentation = withContext(Dispatchers.IO) {
        val disk = disk()
        NotePresentation(
            paper = NotePaper.entries.firstOrNull { it.name == disk.getString("paper", null) } ?: NotePaper.PLAIN,
            letters = NoteLetters.entries.firstOrNull { it.name == disk.getString("letters", null) } ?: NoteLetters.EASY_READ,
            spacing = NoteSpacing.entries.firstOrNull { it.name == disk.getString("spacing", null) } ?: NoteSpacing.NORMAL,
            easyView = disk.getBoolean("easy_view", false),
            dictation = disk.getBoolean("dictation", true),
            readBack = disk.getBoolean("read_back", true),
        )
    }

    suspend fun save(draft: NotePresentation) = withContext(Dispatchers.IO) {
        check(disk().edit().putString("paper", draft.paper.name).putString("letters", draft.letters.name)
            .putString("spacing", draft.spacing.name).putBoolean("easy_view", draft.easyView)
            .putBoolean("dictation", draft.dictation).putBoolean("read_back", draft.readBack).commit())
    }
}

@Composable
internal fun NotePresentation.textStyle(base: TextStyle, heading: Boolean = false): TextStyle {
    val russian = LocalConfiguration.current.locales.get(0)?.language == "ru"
    val family = when (letters) {
        NoteLetters.EASY_READ -> when {
            heading -> base.fontFamily
            russian -> HaloBrandFonts.cyrillic
            else -> HaloBrandFonts.reading
        }
        NoteLetters.BOOK -> FontFamily.Serif
        NoteLetters.ROUND -> if (russian) HaloBrandFonts.cyrillic else HaloBrandFonts.display
    }
    val size = if (easyView && !heading) maxOf(base.fontSize.value, 21f).sp else base.fontSize
    val normalHeight = maxOf(if (easyView && !heading) 29f else 0f, base.lineHeight.value.takeIf { it.isFinite() } ?: 25f)
    return base.copy(fontFamily = family, fontSize = size, lineHeight = (normalHeight * spacing.factor).sp)
}

internal fun Modifier.notePaper(paper: NotePaper, ink: Color): Modifier =
    if (paper == NotePaper.PLAIN) this else drawWithCache {
        val interval = if (paper == NotePaper.LINED) 26.dp.toPx() else 16.dp.toPx()
        val line = 1.dp.toPx()
        onDrawBehind {
            var y = interval
            while (y < size.height) {
                drawLine(ink, Offset(0f, y), Offset(size.width, y), line)
                y += interval
            }
            if (paper == NotePaper.GRID) {
                var x = interval
                while (x < size.width) {
                    drawLine(ink, Offset(x, 0f), Offset(x, size.height), line)
                    x += interval
                }
            }
        }
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun NotePresentationSheet(saved: NotePresentation, onApply: suspend (NotePresentation) -> Unit, onDismiss: () -> Unit) {
    var draft by remember(saved) { mutableStateOf(saved) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.note_look_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.note_look_paper), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
                for (paper in NotePaper.entries) {
                    val active = draft.paper == paper
                    Surface(onClick = { draft = draft.copy(paper = paper) }, enabled = !saving,
                        modifier = Modifier.weight(1f).semantics { selected = active; role = Role.RadioButton },
                        shape = HaloShapes.control,
                        border = BorderStroke(if (active) 2.dp else 1.dp,
                            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.notePaper(paper, MaterialTheme.colorScheme.outline.copy(alpha = .27f)).padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Spacer(Modifier.fillMaxWidth(.75f).height(3.dp).background(MaterialTheme.colorScheme.outline))
                            Spacer(Modifier.fillMaxWidth(.5f).height(3.dp).background(MaterialTheme.colorScheme.outline))
                            Text(stringResource(paper.label), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            Text(stringResource(R.string.note_look_letters), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (letters in NoteLetters.entries) FilterChip(draft.letters == letters,
                    { draft = draft.copy(letters = letters) }, { Text(stringResource(letters.label)) },
                    enabled = !saving, modifier = Modifier.heightIn(min = 48.dp))
            }
            Text(stringResource(R.string.note_look_spacing), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (spacing in NoteSpacing.entries) FilterChip(draft.spacing == spacing,
                    { draft = draft.copy(spacing = spacing) }, { Text(stringResource(spacing.label)) },
                    enabled = !saving, modifier = Modifier.heightIn(min = 48.dp))
            }
            HaloWorkspaceToggleRow(title = stringResource(R.string.note_look_easy_view),
                checked = draft.easyView, enabled = !saving, onCheckedChange = { draft = draft.copy(easyView = it) })
            HaloWorkspaceToggleRow(title = stringResource(R.string.note_look_dictation),
                checked = draft.dictation, enabled = !saving, onCheckedChange = { draft = draft.copy(dictation = it) })
            HaloWorkspaceToggleRow(title = stringResource(R.string.note_look_read_controls),
                checked = draft.readBack, enabled = !saving, onCheckedChange = { draft = draft.copy(readBack = it) })
            Surface(shape = HaloShapes.card, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Text(stringResource(R.string.note_look_preview), Modifier.fillMaxWidth()
                    .notePaper(draft.paper, MaterialTheme.colorScheme.outline.copy(alpha = .24f)).padding(14.dp),
                    style = draft.textStyle(MaterialTheme.typography.bodyLarge))
            }
            if (failed) Text(uiText("Could not save your change. Try again."), color = MaterialTheme.colorScheme.error)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { draft = saved; failed = false }, enabled = !saving && draft != saved,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(uiText("Undo")) }
                Button(onClick = {
                    saving = true
                    failed = false
                    scope.launch {
                        try {
                            onApply(draft)
                            onDismiss()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            failed = true
                        } finally {
                            saving = false
                        }
                    }
                }, enabled = !saving && draft != saved, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(uiText("Apply"))
                }
            }
        }
    }
}
