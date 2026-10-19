package com.halo.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.audio.*
import com.halo.app.language.uiText
import com.halo.sdk.runtime.SpeechOutputTarget
import kotlinx.coroutines.*

private data class SupertonicPreviewTicket(
    val transactionId: String, val fingerprint: String, val language: String, val voice: Int
)

/** Import and explicitly audition a language/voice before enabling the provider. */
@Composable
internal fun SupertonicPackControls(
    provider: SupertonicSpeechProvider,
    output: HaloSpeechOutputCoordinator,
    testTarget: SpeechOutputTarget,
    testLabel: String,
    testBusy: Boolean,
) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val state by provider.packs.state.collectAsState()
    val progress by provider.packs.downloadProgress.collectAsState()
    val test by output.outputTest.collectAsState()
    val scope=rememberCoroutineScope()
    var installed by remember { mutableStateOf(emptyList<SupertonicInstalledPack>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var ticket by remember { mutableStateOf<SupertonicPreviewTicket?>(null) }
    val storage=com.halo.app.di.ManualDependencyGraph.storage
    var warm by remember { mutableStateOf(storage.getBoolean("supertonic_warm",false)) }
    var threads by remember { mutableIntStateOf(storage.getInt("supertonic_threads",2).coerceIn(1,4)) }
    var steps by remember { mutableIntStateOf(storage.getInt("supertonic_steps",8).coerceIn(4,16)) }
    var speed by remember { mutableFloatStateOf((storage.getString("supertonic_speed")?.toFloatOrNull() ?: 1f).coerceIn(.6f,1.5f)) }
    var advanced by remember { mutableStateOf(false) }
    LaunchedEffect(state) { installed=provider.installedPacks() }
    fun action(task: suspend () -> Unit) {
        scope.launch {
            busy=true; error=null
            try { task(); installed=provider.installedPacks() }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(e: Exception) { error=e.message ?: "Supertonic operation failed" }
            finally { busy=false }
        }
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if(uri!=null) action { provider.importPack(uri) }
    }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(uiText("Download or import the official Supertonic 3 INT8 pack. Preview and confirm each voice/language before use."),
            style=MaterialTheme.typography.bodySmall)
        Button(enabled=!busy && !testBusy,onClick={action { provider.packs.downloadOfficial() }}) {
            Text(uiText("Download Supertonic 3 INT8"))
        }
        progress?.let { bytes ->
            LinearProgressIndicator(progress={
                (bytes.toFloat() / SupertonicPackStore.OFFICIAL_BYTES.toFloat()).coerceIn(0f,1f)
            },modifier=Modifier.fillMaxWidth())
            Text("${bytes / 1_000_000} / ${SupertonicPackStore.OFFICIAL_BYTES / 1_000_000} MB")
            TextButton(onClick={provider.packs.cancelDownload()}) { Text(uiText("Pause download")) }
        }
        TextButton(enabled=!busy && !testBusy,onClick={
            picker.launch(arrayOf("application/x-bzip2","application/zip","application/octet-stream","*/*"))
        }) { Text(uiText("Import Supertonic pack")) }
        TextButton(onClick={
            context.startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://k2-fsa.github.io/sherpa/onnx/tts/supertonic.html")))
        }) { Text(uiText("Supertonic model files and license")) }
        installed.forEach { pack ->
            Text(pack.fingerprint.take(12)+" · "+uiText(if(pack.selected) "Selected" else "Not selected"))
            Row {
                TextButton(enabled=!busy && !testBusy && !pack.selected,
                    onClick={action { provider.selectPack(pack.fingerprint); ticket=null }}) {
                    Text(uiText("Use this voice version"))
                }
                TextButton(enabled=!busy && !testBusy && !pack.selected,
                    onClick={action { provider.removePack(pack.fingerprint) }}) {
                    Text(uiText("Remove pack"))
                }
            }
        }
        if(state.fingerprint!=null) {
            val fingerprint = requireNotNull(state.fingerprint)
            var attestAll by remember(fingerprint) { mutableStateOf(false) }
            TextButton(enabled=!busy && !testBusy, onClick={ attestAll = true }) {
                Text(uiText("I have tested all 10 voices in EN, LV, RU and PL"))
            }
            if(attestAll) {
                AlertDialog(
                    onDismissRequest={ attestAll = false },
                    title={ Text(uiText("Confirm this installed Supertonic pack")) },
                    text={ Text(uiText("This authorizes 40 voice/language combinations on the selected pack only. Confirm only if you personally heard all of them; a different model download will require its own confirmation.")) },
                    confirmButton={ TextButton(onClick={
                        attestAll = false
                        action { provider.packs.confirmAllAudible(fingerprint) }
                    }) { Text(uiText("Confirm all 40 tested combinations")) } },
                    dismissButton={ TextButton(onClick={ attestAll = false }) { Text(uiText("Cancel")) } }
                )
            }
            val qualified=provider.packs.approved(fingerprint,state.language,state.voice)
            Text(uiText(if(qualified) "Audible voice confirmed" else "Audible voice not yet confirmed"))
            Text(uiText("Language"))
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf("en","lv","ru","pl").forEach { lang ->
                    FilterChip(selected=state.language==lang,onClick={
                        ticket=null
                        action { provider.chooseVoice(lang,state.voice) }
                    },enabled=!busy && !testBusy,label={Text(lang.uppercase())})
                }
            }
            Text(uiText("Voice")+": "+state.voice)
            var voiceMenu by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(enabled=!busy && !testBusy,onClick={voiceMenu=true}) {
                    Text(uiText("Choose a voice"))
                }
                DropdownMenu(expanded=voiceMenu,onDismissRequest={voiceMenu=false}) {
                    (0..9).forEach { voice ->
                        DropdownMenuItem(text={Text(uiText("Voice")+" "+voice)},onClick={
                            voiceMenu=false; ticket=null
                            action { provider.chooseVoice(state.language,voice) }
                        })
                    }
                }
            }
            val examples=mapOf(
                "en" to "Hello from Halo. Please confirm that you can hear this voice clearly.",
                "lv" to "Sveiki! Šī ir Halo balss pārbaude latviešu valodā.",
                "ru" to "Здравствуйте! Это проверка голоса Halo на русском языке.",
                "pl" to "Cześć! To jest próba głosu Halo w języku polskim."
            )
            Button(enabled=!busy && !testBusy,onClick={
                val fingerprint=requireNotNull(state.fingerprint)
                val language=state.language
                val voice=state.voice
                output.testOutput(testTarget,testLabel,examples.getValue(language),
                    language=language,previewProviderId=SupertonicSpeechProvider.ID,
                    previewModelId=fingerprint,previewVoiceId=voice.toString())
                val transaction=output.outputTest.value.transactionId
                ticket=transaction?.let { SupertonicPreviewTicket(it,fingerprint,language,voice) }
            }) { Text(uiText("Preview Supertonic on selected audio output")) }
            val match=ticket?.takeIf {
                it.transactionId==test.transactionId && it.fingerprint==state.fingerprint &&
                    it.language==state.language && it.voice==state.voice
            }
            if(match!=null && test.outcome=="Playback completed" && test.observed!=null) {
                Text(uiText("Only confirm after hearing the preview on the intended device."),
                    style=MaterialTheme.typography.bodySmall)
                Button(enabled=!busy && !qualified,onClick={
                    // A different test may have superseded this preview after
                    // it rendered. Never authorize that other test's voice.
                    if(output.confirmOutputTest(match.transactionId)) {
                        ticket=null
                        action { provider.confirmAudible(match.fingerprint,match.language,match.voice) }
                    }
                }) { Text(uiText("I heard this Supertonic voice")) }
            }
            TextButton(onClick={advanced=!advanced}) {
                Text(uiText(if(advanced) "Hide advanced details" else "Advanced details"))
            }
            if(advanced) {
                Row {
                    Text(uiText("Keep voice warm when memory permits"),modifier=Modifier.weight(1f))
                    Switch(checked=warm,enabled=!busy,onCheckedChange={ enabled ->
                        action { provider.setWarm(enabled); warm=enabled }
                    })
                }
                Text(uiText("CPU threads")+": "+threads)
                Slider(threads.toFloat(),onValueChange={threads=it.toInt().coerceIn(1,4)},
                    enabled=!busy,valueRange=1f..4f,steps=2,
                    onValueChangeFinished={action {
                        withContext(Dispatchers.IO) { storage.putInt("supertonic_threads",threads) }
                    }})
                Text(uiText("Diffusion steps")+": "+steps)
                Slider(steps.toFloat(),onValueChange={steps=it.toInt().coerceIn(4,16)},
                    enabled=!busy,valueRange=4f..16f,steps=11,
                    onValueChangeFinished={action {
                        withContext(Dispatchers.IO) { storage.putInt("supertonic_steps",steps) }
                    }})
                Text(uiText("Speech speed"))
                Slider(speed,onValueChange={speed=it},enabled=!busy,valueRange=.6f..1.5f,
                    onValueChangeFinished={action {
                        withContext(Dispatchers.IO) { storage.putString("supertonic_speed",speed.toString()) }
                    }})
                Text("Sherpa-ONNX 1.13.8 · INT8 · CPU · 10 voices")
                Text(uiText("This approval covers only the selected model, language and voice."),
                    style=MaterialTheme.typography.bodySmall)
            }
        }
        state.message?.let { Text(uiText(it),style=MaterialTheme.typography.bodySmall) }
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    }
}
