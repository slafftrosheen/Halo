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
    var savedToScreen by remember { mutableStateOf(false) }
    var savingToScreen by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf(false) }
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
                HaloTextField(query, { service.discard(prepared); prepared = null; query = it.take(2000); result = null; savedToScreen = false; saveError = false },
                    enabled = !busy, label = { Text(stringResource(R.string.online_query)) }, kind = VoiceFieldKind.SEARCH,
                    modifier = Modifier.fillMaxWidth(), minLines = 2)
                Text(stringResource(R.string.online_query_only), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.online_search_price), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { costs = true }) { Text(stringResource(R.string.online_costs)) }
                if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.online_loading)) }
                error?.let { Text(onlineProblemText(it), color = MaterialTheme.colorScheme.error)
                    if (it == OnlineProblem.LOCAL_ONLY) com.halo.app.ui.components.CloudModeResolution() }
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
                    TextButton(enabled = !savedToScreen && !savingToScreen && answer.sources.any { it.url.startsWith("https://") }, onClick = {
                        savingToScreen = true
                        scope.launch {
                            try {
                                val store = com.halo.app.di.ManualDependencyGraph.screenStore
                                val idDigest = java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(query.trim().lowercase(java.util.Locale.ROOT).toByteArray(Charsets.UTF_8))
                                    .take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }
                                val itemId = "research:$idDigest"
                                val item = com.halo.app.screen.ScreenItem(
                                    id = itemId,
                                    sourceType = com.halo.app.screen.ScreenSourceType.RESEARCH,
                                    sourceId = null,
                                    title = query.trim().take(200),
                                    summary = answer.answer.take(6000),
                                    contentType = com.halo.app.screen.ScreenContentType.ARTICLES,
                                    updatedAt = java.time.Instant.ofEpochMilli(answer.retrievedAtMs),
                                    lastSuccessAt = java.time.Instant.ofEpochMilli(answer.retrievedAtMs),
                                    expiresAt = null,
                                    state = com.halo.app.screen.ScreenItemState.READY,
                                    sensitivity = com.halo.app.screen.ScreenSensitivity.PRIVATE,
                                    attribution = "Grounded web search",
                                    availableActions = listOf(com.halo.app.screen.ScreenAction.OpenSource,
                                        com.halo.app.screen.ScreenAction.SaveToNotebook, com.halo.app.screen.ScreenAction.HideFromScreen),
                                    sourceLinks = answer.sources.asSequence().filter { it.url.startsWith("https://") }
                                        .take(8).map { com.halo.app.screen.ScreenSourceLink(it.title.take(200), it.url.take(2048)) }.toList(),
                                )
                                val current = store.layout.value ?: com.halo.app.screen.ScreenWidgets.starter("Morning")
                                val existing = current.cards.indexOfFirst { it.widgetType == "research" && it.itemId == itemId }
                                val unbound = current.cards.indexOfFirst { it.widgetType == "research" && it.itemId == null }
                                if (existing < 0 && unbound < 0 && current.cards.size >= 24) {
                                    saveError = true
                                    return@launch
                                }
                                val cards = when {
                                    existing >= 0 -> current.cards
                                    unbound >= 0 -> current.cards.mapIndexed { index, card ->
                                        if (index == unbound) card.copy(itemId = itemId) else card
                                    }
                                    else -> current.cards + com.halo.app.screen.ScreenCard("research", itemId,
                                        com.halo.app.screen.CardSize.STANDARD, false)
                                }
                                store.publishAndSaveLayout(item, current.copy(cards = cards))
                                savedToScreen = true
                                saveError = false
                            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (_: Exception) { saveError = true }
                            finally { savingToScreen = false }
                        }
                    }) { Text(com.halo.app.language.uiText(when {
                        savedToScreen -> "Saved to Screen"
                        savingToScreen -> "Saving to Screen"
                        else -> "Save to Screen"
                    })) }
                    if (answer.sources.none { it.url.startsWith("https://") }) Text(com.halo.app.language.uiText("No source links to save."),
                        style = MaterialTheme.typography.bodySmall)
                    if (saveError) Text(com.halo.app.language.uiText("Screen is full or could not save this research."),
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
