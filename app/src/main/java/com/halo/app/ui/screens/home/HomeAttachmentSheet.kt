package com.halo.app.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText

/** One expanded chooser, with dismissal outside the source list's scroll viewport. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeAttachmentSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        CompositionLocalProvider(LocalDensity provides density) {
            Column(Modifier.fillMaxWidth().heightIn(max = 680.dp).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(uiText("Attachments and context"), style = MaterialTheme.typography.titleMedium)
                    content()
                }
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(uiText("Done"))
                }
            }
        }
    }
}
