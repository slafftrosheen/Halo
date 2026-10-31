package com.halo.app.ui.screens.kids

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/** Stop the current child action before closing input or returning to the existing choices. */
@Composable
internal fun KidsSystemBack(destination: KidsDestination, inputOpen: Boolean, onStop: () -> Unit,
    onCloseInput: () -> Unit, onHome: () -> Unit) {
    BackHandler(inputOpen || destination != KidsDestination.HOME) {
        onStop()
        if (inputOpen) onCloseInput() else onHome()
    }
}
