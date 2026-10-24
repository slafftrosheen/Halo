package com.halo.app.ui.screens.devices

import androidx.compose.runtime.Composable
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloOverflowAction
import com.halo.app.ui.components.HaloScreenOverflow

@Composable
internal fun DevicesOverflowMenu(hasAudio: Boolean, onPhoneDetails: () -> Unit, onTestAudio: () -> Unit) {
    HaloScreenOverflow(listOfNotNull(
        HaloOverflowAction(uiText("This phone"), onClick = onPhoneDetails),
        if (hasAudio) HaloOverflowAction(uiText("Test audio output"), onClick = onTestAudio) else null,
    ))
}
