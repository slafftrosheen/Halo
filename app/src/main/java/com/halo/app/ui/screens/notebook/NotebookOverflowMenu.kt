package com.halo.app.ui.screens.notebook

import androidx.compose.runtime.Composable
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloOverflowAction
import com.halo.app.ui.components.HaloScreenOverflow

@Composable
internal fun NotebookOverflowMenu(workingSetSize: Int, onSavedChats: () -> Unit,
    onWorkingSet: () -> Unit, onRelatedNotes: () -> Unit) {
    HaloScreenOverflow(listOf(
        HaloOverflowAction(uiText("Saved chats"), onClick = onSavedChats),
        HaloOverflowAction(uiText("In chat (%1\$d)", workingSetSize), onClick = onWorkingSet),
        HaloOverflowAction(uiText("Find related notes"), onClick = onRelatedNotes),
    ))
}
