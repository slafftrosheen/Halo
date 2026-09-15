package com.halo.app.ui.glass.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloGlassMaterial
import com.halo.app.ui.glass.material.HaloGlassMaterials
import com.halo.app.ui.glass.material.HaloGlassTokens
import com.halo.app.ui.glass.material.HaloTypography

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
    val textColor = if (enabled) HaloGlassColors.TextPrimary else HaloGlassColors.TextMuted
    val containerColor = if (enabled) {
        material.containerColor
    } else {
        material.containerColor.copy(alpha = 0.22f)
    }

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = HaloTypography.labelLarge,
                color = if (enabled) HaloGlassColors.TextSecondary else HaloGlassColors.TextMuted,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        HaloGlassSurface(
            material = material.copy(containerColor = containerColor),
            shape = HaloGlassTokens.mediumShape
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                maxLines = maxLines,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                visualTransformation = visualTransformation,
                textStyle = HaloTypography.bodyMedium.copy(color = textColor),
                cursorBrush = SolidColor(HaloGlassColors.Accent),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            style = HaloTypography.bodyMedium,
                            color = HaloGlassColors.TextMuted
                        )
                    }
                    innerTextField()
                }
            )

            if (trailingIcon != null) {
                trailingIcon()
            }
        }
    }
}
