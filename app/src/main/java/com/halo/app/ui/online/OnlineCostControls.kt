package com.halo.app.ui.online

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.online.*
import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.sdk.provider.PaidMediaPolicy
import kotlinx.coroutines.*
import java.text.DateFormat
import java.util.Date

@Composable fun OnlineCostDialog(onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.online_costs)) },
        text = { Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) { OnlineCostControls() } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.online_close)) } })
}

@Composable fun OnlineCostControls() {
    val graph = com.halo.app.di.ManualDependencyGraph
    val storage = graph.storage
    val ledger = remember(storage) { OnlineOperationLedger(storage) }
    val service = rememberOnlineSearchService()
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val kids by graph.kidsPreferences.enabled.collectAsStateWithLifecycle()
    val kidsLoaded by graph.kidsPreferences.loadedState.collectAsStateWithLifecycle()
    var localLoaded by remember { mutableStateOf(false) }
    val canChange = kidsLoaded && !kids && localLoaded
    var snapshot by remember { mutableStateOf<OnlineSpendSnapshot?>(null) }
    var monthLimit by remember { mutableStateOf("") }
    var requestLimit by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(false) }
    var confirmMedia by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<OnlineProblem?>(null) }
    var busy by remember { mutableStateOf(false) }
    var usage by remember { mutableStateOf<OpenRouterKeyUsage?>(null) }
    var usageJob by remember { mutableStateOf<Job?>(null) }
    var readVersion by remember { mutableLongStateOf(0L) }
    fun readLocal(resetFields: Boolean = false) {
        val version = ++readVersion
        localLoaded = false
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { ledger.snapshot() to storage.getBoolean(PaidMediaPolicy.CONSENT_KEY, false) }
                if (version != readVersion) return@launch
                snapshot = result.first; enabled = result.second; localLoaded = true
                if (resetFields) { monthLimit = result.first.monthlyLimitUsd?.toPlainString().orEmpty(); requestLimit = result.first.requestLimitUsd?.toPlainString().orEmpty() }
                error = null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (version == readVersion) { error = OnlineProblem.LEDGER_UNAVAILABLE; localLoaded = false } }
        }
    }
    LaunchedEffect(ledger) { readLocal(resetFields = true) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) { usageJob?.cancel(); confirmMedia = false }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer); usageJob?.cancel() }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.online_cost_scope), style = MaterialTheme.typography.bodySmall)
        snapshot?.let {
            Text(stringResource(R.string.online_month, it.month), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.online_reported, it.reportedUsd.toPlainString()))
            Text(stringResource(R.string.online_reserved, it.estimatedUsd.toPlainString(), it.unconfirmed))
        }
        Text(stringResource(R.string.online_cost_warning), style = MaterialTheme.typography.bodySmall)
        HaloTextField(monthLimit, { monthLimit = it.take(18) }, enabled = canChange && !busy,
            label = { Text(stringResource(R.string.online_monthly_limit)) }, kind = VoiceFieldKind.NUMBER, singleLine = true, modifier = Modifier.fillMaxWidth())
        HaloTextField(requestLimit, { requestLimit = it.take(18) }, enabled = canChange && !busy,
            label = { Text(stringResource(R.string.online_request_limit)) }, kind = VoiceFieldKind.NUMBER, singleLine = true, modifier = Modifier.fillMaxWidth())
        TextButton(enabled = canChange && !busy && snapshot != null, onClick = {
            val monthly = OnlineOperationLedger.parseMoney(monthLimit)
            val perRequest = OnlineOperationLedger.parseMoney(requestLimit)
            if ((monthLimit.isNotBlank() && monthly == null) || (requestLimit.isNotBlank() && perRequest == null)) error = OnlineProblem.INVALID_AMOUNT
            else scope.launch {
                busy = true
                try {
                    withContext(Dispatchers.IO) {
                        if (!graph.kidsPreferences.loadedState.value || graph.kidsPreferences.enabled.value)
                            throw OnlineOperationRejected(OnlineProblem.CHILD_RESTRICTED)
                        ledger.configure(monthly, perRequest)
                    }
                    readLocal(); error = null
                }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: OnlineOperationRejected) { error = failure.problem }
                catch (_: Exception) { error = OnlineProblem.LEDGER_UNAVAILABLE }
                finally { busy = false }
            }
        }) { Text(stringResource(R.string.online_save)) }
        TextButton(onClick = { readLocal() }, enabled = !busy) { Text(stringResource(R.string.online_refresh)) }
        HorizontalDivider()
        Text(com.halo.app.language.uiText("Media sharing follows the Private / Power switch at the top of Halo."), style = MaterialTheme.typography.bodySmall)
        if (!kidsLoaded || kids) Text(stringResource(R.string.online_child_restricted), style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        TextButton(onClick = { runCatching { uri.openUri("https://openrouter.ai/settings/keys") } }) {
            Text(stringResource(R.string.online_provider_limits))
        }
        Text(stringResource(R.string.online_key_scope), style = MaterialTheme.typography.bodySmall)
        TextButton(enabled = !busy && canChange, onClick = {
            usageJob = scope.launch {
                busy = true; usage = null; error = null
                try { usage = service.usage() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: OnlineOperationRejected) { error = failure.problem }
                catch (_: Exception) { error = OnlineProblem.NETWORK }
                finally { busy = false }
            }
        }) { Text(stringResource(R.string.online_key_usage)) }
        usage?.let {
            Text(stringResource(R.string.online_key_checked, DateFormat.getDateTimeInstance().format(Date(it.checkedAtMs))))
            Text(stringResource(R.string.online_key_month, it.monthlyUsd?.toPlainString() ?: stringResource(R.string.online_unknown)))
            Text(stringResource(R.string.online_key_limit, it.limitUsd?.let { amount -> "USD ${amount.toPlainString()}" } ?: stringResource(R.string.online_no_cap),
                it.remainingUsd?.let { amount -> "USD ${amount.toPlainString()}" } ?: stringResource(R.string.online_unknown)))
            Text(stringResource(R.string.online_key_byok, it.byokMonthlyUsd?.toPlainString() ?: stringResource(R.string.online_unknown),
                when (it.includesByok) { true -> stringResource(R.string.online_yes); false -> stringResource(R.string.online_no); null -> stringResource(R.string.online_unknown) }))
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(onlineProblemText(it), color = MaterialTheme.colorScheme.error)
            if (it == OnlineProblem.LOCAL_ONLY) com.halo.app.ui.components.CloudModeResolution() }
    }
}
