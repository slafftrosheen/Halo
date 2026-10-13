package com.halo.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.halo.app.ui.design.HaloSpacing
import com.halo.app.ui.workspace.HaloWorkspaceSection

/** Shared settings inset: plain text and controls stay inside the same card boundary. */
@Composable
fun HaloSettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
    content: @Composable () -> Unit,
) {
    HaloWorkspaceSection(title, modifier, trailingText) {
        Column(Modifier.padding(HaloSpacing.lg), verticalArrangement = Arrangement.spacedBy(HaloSpacing.sm)) {
            content()
        }
    }
}
