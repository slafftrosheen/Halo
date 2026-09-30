package com.halo.app.ui.glass.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.halo.app.ui.glass.material.HaloTypography

@Composable
fun HaloGlassBackHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = HaloTypography.headlineMedium,
) = HaloGlassHeader(title = title, onBack = onBack, modifier = modifier, titleStyle = titleStyle)
