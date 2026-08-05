package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.components.HaloDividerLine
import com.example.ui.components.StateTile
import com.example.ui.theme.HaloTextPrimary
import com.example.ui.theme.HaloTextSecondary

@Composable
fun HomeScreen() {
    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(vertical = 24.dp)
        ) {
            item {
                Text(text = "09:42", style = MaterialTheme.typography.displayLarge, color = HaloTextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Connected", style = MaterialTheme.typography.titleLarge, color = HaloTextPrimary)
                Text(text = "HeyCyan G1", style = MaterialTheme.typography.bodyLarge, color = HaloTextSecondary)
                Text(text = "Battery 82%", style = MaterialTheme.typography.bodyLarge, color = HaloTextSecondary)
                HaloDividerLine()
            }
            
            item {
                Text(text = "Listening Ready", style = MaterialTheme.typography.titleLarge, color = HaloTextPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Local AI", style = MaterialTheme.typography.bodyLarge, color = HaloTextSecondary)
                Text(text = "llama.cpp", style = MaterialTheme.typography.bodyLarge, color = HaloTextSecondary)
                HaloDividerLine()
            }
            
            item {
                Text(text = "Today", style = MaterialTheme.typography.titleLarge, color = HaloTextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                StateTile(title = "Meeting", value = "10:30")
                Spacer(modifier = Modifier.height(8.dp))
                StateTile(title = "Reminders", value = "2 pending")
                HaloDividerLine()
            }
            
            item {
                Text(text = "Hold button to talk", style = MaterialTheme.typography.bodyLarge, color = HaloTextSecondary)
            }
        }
    }
}
