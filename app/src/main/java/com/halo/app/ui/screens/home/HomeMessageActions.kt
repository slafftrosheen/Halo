package com.halo.app.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.language.uiText

/** The menu prepares host actions; it does not submit a prompt or authorize a tool. */
@Composable
internal fun HomeMessageActions(onCopy: () -> Unit, onShare: () -> Unit,
    onSave: (() -> Unit)? = null, onRead: (() -> Unit)? = null, onEdit: (() -> Unit)? = null,
    identityKey: Any? = null) {
    var expanded by remember(identityKey) { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.halo_ic_more), stringResource(R.string.home_message_actions), Modifier.size(20.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(uiText("Copy")) }, onClick = { expanded = false; onCopy() })
            DropdownMenuItem(text = { Text(uiText("Share")) }, onClick = { expanded = false; onShare() })
            onRead?.let { action -> DropdownMenuItem(text = { Text(uiText("Read aloud")) }, onClick = { expanded = false; action() }) }
            onSave?.let { action -> DropdownMenuItem(text = { Text(uiText("Save to Notebook")) }, onClick = { expanded = false; action() }) }
            onEdit?.let { action -> DropdownMenuItem(text = { Text(uiText("Edit as a new message")) }, onClick = { expanded = false; action() }) }
        }
    }
}
