@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.halo.app.interaction

import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloActionButton
import com.halo.app.ui.components.HaloActionStyle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.halo.app.ui.presence.HaloSignalGlyph
import com.halo.app.ui.presence.HaloSignalKind
import com.halo.runtime.cognition.agent.TemporalInputMode
import com.halo.runtime.cognition.interaction.InteractionAction
import com.halo.runtime.cognition.interaction.InteractionActionSemantic
import com.halo.runtime.cognition.interaction.InteractionContent
import com.halo.runtime.cognition.interaction.InteractionResponse
import com.halo.runtime.cognition.interaction.InteractionRiskLevel
import com.halo.runtime.cognition.interaction.InteractionSurface
import com.halo.runtime.cognition.interaction.InteractionSupplement
import com.halo.runtime.cognition.interaction.TemporalInputValue
import com.halo.runtime.cognition.interaction.CURRENT_PRESENTATION_SCHEMA_VERSION
import com.halo.app.ui.design.HaloSpacing

/**
 * Host presentation hint — Android UI layer concept, deliberately NOT part of
 * the Runtime contract. Same surface, different rendering density.
 */
enum class SurfaceRenderMode { FULL, COMPACT }

/**
 * RUNTIME-019 Compose renderer. Branches on [InteractionContent] only — never
 * on tool names, device names or protocol names. All host presentation
 * decisions (sheet vs dialog, ordering, styling) live here.
 */
@Composable
fun InteractionSurfaceRenderer(
    surface: InteractionSurface,
    onRespond: (InteractionResponse) -> Unit,
    modifier: Modifier = Modifier,
    mode: SurfaceRenderMode = SurfaceRenderMode.FULL,
) {
    when (val content = surface.content) {
        is InteractionContent.Confirmation -> HaloConfirmationSurface(
            surface = surface,
            content = content,
            onRespond = onRespond,
            mode = mode,
        )
        is InteractionContent.Choice -> HaloChoiceSurface(surface, content, onRespond, mode)
        is InteractionContent.TextInput -> HaloTextInputSurface(surface, content, onRespond, mode)
        is InteractionContent.TemporalInput -> HaloTemporalSurface(surface, content, onRespond, mode)
        is InteractionContent.FileSelection -> HaloFileSelectionSurface(surface, content, onRespond, mode)
        is InteractionContent.Progress -> HaloProgressSurface(surface, content, onRespond, mode)
        is InteractionContent.Result -> HaloResultSurface(surface, content, onRespond, mode)
        is InteractionContent.Error -> HaloErrorSurface(surface, content, onRespond, mode)
        // MINING-P2-001: bounded structured presentation. It never renders raw payload text and
        // never falls back to a serialized tree; unsupported schema shows the text fallback.
        is InteractionContent.Presentation -> HaloPresentationSurface(surface, content, mode)
    }
}

