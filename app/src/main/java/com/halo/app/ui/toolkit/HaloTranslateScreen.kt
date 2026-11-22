package com.halo.app.ui.toolkit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.ui.instruments.HaloInstrumentAction
import com.halo.app.ui.instruments.HaloInstrumentBar
import com.halo.app.ui.screens.notebook.ToolZoneHeader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private data class TranslationTurn(val original: String, val translated: String, val language: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HaloTranslateScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reader = Graph.readAloud
    var readerToken by remember { mutableStateOf<String?>(null) }
    DisposableEffect(reader) { onDispose { readerToken?.let(reader::stopSession) } }
    var from by rememberSaveable { mutableStateOf("en") }
    var to by rememberSaveable { mutableStateOf("ru") }
    var conversation by rememberSaveable { mutableStateOf(false) }
    var sayTurns by rememberSaveable { mutableStateOf(false) }
    var cloudVoice by rememberSaveable { mutableStateOf(false) }
    var translateSpoken by rememberSaveable { mutableStateOf(true) }
    var input by remember { mutableStateOf("") }
    var replyInput by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<TranslationTurn?>(null) }
    var turns by remember { mutableStateOf(emptyList<TranslationTurn>()) }
    var busy by remember { mutableStateOf(false) }
    var installed by remember { mutableStateOf<Boolean?>(null) }
    var checked by remember { mutableIntStateOf(0) }
    var packs by remember { mutableStateOf(false) }
    var keep by remember { mutableStateOf<TranslationTurn?>(null) }
    var savedId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableIntStateOf(0) }
    LaunchedEffect(from, to, checked) {
        installed = null
        installed = try { from == to || scanTranslationReady(from, to) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
    }
    fun select(source: String, target: String) {
        com.halo.app.ui.components.VoiceFieldCancellation.stop()
        from = source
        to = target
        result = null
        savedId = null
        installed = null
        checked++
    }
    fun perform(failure: Int, operation: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = 0
        scope.launch {
            try { requireAdultToolkit(context); operation() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = failure }
            finally { busy = false }
        }
    }
    fun read(turn: TranslationTurn) {
        readerToken?.let(reader::stopSession)
        readerToken = reader.start("translate", turn.translated,
            Graph.speechSynthesisRegistry.selector.ttsSelection.value.id,
            cloudApproved = cloudVoice && Graph.powerModeEnabled(), language = turn.language)
    }
    fun translate(reverse: Boolean, speech: String? = null) {
        val original = (speech ?: if (reverse) replyInput else input).trim().take(4000)
        if (original.isBlank()) return
        val source = if (reverse) to else from
        val target = if (reverse) from else to
        val isConversation = conversation
        val say = sayTurns && isConversation
        perform(R.string.halo11_translate_failed) {
            val family = requireAdultToolkit(context)
            val output = translateScanLines(listOf(HaloScanLine(original, null, 0)), source, target, download = false).single().text
            requireAdultToolkit(context, family)
            val turn = TranslationTurn(original, output, target)
            result = turn
            savedId = null
            if (isConversation) {
                turns = (turns + turn).takeLast(12)
                if (reverse) replyInput = "" else input = ""
                if (say) read(turn)
            }
        }
    }
    val latest = result
    val currentResult by rememberUpdatedState(result)
    val keptId = savedId
    val openPhrase: () -> Unit = {
        if (keptId != null) onOpen("notebook/note/${android.net.Uri.encode(keptId)}")
        else if (latest != null) keep = latest
        else onOpen("notebook")
    }
    Column(Modifier.fillMaxSize()) {
        ToolZoneHeader(stringResource(R.string.halo11_translate_title), onBack) {}
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HaloInstrumentBar("translate", mapOf(
                "translate.talk" to HaloInstrumentAction({ conversation = true }, !busy, selected = conversation),
                "translate.camera" to HaloInstrumentAction({
                    onOpen("scan?translate=true&from=${android.net.Uri.encode(from)}&to=${android.net.Uri.encode(to)}")
                }, !busy),
                "translate.type" to HaloInstrumentAction({ conversation = false }, !busy, selected = !conversation),
                "translate.offline" to HaloInstrumentAction({ packs = true }, !busy),
                "translate.phrasebook" to HaloInstrumentAction(openPhrase, !busy),
            ), primary = if (conversation) "translate.talk" else "translate.type")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TranslateLanguageMenu(from, !busy) { select(it, to) }
                TextButton({ select(to, from) }, enabled = !busy) { Text(stringResource(R.string.halo11_translate_swap)) }
                TranslateLanguageMenu(to, !busy) { select(from, it) }
            }
            if (conversation) {
                Text(stringResource(R.string.halo11_translate_turns))
                com.halo.app.ui.workspace.HaloWorkspaceToggleRow(
                    title = stringResource(R.string.halo11_translate_spoken_turns), checked = translateSpoken,
                    onCheckedChange = { translateSpoken = it })
                com.halo.app.ui.workspace.HaloWorkspaceToggleRow(
                    title = stringResource(R.string.halo11_translate_say_turns), checked = sayTurns,
                    onCheckedChange = { sayTurns = it })
                if (Graph.powerModeEnabled()) com.halo.app.ui.workspace.HaloWorkspaceToggleRow(
                    title = stringResource(R.string.halo11_translate_cloud_voice), checked = cloudVoice,
                    onCheckedChange = { cloudVoice = it })
            }
            Text(stringResource(when (installed) {
                true -> R.string.halo11_translate_local
                false -> R.string.halo11_translate_pack_needed
                null -> R.string.halo11_translate_checking
            }), style = MaterialTheme.typography.bodySmall)
            if (installed == false) TextButton({ packs = true }, enabled = !busy) { Text(stringResource(R.string.halo11_translate_packs)) }
            val ready = !busy && installed == true
            if (conversation) {
                TranslationSpeaker(
                    language = from, value = input, onChange = { input = it }, busy = busy,
                    canTranslate = ready, onTranslate = { translate(false) },
                    onSpeech = if (ready && translateSpoken) ({ spoken -> input = spoken.take(4000); translate(false, spoken) }) else null,
                )
                TranslationSpeaker(
                    language = to, value = replyInput, onChange = { replyInput = it }, busy = busy,
                    canTranslate = ready, onTranslate = { translate(true) },
                    onSpeech = if (ready && translateSpoken) ({ spoken -> replyInput = spoken.take(4000); translate(true, spoken) }) else null,
                )
            } else {
                HaloTextField(input, { input = it.take(4000); result = null; savedId = null }, kind = VoiceFieldKind.TEXT,
                    modifier = Modifier.fillMaxWidth(), enabled = !busy, language = from,
                    voiceEnabled = false, maxChars = 4000, readText = null,
                    label = { Text(stringResource(R.string.halo11_translate_input)) }, minLines = 3, maxLines = 7)
                Button({ translate(false) }, enabled = ready && input.isNotBlank()) {
                    Text(stringResource(R.string.halo11_translate_title))
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error != 0) Text(stringResource(error), color = MaterialTheme.colorScheme.error)
            if (latest != null) OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    SelectionContainer { Text(latest.translated) }
                    FlowRow {
                        TextButton({ read(latest) }) { Text(stringResource(R.string.halo11_translate_listen)) }
                        TextButton(openPhrase, enabled = !busy) {
                            Text(stringResource(if (keptId == null) R.string.halo11_translate_keep else R.string.halo11_translate_saved))
                        }
                    }
                    HaloReviewedDeviceText(latest.translated, latest.language, enabled = !busy) { currentResult === latest }
                }
            }
            if (conversation) for (turn in turns.asReversed().filter { it !== latest }) {
                ListItem(headlineContent = { Text(turn.translated) }, supportingContent = { Text(turn.original) },
                    trailingContent = { TextButton({ read(turn) }) { Text(stringResource(R.string.halo11_translate_listen)) } })
            }
        }
    }
    if (packs) AlertDialog(onDismissRequest = { packs = false },
        title = { Text(stringResource(R.string.halo11_translate_packs)) },
        text = { Text(stringResource(R.string.halo11_translate_packs_explain)) },
        confirmButton = { TextButton({
            packs = false
            perform(R.string.halo11_translate_pack_failed) {
                translateScanLines(emptyList(), from, to, download = true)
                checked++
            }
        }, enabled = !busy && installed == false) { Text(stringResource(R.string.halo11_translate_download)) } },
        dismissButton = { TextButton({ packs = false }) { Text(stringResource(android.R.string.cancel)) } })
    keep?.let { turn ->
        AlertDialog(onDismissRequest = { keep = null }, title = { Text(stringResource(R.string.halo11_translate_phrasebook)) },
            text = { Text(stringResource(R.string.halo11_translate_review, turn.original.take(200), turn.translated.take(200))) },
            confirmButton = { TextButton({
                keep = null
                perform(R.string.halo11_translate_keep_failed) {
                    savedId = Graph.notebookRepository.createNote(title = turn.original.lineSequence().first().take(100),
                        body = "${turn.original}\n\n${turn.translated}").id
                }
            }) { Text(stringResource(R.string.halo11_translate_keep)) } },
            dismissButton = { TextButton({ keep = null }) { Text(stringResource(android.R.string.cancel)) } })
    }
}

