package com.halo.app.ui.glass.components

import com.halo.app.language.uiText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography

@Composable
fun HaloGlassHeader(
    title: String,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = HaloTypography.headlineMedium,
    onBack: (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp)
    ) {
        if (onBack != null) {
            HaloGlassIconButton(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = uiText("Back"),
                onClick = onBack,
                size = 48.dp,
                iconSize = 24.dp
            )
        }

        Text(
            text = title,
            style = titleStyle,
            color = HaloGlassColors.TextPrimary,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = if (onBack != null) 4.dp else 0.dp)
        )

        if (actions != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = actions
            )
        }
    }
}
