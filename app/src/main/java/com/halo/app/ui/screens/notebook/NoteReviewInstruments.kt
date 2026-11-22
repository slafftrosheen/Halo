package com.halo.app.ui.screens.notebook

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.data.notebook.*
import com.halo.app.language.uiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal enum class NoteReviewInstrument { FONT, PARAGRAPH, FURNITURE, PAGE, COMMENTS, TRACK_CHANGES }

internal data class NoteInstrumentTarget(val selection: DocumentSelection, val block: NoteBlock)
internal data class NoteReviewRequest(val instrument: NoteReviewInstrument, val target: NoteInstrumentTarget?, val page: NotePageSettings)

internal fun NoteEditorUiState.noteInstrumentTarget(preferredBlock: String? = null): NoteInstrumentTarget? {
    val active = selection
    val block = blocks.firstOrNull { it.id == (preferredBlock ?: active?.blockId) && !it.isPendingDeletion() }
        ?: blocks.firstOrNull { !it.isPendingDeletion() && it.type != NoteBlockType.DIVIDER }
        ?: return null
    val range = active?.takeIf { it.blockId == block.id }?.normalized(block.text.length)
    val target = if (range == null || range.isEmpty) DocumentSelection(block.id, 0, block.text.length) else range
    return NoteInstrumentTarget(target, block)
}

@Composable
internal fun NoteFontDialog(
    target: NoteInstrumentTarget, enabled: Boolean, onApply: (String, Int) -> Unit, onDismiss: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val library = remember(context) { NoteFontLibrary(context) }
    val scope = rememberCoroutineScope()
    val current = target.block.presentation.fonts.singleOrNull { it.start <= target.selection.start && it.end >= target.selection.end }
    var name by remember(target) { mutableStateOf(current?.font ?: "Default") }
    var size by remember(target) { mutableIntStateOf(current?.size ?: 16) }
    var fonts by remember(library) { mutableStateOf(library.choices()) }
    var importing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            failed = false
            try {
                name = library.import(uri)
                fonts = library.choices()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { importing = false }
        }
    }
    val preview = target.block.text.substring(target.selection.start, target.selection.end).take(100)
    NoteReviewDialog("Font & size", onDismiss, enabled && !importing, onApply = { onApply(name, size) }) {
        Text(preview, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily(library.typeface(name)),
            fontSize = size.sp), maxLines = 3)
        if (current == null && target.block.presentation.fonts.any { it.end > target.selection.start && it.start < target.selection.end }) {
            Text(uiText("Mixed formatting"), style = MaterialTheme.typography.bodySmall)
        }
        NoteOptions(fonts, name, { name = it })
        TextButton(onClick = { fontPicker.launch(arrayOf("font/*", "application/x-font-ttf", "application/vnd.ms-opentype", "application/octet-stream")) },
            enabled = !importing) { Text(uiText("Import font")) }
        if (failed) Text(uiText("That font could not be imported."), color = MaterialTheme.colorScheme.error)
        Text(uiText("Font size: %1\$d pt", size))
        Slider(size.toFloat(), { size = it.roundToInt() }, valueRange = 8f..72f, steps = 63)
    }
}

@Composable
internal fun NoteParagraphDialog(block: NoteBlock, enabled: Boolean, onApply: (NoteAlignment, Int) -> Unit, onDismiss: () -> Unit) {
    var alignment by remember(block) { mutableStateOf(block.presentation.alignment ?: NoteAlignment.LEFT) }
    var spacing by remember(block) { mutableIntStateOf(block.presentation.spacing ?: 15) }
    NoteReviewDialog("Align & spacing", onDismiss, enabled, onApply = { onApply(alignment, spacing) }) {
        NoteOptions(NoteAlignment.entries.map { it to it.label }, alignment, { alignment = it })
        Text(uiText("Line spacing: %1$.1f", spacing / 10f))
        Slider(spacing.toFloat(), { spacing = it.roundToInt() }, valueRange = 10f..30f, steps = 19)
        Text(block.text.take(120), Modifier.fillMaxWidth(), style = noteParagraphStyle(
            MaterialTheme.typography.bodyLarge, NoteBlockPresentation(alignment = alignment, spacing = spacing)))
    }
}

