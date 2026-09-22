package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.halo.app.data.notebook.WorkspaceLifecycle
import com.halo.app.ui.presence.HaloSignalGlyph
import com.halo.app.ui.presence.HaloSignalKind
import com.halo.app.ui.theme.HaloPastelBlue
import com.halo.app.ui.theme.HaloPastelMint
import com.halo.app.ui.theme.HaloPastelPink

@Composable
fun NoteEditorScreen(viewModel: NoteEditorViewModel, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.flush()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            viewModel.flush()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        val imageUri = androidx.compose.runtime.remember(state.body) {
            Regex("content://" + Regex.escape(context.packageName) + "\\.captures/[^)\\s]+").find(state.body)?.value
        }
        imageUri?.let { uri ->
            androidx.compose.material3.TextButton(onClick = {
                runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW)
                    .setDataAndType(android.net.Uri.parse(uri), "image/*")
                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
            }) { Text("Open captured image") }
        }
        EditorToolbar(
            lifecycle = state.lifecycle,
            onBack = {
                viewModel.flush()
                onBack()
            },
            onArchive = viewModel::archive,
            onTrash = viewModel::moveToTrash,
            onRestore = viewModel::restore,
            onRemember = viewModel::rememberNote
        )

        if (state.status == SaveStatus.MISSING) {
            MissingNoteState(onBack, Modifier.weight(1f))
        } else {
            Spacer(Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                HaloSignalGlyph(kind = HaloSignalKind.NOTE, size = 34.dp)
                Spacer(Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = noteTitle(state.body),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = when (state.lifecycle) {
                            WorkspaceLifecycle.ACTIVE -> "Note"
                            WorkspaceLifecycle.ARCHIVED -> "Archived note"
                            WorkspaceLifecycle.TRASHED -> "Trashed note"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Spacer(Modifier.height(8.dp))

            // Markdown & Action Toolbar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val helpers = listOf("#", "✓", "•", "1.", "Link")
                helpers.forEach { symbol ->
                    Surface(
                        onClick = { viewModel.insertMarkdownHelper(symbol) },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = symbol,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Surface(
                    onClick = {
                        viewModel.requestSummary()
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                            text = if (state.isTransforming) "Summarizing…" else "Summarize",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Transformation Preview Box if active
            state.transformPreview?.let { preview ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = "AI Transformation Preview",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = preview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = viewModel::applyPreviewReplace) {
                                Text("Replace")
                            }
                            TextButton(onClick = viewModel::applyPreviewInsertBelow) {
                                Text("Insert below")
                            }
                            TextButton(onClick = viewModel::discardPreview) {
                                Text("Discard")
                            }
                        }
                    }
                }
            }
            state.transformError?.let { error ->
                Text(error, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }

            BasicTextField(
                value = state.body,
                onValueChange = viewModel::updateBody,
                enabled = state.status != SaveStatus.LOADING &&
                    state.lifecycle != WorkspaceLifecycle.TRASHED,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                    lineHeight = 25.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.secondary),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                decorationBox = { innerTextField ->
                    if (state.body.isEmpty()) {
                        Text(
                            text = "# Untitled note\n\nStart writing…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            )

            EditorStatusBar(state)
        }
    }
}

@Composable
private fun EditorToolbar(
    lifecycle: WorkspaceLifecycle,
    onBack: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onRemember: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        Text(
            text = "NOTE",
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box {
            TextButton(onClick = { menuExpanded = true }) { Text("More") }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                if (lifecycle == WorkspaceLifecycle.ACTIVE) {
                    DropdownMenuItem(text = { Text("Remember") }, onClick = {
                        menuExpanded = false
                        onRemember()
                    })
                    DropdownMenuItem(text = { Text("Archive") }, onClick = {
                        menuExpanded = false
                        onArchive()
                    })
                } else {
                    DropdownMenuItem(text = { Text("Restore") }, onClick = {
                        menuExpanded = false
                        onRestore()
                    })
                }
                if (lifecycle != WorkspaceLifecycle.TRASHED) {
                    DropdownMenuItem(text = { Text("Move to trash") }, onClick = {
                        menuExpanded = false
                        onTrash()
                    })
                }
            }
        }
    }
}

@Composable
private fun EditorStatusBar(state: NoteEditorUiState) {
    val label = when (state.status) {
        SaveStatus.LOADING -> "Loading…"
        SaveStatus.SAVING -> "Saving…"
        SaveStatus.SAVED -> "Saved locally"
        SaveStatus.ERROR -> "Could not save"
        SaveStatus.MISSING -> "Unavailable"
    }
    val color = when (state.status) {
        SaveStatus.LOADING -> MaterialTheme.colorScheme.onSurfaceVariant
        SaveStatus.SAVING -> HaloPastelBlue
        SaveStatus.SAVED -> HaloPastelMint
        SaveStatus.ERROR, SaveStatus.MISSING -> MaterialTheme.colorScheme.error
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, RoundedCornerShape(3.dp))
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = color
            )
        }
        Text(
            text = "${wordCount(state.body)} words",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MissingNoteState(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            onClick = onBack,
            shape = RoundedCornerShape(18.dp),
            color = HaloPastelPink.copy(alpha = 0.16f)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Note unavailable",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "This note was deleted or is no longer available. Tap to return.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun noteTitle(body: String): String = body.lineSequence()
    .map(String::trim)
    .firstOrNull(String::isNotEmpty)
    ?.removePrefix("#")
    ?.trim()
    ?.take(80)
    .orEmpty()
    .ifEmpty { "Untitled note" }

private fun wordCount(body: String): Int = body
    .trim()
    .split(Regex("\\s+"))
    .count(String::isNotBlank)
