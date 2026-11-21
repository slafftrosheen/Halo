package com.halo.app.ui.screens.home

import android.app.NotificationManager
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.ui.toolkit.HaloFocusOwner
import com.halo.runtime.cognition.context.ContextReference
import com.halo.runtime.cognition.context.ContextualNextAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/** Home suggestions are a bounded presentation. They can only open an existing reviewed action. */
@Composable
internal fun HomeNextForYou(
    candidates: List<ContextualNextAction>,
    onPick: (ContextualNextAction) -> Unit,
) {
    val context = LocalContext.current
    val store = remember(context) { HomeSuggestionBudget(context.applicationContext) }
    val focus by remember(context) { HaloFocusOwner.get(context).state }.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var active by remember { mutableStateOf<ContextualNextAction?>(null) }
    var dismissed by remember { mutableIntStateOf(0) }
    var reason by remember { mutableStateOf(false) }
    LaunchedEffect(candidates, focus?.version, dismissed) {
        active = null
        reason = false
        val candidate = withContext(Dispatchers.IO) {
            if (focus?.left()?.let { it > 0L } == true || context.isSuggestionQuiet()) null
            else candidates.firstOrNull { store.admit(it, LocalDateTime.now()) }
        } ?: return@LaunchedEffect
        val expiry = candidate.expiresAtMs ?: Long.MAX_VALUE
        active = candidate
        while (true) {
            val validFor = (expiry - System.currentTimeMillis()).coerceAtLeast(0L)
            if (validFor == 0L || context.isSuggestionQuiet()) break
            delay(minOf(15_000L, validFor))
        }
        active = null
        reason = false
    }
    val item = active?.takeIf { current -> candidates.any { it.id == current.id } } ?: return
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(stringResource(R.string.halo_next_title), style = MaterialTheme.typography.labelLarge)
            TextButton({
                if ((item.expiresAtMs ?: Long.MAX_VALUE) > System.currentTimeMillis()) onPick(item)
            }, modifier = Modifier.heightIn(min = 48.dp)) { Text(contextualNextActionLabel(item.kind)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ reason = true }) { Text(stringResource(R.string.halo_next_why)) }
                TextButton({
                    active = null
                    scope.launch {
                        withContext(Dispatchers.IO) { store.dismiss(item, LocalDateTime.now()) }
                        dismissed++
                    }
                }) { Text(stringResource(R.string.halo_next_dismiss)) }
            }
        }
    }
    if (reason) AlertDialog(onDismissRequest = { reason = false },
        title = { Text(stringResource(R.string.halo_next_why)) },
        text = { Text(stringResource(if (item.sourceReference is ContextReference.WorkspaceObject) R.string.halo_next_reason_source
            else R.string.halo_next_reason_reply)) },
        confirmButton = { TextButton({ reason = false }) { Text(stringResource(android.R.string.ok)) } })
}

private fun Context.isSuggestionQuiet(): Boolean {
    val quietTime = LocalDateTime.now().hour.let { it < 7 || it >= 21 }
    val car = getSystemService(UiModeManager::class.java)?.currentModeType == Configuration.UI_MODE_TYPE_CAR
    val filter = getSystemService(NotificationManager::class.java)?.currentInterruptionFilter
    return quietTime || car || filter in setOf(NotificationManager.INTERRUPTION_FILTER_NONE,
        NotificationManager.INTERRUPTION_FILTER_ALARMS, NotificationManager.INTERRUPTION_FILTER_PRIORITY)
}

private class HomeSuggestionBudget(context: Context) {
    private val disk = context.getSharedPreferences("halo_next_suggestion_v1", Context.MODE_PRIVATE)
    fun admit(action: ContextualNextAction, now: LocalDateTime): Boolean = synchronized(lock) {
        if ((action.expiresAtMs ?: Long.MAX_VALUE) <= System.currentTimeMillis()) return false
        val date = now.toLocalDate().toString()
        val sameDay = date == disk.getString("date", null)
        val dismissed = if (sameDay) disk.getStringSet("dismissed", emptySet()).orEmpty() else emptySet()
        if (action.kind.name in dismissed) return false
        val shown = if (sameDay) disk.getStringSet("shown", emptySet()).orEmpty() else emptySet()
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(action.id.toByteArray(Charsets.UTF_8))
            .take(12).joinToString("") { "%02x".format(it) }
        when {
            digest in shown -> true
            shown.size >= 3 -> false
            else -> disk.edit().putString("date", date).putStringSet("shown", shown + digest)
                .putStringSet("dismissed", dismissed).commit()
        }
    }

    fun dismiss(action: ContextualNextAction, now: LocalDateTime) = synchronized(lock) {
        val date = now.toLocalDate().toString()
        val sameDay = date == disk.getString("date", null)
        val dismissed = if (sameDay) disk.getStringSet("dismissed", emptySet()).orEmpty() else emptySet()
        val shown = if (sameDay) disk.getStringSet("shown", emptySet()).orEmpty() else emptySet()
        disk.edit().putString("date", date).putStringSet("shown", shown)
            .putStringSet("dismissed", dismissed + action.kind.name).commit()
    }

    private companion object { val lock = Any() }
}