@Composable
internal fun NotePageDialog(
    initial: NotePageSettings, furniture: Boolean, enabled: Boolean, onApply: (NotePageSettings) -> Unit, onDismiss: () -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    var firstPage by remember(initial) { mutableStateOf(initial.firstPage.toString()) }
    val number = firstPage.toIntOrNull()?.takeIf { it in 1..9999 }
    val ready = enabled && (!furniture || value.pageNumbers == NotePageNumbers.NONE || number != null)
    NoteReviewDialog(if (furniture) "Header, footer, page numbers" else "Page setup", onDismiss, ready,
        onApply = { onApply(value.copy(firstPage = number ?: initial.firstPage)) }) {
        if (furniture) {
            OutlinedTextField(value.header, { value = value.copy(header = it.take(160)) }, singleLine = true,
                label = { Text(uiText("Header text")) })
            OutlinedTextField(value.footer, { value = value.copy(footer = it.take(160)) }, singleLine = true,
                label = { Text(uiText("Footer text")) })
            NoteOptions(NotePageNumbers.entries.map { it to it.label }, value.pageNumbers, { value = value.copy(pageNumbers = it) })
            if (value.pageNumbers != NotePageNumbers.NONE) OutlinedTextField(firstPage, { firstPage = it.filter(Char::isDigit).take(4) },
                singleLine = true, label = { Text(uiText("Start numbering at")) })
        } else {
            Text(uiText("Paper size"))
            NoteOptions(NotePageSize.entries.map { it to it.label }, value.size, { value = value.copy(size = it) })
            NoteOptions(listOf(false to "Portrait", true to "Landscape"), value.landscape, { value = value.copy(landscape = it) })
            Text(uiText("Margins: %1$.1f cm", value.marginMm / 10f))
            Slider(value.marginMm.toFloat(), { value = value.copy(marginMm = it.roundToInt()) }, valueRange = 5f..50f, steps = 44)
            Text(uiText("Columns"))
            NoteOptions((1..3).map { it to it.toString() }, value.columns, { value = value.copy(columns = it) })
        }
        NotePaperPreview(value.copy(firstPage = number ?: initial.firstPage), Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun NotePaperPreview(value: NotePageSettings, modifier: Modifier = Modifier) {
    val width = if (value.landscape) 180.dp else 132.dp
    val height = width * value.height.toFloat() / value.width
    val color = MaterialTheme.colorScheme.outlineVariant
    Column(modifier.size(width, height).border(1.dp, color).padding((3f + value.marginMm / 4f).dp)) {
        NotePageFurniture(value, true)
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(value.columns) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(5) { line -> Box(Modifier.fillMaxWidth(if (line == 4) .65f else 1f).height(2.dp).background(color)) }
                }
            }
        }
        NotePageFurniture(value, false)
    }
}

