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
        TextButton(onClick={details=!details}) { Text(uiText(if(details) "Hide advanced details" else "Advanced details")) }
        if(details) {
            Text("Sherpa 1.13.8 · CPU")
            Text(uiText("Supertonic and INT8 candidates require separate qualification."))
            SpeechPackRegistry.candidates.filter { !it.experimental }.forEach { pack ->
                TextButton(enabled=!busy,onClick={action { provider.downloadPack(pack.id) }}) { Text(pack.family+" "+pack.version+" · "+uiText("Download")) }
            }
            Row {
                Text(uiText("Keep voice warm when memory permits"),modifier=androidx.compose.ui.Modifier.weight(1f))
                Switch(warm,onCheckedChange={ value -> action {
                    withContext(Dispatchers.IO) { storage.putBoolean("kokoro_warm",value) }; warm=value
                    if(!value) provider.releaseIfIdle()
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
                listOf("en","zh").forEach { language ->
                    TextButton(onClick={action { withContext(Dispatchers.IO) { storage.putString("kokoro_language",language) } }}) { Text(language) }
                }
            }
            Text(uiText("Speech is delivered as completed sentences, not native sample streaming."))
        }
        if(failed) Text(uiText("Voice operation failed. Previous selection is preserved."),color=MaterialTheme.colorScheme.error)
    }
}
