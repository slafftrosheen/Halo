package com.halo.app.ui.screens.kids

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.halo.app.language.uiText

/** Uses the phone's existing credential. No app PIN or unlocked state is persisted. */
@Suppress("DEPRECATION")
@Composable
internal fun rememberGrownUpAccess(onUnlocked: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val unlocked by rememberUpdatedState(onUnlocked)
    var missingLock by remember { mutableStateOf(false) }
    var launchFailed by remember { mutableStateOf(false) }
    var authenticating by remember { mutableStateOf(false) }
    val title = uiText("Grown-up space")
    val detail = uiText("Use your phone’s screen lock to open grown-up controls.")
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        authenticating = false
        if (result.resultCode == Activity.RESULT_OK) unlocked()
    }
    val securitySettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Setting a lock is not authentication. The adult must open the gate again.
        missingLock = false
    }
    if (missingLock || launchFailed) AlertDialog(
        onDismissRequest = { missingLock = false; launchFailed = false },
        title = { Text(uiText(if (missingLock) "Screen lock needed" else "Could not open screen lock")) },
        text = { Text(uiText(if (missingLock)
            "Set a PIN, pattern or password in Android Settings, then try again."
            else "Grown-up controls stay locked. Try again when screen lock is available.")) },
        confirmButton = {
            if (missingLock) TextButton(onClick = {
                try { securitySettings.launch(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
                catch (_: android.content.ActivityNotFoundException) { missingLock = false; launchFailed = true }
                catch (_: SecurityException) { missingLock = false; launchFailed = true }
            }) { Text(uiText("Open Android settings")) }
            else TextButton(onClick = { launchFailed = false }) { Text(uiText("Close")) }
        },
        dismissButton = { if (missingLock) TextButton(onClick = { missingLock = false }) { Text(uiText("Not now")) } },
    )
    return {
        if (!authenticating) {
            val keyguard = context.getSystemService(KeyguardManager::class.java)
            val intent = keyguard?.takeIf { it.isDeviceSecure }?.createConfirmDeviceCredentialIntent(title, detail)
            if (intent == null) missingLock = true else {
                authenticating = true
                try { launcher.launch(intent) }
                catch (_: android.content.ActivityNotFoundException) { authenticating = false; launchFailed = true }
                catch (_: SecurityException) { authenticating = false; launchFailed = true }
            }
        }
    }
}
