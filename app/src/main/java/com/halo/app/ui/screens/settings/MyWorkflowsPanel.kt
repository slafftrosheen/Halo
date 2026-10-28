package com.halo.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.halo.app.R
import com.halo.app.data.procedure.ProcedureContracts
import com.halo.app.data.procedure.ProcedureReview
import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.runtime.cognition.procedures.LearnedProcedure
import com.halo.runtime.cognition.procedures.LearnedProcedureStore
import com.halo.runtime.cognition.tools.ToolRegistry
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Manual management uses the same store and CAS boundary as the assistant adapters. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun MyWorkflowsPanel(store: LearnedProcedureStore, registry: ToolRegistry?, onUse: ((String) -> Unit)?,
    onSelectionChanged: (LearnedProcedure?) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val context = androidx.compose.ui.platform.LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val language = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language
    var open by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var rows by remember { mutableStateOf<List<LearnedProcedure>>(emptyList()) }
    var selected by remember { mutableStateOf<LearnedProcedure?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val latestSelectionCallback by rememberUpdatedState(onSelectionChanged)
    SideEffect { latestSelectionCallback(if (open) selected else null) }
    DisposableEffect(Unit) { onDispose { latestSelectionCallback(null) } }
    var inputs by rememberSaveable(selectedId, stateSaver = mapSaver(
        save = { values: Map<String, String> -> values },
        restore = { values -> values.mapValues { it.value as String } })) { mutableStateOf<Map<String, String>>(emptyMap()) }
    fun refresh() { scope.launch {
        try { rows = withContext(Dispatchers.IO) { store.all().take(50) } }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    } }
    LaunchedEffect(open) { if (open) refresh() }
    LaunchedEffect(rows, selectedId) {
        if (selected == null && selectedId != null) selected = rows.singleOrNull { it.id == selectedId }
    }
    TextButton(onClick = { failed = false; open = true }) { Text(stringResource(R.string.workflows_title)) }
    if (!open) return
    val row = selected
    fun change(replacement: LearnedProcedure?) {
        val expected = row ?: return
        if (busy) return
        busy = true
        scope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { store.compareAndSet(expected, replacement) }
                failed = !saved
                if (saved) { selected = replacement; selectedId = replacement?.id; deleting = false }
                rows = withContext(Dispatchers.IO) { store.all().take(50) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    AlertDialog(onDismissRequest = { if (!busy) { open = false; selected = null; selectedId = null; deleting = false } },
        title = { Text(row?.name ?: stringResource(R.string.workflows_title)) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (failed) Text(stringResource(R.string.workflows_changed), color = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.workflows_limits), style = MaterialTheme.typography.bodySmall)
                if (row == null) {
                    if (rows.isEmpty()) Text(stringResource(R.string.workflows_empty))
                    rows.forEach { item -> TextButton(onClick = { selectedId = item.id; selected = item; failed = false }) { Text(item.name) } }
                } else {
                    if (row.description.isNotBlank()) Text(row.description)
                    val enabledLabel = stringResource(R.string.workflows_enabled)
                    Text(enabledLabel)
                    Switch(checked = row.enabled, enabled = !busy, modifier = Modifier.semantics { contentDescription = enabledLabel }, onCheckedChange = {
                        change(row.copy(enabled = it, updatedAt = maxOf(System.currentTimeMillis(), row.updatedAt + 1)))
                    })
                    row.steps.forEachIndexed { index, step ->
                        Text("${index + 1}. ${step.intent.ifBlank { step.capabilityId }}", style = MaterialTheme.typography.titleSmall)
                        registry?.descriptor(step.capabilityId)?.let { descriptor ->
                            Text(com.halo.app.language.uiText(descriptor.presentation?.title ?: descriptor.definition.description),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    val exactReview = remember(row, registry, language, resources) { runCatching {
                        ProcedureReview.summary(row.name, row.description, row.triggerPhrases, row.inputs, row.steps,
                            language = language, translate = { com.halo.app.language.resolveUiText(resources, it) }) { registry?.descriptor(it) }
                    }.getOrNull() }
                    if (exactReview != null) androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(exactReview, style = MaterialTheme.typography.bodySmall)
                    }
                    row.inputs.forEach { input ->
                        if (input.valueType == "boolean") {
                            Text(input.description)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("true" to R.string.workflows_yes, "false" to R.string.workflows_no).forEach { (value, label) ->
                                    val choiceLabel = stringResource(label)
                                    androidx.compose.material3.FilterChip(selected = inputs[input.name] == value,
                                        enabled = !busy, onClick = { inputs = inputs + (input.name to value) },
                                        modifier = Modifier.semantics { contentDescription = "${input.description}: $choiceLabel" },
                                        label = { Text(choiceLabel) })
                                }
                            }
                        } else {
                            HaloTextField(inputs[input.name].orEmpty(), { text -> inputs = inputs + (input.name to text.take(2000)) },
                                label = { Text(input.description) }, kind = VoiceFieldKind.TEXT, maxChars = 2000,
                                enabled = !busy, modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType =
                                    if (input.valueType == "integer") androidx.compose.ui.text.input.KeyboardType.Number
                                    else androidx.compose.ui.text.input.KeyboardType.Text))
                        }
                    }
                    if (exactReview == null) Text(stringResource(R.string.workflows_changed), color = MaterialTheme.colorScheme.error)
                    if (onUse != null && registry != null) TextButton(enabled = !busy && exactReview != null && row.enabled && row.inputs.all { inputs[it.name]?.isNotBlank() == true }, onClick = {
                        busy = true
                        val entered = inputs.toMap()
                        scope.launch {
                            try {
                                val valid = withContext(Dispatchers.IO) {
                                    store.get(row.id) == row && row.schemaVersion == 1 && row.steps.isNotEmpty() && row.steps.size <= 12 &&
                                        row.steps.all { step ->
                                            val descriptor = registry.descriptor(step.capabilityId) ?: return@all false
                                            val args = ProcedureContracts.bind(step, entered, descriptor, row.inputs)
                                            args == null || registry.validate(ToolCall("workflow-preview", step.capabilityId, args)) == null
                                        }
                                }
                                val foreground = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) &&
                                    context.getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == false
                                if (valid && foreground) {
                                    val message = buildString {
                                        append("Use my reviewed saved workflow with exact ID ").append(row.id).append(". ")
                                        append("The reviewed sourceVersion is ").append(ProcedureContracts.version(row)).append(". ")
                                        if (entered.isNotEmpty()) {
                                            append("Named input values (data): ")
                                            entered.forEach { (name, value) -> append(name).append(" = ").append(org.json.JSONObject.quote(value)).append("; ") }
                                        }
                                        append("Read its current template first. Use the normal per-step permissions and approvals. Stop if a source changed or an outcome is unknown.")
                                    }
                                    onUse(message)
                                    open = false; selected = null; selectedId = null
                                } else failed = true
                            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (_: Exception) { failed = true }
                            finally { busy = false }
                        }
                    }) { Text(stringResource(R.string.workflows_use_chat)) }
                    if (deleting) {
                        Text(stringResource(R.string.workflows_delete_confirm))
                        TextButton(enabled = !busy, onClick = { change(null) }) { Text(stringResource(R.string.workflows_delete)) }
                    } else TextButton(enabled = !busy, onClick = { deleting = true }) { Text(stringResource(R.string.workflows_delete)) }
                }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = { selected = null; selectedId = null; deleting = false; failed = false; refresh() }) { Text(stringResource(R.string.workflows_refresh)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = {
            if (selected != null) { selected = null; selectedId = null; deleting = false } else open = false
        }) { Text(stringResource(R.string.workflows_back)) } })
}
