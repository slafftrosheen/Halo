package com.halo.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.ui.components.SectionHeader
import com.halo.app.ui.components.TimelineEntry

@Composable
fun ActivityScreen() {
    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(vertical = 24.dp)
        ) {
            item {
                SectionHeader(title = "Activity", subtitle = "Recent actions and events.")
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            item {
                TimelineEntry(time = "09:22", action = "Translated Sign")
                TimelineEntry(time = "09:18", action = "Reminder Created")
                TimelineEntry(time = "09:01", action = "Meeting Summary")
                TimelineEntry(time = "Yesterday 18:45", action = "Navigation to Home started")
                TimelineEntry(time = "Yesterday 14:20", action = "Answered query about llama.cpp")
            }
        }
    }
}
