package com.halo.app.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R

@Composable
internal fun HomeImportedSourceDialog(source: HomeImportedSource, onAttach: () -> Unit,
    onStudy: () -> Unit, onNotebook: () -> Unit, onDismiss: () -> Unit, binding: Boolean = false) {
    val density = LocalDensity.current
    AlertDialog(onDismissRequest = onDismiss,
        title = { CompositionLocalProvider(LocalDensity provides density) { Text(stringResource(R.string.home_attach_saved)) } },
        text = {
            CompositionLocalProvider(LocalDensity provides density) {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(source.title)
                Text(stringResource(when (source.kind) {
                    HomeImportedSourceKind.PDF -> R.string.home_attach_pdf_saved
                    HomeImportedSourceKind.AUDIO -> R.string.home_attach_audio_saved
                    HomeImportedSourceKind.OTHER -> R.string.home_attach_other_saved
                    else -> R.string.home_attach_not_attached
                }))
                if (source.attachmentFailed) Text(stringResource(R.string.home_attach_bind_failed), color = MaterialTheme.colorScheme.error)
                if (binding) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.home_attach_checking)) }
                if (source.canAttach) Button(onClick = onAttach, enabled = !binding, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.home_attach_to_chat))
                }
                if (source.kind == HomeImportedSourceKind.PDF) Button(onClick = onStudy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.home_tools_open_study)) }
                TextButton(onClick = onNotebook, enabled = !binding, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.home_tools_open_notebook))
                }
            }
            }
        },
        confirmButton = { CompositionLocalProvider(LocalDensity provides density) { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.home_tools_close))
        } } })
}
