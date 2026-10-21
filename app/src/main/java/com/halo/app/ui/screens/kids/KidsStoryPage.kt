package com.halo.app.ui.screens.kids

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.design.HaloShapes

/** K05 reading surface. All text and highlighting come from the actual saved passage. */
@Composable
internal fun KidsStoryPage(passage: AnnotatedString, large: Boolean, spacing: Boolean,
    onWordClick: (Int) -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = HaloShapes.card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        ClickableText(passage, modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp).padding(20.dp),
            style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface,
                fontSize = if (large) 26.sp else 22.sp,
                lineHeight = if (spacing) (if (large) 49.sp else 42.sp) else (if (large) 42.sp else 35.sp),
                letterSpacing = if (spacing) .78.sp else 0.sp), onClick = onWordClick)
    }
}