@Composable
private fun HaloPresentationSurface(
    surface: InteractionSurface,
    content: InteractionContent.Presentation,
    mode: SurfaceRenderMode,
) {
    val presentation = content.presentation
    val supported = presentation.schemaVersion <= CURRENT_PRESENTATION_SCHEMA_VERSION
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(HaloSpacing.xs),
    ) {
        surface.title?.let {
            Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        }
        if (supported) {
            StructuredPresentationRenderer(
                presentation = presentation,
                density = if (mode == SurfaceRenderMode.COMPACT) PresentationDensity.COMPACT else PresentationDensity.FULL,
            )
        }
        // The textual fallback is always available, including for a newer schema version.
        content.textFallback.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        surface.message?.takeIf { it.isNotBlank() && it != content.textFallback }?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Human-readable risk copy; never raw enum names. */
private fun riskCopy(risk: InteractionRiskLevel): String = when (risk) {
    InteractionRiskLevel.READ_ONLY -> "This only reads information."
    InteractionRiskLevel.REVERSIBLE -> "You can undo this afterwards."
    InteractionRiskLevel.EXTERNAL_SIDE_EFFECT -> "This reaches beyond halo."
    InteractionRiskLevel.SENSITIVE -> "This touches personal data."
    InteractionRiskLevel.DESTRUCTIVE -> "This cannot be undone."
}

@Composable
private fun InteractionSupplements(surface: InteractionSurface) {
    if (surface.supplements.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        surface.supplements.forEach { supplement ->
            val copy = when (supplement) {
                is InteractionSupplement.Fact -> "${supplement.label} · ${supplement.value}"
                is InteractionSupplement.Status ->
                    "${supplement.label} · ${supplement.state.name.lowercase().replace('_', ' ')}"
                is InteractionSupplement.Notice -> supplement.text
            }
            Text(copy, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HaloConfirmationSurface(
    surface: InteractionSurface,
    content: InteractionContent.Confirmation,
    onRespond: (InteractionResponse) -> Unit,
    mode: SurfaceRenderMode,
) {
    val attentionInk = if (MaterialTheme.colorScheme.background.luminance() < .5f)
        Color(0xFFFF9F6B) else Color(0xFFA84B12)
    val confirmLabel = confirmationActionLabel(surface, content)
    val denyLabel = actionLabel(surface, InteractionActionSemantic.DENY)
        ?.takeUnless { it in setOf("Deny", "Decline") }?.let { uiText(it) } ?: uiText("Don’t do it")
    if (mode == SurfaceRenderMode.COMPACT) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            shape = HaloShapes.card, border = BorderStroke(1.dp, attentionInk),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                InteractionSupplements(surface)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HaloIcon(HaloGlyph.APPROVAL, null, Modifier.size(24.dp), tint = attentionInk)
                    Spacer(Modifier.padding(start = 8.dp))
                    Text(content.subject, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                }
                content.detail?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                androidx.compose.foundation.layout.FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HaloActionButton(onClick = { onRespond(InteractionResponse.Denied(surface.id)) }, style = HaloActionStyle.STOP) {
                        Text(denyLabel)
                    }
                    Spacer(Modifier.padding(start = 8.dp))
                    HaloActionButton(onClick = { onRespond(InteractionResponse.Confirmed(surface.id)) }) {
                        Text(confirmLabel)
                    }
                }
            }
        }
        return
    }

    AlertDialog(
        onDismissRequest = { onRespond(InteractionResponse.Dismissed(surface.id)) },
        shape = HaloShapes.hero,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HaloIcon(HaloGlyph.APPROVAL, null, Modifier.size(32.dp), tint = attentionInk)
                Spacer(Modifier.padding(start = 8.dp))
                Text(content.subject, fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InteractionSupplements(surface)
                Text(uiText("Needs your OK"), style = MaterialTheme.typography.titleSmall, color = attentionInk)
                content.detail?.let { Text(it) }
                Text(uiText("Choose the action to continue, or decline it. Closing leaves it waiting in Activity."), style = MaterialTheme.typography.bodyMedium)
                content.risk?.let { risk ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = risk.summary?.takeUnless { it == content.detail } ?: uiText(riskCopy(risk.level)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            HaloActionButton(onClick = { onRespond(InteractionResponse.Confirmed(surface.id)) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            HaloActionButton(onClick = { onRespond(InteractionResponse.Denied(surface.id)) }, style = HaloActionStyle.STOP) {
                Text(denyLabel)
            }
        },
    )
}

@Composable
private fun HaloChoiceSurface(
    surface: InteractionSurface,
    content: InteractionContent.Choice,
    onRespond: (InteractionResponse) -> Unit,
    mode: SurfaceRenderMode,
) {
    var selected by remember(surface.id) { mutableStateOf(setOf<String>()) }

    if (mode == SurfaceRenderMode.COMPACT) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                InteractionSupplements(surface)
                Text(content.prompt, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                content.options.take(4).forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Checkbox(
                            checked = option.id in selected,
                            onCheckedChange = { checked ->
                                selected = if (content.allowMultiple) {
                                    if (checked) selected + option.id else selected - option.id
                                } else {
                                    setOf(option.id)
                                }
                            }
                        )
                        Text(option.label, style = MaterialTheme.typography.bodySmall)
                    }
                }
                androidx.compose.foundation.layout.FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HaloActionButton(onClick = { onRespond(InteractionResponse.Cancelled(surface.id)) }, style = HaloActionStyle.STOP) { Text(uiText("Cancel")) }
                    Spacer(Modifier.padding(start = 8.dp))
                    HaloActionButton(
                        onClick = { onRespond(InteractionResponse.ChoiceSelected(surface.id, selected)) },
                        enabled = selected.isNotEmpty()
                    ) { Text(uiText(actionLabel(surface, InteractionActionSemantic.SUBMIT) ?: "Done")) }
                }
            }
        }
        return
    }

    AlertDialog(
        onDismissRequest = { onRespond(InteractionResponse.Dismissed(surface.id)) },
        title = { Text(content.prompt, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InteractionSupplements(surface)
                content.options.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        Checkbox(
                            checked = option.id in selected,
                            onCheckedChange = { checked ->
                                selected = if (content.allowMultiple) {
                                    if (checked) selected + option.id else selected - option.id
                                } else {
                                    setOf(option.id)
                                }
                            },
                        )
                        Column {
                            Text(option.label)
                            option.description?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            HaloActionButton(
                onClick = { onRespond(InteractionResponse.ChoiceSelected(surface.id, selected)) },
                enabled = selected.isNotEmpty(),
            ) { Text(uiText(actionLabel(surface, InteractionActionSemantic.SUBMIT) ?: "Done")) }
        },
        dismissButton = {
            HaloActionButton(onClick = { onRespond(InteractionResponse.Cancelled(surface.id)) }, style = HaloActionStyle.STOP) { Text(uiText("Cancel")) }
        },
    )
}

@Composable
private fun HaloTextInputSurface(
    surface: InteractionSurface,
    content: InteractionContent.TextInput,
    onRespond: (InteractionResponse) -> Unit,
    mode: SurfaceRenderMode,
) {
    var value by remember(surface.id) { mutableStateOf(content.initialValue.orEmpty()) }

    if (mode == SurfaceRenderMode.COMPACT) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                InteractionSupplements(surface)
                Text(content.prompt, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = { content.placeholder?.let { Text(it) } },
                    singleLine = content.constraints.singleLine,
                    visualTransformation = if (content.constraints.sensitive) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.foundation.layout.FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HaloActionButton(onClick = { onRespond(InteractionResponse.Cancelled(surface.id)) }, style = HaloActionStyle.STOP) { Text(uiText("Cancel")) }
                    Spacer(Modifier.padding(start = 8.dp))
                    HaloActionButton(
                        onClick = { onRespond(InteractionResponse.TextSubmitted(surface.id, value)) },
                        enabled = value.length >= content.constraints.minLength
                    ) { Text(uiText(actionLabel(surface, InteractionActionSemantic.SUBMIT) ?: "Send")) }
                }
            }
        }
        return
    }

    AlertDialog(
        onDismissRequest = { onRespond(InteractionResponse.Dismissed(surface.id)) },
        title = { Text(content.prompt, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InteractionSupplements(surface)
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = { content.placeholder?.let { Text(it) } },
                    singleLine = content.constraints.singleLine,
                    visualTransformation = if (content.constraints.sensitive) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            HaloActionButton(
                onClick = { onRespond(InteractionResponse.TextSubmitted(surface.id, value)) },
                enabled = value.length >= content.constraints.minLength,
            ) { Text(uiText(actionLabel(surface, InteractionActionSemantic.SUBMIT) ?: "Send")) }
        },
        dismissButton = {
            HaloActionButton(onClick = { onRespond(InteractionResponse.Cancelled(surface.id)) }, style = HaloActionStyle.STOP) { Text(uiText("Cancel")) }
        },
    )
}

