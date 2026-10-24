package com.halo.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode

/** Layout/navigation checks deliberately omit the live Android speech host. */
@Composable
fun HostlessHaloTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontScale: Float = 0f,
    palette: HaloPalette = HaloPalette(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalInspectionMode provides true) {
        HaloTheme(darkTheme = darkTheme, fontScale = fontScale, palette = palette, content = content)
    }
}
