package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Memory
import com.example.ui.components.HaloDividerLine
import com.example.ui.components.SectionHeader
import com.example.ui.theme.HaloAccent
import com.example.ui.theme.HaloTextPrimary
import com.example.ui.theme.HaloTextSecondary
import com.example.ui.viewmodel.MemoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryListScreen(viewModel: MemoryViewModel) {
    val memories by viewModel.allMemories.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var memoryToEdit by remember { mutableStateOf<Memory?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = HaloAccent,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Memory")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(vertical = 24.dp)
        ) {
            item {
                SectionHeader(title = "Memory", subtitle = "Everything visible. Editable. Deletable.")
                HaloDividerLine()
            }
            if (memories.isEmpty()) {
                item {
                    Text(
                        text = "Nothing here yet.\nThe assistant hasn't stored anything.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = HaloTextSecondary,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            } else {
                items(memories, key = { it.id }) { memory ->
                    MemoryCard(
                        memory = memory,
                        onEdit = { memoryToEdit = memory },
                        onDelete = { viewModel.deleteMemory(memory) }
                    )
                    HaloDividerLine()
                }
            }
        }
    }

    if (showAddDialog) {
        MemoryDialog(
            title = "Add Memory",
            initialText = "",
            onDismiss = { showAddDialog = false },
            onConfirm = { text ->
                viewModel.addMemory(text)
                showAddDialog = false
            }
        )
    }

    memoryToEdit?.let { memory ->
        MemoryDialog(
            title = "Edit Memory",
            initialText = memory.content,
            onDismiss = { memoryToEdit = null },
            onConfirm = { text ->
                viewModel.updateMemory(memory, text)
                memoryToEdit = null
            }
        )
    }
}

@Composable
fun MemoryCard(
    memory: Memory,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = memory.content,
            style = MaterialTheme.typography.bodyLarge,
            color = HaloTextPrimary,
            modifier = Modifier.weight(1f)
        )
        Row {
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit Memory",
                    tint = HaloTextSecondary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete Memory",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun MemoryDialog(
    title: String,
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = HaloTextPrimary) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = HaloTextPrimary,
                    unfocusedTextColor = HaloTextPrimary
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank()
            ) {
                Text("Save", color = HaloAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = HaloTextSecondary)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
