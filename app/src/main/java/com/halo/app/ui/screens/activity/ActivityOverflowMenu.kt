package com.halo.app.ui.screens.activity

import androidx.compose.runtime.Composable
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloOverflowAction
import com.halo.app.ui.components.HaloScreenOverflow

@Composable
internal fun ActivityOverflowMenu(onDetails: () -> Unit) {
    HaloScreenOverflow(listOf(HaloOverflowAction(uiText("Details"), onClick = onDetails)))
}
