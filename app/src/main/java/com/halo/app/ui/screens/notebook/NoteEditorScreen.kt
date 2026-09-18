package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.halo.app.data.notebook.WorkspaceLifecycle
import com.halo.app.ui.glass.components.HaloGlassButton
import com.halo.app.ui.glass.components.HaloGlassPage
import com.halo.app.ui.glass.components.HaloGlassTextField
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography

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

    HaloGlassPage(title = "Note", scrollable = false) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HaloGlassButton.Ghost(
                text = "Back",
                onClick = {
                    viewModel.flush()
                    onBack()
                }
            )
            Text(
                text = when (state.status) {
                    SaveStatus.LOADING -> "Loading…"
                    SaveStatus.SAVING -> "Saving…"
                    SaveStatus.SAVED -> "Saved"
                    SaveStatus.ERROR -> "Could not save"
                    SaveStatus.MISSING -> "Note unavailable"
                },
                style = HaloTypography.labelMedium,
                color = if (state.status == SaveStatus.ERROR) HaloGlassColors.Danger else HaloGlassColors.TextMuted,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        if (state.status == SaveStatus.MISSING) {
            Text(
                text = "This note was deleted or is no longer available.",
                style = HaloTypography.bodyLarge,
                color = HaloGlassColors.TextSecondary
            )
        } else {
            HaloGlassTextField(
                value = state.body,
                onValueChange = viewModel::updateBody,
                placeholder = "Start writing…",
                enabled = state.status != SaveStatus.LOADING && state.lifecycle != WorkspaceLifecycle.TRASHED,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state.lifecycle) {
                    WorkspaceLifecycle.ACTIVE -> {
                        HaloGlassButton.Ghost(text = "Archive", onClick = viewModel::archive)
                        HaloGlassButton.Danger(text = "Move to trash", onClick = viewModel::moveToTrash)
                    }
                    WorkspaceLifecycle.ARCHIVED -> {
                        HaloGlassButton.Ghost(text = "Restore", onClick = viewModel::restore)
                        HaloGlassButton.Danger(text = "Move to trash", onClick = viewModel::moveToTrash)
                    }
                    WorkspaceLifecycle.TRASHED ->
                        HaloGlassButton.Ghost(text = "Restore", onClick = viewModel::restore)
                }
            }
        }
    }
}
