package com.halo.app.ui.screens.study

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.screens.kids.KidsPrimaryButton
import com.halo.app.ui.screens.kids.KidsSecondaryButton

/** S04/K03: display extracted wording as a draft until the learner confirms it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeworkWordingReview(text: String, uncertainty: String?, kids: Boolean,
    enabled: Boolean, onTextChange: (String) -> Unit, onConfirm: () -> Unit) {
    var editing by rememberSaveable { mutableStateOf(text.isBlank()) }
    val target = if (kids) 56.dp else 48.dp
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(uiText(if (kids) "Is this right?" else "Check what halo read"),
            style = MaterialTheme.typography.titleLarge)
        Surface(shape = HaloShapes.card, color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                if (editing) HaloTextField(text, onTextChange, kind = VoiceFieldKind.TEXT,
                    label = { Text(uiText("Exercise wording")) }, modifier = Modifier.fillMaxWidth(),
                    enabled = enabled, minLines = 3, maxChars = 4000)
                else Text(text, style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = if (kids) 20.sp else 18.sp, lineHeight = if (kids) 32.sp else 28.sp))
            }
        }
        uncertainty?.takeIf { it.isNotBlank() }?.let {
            val attention = MaterialTheme.colorScheme.tertiary
            Surface(shape = HaloShapes.control,
                color = lerp(MaterialTheme.colorScheme.surface, attention, .13f),
                border = BorderStroke(1.dp, attention.copy(alpha = .4f))) {
                Text(it, Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Text(uiText("Check every word and symbol against your material before starting."),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (kids) {
                KidsSecondaryButton(onClick = { editing = !editing }, enabled = enabled) {
                    Text(uiText(if (editing) "Preview" else "Fix it"))
                }
                KidsPrimaryButton(onConfirm, enabled = enabled && text.isNotBlank()) { Text(uiText("It’s right")) }
            } else {
                OutlinedButton(onClick = { editing = !editing }, enabled = enabled,
                    modifier = Modifier.heightIn(min = target)) { Text(uiText(if (editing) "Preview" else "Fix it")) }
                Button(onConfirm, enabled = enabled && text.isNotBlank(), modifier = Modifier.heightIn(min = target),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) { Text(uiText("Looks right")) }
            }
        }
    }
}
