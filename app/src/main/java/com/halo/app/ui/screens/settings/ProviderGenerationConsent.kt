package com.halo.app.ui.screens.settings

import androidx.compose.runtime.*
import androidx.compose.material3.*
import com.halo.app.ai.ProviderControlRepository
import com.halo.app.language.uiText
import com.halo.sdk.provider.AiExecutionLocation

/** Per-test consent, no persisted opt-in and no automatic request on screen entry. */
@Composable
internal fun rememberProviderGenerationCheck(control: ProviderControlRepository): (String) -> Unit {
    var pending by remember { mutableStateOf<String?>(null) }
    pending?.let { providerId ->
        AlertDialog(onDismissRequest = { pending = null },
            title = { Text(uiText("Test an online answer?")) },
            text = { Text(uiText("This sends a short test phrase to this provider and may use paid credits. No notes, history, images or tools are included.")) },
            confirmButton = { TextButton(onClick = {
                pending = null
                control.checkGeneration(providerId, allowBillable = true)
            }) { Text(uiText("Test answer")) } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(uiText("Cancel")) } })
    }
    return { providerId ->
        if (control.rows.value.firstOrNull { it.descriptor.providerId == providerId }?.descriptor?.location == AiExecutionLocation.REMOTE)
            pending = providerId
        else control.checkGeneration(providerId)
    }
}
