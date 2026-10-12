package com.halo.app.ui.screens.settings

import com.halo.app.language.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    val toggleLabel = uiText("Test toggle")
    val textLabel = uiText("Test text")
    val rowsLabel = uiText("Test rows")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HaloScreenHeader(uiText("Phone-control sandbox"), uiText("Local controls for later device acceptance"), navigation = onBack)
        Text("Expected state · Counter $counter · Toggle ${if (checked) "On" else "Off"} · Input length ${text.length} · Page ${if (destination) "Details" else "Controls"}")
        if (destination) {
            Text(uiText("Sandbox details"), style = MaterialTheme.typography.titleLarge)
            Button(onClick = { destination = false }) { Text(uiText("Return to controls")) }
        } else {
            Button(onClick = { counter++ }) { Text(uiText("Increment counter")) }
            Row {
                Text(uiText("Test toggle"))
                Switch(checked = checked, onCheckedChange = { checked = it }, modifier = Modifier.semantics { contentDescription = toggleLabel })
            }
            OutlinedTextField(value = text, onValueChange = { text = it.take(2000) }, label = { Text(uiText("Test text")) },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = textLabel })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { dialog = true }) { Text(uiText("Open test dialog")) }
                Button(onClick = { destination = true }) { Text(uiText("Open details")) }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().semantics { contentDescription = rowsLabel; collectionInfo = CollectionInfo(50, 1) }) {
                items(50) { index -> Text("Sandbox row ${index + 1}", Modifier.fillMaxWidth().padding(16.dp).semantics {
                    collectionItemInfo = CollectionItemInfo(index, 1, 0, 1)
                }) }
            }
        }
    }
    if (dialog) AlertDialog(onDismissRequest = { dialog = false }, title = { Text(uiText("Test dialog")) },
        text = { Text(uiText("This dialog changes no external state.")) }, confirmButton = {
            TextButton(onClick = { dialog = false }) { Text(uiText("Close test dialog")) }
        })
}
