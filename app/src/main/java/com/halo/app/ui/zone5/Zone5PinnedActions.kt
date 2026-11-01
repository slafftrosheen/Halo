package com.halo.app.ui.zone5

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.presence.HaloOrbContextAction
import com.halo.app.ui.theme.ActionPinCodec
import com.halo.app.ui.theme.ActionPinSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Preference editing only. No command callback is available to this component. */
@Composable
internal fun Zone5PinnedActionsEditor(
    family: String,
    settings: ActionPinSettings,
    actions: List<HaloOrbContextAction>,
    onChange: suspend (String, List<String>, List<String>) -> Unit,
    onDone: () -> Unit,
) {
    val pins = settings.families[family].orEmpty()
    val scope = rememberCoroutineScope()
    var saving by remember(family) { mutableStateOf(false) }
    var failed by remember(family) { mutableStateOf(false) }
    fun save(next: List<String>) {
        if (saving) return
        saving = true
        scope.launch {
            try { onChange(family, pins, next); failed = false }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { saving = false }
        }
    }
    Text(stringResource(R.string.zone5_pins_explain, ActionPinCodec.MAX_PER_FAMILY))
    if (settings.unreadable) Text(stringResource(R.string.zone5_pins_unreadable), color = MaterialTheme.colorScheme.error)
    if (pins.any { id -> actions.none { it.id == id } }) Text(stringResource(R.string.zone5_pins_unavailable))
    if (failed) Text(stringResource(R.string.zone5_pins_save_failed), color = MaterialTheme.colorScheme.error)
    if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
    actions.forEach { action ->
        val selected = action.id in pins
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                value = selected,
                enabled = !saving && (selected || pins.size < ActionPinCodec.MAX_PER_FAMILY),
                role = Role.Checkbox,
                onValueChange = { checked -> save(if (checked) pins + action.id else pins - action.id) },
            ), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(selected, onCheckedChange = null, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp))
                Text(uiText(action.title), Modifier.weight(1f))
            }
            if (!action.enabled) Text(action.disabledReason?.let { uiText(it) }
                ?: stringResource(R.string.zone5_unavailable), style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 48.dp, bottom = 8.dp))
        }
    }
    if (pins.isNotEmpty() || settings.unreadable) TextButton(onClick = { save(emptyList()) }, enabled = !saving,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.zone5_pins_clear)) }
    TextButton(onClick = onDone, enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.zone5_pins_done))
    }
}
