package com.halo.app.ui.screens.settings

import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.design.HaloFocusAction
import com.halo.app.ui.icons.HaloGlyph

internal fun backupExportAllowed(busy: Boolean, pickerPending: Boolean, protect: Boolean, password: String) =
    !busy && !pickerPending && protect && password.isNotBlank()

/** Password stays ephemeral; callers save only the profile and protection choice. */
@Composable
internal fun BackupExportOptions(includeFiles: Boolean, protect: Boolean, password: String,
    busy: Boolean, pickerPending: Boolean, onFiles: (Boolean) -> Unit, onProtect: (Boolean) -> Unit,
    onPassword: (String) -> Unit, onExport: () -> Unit) {
    val editing = !busy && !pickerPending
    androidx.compose.runtime.LaunchedEffect(protect) { if (!protect) onProtect(true) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(uiText("Include workspace files"), Modifier.weight(1f))
            Switch(includeFiles, onFiles, enabled = editing)
        }
        Text(uiText("Backups are protected with your password."))
        if (protect) {
            HaloTextField(password, onPassword, enabled = editing, label = { Text(uiText("Password")) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth(), kind = VoiceFieldKind.SECRET)
            Text(uiText("Passwords are not saved in screen state. After an interruption, enter the password again."), style = MaterialTheme.typography.bodySmall)
        }
        HaloFocusAction(uiText("Export backup"), HaloGlyph.STORAGE,
            backupExportAllowed(busy, pickerPending, protect, password), onExport, active = false)
    }
}
