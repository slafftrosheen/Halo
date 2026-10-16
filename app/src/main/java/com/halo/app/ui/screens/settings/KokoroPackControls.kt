package com.halo.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.halo.app.audio.*
import com.halo.app.language.uiText
import com.halo.sdk.provider.InstalledSpeechPack
import kotlinx.coroutines.*

@Composable
internal fun KokoroPackControls(provider: KokoroSpeechProvider) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val storage=com.halo.app.di.ManualDependencyGraph.storage
    val state by provider.state.collectAsState()
    val downloadProgress by provider.downloadProgress.collectAsState()
    val scope=rememberCoroutineScope()
    var packs by remember { mutableStateOf(emptyList<InstalledSpeechPack>()) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var warm by remember { mutableStateOf(storage.getBoolean("kokoro_warm",false)) }
    var threads by remember { mutableIntStateOf(storage.getInt("kokoro_threads",2)) }
    var idle by remember { mutableIntStateOf(storage.getInt("kokoro_idle_seconds",30)) }
    LaunchedEffect(state) { packs=provider.installedPacks() }
    fun action(block: suspend () -> Unit) { scope.launch {
        busy=true; failed=false
        try { block(); packs=provider.installedPacks() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed=true }
        finally { busy=false }
    } }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(uiText("Installed voice versions"),style=MaterialTheme.typography.titleSmall)
        packs.forEach { pack ->
            Text((pack.modelId ?: uiText("Installed pack"))+" · "+pack.fingerprint.take(12))
            Text(uiText(if(pack.selected) "Selected" else "Not selected")+" · "+uiText("Physical qualification pending"))
            Row {
                TextButton(enabled=!busy && pack.complete && !pack.selected,onClick={action { provider.selectPack(pack.fingerprint) }}) {
                    Text(uiText("Use this voice version"))
                }
                TextButton(enabled=!busy && !pack.selected,onClick={action { provider.removePack(pack.fingerprint) }}) {
                    Text(uiText("Remove pack"))
                }
            }
        }
        // Language/version selection is a primary voice action, not a developer-only option.
        // Installing a version never changes which pack is currently selected.
        SpeechPackRegistry.candidates.filter { !it.experimental && it.providerId==KokoroSpeechProvider.ID }.forEach { pack ->
            val installed=packs.any { it.modelId==pack.id && it.complete }
            TextButton(enabled=!busy && !installed,onClick={action { provider.downloadPack(pack.id) }}) {
                Text(pack.family+" "+pack.version+" · "+pack.languages.sorted().joinToString("/")+
                    " · "+uiText(if(installed) "Installed" else "Download"))
            }
        }
        downloadProgress?.let { bytes ->
            Text("${bytes / 1_000_000} MB · "+uiText("Downloading"))
            TextButton(enabled=busy,onClick={provider.cancelDownload()}) { Text(uiText("Pause download")) }
        }
        TextButton(onClick={details=!details}) { Text(uiText(if(details) "Hide advanced details" else "Advanced details")) }
        if(details) {
            Text("Sherpa 1.13.8 · CPU")
            Text(uiText("Supertonic and INT8 candidates require separate qualification."))
            Row {
                Text(uiText("Keep voice warm when memory permits"),modifier=androidx.compose.ui.Modifier.weight(1f))
                Switch(warm,onCheckedChange={ value -> action {
                    provider.setWarmEnabled(value); warm=value
                } },enabled=!busy)
            }
            Text(uiText("CPU threads")+": $threads")
            Slider(threads.toFloat(),onValueChange={threads=it.toInt()},valueRange=1f..4f,steps=2,
                onValueChangeFinished={action { withContext(Dispatchers.IO) { storage.putInt("kokoro_threads",threads) } }})
            Text(uiText("Warm idle seconds")+": $idle")
            Slider(idle.toFloat(),onValueChange={idle=it.toInt()},valueRange=5f..120f,
                onValueChangeFinished={action { withContext(Dispatchers.IO) { storage.putInt("kokoro_idle_seconds",idle) } }})
            Text(uiText("Speech speed"))
            var speed by remember { mutableFloatStateOf(storage.getString("kokoro_speed")?.toFloatOrNull() ?: 1f) }
            Slider(speed,onValueChange={speed=it},valueRange=.6f..1.5f,
                onValueChangeFinished={action { withContext(Dispatchers.IO) { storage.putString("kokoro_speed",speed.toString()) } }})
            Row {
                (if(provider.multilingualInstalled()) listOf("en","zh") else listOf("en")).forEach { language ->
                    TextButton(onClick={action { withContext(Dispatchers.IO) { storage.putString("kokoro_language",language) } }}) { Text(language) }
                }
            }
            Text(uiText("Speech is delivered as completed sentences, not native sample streaming."))
        }
        if(failed) Text(uiText("Voice operation failed. Previous selection is preserved."),color=MaterialTheme.colorScheme.error)
    }
}