@Composable
internal fun NotePageFurniture(value: NotePageSettings, top: Boolean) {
    val text = if (top) value.header else value.footer
    val name = value.pageNumbers.name
    val showsNumber = name.startsWith(if (top) "HEADER" else "FOOTER")
    if (text.isEmpty() && !showsNumber) return
    val number = if (showsNumber) value.firstPage.toString() else ""
    val left = showsNumber && name.endsWith("LEFT")
    val centered = showsNumber && name.endsWith("CENTER")
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (left) Text(number, style = MaterialTheme.typography.labelSmall)
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
        if (centered) Text(number, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
        else if (showsNumber && !left) Text(number, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun NoteCommentsDialog(
    target: NoteInstrumentTarget?, blocks: List<NoteBlock>, enabled: Boolean,
    onAdd: (String, String) -> Unit, onChange: (String, String, String, String?, Boolean?) -> Unit, onDismiss: () -> Unit,
) {
    var initials by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var thread by remember { mutableStateOf<String?>(null) }
    val signature = initials.trim().ifBlank { uiText("You") }
    val comments = blocks.flatMap { block -> block.presentation.comments.map { block.id to it } }
        .sortedBy { (_, comment) -> comment.resolved }
    val current = comments.firstOrNull { (_, comment) -> comment.id == thread }
    val selected = target?.let { it.block.text.substring(it.selection.start, it.selection.end).take(240) }.orEmpty()
    val canAdd = current == null && target != null && message.isNotBlank() && enabled && target.block.presentation.comments.size < 40
    val canReply = current != null && current.second.replies.size < 25 && message.isNotBlank() && enabled
    NoteReviewDialog("Note comments", onDismiss, canAdd || canReply,
        applyLabel = if (current == null) "Add comment" else "Send reply", onApply = {
            if (current == null) onAdd(signature, message)
            else onChange(current.first, current.second.id, signature, message, null)
            message = ""
        }) {
        if (current == null) {
            if (selected.isNotBlank()) Text(selected, style = MaterialTheme.typography.labelLarge)
            if (comments.isEmpty()) Text(uiText("There are no comments yet."))
            comments.forEach { (_, comment) ->
                TextButton(onClick = { thread = comment.id; message = "" }) {
                    Text(comment.author + " · " + comment.text.take(70) + if (comment.resolved) " · " + uiText("Resolved") else "")
                }
            }
        } else {
            val comment = current.second
            TextButton({ thread = null; message = "" }) { Text(uiText("All comments")) }
            if (comment.quote.isNotBlank()) Text(comment.quote, style = MaterialTheme.typography.labelLarge)
            NoteReviewThread(comment.author, comment.text, comment.replies, comment.resolved)
            TextButton(onClick = { onChange(current.first, comment.id, signature, null, !comment.resolved) }, enabled = enabled) {
                Text(uiText(if (comment.resolved) "Reopen" else "Resolve"))
            }
        }
        if (enabled && (target != null || current != null)) {
            OutlinedTextField(initials, { initials = it.take(24) }, label = { Text(uiText("Initials")) }, singleLine = true)
            OutlinedTextField(message, { message = it.take(1200) }, maxLines = 5, minLines = 2,
                label = { Text(uiText(if (current == null) "Comment" else "Reply")) })
        }
    }
}

@Composable
internal fun NoteTrackChangesDialog(
    state: NoteEditorUiState, enabled: Boolean, onToggle: (Boolean) -> Unit,
    onResolve: (String?, Boolean, Int?) -> Unit, onDismiss: () -> Unit,
) {
    val changes = state.blocks.filter { it.presentation.change != null }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(uiText("Track changes")) }, text = {
        Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(uiText("Track new edits"), Modifier.weight(1f))
                Switch(state.pageSettings.trackChanges, onToggle, enabled = enabled)
            }
            if (changes.isEmpty()) Text(uiText("There are no changes to review."))
            if (changes.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton({ onResolve(null, true, null) }, enabled = enabled) { Text(uiText("Accept all")) }
                TextButton({ onResolve(null, false, null) }, enabled = enabled) { Text(uiText("Reject all")) }
            }
            changes.forEach { block ->
                val change = requireNotNull(block.presentation.change)
                val hunks = remember(change, block.text) { reviewHunks(block, change) }
                hunks.forEachIndexed { index, hunk ->
                    HorizontalDivider()
                    NoteChangeText(hunk.removed, hunk.inserted, maxLength = 1500)
                    Row {
                        val hunkIndex = index.takeIf { change.kind == NoteChangeKind.TEXT }
                        TextButton({ onResolve(block.id, true, hunkIndex) }, enabled = enabled) { Text(uiText("Accept")) }
                        TextButton({ onResolve(block.id, false, hunkIndex) }, enabled = enabled) { Text(uiText("Reject")) }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onDismiss) { Text(uiText("Done")) } })
}

private fun reviewHunks(block: NoteBlock, change: NoteChange): List<NoteTextHunk> = when (change.kind) {
    NoteChangeKind.TEXT -> noteTextHunks(change.before, block.text)
    NoteChangeKind.INSERTION -> listOf(NoteTextHunk(0, 0, "", block.text))
    NoteChangeKind.DELETION -> listOf(NoteTextHunk(0, 0, change.before, ""))
}

@Composable
internal fun NoteInlineReview(block: NoteBlock) {
    val change = block.presentation.change ?: return
    val hunks = remember(change, block.text) { reviewHunks(block, change) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        hunks.take(3).forEach { NoteChangeText(it.removed, it.inserted, 150) }
    }
}

@Composable
private fun NoteChangeText(removed: String, inserted: String, maxLength: Int) {
    val green = if (MaterialTheme.colorScheme.surface.red > .5f) Color(0xff15633b) else Color(0xff9fe0b2)
    val red = MaterialTheme.colorScheme.error
    val text = buildAnnotatedString {
        if (removed.isNotEmpty()) withStyle(SpanStyle(color = red, textDecoration = TextDecoration.LineThrough)) { append(removed.take(maxLength)) }
        if (removed.isNotEmpty() && inserted.isNotEmpty()) append("  ")
        if (inserted.isNotEmpty()) withStyle(SpanStyle(color = green)) { append(inserted.take(maxLength)) }
    }
    Text(text, style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun NoteAnchoredCommentMarkers(block: NoteBlock, onOpen: () -> Unit) {
    val opened = block.presentation.comments.filterNot(NoteComment::resolved)
    if (opened.isEmpty()) return
    val initials = opened.map { it.author.trim().take(3) }.filter(String::isNotEmpty).distinct()
    TextButton(onClick = onOpen, modifier = Modifier.padding(start = 8.dp)) {
        Text(initials.joinToString(" · ").ifEmpty { uiText("Comment") } + " · " + uiText("%1\$d open", opened.size))
    }
}

internal fun noteParagraphStyle(base: TextStyle, presentation: NoteBlockPresentation): TextStyle {
    val alignment = when (presentation.alignment) {
        NoteAlignment.LEFT -> TextAlign.Left
        NoteAlignment.CENTER -> TextAlign.Center
        NoteAlignment.RIGHT -> TextAlign.Right
        NoteAlignment.JUSTIFY -> TextAlign.Justify
        null -> base.textAlign
    }
    val lineHeight = (presentation.spacing?.let { spacing ->
        val spanSize = presentation.fonts.maxOfOrNull(NoteFontSpan::size)?.toFloat()
        (spanSize ?: base.fontSize.value.takeIf(Float::isFinite) ?: 16f).times(spacing / 10f).sp
    }) ?: base.lineHeight
    return base.copy(textAlign = alignment, lineHeight = lineHeight)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> NoteOptions(options: List<Pair<T, String>>, current: T, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) -> FilterChip(current == value, { onSelect(value) }, label = { Text(uiText(label)) }) }
    }
}

@Composable
private fun NoteReviewDialog(
    title: String, onDismiss: () -> Unit, enabled: Boolean, onApply: () -> Unit, applyLabel: String = "Apply",
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(uiText(title)) }, text = {
        Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }, confirmButton = { TextButton(onApply, enabled = enabled) { Text(uiText(applyLabel)) } },
        dismissButton = { TextButton(onDismiss) { Text(uiText("Close")) } })
}

