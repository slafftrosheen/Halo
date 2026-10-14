package com.halo.app.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Host-only action hierarchy, built on the existing Material theme. */
enum class HaloActionStyle { PRIMARY, SECONDARY, STOP, UTILITY }

@Composable
fun HaloActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: HaloActionStyle = HaloActionStyle.PRIMARY,
    content: @Composable RowScope.() -> Unit,
) {
    val target = modifier.heightIn(min = 48.dp)
    val padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    when (style) {
        HaloActionStyle.PRIMARY -> Button(onClick, target, enabled, shape = com.halo.app.ui.design.HaloShapes.control, contentPadding = padding,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer), content = content)
        HaloActionStyle.SECONDARY -> OutlinedButton(onClick, target, enabled, shape = com.halo.app.ui.design.HaloShapes.control, contentPadding = padding, content = content)
        HaloActionStyle.STOP -> OutlinedButton(onClick, target, enabled,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            shape = com.halo.app.ui.design.HaloShapes.control, contentPadding = padding, content = content)
        HaloActionStyle.UTILITY -> TextButton(onClick, target, enabled, shape = com.halo.app.ui.design.HaloShapes.control, contentPadding = padding, content = content)
    }
}
