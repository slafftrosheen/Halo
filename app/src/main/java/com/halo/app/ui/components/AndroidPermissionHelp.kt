package com.halo.app.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.halo.app.language.uiText

/** Targeted recovery after Android denies access; never launches a permission tour at startup. */
@Composable fun AndroidPermissionHelp() {
    val context = LocalContext.current
    TextButton(onClick = {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }) { Text(uiText("Open Android app permissions")) }
}
