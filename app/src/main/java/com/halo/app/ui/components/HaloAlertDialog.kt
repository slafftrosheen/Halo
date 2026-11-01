package com.halo.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity

/** Android dialog windows must retain Halo's selected reading scale in every content slot. */
@Composable
internal fun HaloAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit = {},
) {
    val density = LocalDensity.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { CompositionLocalProvider(LocalDensity provides density, content = title) },
        text = { CompositionLocalProvider(LocalDensity provides density, content = text) },
        confirmButton = { CompositionLocalProvider(LocalDensity provides density, content = confirmButton) },
        dismissButton = { CompositionLocalProvider(LocalDensity provides density, content = dismissButton) },
    )
}
