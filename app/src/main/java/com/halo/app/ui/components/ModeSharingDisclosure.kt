package com.halo.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.ai.PrivacyMode
import com.halo.app.ai.OnlineFeature
import com.halo.app.ai.enabledRemoteCategories
import com.halo.sdk.provider.RemoteDataCategory
import com.halo.app.ui.screens.settings.categoryResource
import com.halo.app.ui.screens.settings.onlineFeatureResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.di.ManualDependencyGraph as Graph
import kotlinx.coroutines.*

/** Both the shell and recovery CTA use this same first-enable sharing decision. */
@Composable internal fun rememberModeChangeAction(): (PrivacyMode) -> Unit {
    val scope = rememberCoroutineScope()
    var disclosure by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    fun activate(mode: PrivacyMode, acknowledge: Boolean = false) {
        if (busy) return
        scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    if (acknowledge) Graph.enableFullPowerAfterDisclosure() else Graph.setPrivacyMode(mode)
                }
                failed = false
                disclosure = false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    val revision by Graph.privacyModes.profileChanges.collectAsStateWithLifecycle()
    if (disclosure) {
        val profile = remember(revision) { Graph.privacyModes.profile(PrivacyMode.CLOUD) }
        PowerSharingDisclosure(Graph.remoteSharingDestinations(), profile.enabledRemoteCategories(), profile.onlineFeatures,
            busy, failed, onConfirm = { activate(PrivacyMode.CLOUD, true) }, onDismiss = { if (!busy) disclosure = false })
    }
    if (failed && !disclosure) Text(stringResource(R.string.mode47_activation_failed), color = MaterialTheme.colorScheme.error)
    return { mode ->
        if (!busy) {
            if (mode == PrivacyMode.CLOUD && !Graph.privacyModes.sharingAcknowledged) disclosure = true
            else activate(mode)
        }
    }
}


/** Stateless disclosure surface; only the calling mode owner can persist authorization. */
@Composable internal fun PowerSharingDisclosure(providers: List<String>, categories: Set<RemoteDataCategory>,
    features: Set<OnlineFeature>, busy: Boolean = false, failed: Boolean = false,
    onConfirm: () -> Unit, onDismiss: () -> Unit) {
    // Android's dialog window supplies its own density. Preserve Halo's enlarged
    // text preference inside every slot as well as the system accessibility scale.
    val inheritedDensity = androidx.compose.ui.platform.LocalDensity.current
    AlertDialog(onDismissRequest = onDismiss,
        title = { CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides inheritedDensity) {
            Text(stringResource(R.string.mode47_enable_power)) } },
        text = { CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides inheritedDensity) { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.mode47_sharing_explanation))
            Text(stringResource(R.string.mode47_configured_providers), style = MaterialTheme.typography.titleSmall)
            Text(providers.joinToString(", ").ifBlank { stringResource(R.string.mode47_no_provider) })
            Text(stringResource(R.string.mode47_features), style = MaterialTheme.typography.titleSmall)
            val featureLabels = features.map { stringResource(onlineFeatureResource(it)) }
            Text(featureLabels.joinToString().ifBlank { stringResource(R.string.mode47_none_enabled) })
            Text(stringResource(R.string.mode47_online_categories), style = MaterialTheme.typography.titleSmall)
            val categoryLabels = categories.map { stringResource(categoryResource(it)) }
            Text(categoryLabels.joinToString())
            Text(stringResource(R.string.mode47_other_routes))
            Text(stringResource(R.string.mode47_billing_explanation))
            Text(stringResource(R.string.mode47_boundaries))
            if (failed) Text(stringResource(R.string.mode47_activation_failed), color = MaterialTheme.colorScheme.error)
        } } },
        confirmButton = { CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides inheritedDensity) {
            TextButton(enabled = !busy, onClick = onConfirm) { Text(stringResource(R.string.mode47_enable_power)) } } },
        dismissButton = { CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides inheritedDensity) {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } } })
}
