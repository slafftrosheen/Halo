package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.components.HaloDividerLine
import com.example.ui.components.MetricRow
import com.example.ui.components.SectionHeader

@Composable
fun SkillsScreen(onNavigateToMemory: () -> Unit = {}) {
    val skills = listOf(
        "Memory" to onNavigateToMemory,
        "Translate" to {},
        "Navigation" to {},
        "GitHub" to {},
        "Calendar" to {},
        "Notifications" to {},
        "Weather" to {},
        "Automation" to {}
    )

    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(vertical = 24.dp)
        ) {
            item {
                SectionHeader(title = "Skills", subtitle = "Toggle what the assistant can do.")
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            skills.forEachIndexed { index, (skill, onClick) ->
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onClick)
                            .padding(vertical = 12.dp)
                    ) {
                        MetricRow(label = skill, value = ">")
                    }
                    if (index < skills.size - 1) {
                        HaloDividerLine()
                    }
                }
            }
        }
    }
}
