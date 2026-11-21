package com.halo.app.ui.screens.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloReadAloud
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.zone6.Zone6PageApplyRow
import com.halo.app.ui.zone6.Zone6ButtonSheet
import com.halo.app.ui.zone6.rememberZone6PageSetting
import com.halo.sdk.provider.RemoteDataCategory
import kotlinx.coroutines.launch
import org.json.JSONObject

internal data class DeviceAppearance(val label: String = "", val icon: HaloGlyph? = null) {
    fun encode(): String = JSONObject().put("label", label).put("icon", icon?.name.orEmpty()).toString()
}

private val iconOptions = mapOf(
    HaloGlyph.GLASSES to R.string.page_devices_glasses,
    HaloGlyph.ROBOT to R.string.page_devices_robot,
    HaloGlyph.WATCH to R.string.page_devices_watch,
    HaloGlyph.DEVICE to R.string.page_devices_other,
)

internal fun deviceAppearance(raw: String?): DeviceAppearance = if (raw.isNullOrEmpty()) DeviceAppearance() else
    runCatching {
        val values = JSONObject(raw)
        DeviceAppearance(values.optString("label").take(64),
            iconOptions.keys.firstOrNull { it.name == values.optString("icon") })
    }.getOrDefault(DeviceAppearance())

internal fun deviceAppearanceKey(id: String): String = "devices.appearance." +
    java.security.MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DeviceAppearanceSheet(devices: List<GenericDeviceUiState>, selectedId: String?,
    onSelect: (String?) -> Unit, onPreview: (String, DeviceAppearance?) -> Unit, onDismiss: () -> Unit) {
    val selected = devices.firstOrNull { it.id == selectedId }
    Zone6ButtonSheet(stringResource(R.string.page_devices_layout), onDismiss) {
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (selected == null) {
                Text(stringResource(R.string.page_devices_layout_help))
                devices.forEach { device ->
                    TextButton(onClick = { onSelect(device.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(device.name)
                    }
                }
            } else key(selected.id) {
                val setting = rememberZone6PageSetting(deviceAppearanceKey(selected.id))
                val baseline = remember { setting.value }
                val initial = remember { deviceAppearance(baseline) }
                var draft by remember { mutableStateOf(initial) }
                var busy by remember { mutableStateOf(false) }
                var error by remember { mutableStateOf(false) }
                val previewState by rememberUpdatedState(onPreview)
                val scope = rememberCoroutineScope()
                LaunchedEffect(draft) { previewState(selected.id, draft) }
                DisposableEffect(selected.id) { onDispose { previewState(selected.id, null) } }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HaloIcon(draft.icon ?: HaloGlyph.DEVICE, contentDescription = null, modifier = Modifier.size(34.dp))
                    Text(draft.label.ifBlank { selected.name }, style = MaterialTheme.typography.titleLarge)
                }
                OutlinedTextField(draft.label, { draft = draft.copy(label = it.take(64)) },
                    modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true,
                    label = { Text(stringResource(R.string.page_devices_name)) }, placeholder = { Text(selected.name) })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf(null to R.string.page_devices_default_icon) + iconOptions.map { it.key to it.value }).forEach { (icon, label) ->
                        FilterChip(selected = draft.icon == icon, { draft = draft.copy(icon = icon) }, enabled = !busy,
                            label = { Text(stringResource(label)) }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
                Zone6PageApplyRow(draft != initial, busy, error, setting.previous != null,
                    onUndo = { draft = initial },
                    onRestore = { draft = deviceAppearance(setting.previous) },
                    onApply = {
                        busy = true
                        scope.launch {
                            try {
                                error = !setting.apply(baseline, draft.copy(label = draft.label.trim()).encode(), DeviceAppearance().encode())
                                if (!error) onDismiss()
                            } finally { busy = false }
                        }
                    })
                TextButton(onClick = { onSelect(null) }, enabled = !busy) { Text(uiText("Back")) }
            }
        }
    }
}

@Composable
internal fun DevicePairingGuide(onFind: () -> Unit, onDismiss: () -> Unit) {
    val directions = listOf(
        HaloGlyph.BATTERY to R.string.page_devices_step_prepare,
        HaloGlyph.BLUETOOTH to R.string.page_devices_step_pair,
        HaloGlyph.SEARCH to R.string.page_devices_step_find,
    )
    Zone6ButtonSheet(stringResource(R.string.page_devices_add), onDismiss) {
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            directions.forEachIndexed { index, (glyph, message) ->
                val instruction = stringResource(message)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    HaloIcon(glyph, contentDescription = null, modifier = Modifier.size(32.dp))
                    Text(stringResource(R.string.page_devices_step, index + 1, instruction), Modifier.weight(1f))
                    HaloReadAloud(instruction, compact = true, category = RemoteDataCategory.TEXT)
                }
            }
            Button(onClick = onFind, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                Text(stringResource(R.string.page_devices_find))
            }
        }
    }
}
