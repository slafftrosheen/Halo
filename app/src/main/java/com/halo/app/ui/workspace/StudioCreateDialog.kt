package com.halo.app.ui.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.data.studio.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StudioCreateDialog(service: StudioService, initial: String,
    onCreate: (String, StudioPreset) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(initial) }
    var preset by remember { mutableStateOf(StudioPreset.BLANK) }
    StudioAlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.studio_new_project)) }, text = {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(title, { title = it.take(120) }, label = { Text(stringResource(R.string.studio_project_name)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text(stringResource(R.string.studio_presets), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StudioPreset.entries.forEach { choice ->
                    val label = when (choice) { StudioPreset.BLANK -> R.string.studio_blank; StudioPreset.SQUARE -> R.string.studio_square
                        StudioPreset.POSTCARD -> R.string.studio_postcard; StudioPreset.PORTRAIT -> R.string.studio_portrait }
                    FilterChip(choice == preset, { preset = choice }, label = { Text(stringResource(label)) })
                }
            }
            if (title.isNotBlank()) StudioDocumentPreview(service, remember(preset, title) { StudioTemplates.document(preset, title) },
                Modifier.fillMaxWidth().height(160.dp))
        }
    }, confirmButton = { TextButton(onClick = { onCreate(title, preset) }, enabled = title.isNotBlank()) { Text(stringResource(R.string.studio_new_project)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.studio_cancel)) } })
}

@Composable
internal fun StudioBackgroundDialog(initial: Int, onApply: (Int) -> Unit, onDismiss: () -> Unit) {
    var value by remember(initial) { mutableStateOf("#" + initial.toUInt().toString(16).padStart(8, '0')) }
    var invalid by remember { mutableStateOf(false) }
    StudioAlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.studio_background)) }, text = {
        Column {
            OutlinedTextField(value, { value = it.take(9) }, label = { Text(stringResource(R.string.studio_color)) },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            StudioColorChoices(value, { value = it })
            if (invalid) Text(stringResource(R.string.studio_invalid_color), color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(onClick = {
        if (value.matches(Regex("#[0-9A-Fa-f]{8}"))) { onApply(value.drop(1).toLong(16).toInt()); onDismiss() }
        else invalid = true
    }) { Text(stringResource(R.string.studio_apply)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.studio_cancel)) } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StudioColorChoices(value: String, onChange: (String) -> Unit, enabled: Boolean = true) {
    val choices = listOf(0xff000000.toInt() to R.string.studio_black, 0xffffffff.toInt() to R.string.studio_white,
        MaterialTheme.colorScheme.primary.toArgb() to R.string.studio_accent, 0 to R.string.studio_transparent)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { (color, label) ->
            val encoded = "#" + color.toUInt().toString(16).padStart(8, '0')
            FilterChip(value.equals(encoded, ignoreCase = true), { onChange(encoded) }, enabled = enabled,
                label = { Text(stringResource(label)) }, modifier = Modifier.heightIn(min = 48.dp),
                leadingIcon = { Box(Modifier.size(16.dp).background(Color(color), CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)) })
        }
    }
}
