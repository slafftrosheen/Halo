package com.halo.app.ui.toolkit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.sdk.runtime.display.ExternalDisplayState

@Composable
internal fun HaloRecordDisplayControl(active: HaloCaptionDisplayStatus?, enabled: Boolean, onChoose: () -> Unit,
    onStop: () -> Unit) {
    if (active == null) OutlinedButton(onChoose, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(R.string.halo_record_display_choose))
    } else {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text(stringResource(R.string.halo_record_display_sending, active.name),
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
                TextButton(onStop, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.halo_record_display_stop))
                }
            }
        }
    }
}

@Composable
internal fun HaloRecordDisplayChooser(state: ExternalDisplayState, onDismiss: () -> Unit,
    onChoose: (HaloCaptionDisplayChoice) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.halo_record_display_choose)) },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.halo_record_display_about))
                when {
                    !state.foreground || state.endpoints.isEmpty() -> Text(stringResource(R.string.halo_record_display_empty))
                    state.activeToken != null -> Text(stringResource(R.string.halo_record_display_in_use))
                    else -> state.endpoints.forEach { endpoint ->
                        OutlinedButton(onClick = { onChoose(HaloCaptionDisplayChoice(endpoint.token, endpoint.name, state.revision)) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(endpoint.name) }
                    }
                }
            }
        }, confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.halo_record_display_cancel)) } })
}

@Composable
internal fun HaloRecordDisplayConfirmation(choice: HaloCaptionDisplayChoice, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.halo_record_display_confirm_title, choice.name)) },
        text = { Text(stringResource(R.string.halo_record_display_confirm_body)) },
        confirmButton = { Button(onConfirm) { Text(stringResource(R.string.halo_record_display_start)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.halo_record_display_cancel)) } })
}
