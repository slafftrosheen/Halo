package com.halo.app.ui.workspace

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun AdaptiveListDetailLayout(
    listPane: @Composable () -> Unit,
    detailPane: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isExpanded = configuration.screenWidthDp >= 600

    if (isExpanded && detailPane != null) {
        Row(modifier = modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(0.4f).fillMaxHeight()) {
                listPane()
            }
            Box(modifier = Modifier.weight(0.6f).fillMaxHeight()) {
                detailPane()
            }
        }
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            if (detailPane != null) {
                detailPane()
            } else {
                listPane()
            }
        }
    }
}
