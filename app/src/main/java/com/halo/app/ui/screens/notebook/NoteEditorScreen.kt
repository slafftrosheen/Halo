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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
        EditorToolbar(
            lifecycle = state.lifecycle,
            onBack = {
                viewModel.flush()
                onBack()
            },
            onArchive = viewModel::archive,
            onTrash = viewModel::moveToTrash,
            onRestore = viewModel::restore
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
            Spacer(Modifier.height(16.dp))

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
    onRestore: () -> Unit
) {
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

        Row {
            when (lifecycle) {
                WorkspaceLifecycle.ACTIVE -> {
                    IconButton(onClick = onArchive) {
                        Icon(
                            Icons.Rounded.Archive,
                            contentDescription = "Archive note",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onTrash) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Move note to trash",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                WorkspaceLifecycle.ARCHIVED -> {
                    IconButton(onClick = onRestore) {
                        Icon(
                            Icons.Rounded.Restore,
                            contentDescription = "Restore note",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onTrash) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Move note to trash",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                WorkspaceLifecycle.TRASHED -> IconButton(onClick = onRestore) {
                    Icon(
                        Icons.Rounded.Restore,
                        contentDescription = "Restore note",
                        tint = MaterialTheme.colorScheme.primary
                    )
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
