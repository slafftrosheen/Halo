package com.halo.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.design.HaloProgressPresentation
import com.halo.app.ui.fx.HaloProgressRing

/** Byte progress is download progress only. Verification still owns installation readiness. */
@Composable
internal fun ModelDownloadProgress(progress: Float?, bytes: Long, total: Long?, resumed: Boolean) {
    val fraction = progress?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (fraction != null) {
            Box(Modifier.progressSemantics(fraction), contentAlignment = Alignment.Center) {
                HaloProgressRing(fraction, MaterialTheme.colorScheme.primary, ringSize = 72.dp)
                Text(uiText("%1\$d%%", (fraction * 100).toInt()), style = MaterialTheme.typography.labelMedium)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (fraction == null) HaloProgressPresentation(uiText("Downloading"))
            else Text(uiText("Downloading"), style = MaterialTheme.typography.titleMedium)
            Text(if (total != null && total > 0L)
                uiText("%1\$s of %2\$s", downloadSize(bytes), downloadSize(total))
                else uiText("%1\$s downloaded", downloadSize(bytes)),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (resumed) Text(uiText("Continuing your download"), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun downloadSize(bytes: Long): String = if (bytes >= 1_000_000_000L)
    uiText("%1\$.1f GB", bytes / 1_000_000_000.0) else uiText("%1\$d MB", bytes / 1_000_000L)

/** A download reaching 100% is insufficient: both installation and artifact validation must finish. */
@Composable
internal fun ModelInstalledMoment(state: com.halo.providers.litert.models.DownloadState, verified: Boolean) {
    val active = state is com.halo.providers.litert.models.DownloadState.Queued ||
        state is com.halo.providers.litert.models.DownloadState.Preparing ||
        state is com.halo.providers.litert.models.DownloadState.Downloading ||
        state is com.halo.providers.litert.models.DownloadState.Verifying
    var observedDownload by remember { mutableStateOf(active) }
    var celebration by remember { mutableStateOf(false) }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    LaunchedEffect(state, verified) {
        if (active) observedDownload = true
        if (state is com.halo.providers.litert.models.DownloadState.Failed ||
            state is com.halo.providers.litert.models.DownloadState.Paused ||
            state is com.halo.providers.litert.models.DownloadState.NotInstalled) observedDownload = false
        if (observedDownload && state is com.halo.providers.litert.models.DownloadState.Installed && verified) {
            observedDownload = false
            celebration = true
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
        }
    }
    LaunchedEffect(celebration) {
        if (celebration) { kotlinx.coroutines.delay(1800); celebration = false }
    }
    if (celebration) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        com.halo.app.ui.fx.HaloFxAnimation(com.halo.app.ui.fx.HaloFxMoment.BLOOM, Modifier.size(64.dp))
        Text(uiText("Model installed and checked"), style = MaterialTheme.typography.bodyMedium)
    }
}
