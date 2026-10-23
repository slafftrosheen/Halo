package com.halo.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.ai.*
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.language.uiText
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.workspace.HaloWorkspaceToggleRow
import com.halo.sdk.provider.*
import kotlinx.coroutines.*
import java.text.DateFormat
import java.util.Date

@Composable internal fun ModeProfilesScreen(onBack: () -> Unit, onAdvanced: () -> Unit,
    onPermissions: () -> Unit, onCosts: () -> Unit, activityOnly: Boolean = false,
    clearLegacyHistory: suspend () -> Unit = {}) {
    val revision by Graph.privacyModes.profileChanges.collectAsStateWithLifecycle()
    val receipts by Graph.privacyModes.activity.receipts.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val ready by Graph.kidsPreferences.loadedState.collectAsStateWithLifecycle()
    val kids by Graph.kidsPreferences.enabled.collectAsStateWithLifecycle()
    var clearFailed by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { HaloScreenHeader(stringResource(if (activityOnly) R.string.mode47_activity else R.string.mode47_profiles),
            if (activityOnly) stringResource(R.string.mode47_activity_scope) else stringResource(R.string.mode47_boundaries), navigation = onBack) }
        if (!activityOnly) {
        item { com.halo.app.ui.components.PrivacyModeControl(detail = true) }
        if (Graph.privacyModes.error != null) item {
            Text(stringResource(R.string.mode47_activation_failed), color = MaterialTheme.colorScheme.error)
        }
        item { ModeProfilePanel(PrivacyMode.PRIVATE, revision, ready && !kids) }
        item { ModeProfilePanel(PrivacyMode.CLOUD, revision, ready && !kids) }
        item { TextButton(onClick = onCosts) { Text(stringResource(R.string.online_costs)) }
            TextButton(onClick = onPermissions) { Text(stringResource(R.string.brand_privacy_permissions)) }
            TextButton(onClick = onAdvanced) { Text(uiText("Advanced intelligence")) } }
        }
        item { Text(stringResource(R.string.mode47_activity), style = MaterialTheme.typography.titleMedium)
            if (receipts.isEmpty()) Text(stringResource(R.string.mode47_no_activity))
            TextButton(enabled = ready && !kids, onClick = { scope.launch {
                try { withContext(Dispatchers.IO) { Graph.privacyModes.activity.clear(); clearLegacyHistory() }; clearFailed = false }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { clearFailed = true }
            } }) { Text(stringResource(R.string.mode47_clear_activity)) }
            if (clearFailed) Text(uiText("Could not save. Try again."), color = MaterialTheme.colorScheme.error)
        }
        // Keep the page readable; the owner retains at most 200 local rows.
        items(minOf(receipts.size, 30)) { index ->
            val receipt = receipts[index]
            val providerName = Graph.aiProviderRouter.getAllProviders().firstOrNull { it.id == receipt.providerId }?.displayName
                ?: when (receipt.providerId) {
                    "provider.elevenlabs.tts" -> "ElevenLabs"
                    "google.mail" -> "Gmail"
                    "web.public" -> stringResource(R.string.mode47_web)
                    "mcp.configured" -> stringResource(R.string.mode47_connected)
                    else -> stringResource(R.string.mode47_no_provider)
                }
            val categories = receipt.categories.map { stringResource(categoryResource(it)) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("$providerName · ${stringResource(operationResource(receipt.operation))} · ${stringResource(outcomeResource(receipt.outcome))}", style = MaterialTheme.typography.labelLarge)
                Text(DateFormat.getDateTimeInstance().format(Date(receipt.atMs)), style = MaterialTheme.typography.bodySmall)
                Text(categories.joinToString(), style = MaterialTheme.typography.bodySmall)
                Text(receipt.providerReportedCostUsd?.let { "USD $it" } ?: stringResource(R.string.mode47_cost_unknown), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable private fun ModeProfilePanel(mode: PrivacyMode, revision: Long, editable: Boolean) {
    val committed = remember(mode, revision) { Graph.privacyModes.profile(mode) }
    var draft by remember(mode, revision) { mutableStateOf(committed) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var expanded by remember(mode) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val catalogue by Graph.aiModelCatalogRepository.state.collectAsStateWithLifecycle()
    val providers = Graph.aiProviderRouter.getAllProviders().filter {
        mode == PrivacyMode.CLOUD || it.id == "provider.litert.local"
    }
    fun string(key: String, value: String?) { draft = draft.copy(strings = draft.strings + (key to value)); saved = false }
    fun boolean(key: String, value: Boolean) { draft = draft.copy(booleans = draft.booleans + (key to value)); saved = false }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (mode == PrivacyMode.PRIVATE) R.string.mode47_private_profile else R.string.mode47_power_profile),
                style = MaterialTheme.typography.titleMedium)
            Text(modeProfileProviderName(committed.providerId), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { expanded = !expanded }) { Text(stringResource(R.string.mode47_edit_profile)) }
            if (expanded) {
            if (mode == PrivacyMode.PRIVATE) Text(stringResource(R.string.mode47_private_only))
            ProfileChoice(stringResource(R.string.mode47_provider), draft.providerId,
                providers.map { it.id to modeProfileProviderName(it.id) }, editable && !busy) {
                string("active_ai_provider", it); string("active_ai_model", null)
            }
            val models = catalogue.models.filter { it.model.providerId == draft.providerId }.map { it.model.id to it.model.displayName }
            ProfileChoice(stringResource(R.string.mode47_model), draft.modelId, models, editable && !busy) { string("active_ai_model", it) }
            Text(stringResource(R.string.mode47_cached_models), style = MaterialTheme.typography.bodySmall)
            ProfileChoice(stringResource(R.string.mode47_routing), draft.strings["ai_routing_policy"],
                if (mode == PrivacyMode.PRIVATE) listOf("LOCAL_ONLY" to stringResource(R.string.mode47_local_only), "EXPLICIT" to stringResource(R.string.mode47_choose_selected))
                else listOf("EXPLICIT" to stringResource(R.string.mode47_choose_selected),
                    "AUTOMATIC" to stringResource(R.string.mode47_routing_automatic),
                    "PREFERRED_WITH_FALLBACK" to stringResource(R.string.mode47_local_fallback)), editable && !busy) { policy ->
                    string("ai_routing_policy", policy)
                    if (mode == PrivacyMode.CLOUD) draft = draft.copy(localFallback = policy == "PREFERRED_WITH_FALLBACK")
                }
            val speech = Graph.providerGraph.providers
            ProfileChoice(stringResource(R.string.mode47_stt), draft.strings["speech_stt_provider"] ?: "AUTOMATIC",
                listOf("AUTOMATIC" to stringResource(R.string.mode47_automatic)) +
                    com.halo.providers.sherpa.stt.SherpaSpeechModelCatalog.recognitionModels.map { it.providerId to it.displayName },
                editable && !busy) { string("speech_stt_provider", it) }
            ProfileChoice(stringResource(R.string.mode47_tts), draft.strings["speech_tts_provider"] ?: "AUTOMATIC",
                listOf("AUTOMATIC" to stringResource(R.string.mode47_automatic)) + speech.ttsProviders.filter {
                    mode == PrivacyMode.CLOUD || it.metadata.local
                }.map { it.metadata.id to it.metadata.displayName }, editable && !busy) { string("speech_tts_provider", it) }
            Text(stringResource(R.string.mode47_context), style = MaterialTheme.typography.titleSmall)
            listOf(KEY_CONVERSATION to RemoteDataCategory.CONVERSATION, KEY_DEVICE_STATE to RemoteDataCategory.DEVICE_STATE,
                KEY_RECENT_EVENTS to RemoteDataCategory.RECENT_EVENTS, KEY_MEMORIES to RemoteDataCategory.MEMORIES,
                KEY_TOOLS to RemoteDataCategory.TOOLS, KEY_LOCATION to RemoteDataCategory.LOCATION, KEY_IMAGES to RemoteDataCategory.IMAGES,
                KEY_AUDIO to RemoteDataCategory.AUDIO, KEY_VIDEO to RemoteDataCategory.VIDEO, KEY_WORKSPACE to RemoteDataCategory.WORKSPACE).forEach { (key, category) ->
                HaloWorkspaceToggleRow(stringResource(categoryResource(category)), draft.booleans[key] == true,
                    { boolean(key, it) }, enabled = editable && !busy)
            }
            if (mode == PrivacyMode.CLOUD) {
                Text(stringResource(R.string.mode47_online_categories), style = MaterialTheme.typography.titleSmall)
                listOf(KEY_CLOUD_WORKSPACE to RemoteDataCategory.WORKSPACE, KEY_CLOUD_SCREEN to RemoteDataCategory.SCREEN,
                    KEY_CLOUD_IMAGES to RemoteDataCategory.IMAGES, "policy_cloud_memories" to RemoteDataCategory.MEMORIES).forEach { (key, category) ->
                    HaloWorkspaceToggleRow(stringResource(categoryResource(category)), draft.booleans[key] == true,
                        { boolean(key, it) }, enabled = editable && !busy)
                }
                Text(stringResource(R.string.mode47_features), style = MaterialTheme.typography.titleSmall)
                OnlineFeature.entries.forEach { feature ->
                    val label = onlineFeatureResource(feature)
                    HaloWorkspaceToggleRow(stringResource(label), feature in draft.onlineFeatures, { enabled ->
                        draft = draft.copy(onlineFeatures = if (enabled) draft.onlineFeatures + feature else draft.onlineFeatures - feature)
                        saved = false
                    }, enabled = editable && !busy)
                }
            }
            TextButton(enabled = editable && !busy && draft != committed, onClick = { scope.launch {
                busy = true
                try { withContext(Dispatchers.IO) { Graph.saveModeProfile(mode, draft) }; failed = false; saved = true }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed = true }
                finally { busy = false }
            } }) { Text(stringResource(R.string.mode47_save)) }
            if (saved) Text(stringResource(R.string.mode47_saved))
            if (failed) Text(stringResource(R.string.mode47_invalid), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

internal fun categoryResource(category: RemoteDataCategory): Int = when (category) {
    RemoteDataCategory.TEXT -> R.string.mode47_category_text
    RemoteDataCategory.CONVERSATION -> R.string.mode47_category_conversation
    RemoteDataCategory.DEVICE_STATE -> R.string.mode47_category_device_state
    RemoteDataCategory.RECENT_EVENTS -> R.string.mode47_category_recent_events
    RemoteDataCategory.TOOLS -> R.string.mode47_category_tools
    RemoteDataCategory.LOCATION -> R.string.mode47_category_location
    RemoteDataCategory.IMAGES -> R.string.mode47_category_images
    RemoteDataCategory.AUDIO -> R.string.mode47_category_audio
    RemoteDataCategory.VIDEO -> R.string.mode47_category_video
    RemoteDataCategory.WORKSPACE -> R.string.mode47_category_workspace
    RemoteDataCategory.SCREEN -> R.string.mode47_category_screen
    RemoteDataCategory.MEMORIES -> R.string.mode47_category_memories
    RemoteDataCategory.ACCOUNT_METADATA -> R.string.mode47_category_account_metadata
}
internal fun onlineFeatureResource(feature: OnlineFeature): Int = when (feature) {
    OnlineFeature.INFERENCE -> R.string.mode47_inference
    OnlineFeature.SPEECH -> R.string.mode47_speech
    OnlineFeature.WEB -> R.string.mode47_web
    OnlineFeature.IMAGES -> R.string.mode47_images
    OnlineFeature.CONNECTED_SERVICES -> R.string.mode47_connected
}
private fun operationResource(operation: RemoteOperation): Int = when (operation) {
    RemoteOperation.INFERENCE -> R.string.mode47_inference
    RemoteOperation.PROVIDER_CHECK -> R.string.mode47_provider_check
    RemoteOperation.HOSTED_SPEECH -> R.string.mode47_speech
    RemoteOperation.WEB -> R.string.mode47_web
    RemoteOperation.IMAGE -> R.string.mode47_images
    RemoteOperation.CONNECTED_SERVICE -> R.string.mode47_connected
}
private fun outcomeResource(outcome: RemoteDispatchOutcome): Int = when (outcome) {
    RemoteDispatchOutcome.STARTED -> R.string.mode47_outcome_started
    RemoteDispatchOutcome.REPLIED -> R.string.mode47_outcome_replied
    RemoteDispatchOutcome.COMPLETED -> R.string.mode47_outcome_completed
    RemoteDispatchOutcome.FAILED -> R.string.mode47_outcome_failed
    RemoteDispatchOutcome.CANCELLED -> R.string.mode47_outcome_cancelled
    RemoteDispatchOutcome.BLOCKED -> R.string.mode47_outcome_blocked
}

@Composable private fun ProfileChoice(label: String, selected: String?, choices: List<Pair<String, String>>, enabled: Boolean,
    onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            TextButton(enabled = enabled && choices.isNotEmpty(), onClick = { expanded = true }) {
                Text(choices.firstOrNull { it.first == selected }?.second ?: stringResource(if (selected == null) R.string.mode47_no_provider else R.string.mode47_saved_selection))
            }
            DropdownMenu(expanded, { expanded = false }) { choices.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { expanded = false; onSelect(id) })
            } }
        }
    }
}

/** Display-only metadata; must not initialize providers or perform discovery in the shell. */
@Composable internal fun modeProfileProviderName(id: String?): String = when (id) {
    "provider.litert.local", "provider.llamacpp.native" -> stringResource(R.string.mode47_on_device_provider)
    "openai" -> "OpenAI"
    "anthropic" -> "Anthropic"
    "gemini" -> "Google Gemini"
    "openrouter" -> "OpenRouter"
    "provider.ollama" -> "Ollama"
    else -> listOf(RemoteProviderPresets.groq, RemoteProviderPresets.cerebras,
        RemoteProviderPresets.xai, RemoteProviderPresets.opencodeZen,
        RemoteProviderPresets.opencodeCompanion, RemoteProviderPresets.customOpenAiCompatible)
        .firstOrNull { it.id == id }?.displayName ?: stringResource(R.string.mode47_no_provider)
}
