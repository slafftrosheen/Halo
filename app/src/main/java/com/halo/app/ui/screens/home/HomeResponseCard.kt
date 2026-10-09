package com.halo.app.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.halo.sdk.provider.CognitiveSessionState
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.glass.components.HaloMarkdownText

@Composable
internal fun HomeResponseCard(uiState: HomeUiState, responseText: String, isActiveSession: Boolean, onCopy: () -> Unit, onSave: () -> Unit) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
                tonalElevation = 1.dp,
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HaloIcon(
                                glyph = HaloGlyph.HALO,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Halo",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (
                            uiState.sessionState is CognitiveSessionState.Streaming ||
                            uiState.sessionState is CognitiveSessionState.Completed
                        ) {
                            Text(
                                text = uiState.activeProvider,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    if (uiState.presentedIdentity?.source == ResponsePresentationSource.INCOMPLETE) {
                        Text("Incomplete response · ${uiState.contextualText}", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                    }
                    /*
                     * MINING-P2-001: a committed structured answer renders as a bounded card, with the
                     * assistant's own text kept underneath as the fallback. Text-only hosts, speech and
                     * the notebook never see the component tree.
                     */
                    uiState.structuredPresentation?.let { presentation ->
                        com.halo.app.interaction.StructuredPresentationRenderer(
                            presentation = presentation,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    if (uiState.sessionState is CognitiveSessionState.Streaming) {
                        Text(responseText, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth())
                    } else {
                        HaloMarkdownText(markdown = responseText, textColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth())
                    }
                    if (!isActiveSession && uiState.sessionState !is CognitiveSessionState.Failed && uiState.presentedIdentity?.turnId != null) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { onCopy() }) { Text("Copy") }
                            TextButton(onClick = { onSave() }) {
                                // Explicit host action; saving is never automatic (Part N).
                                Text(
                                    if (uiState.structuredPresentation != null || uiState.researchResult) "Save to Notebook" else "Save",
                                )
                            }
                        }
                    }
                }
            }
}
