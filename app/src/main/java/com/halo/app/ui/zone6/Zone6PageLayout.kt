package com.halo.app.ui.zone6

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.language.uiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val RESTORE_WINDOW_MS = 7 * 24 * 60 * 60 * 1000L

/** Presentation preferences only. Saved objects, device IDs, and access policy stay with their owners. */
internal class Zone6PageSetting(context: Context, private val key: String) {
    private val application = context.applicationContext
    private val prefs = application.getSharedPreferences("halo_page_layouts_v7", Context.MODE_PRIVATE)
    var value by mutableStateOf(prefs.getString(key, null))
        private set
    var previous by mutableStateOf(readPrevious())
        private set

    private fun readPrevious(): String? = prefs.getString("$key.previous", null)?.takeIf {
        (System.currentTimeMillis() - prefs.getLong("$key.changed", 0L)) in 0..RESTORE_WINDOW_MS
    }

    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
        if (changed == null || changed == key || changed.startsWith("$key.")) {
            value = prefs.getString(key, null)
            previous = readPrevious()
        }
    }

    fun observe() {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        value = prefs.getString(key, null)
        previous = readPrevious()
    }
    fun stopObserving() = prefs.unregisterOnSharedPreferenceChangeListener(listener)

    suspend fun apply(expected: String?, next: String, defaultValue: String): Boolean {
        val saved = withContext(Dispatchers.IO) {
            synchronized(writeLock) {
                if (key in setOf(WORKSPACE_DOCK_KEY, "places.order", "workspace.shelf.v8")) {
                    try { com.halo.app.ui.theme.HaloLayoutLock.get(application).requireUnlocked() }
                    catch (_: IllegalStateException) { return@synchronized false }
                }
                val current = prefs.getString(key, null)
                when {
                    current != expected -> false
                    (current ?: defaultValue) == next -> true
                    else -> prefs.edit()
                        .putString("$key.previous", current ?: defaultValue)
                        .putLong("$key.changed", System.currentTimeMillis())
                        .putString(key, next)
                        .commit()
                }
            }
        }
        value = prefs.getString(key, null)
        previous = readPrevious()
        return saved
    }

    private companion object { val writeLock = Any() }
}

@Composable
internal fun rememberZone6PageSetting(key: String): Zone6PageSetting {
    val context = LocalContext.current.applicationContext
    val setting = remember(context, key) { Zone6PageSetting(context, key) }
    DisposableEffect(setting) {
        setting.observe()
        onDispose { setting.stopObserving() }
    }
    return setting
}

@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.editZone6PageOnHold(onEdit: () -> Unit): Modifier = composed {
    val caption = stringResource(R.string.page_layout_edit)
    combinedClickable(onClick = onEdit, onLongClickLabel = caption, onLongClick = onEdit)
}

@Composable
internal fun Zone6PageChoiceSheet(
    title: String,
    choices: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    dirty: Boolean,
    busy: Boolean,
    error: Boolean,
    canRestore: Boolean,
    onUndo: () -> Unit,
    onRestore: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    Zone6ButtonSheet(title, { if (!busy) onDismiss() }) {
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            choices.forEach { (id, label) ->
                Surface(onClick = { onSelect(id) }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected == id, onClick = null, enabled = !busy,
                            modifier = Modifier.padding(12.dp))
                        Text(label, modifier = Modifier.weight(1f))
                    }
                }
            }
            Zone6PageApplyRow(dirty, busy, error, canRestore, onUndo, onRestore, onApply)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun Zone6PageApplyRow(dirty: Boolean, busy: Boolean, error: Boolean, canRestore: Boolean,
    onUndo: () -> Unit, onRestore: () -> Unit, onApply: () -> Unit) {
    if (error) Text(stringResource(R.string.page_layout_error), color = MaterialTheme.colorScheme.error)
    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (canRestore) TextButton(onRestore, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.page_layout_restore))
        }
        TextButton(onUndo, enabled = dirty && !busy, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(uiText("Undo"))
        }
        Button(onApply, enabled = dirty && !busy, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(uiText("Apply"))
        }
    }
}

/** A single choice previews in the real page and resets on cancel, including Android Back. */
@Composable
internal fun Zone6PageSetting.Choose(default: String, title: String, choices: List<Pair<String, String>>,
    selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val startingValue = remember(this) { value }
    val initial = startingValue ?: default
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Zone6PageChoiceSheet(title, choices, selected, onSelect, selected != initial, busy, error, previous != null,
        onUndo = { onSelect(initial) },
        onRestore = { previous?.let(onSelect) },
        onApply = {
            busy = true
            scope.launch {
                try {
                    error = !apply(startingValue, selected, default)
                    if (!error) onDismiss()
                } finally { busy = false }
            }
        },
        onDismiss = onDismiss)
}
