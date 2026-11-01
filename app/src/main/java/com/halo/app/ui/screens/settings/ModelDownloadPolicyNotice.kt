package com.halo.app.ui.screens.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.ai.OnlineFeature
import com.halo.app.ai.PrivacyMode
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.language.uiText

/** Presentation of the one global policy. The transport still rechecks every request. */
@Composable
internal fun modelDownloadsAllowed(): Boolean {
    val mode by Graph.privacyModes.mode.collectAsStateWithLifecycle()
    val revision by Graph.privacyModes.profileChanges.collectAsStateWithLifecycle()
    val ready by Graph.kidsPreferences.loadedState.collectAsStateWithLifecycle()
    val kids by Graph.kidsPreferences.enabled.collectAsStateWithLifecycle()
    val childCloud by Graph.kidsPreferences.cloudAllowed.collectAsStateWithLifecycle()
    return remember(mode, revision, ready, kids, childCloud) {
        mode == PrivacyMode.CLOUD && Graph.privacyModes.sharingAcknowledged && ready && (!kids || childCloud) &&
            OnlineFeature.CONNECTED_SERVICES in Graph.privacyModes.profile(PrivacyMode.CLOUD).onlineFeatures
    }
}

@Composable
internal fun ModelDownloadPolicyNotice(allowed: Boolean = modelDownloadsAllowed()) {
    if (!allowed) Text(uiText(com.halo.sdk.provider.MODEL_DOWNLOAD_BLOCKED_MESSAGE),
        style = MaterialTheme.typography.bodyMedium)
}
