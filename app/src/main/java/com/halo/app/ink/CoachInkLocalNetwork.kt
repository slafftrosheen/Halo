package com.halo.app.ink

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.halo.app.R

internal fun coachInkHasLocalNetwork(context: Context): Boolean =
    Build.VERSION.SDK_INT < 37 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

/** Shows the OS permission action only when the paired local transport requires approval. */
@Composable
internal fun CoachInkLocalNetworkAccess(enabled: Boolean): Boolean {
    if (Build.VERSION.SDK_INT < 37) return true
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var granted by remember(context) { mutableStateOf(coachInkHasLocalNetwork(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = coachInkHasLocalNetwork(context)
    }
    DisposableEffect(context, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = coachInkHasLocalNetwork(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (!granted) TextButton({
        launcher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
    }, enabled = enabled) { Text(stringResource(R.string.halo11_coach_ink_network_permission)) }
    return granted
}
