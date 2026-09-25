package com.halo.app.interaction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.halo.runtime.cognition.interaction.TemporalInputValue

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
    }
}

/** Human-readable risk copy; never raw enum names. */
private fun riskCopy(risk: InteractionRiskLevel): String = when (risk) {
    InteractionRiskLevel.READ_ONLY -> "This only reads information."
    InteractionRiskLevel.REVERSIBLE -> "You can undo this afterwards."
    InteractionRiskLevel.EXTERNAL_SIDE_EFFECT -> "This reaches beyond Halo."
    InteractionRiskLevel.SENSITIVE -> "This touches personal data."
    InteractionRiskLevel.DESTRUCTIVE -> "This cannot be undone."
}

@Composable
private fun HaloConfirmationSurface(
    surface: InteractionSurface,
    content: InteractionContent.Confirmation,
    onRespond: (InteractionResponse) -> Unit,
    mode: SurfaceRenderMode,
) {
    if (mode == SurfaceRenderMode.COMPACT) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HaloSignalGlyph(kind = HaloSignalKind.ACTION, size = 20.dp)
                    Spacer(Modifier.padding(start = 8.dp))
                    Text(content.subject, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                }
                content.detail?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onRespond(InteractionResponse.Denied(surface.id)) }) {
                        Text(actionLabel(surface, InteractionActionSemantic.DENY) ?: "Deny")
                    }
                    Spacer(Modifier.padding(start = 8.dp))
                    TextButton(onClick = { onRespond(InteractionResponse.Confirmed(surface.id)) }) {
                        Text(actionLabel(surface, InteractionActionSemantic.CONFIRM) ?: "Approve")
                    }
                }
            }
        }
        return
    }

    AlertDialog(
        onDismissRequest = { onRespond(InteractionResponse.Dismissed(surface.id)) },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HaloSignalGlyph(kind = HaloSignalKind.ACTION, size = 24.dp)
                Spacer(Modifier.padding(start = 8.dp))
                Text(content.subject, fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column {
                content.detail?.let { Text(it) }
                content.risk?.let { risk ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = riskCopy(risk.level),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onRespond(InteractionResponse.Confirmed(surface.id)) }) {
                Text(actionLabel(surface, InteractionActionSemantic.CONFIRM) ?: "Approve")
            }
        },
        dismissButton = {
            TextButton(onClick = { onRespond(InteractionResponse.Denied(surface.id)) }) {
                Text(actionLabel(surface, InteractionActionSemantic.DENY) ?: "Deny")
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
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onRespond(InteractionResponse.Cancelled(surface.id)) }) { Text("Cancel") }
                    Spacer(Modifier.padding(start = 8.dp))
                    TextButton(
                        onClick = { onRespond(InteractionResponse.ChoiceSelected(surface.id, selected)) },
                        enabled = selected.isNotEmpty()
                    ) { Text(actionLabel(surface, InteractionActionSemantic.SUBMIT) ?: "Done") }
                }
            }
        }
        return
    }

    AlertDialog(
        onDismissRequest = { onRespond(InteractionResponse.Dismissed(surface.id)) },
        title = { Text(content.prompt, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
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
            TextButton(
                onClick = { onRespond(InteractionResponse.ChoiceSelected(surface.id, selected)) },
                enabled = selected.isNotEmpty(),
            ) { Text(actionLabel(surface, InteractionActionSemantic.SUBMIT) ?: "Done") }
        },
        dismissButton = {
            TextButton(onClick = { onRespond(InteractionResponse.Cancelled(surface.id)) }) { Text("Cancel") }
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
                Text(content.prompt, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = { content.placeholder?.let { Text(it) } },
                    singleLine = content.constraints.singleLine,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onRespond(InteractionResponse.Cancelled(surface.id)) }) { Text("Cancel") }
                    Spacer(Modifier.padding(start = 8.dp))
                    TextButton(
                        onClick = { onRespond(InteractionResponse.TextSubmitted(surface.id, value)) },
                        enabled = value.length >= content.constraints.minLength
                    ) { Text(actionLabel(surface, InteractionActionSemantic.SUBMIT) ?: "Submit") }
                }
            }
        }
        return
    }

    AlertDialog(
        onDismissRequest = { onRespond(InteractionResponse.Dismissed(surface.id)) },
        title = { Text(content.prompt, fontWeight = FontWeight.SemiBold) },
        text = {
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
        },
        confirmButton = {
            TextButton(
                onClick = { onRespond(InteractionResponse.TextSubmitted(surface.id, value)) },
                enabled = value.length >= content.constraints.minLength,
            ) { Text(actionLabel(surface, InteractionActionSemantic.SUBMIT) ?: "Submit") }
        },
        dismissButton = {
            TextButton(onClick = { onRespond(InteractionResponse.Cancelled(surface.id)) }) { Text("Cancel") }
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
                Text(content.prompt, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onRespond(InteractionResponse.Cancelled(surface.id)) }) { Text("Cancel") }
                    Spacer(Modifier.padding(start = 8.dp))
                    TextButton(
                        onClick = {
                            parseTemporal(value, content.mode)?.let {
                                onRespond(InteractionResponse.TemporalSubmitted(surface.id, it))
                            }
                        },
                        enabled = parseTemporal(value, content.mode) != null
                    ) { Text(actionLabel(surface, InteractionActionSemantic.SUBMIT) ?: "Set") }
                }
            }
        }
        return
    }

    AlertDialog(
        onDismissRequest = { onRespond(InteractionResponse.Dismissed(surface.id)) },
        title = { Text(content.prompt, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = {
                        Text(
                            when (content.mode) {
                                TemporalInputMode.DATE -> "Date (YYYY-MM-DD)"
                                TemporalInputMode.TIME -> "Time (HH:MM)"
                                TemporalInputMode.DATE_TIME -> "Date & time"
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "You can also answer by voice.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    parseTemporal(value, content.mode)?.let {
                        onRespond(InteractionResponse.TemporalSubmitted(surface.id, it))
                    }
                },
                enabled = parseTemporal(value, content.mode) != null,
            ) { Text(actionLabel(surface, InteractionActionSemantic.SUBMIT) ?: "Set") }
        },
        dismissButton = {
            TextButton(onClick = { onRespond(InteractionResponse.Cancelled(surface.id)) }) { Text("Cancel") }
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
                Text(content.prompt, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text("Open Halo app to select files.", style = MaterialTheme.typography.bodySmall)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onRespond(InteractionResponse.Dismissed(surface.id)) }) { Text("Later") }
                    Spacer(Modifier.padding(start = 8.dp))
                    TextButton(onClick = { picker() }) { Text("Choose") }
                }
            }
        }
        return
    }

    AlertDialog(
        onDismissRequest = { onRespond(InteractionResponse.Dismissed(surface.id)) },
        title = { Text(content.prompt, fontWeight = FontWeight.SemiBold) },
        text = {
            Text("Halo will open the system file picker.")
        },
        confirmButton = {
            TextButton(onClick = { picker() }) {
                Text(actionLabel(surface, InteractionActionSemantic.SUBMIT) ?: "Choose")
            }
        },
        dismissButton = {
            TextButton(onClick = { onRespond(InteractionResponse.Cancelled(surface.id)) }) { Text("Cancel") }
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
            Text(content.phase.name, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(4.dp))
            Text(content.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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
            Text(content.headline, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(content.detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun actionLabel(surface: InteractionSurface, semantic: InteractionActionSemantic): String? =
    surface.actions.firstOrNull { it.semantic == semantic }?.label

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
