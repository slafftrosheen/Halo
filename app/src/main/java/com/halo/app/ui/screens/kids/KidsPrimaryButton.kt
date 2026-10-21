package com.halo.app.ui.screens.kids

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.design.HaloShapes

/** Package D: 56dp pill, 17sp label, accent fill rather than accent ink. */
@Composable
internal fun KidsPrimaryButton(onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Button(onClick, modifier.heightIn(min = 56.dp), enabled,
        shape = HaloShapes.pill,
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontSize = 17.sp,
            lineHeight = 23.sp, fontWeight = FontWeight.SemiBold)) { content() }
    }
}

/** Package D ghost action: warm surface fill with the same child-sized label and target. */
@Composable
internal fun KidsSecondaryButton(onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    FilledTonalButton(onClick, modifier.heightIn(min = 56.dp), enabled,
        shape = HaloShapes.pill,
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface)) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontSize = 17.sp,
            lineHeight = 23.sp, fontWeight = FontWeight.SemiBold)) { content() }
    }
}
