package com.halo.app.ui.glass.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.glass.material.HaloGlassMaterial
import com.halo.app.ui.glass.material.HaloGlassMaterials

/**
 * Legacy call sites retain their contract but now use Material's actual text-field
 * semantics: focused label, cursor and IME actions, error-free TalkBack traversal,
 * and a complete outlined focus indication. No separate glass text-input renderer.
 */
@Composable
fun HaloGlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
    material: HaloGlassMaterial = HaloGlassMaterials.Interactive
) {
    val labelSlot: @Composable (() -> Unit)? = label?.let { text -> { Text(text) } }
    val placeholderSlot: @Composable (() -> Unit)? = placeholder?.let { text -> { Text(text) } }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = labelSlot,
        placeholder = placeholderSlot,
        enabled = enabled,
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = HaloShapes.control,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = material.containerColor,
            unfocusedContainerColor = material.containerColor,
            disabledContainerColor = material.containerColor,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
        )
    )
}
