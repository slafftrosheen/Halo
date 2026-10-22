package com.halo.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.halo.app.ai.WorkloadPreferences
import com.halo.app.language.uiText
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.ResidencyPreference
import kotlinx.coroutines.*

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun WorkloadPolicyControls() {
    val preferences=remember { WorkloadPreferences(com.halo.app.di.ManualDependencyGraph.storage) }
    val scope=rememberCoroutineScope()
    var workload by remember { mutableStateOf(LocalWorkload.CHAT) }
    var profile by remember(workload) { mutableStateOf(preferences.profile(workload)) }
    var details by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    fun save(next: WorkloadExecutionProfile) { val selected=workload; scope.launch {
        try { withContext(Dispatchers.IO) { preferences.save(selected,next) }; if(workload==selected) profile=next; failed=false }
        catch(cancelled: CancellationException) { throw cancelled }
        catch(_: Exception) { failed=true }
    } }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        TextButton(onClick={details=!details}) { Text(uiText("Workload policies")) }
        if(details) {
            Text(uiText("Assignments remain in Model roles, Voice input and Speech output."))
            androidx.compose.foundation.layout.FlowRow { LocalWorkload.entries.forEach { kind ->
                TextButton(onClick={workload=kind}) { Text(kind.name) }
            } }
            Text(workload.name)
            Row {
                TextButton(onClick={save(profile.copy(maxOutputTokens=128,contextTokens=1024))}) { Text(uiText("Fast")) }
                TextButton(onClick={save(profile.copy(maxOutputTokens=256,contextTokens=2048))}) { Text(uiText("Balanced")) }
                TextButton(onClick={save(profile.copy(maxOutputTokens=512,contextTokens=4096))}) { Text(uiText("Quality")) }
            }
            Text(uiText("Requested backend")+": "+profile.backend.name)
            Row { (if(workload.legacyRole!=null) listOf(WorkloadBackend.AUTO,WorkloadBackend.CPU,WorkloadBackend.GPU)
                else listOf(WorkloadBackend.AUTO,WorkloadBackend.CPU)).forEach { backend ->
                TextButton(onClick={save(profile.copy(backend=backend))}) { Text(backend.name) }
            } }
            Text(uiText("Backend availability and task qualification are checked separately."))
            Row { listOf(ResidencyPreference.ON_DEMAND,ResidencyPreference.WARM_EJECTABLE).forEach { policy ->
                TextButton(onClick={save(profile.copy(residency=policy))}) { Text(uiText(if(policy==ResidencyPreference.ON_DEMAND) "On demand" else "Warm when safe")) }
            } }
            Text(uiText("Specialized voice providers retain their own settings; these execution overrides apply to conversational models."))
            if(workload.legacyRole!=null) {
                Row { Switch(profile.inheritRoutingPolicy,{save(profile.copy(inheritRoutingPolicy=it))}); Text(uiText("Inherit existing routing policy")) }
                if(!profile.inheritRoutingPolicy) {
                    Text(uiText("Media sharing follows the Private / Power switch at the top of Halo."))
                    Row { Switch(profile.allowFallback,{save(profile.copy(allowFallback=it))}); Text(uiText("Allow configured fallback")) }
                }
            }
            Text(uiText("One switch at the top of every screen controls sharing. No separate image, memory, or workspace permissions are needed."))
            Text(uiText("These policies do not overwrite native model tuning."))
        }
        if(failed) Text(uiText("Unable to save workload policy"),color=MaterialTheme.colorScheme.error)
    }
}