@Composable
private fun HaloTemporalSurface(
    surface: InteractionSurface,
    content: InteractionContent.TemporalInput,
    onRespond: (InteractionResponse) -> Unit,
    mode: SurfaceRenderMode,
) {
    var value by remember(surface.id) { mutableStateOf(content.initial?.let { initialText(it) }.orEmpty()) }

    if (mode == SurfaceRenderMode.COMPACT) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                InteractionSupplements(surface)
                Text(content.prompt, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.foundation.layout.FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HaloActionButton(onClick = { onRespond(InteractionResponse.Cancelled(surface.id)) }, style = HaloActionStyle.STOP) { Text(uiText("Cancel")) }
                    Spacer(Modifier.padding(start = 8.dp))
                    HaloActionButton(
                        onClick = {
                            parseTemporal(value, content.mode)?.let {
                                onRespond(InteractionResponse.TemporalSubmitted(surface.id, it))
                            }
                        },
                        enabled = parseTemporal(value, content.mode) != null
                    ) { Text(uiText(actionLabel(surface, InteractionActionSemantic.SUBMIT) ?: "Set")) }
                }
            }
        }
        return
    }

    AlertDialog(
        onDismissRequest = { onRespond(InteractionResponse.Dismissed(surface.id)) },
        title = { Text(content.prompt, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InteractionSupplements(surface)
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = {
                        Text(
                            uiText(when (content.mode) {
                                TemporalInputMode.DATE -> "Date (YYYY-MM-DD)"
                                TemporalInputMode.TIME -> "Time (HH:MM)"
                                TemporalInputMode.DATE_TIME -> "Date & time"
                            })
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            HaloActionButton(
                onClick = {
                    parseTemporal(value, content.mode)?.let {
                        onRespond(InteractionResponse.TemporalSubmitted(surface.id, it))
                    }
                },
                enabled = parseTemporal(value, content.mode) != null,
            ) { Text(uiText(actionLabel(surface, InteractionActionSemantic.SUBMIT) ?: "Set")) }
        },
        dismissButton = {
            HaloActionButton(onClick = { onRespond(InteractionResponse.Cancelled(surface.id)) }, style = HaloActionStyle.STOP) { Text(uiText("Cancel")) }
        },
    )
}

@Composable
private fun HaloFileSelectionSurface(
    surface: InteractionSurface,
    content: InteractionContent.FileSelection,
    onRespond: (InteractionResponse) -> Unit,
    mode: SurfaceRenderMode,
) {
    val picker = rememberAndroidFileSelectionLauncher(
        acceptedKinds = content.acceptedKinds,
        allowMultiple = content.allowMultiple,
        onSelected = { references ->
            onRespond(InteractionResponse.FilesSelected(surface.id, references))
        },
    )

    if (mode == SurfaceRenderMode.COMPACT) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                InteractionSupplements(surface)
                Text(content.prompt, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(uiText("Open Halo app to select files."), style = MaterialTheme.typography.bodySmall)
                androidx.compose.foundation.layout.FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onRespond(InteractionResponse.Dismissed(surface.id)) }) { Text(uiText("Later")) }
                    Spacer(Modifier.padding(start = 8.dp))
                    HaloActionButton(onClick = { picker() }) { Text(uiText("Choose")) }
                }
            }
        }
        return
    }

    AlertDialog(
        onDismissRequest = { onRespond(InteractionResponse.Dismissed(surface.id)) },
        title = { Text(content.prompt, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InteractionSupplements(surface)
                Text(uiText("Halo will open the system file picker."))
            }
        },
        confirmButton = {
            HaloActionButton(onClick = { picker() }) {
                Text(uiText(actionLabel(surface, InteractionActionSemantic.SUBMIT) ?: "Choose"))
            }
        },
        dismissButton = {
            HaloActionButton(onClick = { onRespond(InteractionResponse.Cancelled(surface.id)) }, style = HaloActionStyle.STOP) { Text(uiText("Cancel")) }
        },
    )
}

