package com.halo.app.ui.glass.components

import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.language.uiText
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography
import com.halo.sdk.provider.AiModel

@Composable
fun HaloGlassModelPickerDialog(
    models: List<AiModel>,
    selectedModelId: String?,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredModels = remember(models, searchQuery) {
        if (searchQuery.isBlank()) {
            models
        } else {
            models.filter {
                it.displayName.contains(searchQuery, ignoreCase = true) ||
                    it.id.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    HaloGlassDialog(
        onDismissRequest = onDismiss,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiText("Select Model"),
                        style = HaloTypography.titleLarge,
                        color = HaloGlassColors.TextPrimary
                    )
                    HaloGlassButton(
                        text = if (isRefreshing) "Loading..." else "Refresh",
                        onClick = onRefresh,
                        enabled = !isRefreshing
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                HaloTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = uiText("Search model..."),
                            style = HaloTypography.bodyMedium,
                            color = HaloGlassColors.TextMuted
                        )
                    },
                    textStyle = HaloTypography.bodyMedium,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = HaloGlassColors.TextPrimary,
                        unfocusedTextColor = HaloGlassColors.TextPrimary,
                        focusedBorderColor = HaloGlassColors.Accent,
                        unfocusedBorderColor = HaloGlassColors.Glass
                    ), kind = VoiceFieldKind.SEARCH)

                Spacer(modifier = Modifier.height(12.dp))

                if (filteredModels.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (models.isEmpty()) "No models available. Try refreshing." else "No matching models.",
                            style = HaloTypography.bodyMedium,
                            color = HaloGlassColors.TextMuted
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredModels) { model ->
                            val isSelected = model.id == selectedModelId

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) HaloGlassColors.GlassElevated else HaloGlassColors.Glass
                                    )
                                    .clickable { onSelect(model.id) }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = model.displayName,
                                        style = HaloTypography.bodyMedium,
                                        color = HaloGlassColors.TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = model.id,
                                        style = HaloTypography.labelSmall,
                                        color = HaloGlassColors.TextMuted
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (model.isFree) {
                                        HaloGlassChip(
                                            text = "Free",
                                            material = com.halo.app.ui.glass.material.HaloGlassMaterials.Interactive.copy(
                                                containerColor = HaloGlassColors.Success.copy(alpha = 0.15f),
                                                contentColor = HaloGlassColors.Success
                                            )
                                        )
                                    }
                                    if (isSelected) {
                                        HaloGlassChip(
                                            text = "Selected",
                                            material = com.halo.app.ui.glass.material.HaloGlassMaterials.Interactive.copy(
                                                containerColor = HaloGlassColors.Accent.copy(alpha = 0.15f),
                                                contentColor = HaloGlassColors.Accent
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}
