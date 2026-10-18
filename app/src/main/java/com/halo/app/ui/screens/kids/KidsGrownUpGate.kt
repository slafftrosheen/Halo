package com.halo.app.ui.screens.kids

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/** Uses the parent's existing Android PIN/password; Halo never receives the credential. */
@Composable
internal fun rememberKidsGrownUpGate(onVerified: () -> Unit, onUnavailable: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val title = com.halo.app.language.uiText("Grown-up space")
    val description = com.halo.app.language.uiText("Confirm your device PIN or password")
    val verified by rememberUpdatedState(onVerified)
    val unavailable by rememberUpdatedState(onUnavailable)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) verified()
    }
    return {
        val manager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        @Suppress("DEPRECATION")
        val intent = if (manager?.isDeviceSecure == true)
            manager.createConfirmDeviceCredentialIntent(title, description) else null
        if (intent == null) unavailable() else launcher.launch(intent)
    }
}
