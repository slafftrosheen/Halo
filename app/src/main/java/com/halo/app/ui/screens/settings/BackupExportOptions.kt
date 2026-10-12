package com.halo.app.ui.screens.settings

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
    !busy && !pickerPending && (!protect || password.isNotBlank())

/** Password stays ephemeral; callers save only the profile and protection choice. */
@Composable
internal fun BackupExportOptions(includeFiles: Boolean, protect: Boolean, password: String,
    busy: Boolean, pickerPending: Boolean, onFiles: (Boolean) -> Unit, onProtect: (Boolean) -> Unit,
    onPassword: (String) -> Unit, onExport: () -> Unit) {
    val editing = !busy && !pickerPending
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(uiText("Include workspace files"), Modifier.weight(1f))
            Switch(includeFiles, onFiles, enabled = editing)
        }
        Row(Modifier.fillMaxWidth()) {
            Text(uiText("Protect with password"), Modifier.weight(1f))
            Switch(protect, onProtect, enabled = editing)
        }
        if (protect) {
            OutlinedTextField(password, onPassword, enabled = editing, label = { Text(uiText("Password")) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(uiText("Passwords are not saved in screen state. After an interruption, enter the password again."), style = MaterialTheme.typography.bodySmall)
        }
        HaloFocusAction(uiText("Export Brain Dump"), HaloGlyph.STORAGE,
            backupExportAllowed(busy, pickerPending, protect, password), onExport, active = false)
    }
}
