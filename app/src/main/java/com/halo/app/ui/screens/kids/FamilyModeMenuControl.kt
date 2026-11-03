package com.halo.app.ui.screens.kids

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.language.uiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Call at shell scope, not inside the transient menu. Android authentication stops the
 * host Activity and dismisses that menu; the credential launcher and write must survive it. */
@Composable
internal fun rememberFamilyModeMenuControl(preferences: KidsPreferences,
    onDisclosureChanged: (Boolean) -> Unit = {},
    beforeChange: () -> Unit = {}): @Composable () -> Unit {
    val kids by preferences.enabled.collectAsStateWithLifecycle()
    val ready by preferences.loadedState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val currentBeforeChange by rememberUpdatedState(beforeChange)
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var unavailable by remember { mutableStateOf(false) }
    var gateOpen by remember { mutableStateOf(false) }
    val disclosure by rememberUpdatedState(onDisclosureChanged)
    SideEffect { disclosure(gateOpen || unavailable || failed) }
    DisposableEffect(Unit) { onDispose { disclosure(false) } }
    fun save(value: Boolean) {
        if (!ready || busy || preferences.enabled.value == value) return
        busy = true
        scope.launch {
            try {
                currentBeforeChange()
                preferences.setEnabled(value)
                failed = false
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) { failed = true }
              finally { busy = false }
        }
    }
    val requestFullMode = rememberKidsGrownUpGate(onVerified = { save(false) }, onUnavailable = { unavailable = true },
        onDisclosureChanged = { gateOpen = it })
    if (unavailable || failed) AlertDialog(onDismissRequest = { unavailable = false; failed = false },
        title = { Text(uiText(if (unavailable) "Grown-up space" else "Kids mode")) },
        text = { Text(uiText(if (unavailable) "Ask a grown-up to set a device PIN or password in Android settings first."
            else "Could not save your change. Try again.")) },
        confirmButton = { TextButton({ unavailable = false; failed = false }) { Text(uiText("OK")) } })
    return {
        FamilyModeMenuRow(kids, ready && !busy) { value ->
            if (value) save(true) else requestFullMode()
        }
    }
}

@Composable
internal fun FamilyModeMenuRow(kids: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val stateLabel = uiText(if (kids) "Kids mode" else "Full halo")
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("family-mode-switch")
        .semantics(mergeDescendants = true) { stateDescription = stateLabel }
        .toggleable(value = kids, enabled = enabled, role = Role.Switch, onValueChange = onChange)
        .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(uiText("Kids mode"), style = MaterialTheme.typography.titleMedium)
            Text(if (kids) uiText("Learning and tools on one page.") else stateLabel,
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = kids, onCheckedChange = null, enabled = enabled)
    }
}
