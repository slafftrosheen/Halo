package com.halo.app.ui.screens.home

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.sdk.provider.AiExecutionLocation

internal enum class HomeAnswerOrigin(val label: Int, val explanation: Int, val icon: HaloGlyph) {
    PHONE(R.string.halo_origin_phone, R.string.halo_origin_phone_info, HaloGlyph.LOCAL),
    ONLINE(R.string.halo_origin_online, R.string.halo_origin_online_info, HaloGlyph.CLOUD),
    LOCAL_NETWORK(R.string.halo_origin_lan, R.string.halo_origin_lan_info, HaloGlyph.WIFI),
    UNKNOWN(R.string.halo_origin_unknown, R.string.halo_origin_unknown_info, HaloGlyph.QUESTION),
}

internal fun homeAnswerOrigin(location: AiExecutionLocation?, walkedNativePath: Boolean = false) = when {
    location == AiExecutionLocation.REMOTE -> HomeAnswerOrigin.ONLINE
    location == AiExecutionLocation.LOCAL_NETWORK -> HomeAnswerOrigin.LOCAL_NETWORK
    location == AiExecutionLocation.ON_DEVICE || walkedNativePath -> HomeAnswerOrigin.PHONE
    else -> HomeAnswerOrigin.UNKNOWN
}

@Composable
internal fun HomeAnswerOriginChip(origin: HomeAnswerOrigin) {
    var showing by remember(origin) { mutableStateOf(false) }
    AssistChip({ showing = true }, label = { Text(stringResource(origin.label)) },
        modifier = Modifier.heightIn(min = 48.dp), leadingIcon = { HaloIcon(origin.icon, null, Modifier) })
    if (showing) AlertDialog(onDismissRequest = { showing = false },
        title = { Text(stringResource(origin.label)) },
        text = { Text(stringResource(origin.explanation)) },
        confirmButton = { TextButton({ showing = false }) { Text(stringResource(android.R.string.ok)) } })
}

@Composable
internal fun HomeShareReview(onDismiss: () -> Unit, onShare: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.halo_share_message_title)) },
        text = { Text(stringResource(R.string.halo_share_message_info)) },
        confirmButton = { TextButton(onShare) { Text(stringResource(R.string.halo_family_share)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(android.R.string.cancel)) } })
}
