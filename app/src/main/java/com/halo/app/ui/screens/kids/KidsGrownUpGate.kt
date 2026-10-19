package com.halo.app.ui.screens.kids

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.fx.HaloPackageFx
import com.halo.app.ui.fx.HaloPackageMoment

/** The brand gate explains the boundary; Android's credential prompt grants access. */
@Composable
internal fun rememberKidsGrownUpGate(onVerified: () -> Unit, onUnavailable: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val title = uiText("Grown-up space")
    val description = uiText("Confirm your device PIN or password")
    val verified by rememberUpdatedState(onVerified)
    val unavailable by rememberUpdatedState(onUnavailable)
    var open by remember { mutableStateOf(false) }
    var unlocked by remember { mutableStateOf(false) }
    var credentialPending by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        credentialPending = false
        if (result.resultCode == Activity.RESULT_OK) unlocked = true
    }
    LaunchedEffect(unlocked) {
        if (unlocked) {
            kotlinx.coroutines.delay(600)
            open = false; unlocked = false
            verified()
        }
    }
    if (open) AlertDialog(
        onDismissRequest = { if (!credentialPending && !unlocked) open = false },
        containerColor = Color(if (MaterialTheme.colorScheme.background.luminance() < .5f) 0xFF2E2238 else 0xFFFFF3EA),
        icon = { key(unlocked) { HaloPackageFx(if (unlocked) HaloPackageMoment.KIDS_UNLOCK else HaloPackageMoment.KIDS_LOCK, Modifier.size(80.dp)) } },
        title = { Text(uiText(if (unlocked) "Unlocked" else "Ask a grown-up")) },
        text = { Text(uiText("This part is for grown-ups. They’ll unlock it with the phone’s screen lock.")) },
        confirmButton = {
            Button(onClick = {
                val manager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                @Suppress("DEPRECATION")
                val intent = if (manager?.isDeviceSecure == true)
                    manager.createConfirmDeviceCredentialIntent(title, description) else null
                if (intent == null) { open = false; unavailable() }
                else {
                    try { credentialPending = true; launcher.launch(intent) }
                    catch (_: android.content.ActivityNotFoundException) { credentialPending = false; open = false; unavailable() }
                }
            }, enabled = !unlocked && !credentialPending, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(uiText("Grown-up: unlock"))
            }
        },
        dismissButton = { TextButton(onClick = { open = false }, enabled = !unlocked && !credentialPending,
            modifier = Modifier.heightIn(min = 56.dp)) { Text(uiText("Go back")) } },
    )
    return { unlocked = false; open = true }
}