@Composable
internal fun NoteReviewDialogs(
    request: NoteReviewRequest, state: NoteEditorUiState, viewModel: NoteEditorViewModel, onDismiss: () -> Unit,
) {
    val target = request.target
    val allowed = state.isEditable && !state.isGenerating && state.status != SaveStatus.CONFLICT
    fun targetIsCurrent(): Boolean {
        val current = viewModel.uiState.value
        return target != null && current.isEditable && current.status != SaveStatus.CONFLICT && current.blocks.any {
            it.id == target.block.id && it.text == target.block.text && !it.isPendingDeletion()
        }
    }
    val canApplyToTarget = allowed && targetIsCurrent()
    when (request.instrument) {
        NoteReviewInstrument.FONT -> if (target != null) NoteFontDialog(target, canApplyToTarget, onApply = { font, size ->
            if (targetIsCurrent()) viewModel.dispatch(DocumentCommand.SetFont(target.selection, font, size))
            onDismiss()
        }, onDismiss)
        NoteReviewInstrument.PARAGRAPH -> if (target != null) NoteParagraphDialog(target.block, canApplyToTarget, onApply = { align, spacing ->
            if (targetIsCurrent()) viewModel.dispatch(DocumentCommand.SetParagraph(target.block.id, align, spacing))
            onDismiss()
        }, onDismiss)
        NoteReviewInstrument.FURNITURE, NoteReviewInstrument.PAGE -> NotePageDialog(
            request.page, request.instrument == NoteReviewInstrument.FURNITURE, allowed && request.page == state.pageSettings,
            onApply = { value -> viewModel.updatePageSettings(request.page, value); onDismiss() }, onDismiss,
        )
        NoteReviewInstrument.COMMENTS -> NoteCommentsDialog(target.takeIf { targetIsCurrent() }, state.blocks, allowed,
            onAdd = { author, message ->
                if (target != null && targetIsCurrent()) viewModel.dispatch(DocumentCommand.AddComment(target.selection, author, message))
            },
            onChange = { block, id, author, reply, resolved ->
                val current = viewModel.uiState.value
                if (current.isEditable && current.status != SaveStatus.CONFLICT) {
                    viewModel.dispatch(DocumentCommand.UpdateComment(block, id, author, reply, resolved))
                }
            }, onDismiss = onDismiss,
        )
        NoteReviewInstrument.TRACK_CHANGES -> NoteTrackChangesDialog(state, allowed,
            onToggle = { viewModel.updatePageSettings(state.pageSettings, state.pageSettings.copy(trackChanges = it)) },
            onResolve = { block, accept, index ->
                val current = viewModel.uiState.value
                if (current.isEditable && current.status != SaveStatus.CONFLICT && current.blocks == state.blocks) {
                    viewModel.dispatch(DocumentCommand.ResolveChanges(block, accept, index))
                }
            }, onDismiss = onDismiss,
        )
    }
}

