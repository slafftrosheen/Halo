package com.halo.app.devtools
import com.halo.sdk.runtime.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.theme.HaloTextPrimary
import com.halo.app.ui.theme.HaloTextSecondary
import com.halo.app.ui.components.*
import kotlinx.coroutines.launch
import com.halo.app.di.ManualDependencyGraph
import androidx.compose.runtime.rememberCoroutineScope
import android.util.Log

@Composable
fun SettingsScreen() {
    val coroutineScope = rememberCoroutineScope()
    
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ConsoleSection("Runtime Controls") {
                Button(onClick = {
                    coroutineScope.launch {
                        ManualDependencyGraph.microkernel.shutdown()
                        ManualDependencyGraph.microkernel.boot()
                    }
                }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("Restart Runtime", fontFamily = FontFamily.Monospace)
                }
                Button(onClick = {
                    coroutineScope.launch {
                        ManualDependencyGraph.workingMemoryProcess.clear()
                    }
                }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("Clear Memory", fontFamily = FontFamily.Monospace)
                }
                Button(onClick = {
                    Log.i("SettingsScreen", "Manual injection triggered")
                }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("Inject Event", fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
