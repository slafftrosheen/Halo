package com.halo.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.unit.dp
import com.halo.app.ui.design.HaloScreenHeader

/** Owned, inert validation surface. Navigation never captures it or exposes it to a model. */
@Composable
fun PhoneControlSandboxScreen(onBack: () -> Unit) {
    var counter by rememberSaveable { mutableStateOf(0) }
    var checked by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    var dialog by rememberSaveable { mutableStateOf(false) }
    var destination by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HaloScreenHeader("Phone-control sandbox", "Local controls for later device acceptance", navigation = onBack)
        Text("Expected state · Counter $counter · Toggle ${if (checked) "On" else "Off"} · Input length ${text.length} · Page ${if (destination) "Details" else "Controls"}")
        if (destination) {
            Text("Sandbox details", style = MaterialTheme.typography.titleLarge)
            Button(onClick = { destination = false }) { Text("Return to controls") }
        } else {
            Button(onClick = { counter++ }) { Text("Increment counter") }
            Row {
                Text("Test toggle")
                Switch(checked = checked, onCheckedChange = { checked = it }, modifier = Modifier.semantics { contentDescription = "Test toggle" })
            }
            OutlinedTextField(value = text, onValueChange = { text = it.take(2000) }, label = { Text("Test text") },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Test text" })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { dialog = true }) { Text("Open test dialog") }
                Button(onClick = { destination = true }) { Text("Open details") }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().semantics { contentDescription = "Test rows"; collectionInfo = CollectionInfo(50, 1) }) {
                items(50) { index -> Text("Sandbox row ${index + 1}", Modifier.fillMaxWidth().padding(16.dp).semantics {
                    collectionItemInfo = CollectionItemInfo(index, 1, 0, 1)
                }) }
            }
        }
    }
    if (dialog) AlertDialog(onDismissRequest = { dialog = false }, title = { Text("Test dialog") },
        text = { Text("This dialog changes no external state.") }, confirmButton = {
            TextButton(onClick = { dialog = false }) { Text("Close test dialog") }
        })
}
