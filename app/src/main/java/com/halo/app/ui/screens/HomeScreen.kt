package com.halo.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.components.*
import com.halo.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HALO", fontWeight = FontWeight.Bold, letterSpacing = 2.sp) },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        EnginePulse("Listening", isActive = true)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HaloBackground,
                    titleContentColor = HaloTextPrimary
                )
            )
        },
        containerColor = HaloBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Good afternoon.", fontSize = 28.sp, color = HaloTextPrimary)
                Text("Halo Runtime Ready.", fontSize = 28.sp, color = HaloTextSecondary)
            }

            item {
                HaloDivider()
            }

            item {
                HaloSection(title = "ENGINES") {
                    val engines = listOf("Speech", "Reasoning", "Memory", "Planning", "Execution")
                    engines.forEach { engine ->
                        HaloEngineCard(name = engine, status = "READY", isReady = true)
                    }
                }
            }
            
            item {
                HaloSection(title = "PROVIDERS") {
                    HaloProviderCard(name = "Ollama", status = "Offline", isOnline = false)
                }
            }

            item {
                HaloDivider()
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(HaloIcons.Mic, contentDescription = null, tint = HaloTextSecondary, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Try saying:", color = HaloTextSecondary, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("\"Hello Halo\"", color = HaloTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}


