package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.halo.app.data.notebook.WorkspaceLifecycle
import com.halo.app.ui.glass.components.HaloGlassCard
import com.halo.app.ui.glass.components.HaloGlassPage
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography

private val EditorBlue = Color(0xFFB8DFFF)
private val EditorMint = Color(0xFF9BEBCF)
private val EditorPink = Color(0xFFF6B8D7)

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

    HaloGlassPage(title = "", scrollable = false) {
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
            MissingNoteState(onBack)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = noteTitle(state.body),
                    style = HaloTypography.headlineMedium,
                    color = HaloGlassColors.TextPrimary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditorChip("Markdown", EditorBlue)
                    if (state.lifecycle != WorkspaceLifecycle.ACTIVE) {
                        EditorChip(
                            state.lifecycle.name.lowercase().replaceFirstChar(Char::uppercase),
                            if (state.lifecycle == WorkspaceLifecycle.TRASHED) EditorPink else EditorMint
                        )
                    }
                }
            }

            HaloGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                BasicTextField(
                    value = state.body,
                    onValueChange = viewModel::updateBody,
                    enabled = state.status != SaveStatus.LOADING &&
                        state.lifecycle != WorkspaceLifecycle.TRASHED,
                    textStyle = HaloTypography.monoMedium.copy(color = HaloGlassColors.TextPrimary),
                    cursorBrush = SolidColor(EditorBlue),
                    modifier = Modifier.fillMaxSize(),
                    decorationBox = { innerTextField ->
                        if (state.body.isEmpty()) {
                            Text(
                                text = "# Untitled note\n\nStart writing…",
                                style = HaloTypography.monoMedium,
                                color = HaloGlassColors.TextMuted
                            )
                        }
                        innerTextField()
                    }
                )
            }

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
                tint = HaloGlassColors.TextPrimary
            )
        }
        Text(
            text = "NOTE",
            style = HaloTypography.labelMedium,
            color = HaloGlassColors.TextMuted
        )
        Row {
            when (lifecycle) {
                WorkspaceLifecycle.ACTIVE -> {
                    IconButton(onClick = onArchive) {
                        Icon(
                            Icons.Rounded.Archive,
                            contentDescription = "Archive note",
                            tint = HaloGlassColors.TextSecondary
                        )
                    }
                    IconButton(onClick = onTrash) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Move note to trash",
                            tint = HaloGlassColors.TextSecondary
                        )
                    }
                }
                WorkspaceLifecycle.ARCHIVED -> {
                    IconButton(onClick = onRestore) {
                        Icon(
                            Icons.Rounded.Restore,
                            contentDescription = "Restore note",
                            tint = EditorMint
                        )
                    }
                    IconButton(onClick = onTrash) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Move note to trash",
                            tint = HaloGlassColors.TextSecondary
                        )
                    }
                }
                WorkspaceLifecycle.TRASHED -> IconButton(onClick = onRestore) {
                    Icon(
                        Icons.Rounded.Restore,
                        contentDescription = "Restore note",
                        tint = EditorMint
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorChip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text = label, style = HaloTypography.labelSmall, color = color)
    }
}

@Composable
private fun EditorStatusBar(state: NoteEditorUiState) {
    val (label, color) = when (state.status) {
        SaveStatus.LOADING -> "Loading…" to HaloGlassColors.TextMuted
        SaveStatus.SAVING -> "Saving…" to EditorBlue
        SaveStatus.SAVED -> "Saved offline" to EditorMint
        SaveStatus.ERROR -> "Could not save" to HaloGlassColors.Danger
        SaveStatus.MISSING -> "Unavailable" to HaloGlassColors.Danger
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
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
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
            Text(label, style = HaloTypography.labelMedium, color = color)
        }
        Text(
            text = "${wordCount(state.body)} words",
            style = HaloTypography.labelMedium,
            color = HaloGlassColors.TextMuted
        )
    }
}

@Composable
private fun MissingNoteState(onBack: () -> Unit) {
    HaloGlassCard(modifier = Modifier.fillMaxWidth(), onClick = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Note unavailable",
                style = HaloTypography.titleMedium,
                color = HaloGlassColors.TextPrimary
            )
            Text(
                text = "This note was deleted or is no longer available. Tap to return to Notebook.",
                style = HaloTypography.bodyMedium,
                color = HaloGlassColors.TextSecondary
            )
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