@Composable
private fun TranslationSpeaker(language: String, value: String, onChange: (String) -> Unit, busy: Boolean,
    canTranslate: Boolean, onTranslate: () -> Unit, onSpeech: ((String) -> Unit)?) {
    val name = uiText(scanLanguages.first { it.code == language }.name)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            HaloTextField(value, { onChange(it.take(4000)) }, kind = VoiceFieldKind.TEXT,
                modifier = Modifier.fillMaxWidth(), enabled = !busy, language = language,
                voiceEnabled = !busy, maxChars = 4000, readText = null, minLines = 2, maxLines = 5,
                onFinalSpeech = onSpeech,
                label = { Text(stringResource(R.string.halo11_translate_speaker, name)) })
            TextButton(onTranslate, enabled = canTranslate && value.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.halo11_translate_title))
            }
        }
    }
}

@Composable
private fun TranslateLanguageMenu(selected: String, enabled: Boolean, onSelect: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ menu = true }, enabled = enabled) { Text(uiText(scanLanguages.first { it.code == selected }.name)) }
        DropdownMenu(menu, { menu = false }) {
            scanLanguages.forEach { language ->
                DropdownMenuItem({ Text(uiText(language.name)) }, { menu = false; onSelect(language.code) })
            }
        }
    }
}