@Composable
private fun HaloProgressSurface(
    surface: InteractionSurface,
    content: InteractionContent.Progress,
    onRespond: (InteractionResponse) -> Unit,
    mode: SurfaceRenderMode,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            InteractionSupplements(surface)
            Text(uiText(when (content.phase) {
                com.halo.runtime.cognition.interaction.InteractionProgressPhase.WAITING -> "Waiting for you"
                else -> "Working on it…"
            }), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(uiText(content.label), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun HaloResultSurface(
    surface: InteractionSurface,
    content: InteractionContent.Result,
    onRespond: (InteractionResponse) -> Unit,
    mode: SurfaceRenderMode,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            InteractionSupplements(surface)
            Text(content.headline, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            content.detail?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun HaloErrorSurface(
    surface: InteractionSurface,
    content: InteractionContent.Error,
    onRespond: (InteractionResponse) -> Unit,
    mode: SurfaceRenderMode,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            InteractionSupplements(surface)
            Text(content.headline, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(content.detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun actionLabel(surface: InteractionSurface, semantic: InteractionActionSemantic): String? =
    surface.actions.firstOrNull { it.semantic == semantic }?.label?.let { label ->
        if (semantic == InteractionActionSemantic.DENY && label == "Deny") "Decline" else label
    }

private fun initialText(initial: TemporalInputValue): String = when (initial) {
    is TemporalInputValue.LocalDateValue -> initial.date.toString()
    is TemporalInputValue.LocalTimeValue -> initial.time.toString()
    is TemporalInputValue.DateTimeValue -> "${initial.date} ${initial.time}"
    is TemporalInputValue.InstantValue -> initial.instant.toString()
}

private fun parseTemporal(
    input: String,
    mode: TemporalInputMode
): com.halo.runtime.cognition.interaction.TemporalInputValue? {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return null
    return runCatching {
        when (mode) {
            TemporalInputMode.DATE ->
                com.halo.runtime.cognition.interaction.TemporalInputValue.LocalDateValue(java.time.LocalDate.parse(trimmed))
            TemporalInputMode.TIME ->
                com.halo.runtime.cognition.interaction.TemporalInputValue.LocalTimeValue(java.time.LocalTime.parse(trimmed))
            TemporalInputMode.DATE_TIME ->
                com.halo.runtime.cognition.interaction.TemporalInputValue.InstantValue(java.time.Instant.parse(trimmed))
        }
    }.getOrNull()
}


/** Keep an explicit host label; otherwise show the exact action the user is approving. */
@Composable
private fun confirmationActionLabel(surface: InteractionSurface, content: InteractionContent.Confirmation): String {
    val provided = actionLabel(surface, InteractionActionSemantic.CONFIRM)
    return when {
        !provided.isNullOrBlank() && provided !in setOf("Approve", "Allow") -> uiText(provided)
        content.subject.isNotBlank() -> content.subject
        else -> uiText("Do this action")
    }
}
