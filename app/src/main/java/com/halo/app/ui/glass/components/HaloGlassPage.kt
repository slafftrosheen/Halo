package com.halo.app.ui.glass.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography

@Composable
fun HaloGlassPage(
    title: String,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    titleStyle: TextStyle = HaloTypography.displayMedium,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollModifier = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HaloGlassColors.Background)
            .then(scrollModifier)
            .padding(
                horizontal = 16.dp,
                vertical = 16.dp
            ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        content = {
            if (title.isNotEmpty()) {
                Text(
                    text = title,
                    style = titleStyle,
                    color = HaloGlassColors.TextPrimary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            content()
        }
    )
}

@Composable
fun HaloGlassLazyPage(
    title: String,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = HaloTypography.displayMedium,
    content: LazyListScope.() -> Unit
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(HaloGlassColors.Background),
        contentPadding = PaddingValues(
            horizontal = 16.dp,
            vertical = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            if (title.isNotEmpty()) {
                Text(
                    text = title,
                    style = titleStyle,
                    color = HaloGlassColors.TextPrimary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        content()
    }
}
