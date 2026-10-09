package com.halo.app.ui.screens.home

import com.halo.app.language.uiText

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.focusRequester

@Composable
internal fun HaloComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onVoice: () -> Unit,
    onStop: () -> Unit,
    enabled: Boolean,
    voiceEnabled: Boolean,
    voiceActive: Boolean,
    voiceContentDescription: String,
    showStop: Boolean,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onVoice,
                enabled = voiceEnabled && !showStop
            ) {
                Icon(
                    imageVector = if (voiceActive) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = voiceContentDescription,
                    tint = if (voiceActive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.secondary
                    }
                )
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .then(if(focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .padding(horizontal = 6.dp, vertical = 12.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.secondary),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            text = uiText("Ask Halo…"),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    inner()
                }
            )

            IconButton(
                onClick = if (showStop) onStop else onSubmit,
                enabled = showStop || (enabled && value.isNotBlank())
            ) {
                Icon(
                    imageVector = if (showStop) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                    contentDescription = if (showStop) "Stop" else "Send",
                    tint = if (showStop) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
