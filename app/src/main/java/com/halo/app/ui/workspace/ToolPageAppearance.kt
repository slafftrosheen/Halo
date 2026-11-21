package com.halo.app.ui.workspace

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.language.uiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Presentation preferences never change the project or board data. */
internal class ToolPageAppearance<T>(
    context: Context,
    private val key: String,
    private val default: T,
    private val decode: (JSONObject) -> T,
    private val encode: (T) -> JSONObject,
) {
    private val storage = context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    data class Snapshot<T>(val current: T, val previous: T? = null, val changed: Long = 0) {
        fun restorable(): T? {
            val age = System.currentTimeMillis() - changed
            return previous?.takeIf { age in 0..604_800_000L }
        }
    }

    private fun read(): Snapshot<T> = runCatching {
        val saved = JSONObject(storage.getString(key, null) ?: return Snapshot(default))
        Snapshot(decode(saved.getJSONObject("current")), saved.optJSONObject("previous")?.let(decode), saved.optLong("changed"))
    }.getOrDefault(Snapshot(default))

    suspend fun load(): Snapshot<T> = withContext(Dispatchers.IO) { read() }

    suspend fun apply(expected: Snapshot<T>, draft: T): Snapshot<T> = withContext(Dispatchers.IO) {
        synchronized(writeLock) {
            val actual = read()
            check(actual == expected) { "Appearance changed" }
            if (draft == actual.current) actual else {
                val updated = Snapshot(draft, actual.current, System.currentTimeMillis())
                val entry = JSONObject().put("current", encode(draft))
                    .put("previous", encode(actual.current)).put("changed", updated.changed)
                check(storage.edit().putString(key, entry.toString()).commit()) { "Appearance could not be saved" }
                updated
            }
        }
    }

    companion object {
        internal const val STORE = "halo_tool_page_appearance"
        private val writeLock = Any()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> ToolLookSelections(@StringRes title: Int, choices: List<Pair<T, Int>>, selected: T,
    onSelect: (T) -> Unit) {
    Text(stringResource(title), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { (value, label) ->
            FilterChip(selected = selected == value, onClick = { onSelect(value) },
                label = { Text(stringResource(label)) }, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
}

@Composable
internal fun <T> ToolLookDialog(
    @StringRes title: Int,
    saved: ToolPageAppearance.Snapshot<T>,
    draft: T,
    onPreview: (T) -> Unit,
    onApply: suspend () -> Unit,
    onDismiss: () -> Unit,
    preview: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var failed by remember(draft) { mutableStateOf(false) }
    StudioAlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                preview()
                content()
                saved.restorable()?.let { previous ->
                    TextButton(onClick = { onPreview(previous) }, enabled = !saving,
                        modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.alive_tool_restore))
                    }
                }
                if (failed) Text(stringResource(R.string.alive_tool_save_failed), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = { onPreview(saved.current) }, enabled = !saving && draft != saved.current,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Undo")) }
            TextButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Cancel")) }
        },
        confirmButton = {
            Button(onClick = {
                saving = true
                scope.launch {
                    try {
                        onApply()
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        failed = true
                    } finally {
                        saving = false
                    }
                }
            }, enabled = !saving && draft != saved.current, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Apply")) }
        },
    )
}

@Composable
internal fun <T> rememberToolAppearance(owner: ToolPageAppearance<T>): MutableState<ToolPageAppearance.Snapshot<T>?> {
    val snapshot = remember(owner) { mutableStateOf<ToolPageAppearance.Snapshot<T>?>(null) }
    LaunchedEffect(owner) { snapshot.value = owner.load() }
    return snapshot
}
