package com.halo.app.ui.toolkit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.language.uiText
import kotlinx.coroutines.CancellationException

@Composable
internal fun HaloRecordScrubber(transport: HaloRecordTransport, selection: HaloRecordWindow?, elapsed: Int) {
    val start: Int = selection?.startMs ?: 0
    val end: Int = selection?.endMs ?: elapsed.coerceAtLeast(1) * 1000
    Slider(transport.position.coerceIn(start, end).toFloat(), { transport.seek(it.toInt()) },
        valueRange = start.toFloat()..end.toFloat())
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(onClick = { transport.seek(transport.position - 10_000) }) { Text("−10 s") }
        Text(haloRecordControlClock(transport.position).substringBefore('.'), Modifier.align(Alignment.CenterVertically))
        TextButton(onClick = { transport.seek(transport.position + 10_000) }) { Text("+10 s") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HaloRecordTrimPanel(audio: HaloRecordAudio, initial: HaloRecordWindow, onClose: () -> Unit,
    onApply: (HaloRecordWindow, Boolean) -> Unit) {
    var selection by remember(audio, initial) { mutableStateOf(initial) }
    var peaks by remember(audio) { mutableStateOf<List<Float>?>(null) }
    var failed by remember(audio) { mutableStateOf(false) }
    LaunchedEffect(audio) {
        try { peaks = readHaloRecordWaveform(audio) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.halo_record_trim_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.halo_record_trim_description))
            if (failed) Text(stringResource(R.string.halo_record_trim_unavailable))
            peaks?.let { values ->
                val selected = MaterialTheme.colorScheme.primary
                val outside = MaterialTheme.colorScheme.outlineVariant
                Canvas(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp)) {
                    val barWidth = size.width / values.size
                    values.forEachIndexed { index, peak ->
                        val x = (index + .5f) * barWidth
                        val instant = (index + .5f) * audio.durationMs / values.size
                        val length = (size.height * peak / 2f).coerceAtLeast(1.dp.toPx())
                        drawLine(if (instant >= selection.startMs && instant <= selection.endMs) selected else outside,
                            Offset(x, center.y - length), Offset(x, center.y + length), strokeWidth = barWidth * .68f)
                    }
                }
            }
            fun change(start: Int = selection.startMs, end: Int = selection.endMs) { selection = audio.checkedWindow(start, end) }
            RangeSlider(value = selection.startMs.toFloat()..selection.endMs.toFloat(), onValueChange = { range ->
                val start = haloRecordTrimHandle(range.start, audio.durationMs)
                val end = if (range.endInclusive == audio.durationMs.toFloat()) audio.durationMs else haloRecordTrimHandle(range.endInclusive, audio.durationMs)
                if (end - start >= minOf(MIN_RECORD_TRIM_MS, audio.durationMs)) change(start, end)
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), valueRange = 0f..audio.durationMs.toFloat(), enabled = !failed)
            Text(stringResource(R.string.halo_record_trim_start, haloRecordControlClock(selection.startMs)), fontFamily = FontFamily.Monospace)
            HaloTrimNudge(onEarlier = { change(start = selection.startMs - 100) }, onLater = { change(start = selection.startMs + 100) }, enabled = !failed)
            Text(stringResource(R.string.halo_record_trim_end, haloRecordControlClock(selection.endMs)), fontFamily = FontFamily.Monospace)
            HaloTrimNudge(onEarlier = { if (selection.durationMs > MIN_RECORD_TRIM_MS) change(end = selection.endMs - 100) },
                onLater = { change(end = selection.endMs + 100) }, enabled = !failed)
            OutlinedButton(onClick = { onApply(selection, true) }, enabled = !failed, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.halo_record_trim_listen))
            }
            Button(onClick = { onApply(selection, false) }, enabled = !failed, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.halo_record_trim_apply))
            }
            TextButton(onClick = { selection = audio.fullWindow }, enabled = selection != audio.fullWindow,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.halo_record_trim_restore)) }
        }
    }
}

@Composable
private fun HaloTrimNudge(onEarlier: () -> Unit, onLater: () -> Unit, enabled: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(onClick = onEarlier, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.halo_record_trim_earlier))
        }
        TextButton(onClick = onLater, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.halo_record_trim_later))
        }
    }
}

@Composable
internal fun HaloRecordExportMenu(hasWords: Boolean, onChoose: (HaloRecordExportFormat) -> Unit, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text(stringResource(R.string.halo_record_export)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HaloRecordExportFormat.entries.forEach { format ->
                OutlinedButton(onClick = { onChoose(format) }, enabled = format == HaloRecordExportFormat.AUDIO || hasWords,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(format.label())) }
            }
            if (!hasWords) Text(stringResource(R.string.halo_record_export_no_words), style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClose) { Text(uiText("Cancel")) } })
}

@Composable
internal fun HaloRecordExportDialog(review: HaloRecordExportReview, onSave: () -> Unit, onClose: () -> Unit) {
    val lines = remember(review.payload) { review.payload?.lines().orEmpty() }
    AlertDialog(onDismissRequest = onClose, title = { Text(stringResource(review.format.label())) }, text = {
        val payload = review.payload
        if (payload == null) Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.halo_record_export_audio_copy))
            Text(stringResource(R.string.halo_record_trim_duration, haloRecordControlClock(review.window.durationMs)))
        } else LazyColumn(Modifier.heightIn(max = 370.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            item { Text(stringResource(R.string.halo_record_export_review_words), Modifier.padding(bottom = 8.dp)) }
            items(lines) { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }, confirmButton = { TextButton(onSave) { Text(uiText("Save a copy")) } },
        dismissButton = { TextButton(onClose) { Text(uiText("Cancel")) } })
}

@Composable
internal fun HaloRecordChaptersPanel(chapters: List<HaloRecordChapter>, renamed: List<String>,
    onSeek: (Int) -> Unit, onRename: (HaloRecordChapter, String) -> Unit, onClose: () -> Unit) {
    var editing by remember { mutableStateOf<HaloRecordChapter?>(null) }
    var name by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onClose, title = { Text(stringResource(R.string.halo_record_chapters)) }, text = {
        LazyColumn(Modifier.heightIn(max = 370.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Text(stringResource(R.string.halo_record_chapters_hint)) }
            items(chapters.size, key = { chapters[it].startMs }) { index ->
                val chapter = chapters[index]
                val label = renamed.firstOrNull { it.substringBefore(':').toIntOrNull() == chapter.startMs }
                    ?.substringAfter(':') ?: stringResource(R.string.halo_record_chapter_number, index + 1)
                val range = "${haloRecordControlClock(chapter.startMs)} – ${haloRecordControlClock(chapter.endMs)}"
                if (editing == chapter) {
                    OutlinedTextField(name, { name = it.take(80) }, label = { Text(uiText("Name")) }, singleLine = true)
                    TextButton(enabled = name.isNotBlank(), onClick = { onRename(chapter, name.trim()); editing = null },
                        modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Save")) }
                } else {
                    OutlinedButton(onClick = { onSeek(chapter.startMs) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Column { Text(label); Text(range, style = MaterialTheme.typography.labelMedium) }
                    }
                    TextButton(onClick = { name = label; editing = chapter }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.halo_record_chapter_rename))
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClose) { Text(uiText("Done")) } })
}

private fun HaloRecordExportFormat.label() = when (this) {
    HaloRecordExportFormat.AUDIO -> R.string.halo_record_export_wave
    HaloRecordExportFormat.TRANSCRIPT -> R.string.halo_record_export_txt
    HaloRecordExportFormat.SUBTITLES -> R.string.halo_record_export_srt
}
