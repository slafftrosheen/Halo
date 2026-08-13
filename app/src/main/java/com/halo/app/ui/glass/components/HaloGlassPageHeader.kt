package com.halo.app.ui.glass.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography

@Composable
fun HaloGlassBackHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = HaloTypography.headlineMedium
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onBack)
            .padding(top = 8.dp, bottom = 8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.ChevronLeft,
            contentDescription = "Back",
            tint = HaloGlassColors.TextPrimary,
            modifier = Modifier.size(32.dp).padding(end = 8.dp)
        )
        Text(
            text = title,
            style = titleStyle,
            color = HaloGlassColors.TextPrimary
        )
    }
}
