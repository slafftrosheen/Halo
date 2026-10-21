package com.halo.app.ui.screens.kids

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.language.uiText
import com.halo.app.study.SentenceSegmenter
import com.halo.app.ui.components.*
import com.halo.app.ui.fx.rememberHaloReducedMotion
import kotlinx.coroutines.launch

/** Pages and practice sentences come only from saved project text. No alignment or score is invented. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun KidsStoryReader(noteId: String, text: String, onDismiss: () -> Unit) {
    val hash = remember(text) { java.security.MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).take(12).joinToString("") { "%02x".format(it) } }
    val sentences = remember(text) { SentenceSegmenter.split(text) }
    val pages = remember(sentences) { sentences.chunked(3).map { it.joinToString(" ") } }
    val key = "kids_reading_v1_${noteId}_$hash"
    var page by rememberSaveable(noteId, hash) { mutableIntStateOf(
        runCatching { Graph.storage.getString(key)?.toIntOrNull() ?: 0 }.getOrDefault(0).coerceIn(0, (pages.size - 1).coerceAtLeast(0))) }
    var practice by rememberSaveable { mutableStateOf(false) }
    var sentenceIndex by rememberSaveable { mutableIntStateOf(0) }
    var heard by rememberSaveable { mutableStateOf("") }
    var large by rememberSaveable { mutableStateOf(false) }
    var spacing by rememberSaveable { mutableStateOf(false) }
    var automatic by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val reading by Graph.readAloud.state.collectAsStateWithLifecycle()
    val follow by Graph.readAloud.followWords.collectAsStateWithLifecycle()
    val reduced = rememberHaloReducedMotion()
    val ownPrefix = "kids-story:$noteId:$hash:"
    val pageId = ownPrefix + "page:$page"
    fun readPage(index: Int) { scope.launch {
        if (!readKidsText(ownPrefix + "page:$index", pages.getOrNull(index).orEmpty()))
            notice = "Stop the microphone before starting reading."
    } }
    fun dismiss() {
        automatic = false
        VoiceFieldCancellation.stop()
        onDismiss()
    }
    LaunchedEffect(page, practice) {
        if (!practice) try { Graph.storage.putString(key, page.toString()) }
        catch (_: Exception) { notice = "Could not save your reading position. Your story is kept." }
    }
    LaunchedEffect(reading?.id, reading?.notice) {
        if (!practice && automatic && reading?.id == pageId && reading?.notice == "Finished reading" && page < pages.lastIndex) {
            page += 1
            readPage(page)
        }
    }
    ModalBottomSheet(onDismissRequest = ::dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.94f)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(uiText(if (practice) "I read, halo listens" else "Read my story"), style = MaterialTheme.typography.titleLarge)
                Text(uiText(if (practice) "Word timing is unavailable. Read one sentence, then compare what halo heard. There is no score."
                    else "Pages use your saved text. Tap a word to hear it again."), style = MaterialTheme.typography.bodyMedium)
                val passage = if (practice) sentences.getOrNull(sentenceIndex).orEmpty() else pages.getOrNull(page).orEmpty()
                val current = reading?.takeIf { it.id.startsWith(ownPrefix) }
                val highlight = MaterialTheme.colorScheme.primaryContainer
                val styled = buildAnnotatedString {
                    append(passage)
                    if (follow && current?.playing == true) {
                        val start = passage.indexOf(current.current)
                        if (start >= 0) {
                            val range = current.range
                            val from = if (range != null) start + range.first else start
                            val to = if (range != null) start + range.last + 1 else start + current.current.length
                            if (from >= 0 && to <= passage.length && from < to)
                                addStyle(if (reduced || range == null) SpanStyle(textDecoration = TextDecoration.Underline)
                                    else SpanStyle(background = highlight), from, to)
                        }
                    }
                }
                KidsStoryPage(styled, large = large, spacing = spacing, onWordClick = { offset ->
                        val word = Regex("[\\p{L}\\p{M}]+(?:['’][\\p{L}\\p{M}]+)*").findAll(passage)
                            .firstOrNull { offset in it.range }?.value
                        if (word != null) { automatic = false; scope.launch { readKidsText(ownPrefix + "word", word) } }
                    })
                Text(uiText(if (practice) "Sentence %1\$d of %2\$d" else "Page %1\$d of %2\$d",
                    if (practice) sentenceIndex + 1 else page + 1,
                    if (practice) sentences.size else pages.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(large, { large = !large }, { Text(uiText("Larger text")) }, modifier = Modifier.heightIn(min = 56.dp))
                    FilterChip(spacing, { spacing = !spacing }, { Text(uiText("More spacing")) }, modifier = Modifier.heightIn(min = 56.dp))
                }
                if (practice) {
                    HaloTextField(value = heard, onValueChange = { heard = it }, kind = VoiceFieldKind.TEXT,
                        label = { Text(uiText("What halo heard")) }, maxChars = 2000)
                    if (heard.isNotBlank()) Text(heard, style = MaterialTheme.typography.bodyLarge)
                    KidsPrimaryButton(onClick = {
                        VoiceFieldCancellation.stop(); automatic = false
                        sentenceIndex = (sentenceIndex + 1).coerceAtMost(sentences.lastIndex); heard = ""
                    }, enabled = sentenceIndex < sentences.lastIndex,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(uiText("Next sentence")) }
                    OutlinedButton(onClick = { scope.launch { readKidsText(ownPrefix + "example", passage) } },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(uiText("Read to me")) }
                } else {
                    KidsPrimaryButton(onClick = { automatic = true; readPage(page) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(uiText("Read to me")) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { automatic = false; VoiceFieldCancellation.stop(); page -= 1 },
                            enabled = page > 0, modifier = Modifier.heightIn(min = 56.dp)) { Text(uiText("Previous page")) }
                        OutlinedButton(onClick = { automatic = false; VoiceFieldCancellation.stop(); page += 1 },
                            enabled = page < pages.lastIndex, modifier = Modifier.heightIn(min = 56.dp)) { Text(uiText("Next page")) }
                    }
                    TextButton(onClick = { automatic = false; VoiceFieldCancellation.stop(); practice = true; sentenceIndex = (page * 3).coerceAtMost(sentences.lastIndex) },
                        modifier = Modifier.heightIn(min = 56.dp)) { Text(uiText("Read it myself")) }
                }
                if (practice) TextButton(onClick = { VoiceFieldCancellation.stop(); practice = false },
                    modifier = Modifier.heightIn(min = 56.dp)) { Text(uiText("Back to story")) }
                notice?.let { Text(uiText(it)) }
            }
            HaloReadAloudBar(allowVoiceSettings = false, onVoiceSettings = {})
            TextButton(onClick = ::dismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).navigationBarsPadding()) {
                Text(uiText("Close"))
            }
        }
    }
}