@Composable
internal fun noteStyledRanges(block: NoteBlock): List<AnnotatedString.Range<SpanStyle>> {
    val context = LocalContext.current.applicationContext
    val fonts = remember(context) { NoteFontLibrary(context) }
    val commentTint = MaterialTheme.colorScheme.tertiaryContainer
    return remember(block.presentation, block.text, fonts, commentTint) {
        val fontRanges = block.presentation.fonts.filter { it.inRange(block.text.length) }.map {
            AnnotatedString.Range(SpanStyle(fontSize = it.size.sp, fontFamily = FontFamily(fonts.typeface(it.font))), it.start, it.end)
        }
        val commentRanges = block.presentation.comments.filter {
            !it.resolved && it.start >= 0 && it.end > it.start && it.end <= block.text.length
        }.map { AnnotatedString.Range(SpanStyle(background = commentTint), it.start, it.end) }
        fontRanges + commentRanges
    }
}

@Composable
internal fun noteEditorTransformation(block: NoteBlock): VisualTransformation {
    val styles = noteStyledRanges(block)
    return remember(styles) {
        if (styles.isEmpty()) VisualTransformation.None
        else VisualTransformation { raw ->
            val converted = buildAnnotatedString {
                append(raw)
                styles.forEach { if (it.end <= raw.length) addStyle(it.item, it.start, it.end) }
            }
            TransformedText(converted, OffsetMapping.Identity)
        }
    }
}

@Composable
internal fun NotePdfExportDialog(busy: Boolean, failed: Boolean, onApply: (Boolean, Int?, Int?) -> Unit, onDismiss: () -> Unit) {
    var includeComments by remember { mutableStateOf(false) }
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    val from = first.toIntOrNull()?.takeIf { it > 0 }
    val through = last.toIntOrNull()?.takeIf { it >= (from ?: 1) }
    NoteReviewDialog("PDF · print layout", onDismiss, !busy && (first.isBlank() || from != null) &&
        (last.isBlank() || through != null), onApply = { onApply(includeComments, from, through) }, applyLabel = "Export") {
        Text(uiText("Leave the page range blank to export every page."))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(first, { first = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f),
                singleLine = true, enabled = !busy, label = { Text(uiText("First page")) })
            OutlinedTextField(last, { last = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f),
                singleLine = true, enabled = !busy, label = { Text(uiText("Last page")) })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(uiText("Include comments"), Modifier.weight(1f))
            Switch(includeComments, { includeComments = it }, enabled = !busy)
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (failed) Text(uiText("The PDF could not be saved. Check the page range and try again."), color = MaterialTheme.colorScheme.error)
    }
}
