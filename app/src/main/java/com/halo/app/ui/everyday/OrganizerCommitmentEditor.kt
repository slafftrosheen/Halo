package com.halo.app.ui.everyday

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.briefing.BriefPeriod
import com.halo.app.everyday.collectors.CollectorState
import com.halo.app.everyday.organizer.*
import com.halo.app.language.uiText
import org.json.JSONObject

@Composable
internal fun OrganizerChoice(label: String, value: String, choices: List<Pair<String, String>>, enabled: Boolean = true, localizeChoices: Boolean = true, choose: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(uiText(label), style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton({ expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(choices.find { it.first == value }?.second?.let { if (localizeChoices) uiText(it) else it } ?: uiText("Choose"))
            }
            DropdownMenu(expanded, { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                choices.forEach { (id, title) -> DropdownMenuItem(text = { Text(if (localizeChoices) uiText(title) else title) }, onClick = { expanded = false; choose(id) }) }
            }
        }
    }
}

@Composable
internal fun OrganizerCommitmentEditor(original: OrganizerRecord?, purpose: CommitmentPurpose, model: OrganizerViewModel,
    state: OrganizerPageState, dismiss: () -> Unit) {
    var title by rememberSaveable(original?.id, original?.version) { mutableStateOf(original?.title.orEmpty()) }
    var intent by rememberSaveable(original?.id, original?.version) { mutableStateOf(original?.commitment?.intent.orEmpty()) }
    var responsibility by rememberSaveable(original?.id, original?.version) { mutableStateOf(original?.commitment?.owner?.name ?: if (purpose in setOf(CommitmentPurpose.ROUTINE, CommitmentPurpose.WATCH)) "HALO" else "ME") }
    var selectedPurpose by rememberSaveable(original?.id, original?.version) { mutableStateOf(original?.commitment?.purpose?.name ?: purpose.name) }
    var actionValues by rememberSaveable(original?.id, original?.version) { mutableStateOf(ArrayList(original?.commitment?.actions.orEmpty().map {
        JSONObject().put("capability", it.capability).put("arguments", JSONObject(it.argumentsJson)).toString()
    })) }
    val sources by remember { Graph.collectorRepository.observe() }.collectAsStateWithLifecycle(emptyList())
    var invalid by remember { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var discard by rememberSaveable { mutableStateOf(false) }
    EverydaySheet({ if (!state.busy) discard = true }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(uiText("Organizer commitment"), style = MaterialTheme.typography.headlineSmall)
            state.error?.let { EverydayMessage(it, true) }
            OutlinedTextField(title, { title = it.take(200) }, label = { Text(uiText("Title")) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(intent, { intent = it.take(2000) }, label = { Text(uiText("What should happen?")) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth(), minLines = 2)
            OrganizerChoice("Responsibility", responsibility, CommitmentOwner.entries.map { it.name to responsibilityLabel(it) },
                enabled = original == null && selectedPurpose != "STOPWATCH" && !state.busy) { responsibility = it }
            if (original != null) Text(uiText("Use the explicit responsibility actions on the item to hand work over."))
            OrganizerChoice("Commitment type", selectedPurpose, CommitmentPurpose.entries.filter { original == null || original.kind == OrganizerKind.TASK || it == CommitmentPurpose.STANDARD }.map { it.name to commitmentPurposeLabel(it) }, enabled = !state.busy) {
                selectedPurpose = it
                if (it == "STOPWATCH") { responsibility = "ME"; actionValues = arrayListOf() }
            }
            if (responsibility != "ME") {
                Text(uiText("Choose the real actions Halo should perform. Saving alone does not start or schedule them."))
                actionValues.forEachIndexed { index, body ->
                    val action = JSONObject(body)
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            WorkCapabilityEditor(action, sources, !state.busy) { next -> actionValues = ArrayList(actionValues.toMutableList().also { it[index] = next.toString() }) }
                            TextButton({ actionValues = ArrayList(actionValues.filterIndexed { i, _ -> i != index }) }, enabled = !state.busy) { Text(uiText("Remove action")) }
                        }
                    }
                }
                TextButton({ actionValues = ArrayList(actionValues + JSONObject().put("capability", "summarize").put("arguments", JSONObject().put("period", "ON_DEMAND")).toString()) },
                    enabled = !state.busy && actionValues.size < 6) { Text(uiText("Add capability")) }
                Text(uiText("Set dates and recurrence in the item editor, then review and enable the exact work plan."))
            }
            if (invalid) EverydayMessage("Choose valid actions and exact selected sources before saving.", true)
            Button({
                val parsed = runCatching { actionValues.map { JSONObject(it).let { row -> OrganizerPlannedAction(row.getString("capability"), row.getJSONObject("arguments").toString()) } }
                    .also { if (it.isNotEmpty()) Graph.organizerWorkCapabilities.validate(it) } }.getOrNull()
                val refs = runCatching { parsed.orEmpty().mapNotNull { action -> val arguments = JSONObject(action.argumentsJson)
                    arguments.optString("sourceId").takeIf { it.isNotBlank() }?.let { OrganizerSourceRef(it, arguments.getLong("sourceVersion")) }
                }.distinctBy { it.id } }.getOrNull()
                invalid = title.isBlank() || parsed == null || refs == null
                if (!invalid) { submitted = true; model.saveCommitment(original, title, intent, CommitmentOwner.valueOf(responsibility), CommitmentPurpose.valueOf(selectedPurpose), parsed!!, refs!!, dismiss) }
            }, enabled = !state.busy && !submitted && title.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(uiText("Save commitment")) }
            if (submitted && !state.busy) Text(uiText("Read the current work and execution record before continuing. Do not repeat an uncertain operation."))
            TextButton({ discard = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(uiText("Cancel")) }
        }
    }
    if (discard) com.halo.app.ui.components.HaloAlertDialog({ discard = false }, title = { Text(uiText("Discard this edit?")) },
        text = { Text(uiText("Your saved item is kept. The changes in this form will be discarded.")) },
        confirmButton = { TextButton({ discard = false; dismiss() }) { Text(uiText("Discard")) } },
        dismissButton = { TextButton({ discard = false }) { Text(uiText("Keep editing")) } })
}

@Composable
private fun WorkCapabilityEditor(action: JSONObject, sources: List<CollectorState>, enabled: Boolean, update: (JSONObject) -> Unit) {
    val capability = action.getString("capability")
    val arguments = action.getJSONObject("arguments")
    fun change(key: String, value: Any) { update(JSONObject().put("capability", capability).put("arguments", JSONObject(arguments.toString()).put(key, value))) }
    OrganizerChoice("Halo action", capability, Graph.organizerWorkCapabilities.list().map { it.id to capabilityLabel(it.id) }, enabled) { next ->
        val defaults = when (next) { "summarize" -> JSONObject().put("period", "ON_DEMAND"); "system.monitor" -> JSONObject().put("condition", "BATTERY_BELOW").put("percent", 20)
            "monitor" -> JSONObject().put("predicate", "CHANGED").put("refresh", false); else -> JSONObject().put("query", "").put("refresh", false) }
        update(JSONObject().put("capability", next).put("arguments", defaults))
    }
    when (capability) {
        "summarize" -> OrganizerChoice("Brief", arguments.optString("period", "ON_DEMAND"), BriefPeriod.entries.map { it.name to when (it) {
            BriefPeriod.MORNING -> "Morning"; BriefPeriod.MIDDAY -> "Midday"; BriefPeriod.EVENING -> "Evening"; BriefPeriod.WEEKLY -> "Weekly"
            BriefPeriod.CHANGES -> "Since morning"; BriefPeriod.ON_DEMAND -> "Your day in Halo"
        } }, enabled) { change("period", it) }
        "web.search", "monitor" -> {
            val eligible = sources.filter { it.source.selected && it.source.enabled && !it.source.muted }
            OrganizerChoice("Selected source", arguments.optString("sourceId"), eligible.map { it.source.id to it.source.title }, enabled, localizeChoices = false) { id ->
                val source = eligible.first { it.source.id == id }
                val next = JSONObject(arguments.toString()).put("sourceId", id).put("sourceVersion", source.source.version)
                if (capability == "monitor") next.put("baseline", source.snapshot?.contentRevision.orEmpty())
                update(JSONObject().put("capability", capability).put("arguments", next))
            }
            if (capability == "web.search") OutlinedTextField(arguments.optString("query"), { change("query", it.take(400)) },
                label = { Text(uiText("Research query")) }, enabled = enabled, modifier = Modifier.fillMaxWidth())
            else {
                OrganizerChoice("Condition", arguments.optString("predicate", "CHANGED"), listOf("CHANGED" to "Source changed", "CONTAINS" to "Source contains text"), enabled) { change("predicate", it) }
                if (arguments.optString("predicate") == "CONTAINS") OutlinedTextField(arguments.optString("value"), { change("value", it.take(200)) }, label = { Text(uiText("Text to watch for")) }, enabled = enabled, modifier = Modifier.fillMaxWidth())
                Text(uiText("Change monitoring uses the exact snapshot selected here as its baseline."))
            }
            EverydayToggle(uiText("Refresh before checking in Power"), arguments.optBoolean("refresh"), enabled) { change("refresh", it) }
        }
        "system.monitor" -> {
            OrganizerChoice("Device condition", arguments.optString("condition", "BATTERY_BELOW"), listOf("BATTERY_BELOW" to "Battery below threshold", "NETWORK_AVAILABLE" to "Network available", "CHARGING" to "Charging"), enabled) { change("condition", it) }
            if (arguments.optString("condition") == "BATTERY_BELOW") OutlinedTextField(arguments.optInt("percent", 20).toString(), { text -> text.toIntOrNull()?.takeIf { it in 1..100 }?.let { change("percent", it) } },
                label = { Text(uiText("Battery percentage")) }, enabled = enabled, modifier = Modifier.fillMaxWidth())
        }
    }
}
