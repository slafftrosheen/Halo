package com.halo.app.ui.screens.settings

import androidx.compose.runtime.Composable
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloOverflowAction
import com.halo.app.ui.components.HaloScreenOverflow

@Composable
internal fun SettingsOverflowMenu(onSetup: () -> Unit, onAbout: () -> Unit) {
    HaloScreenOverflow(listOf(
        HaloOverflowAction(uiText("Guided setup"), onClick = onSetup),
        HaloOverflowAction(uiText("About halo"), onClick = onAbout),
    ))
}
