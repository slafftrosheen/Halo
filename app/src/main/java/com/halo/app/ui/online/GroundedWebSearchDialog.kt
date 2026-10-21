package com.halo.app.ui.online

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.online.*
import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import kotlinx.coroutines.*
import java.text.DateFormat
import java.util.Date

@Composable fun GroundedWebSearchDialog(initialQuery: String, onDismiss: () -> Unit) {
    val service = rememberOnlineSearchService()
    val language by com.halo.app.di.ManualDependencyGraph.appLanguagePreferences.language.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    var query by remember { mutableStateOf(initialQuery.take(2000)) }
    var result by remember { mutableStateOf<GroundedWebAnswer?>(null) }
    var prepared by remember { mutableStateOf<OnlineConsentChallenge?>(null) }
    var preparedQuery by remember { mutableStateOf("") }
    var preparedLanguage by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<OnlineProblem?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var generation by remember { mutableLongStateOf(0L) }
    var costs by remember { mutableStateOf(false) }
    fun cancel() {
        generation++; job?.cancel(); service.discard(prepared); prepared = null; busy = false
    }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner, service) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) cancel() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); cancel() }
    }
    AlertDialog(onDismissRequest = { cancel(); onDismiss() }, title = { Text(stringResource(R.string.online_title)) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HaloTextField(query, { service.discard(prepared); prepared = null; query = it.take(2000); result = null },
                    enabled = !busy, label = { Text(stringResource(R.string.online_query)) }, kind = VoiceFieldKind.SEARCH,
                    modifier = Modifier.fillMaxWidth(), minLines = 2)
                Text(stringResource(R.string.online_query_only), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.online_search_price), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { costs = true }) { Text(stringResource(R.string.online_costs)) }
                if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.online_loading)) }
                error?.let { Text(onlineProblemText(it), color = MaterialTheme.colorScheme.error) }
                result?.let { answer ->
                    Text(answer.answer) // Plain text: model-authored URLs never become trusted clickable sources.
                    Text(stringResource(R.string.online_retrieved, DateFormat.getDateTimeInstance().format(Date(answer.retrievedAtMs))), style = MaterialTheme.typography.labelSmall)
                    Text(stringResource(R.string.online_sources), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.online_source_warning), style = MaterialTheme.typography.bodySmall)
                    answer.sources.forEach { source ->
                        OutlinedButton(onClick = { runCatching { uri.openUri(source.url) } }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) { Text(source.title); Text(source.url, style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                    answer.reportedCostUsd?.let { Text(stringResource(R.string.online_reported, it.toPlainString())) }
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(answer.answer + "\n\n" +
                            "Retrieved (UTC): ${java.time.Instant.ofEpochMilli(answer.retrievedAtMs)}\n" + answer.sources.joinToString("\n") { "${it.title}: ${it.url}" }))
                    }) { Text(stringResource(R.string.online_copy)) }
                }
            }
        },
        confirmButton = { TextButton(enabled = !busy && query.isNotBlank(), onClick = {
            val version = ++generation
            val text = query.trim(); val lang = language.tag
            busy = true; error = null
            job = scope.launch {
                try {
                    val challenge = service.prepare(text, lang)
                    if (version == generation) { preparedQuery = text; preparedLanguage = lang; prepared = challenge }
                    else service.discard(challenge)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: OnlineOperationRejected) { if (version == generation) error = failure.problem }
                catch (_: Exception) { if (version == generation) error = OnlineProblem.UNAVAILABLE }
                finally { if (version == generation) busy = false }
            }
        }) { Text(stringResource(R.string.online_search)) } },
        dismissButton = { TextButton(onClick = { cancel(); onDismiss() }) { Text(stringResource(if (busy) R.string.online_cancel else R.string.online_close)) } })
    prepared?.let { challenge ->
        OnlineSpendApprovalDialog(challenge.id, "OpenRouter", OpenRouterOnlineClient.FREE_MODEL,
            preparedQuery + "\n\n" + stringResource(R.string.online_search_price), initialReservation = "0.01",
            pricingUrl = "https://openrouter.ai/docs/guides/features/plugins/web-search",
            onDismiss = { service.discard(challenge); prepared = null }, onApprove = { amount ->
                prepared = null
                val text = preparedQuery; val lang = preparedLanguage
                val version = ++generation
                busy = true; error = null; result = null
                job = scope.launch {
                    try {
                        val answer = service.search(text, lang, challenge, amount, acknowledgeVariableCost = true)
                        if (version == generation) result = answer
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: OnlineOperationRejected) { if (version == generation) error = failure.problem }
                    catch (_: Exception) { if (version == generation) error = OnlineProblem.NETWORK }
                    finally { if (version == generation) busy = false }
                }
            })
    }
    if (costs) OnlineCostDialog { costs = false }
}
