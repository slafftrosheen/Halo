package com.halo.app.ui.screens.study

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.sdk.runtime.study.*

@Composable
fun StudyLearningSetup(level: StudyLearnerLevel, style: StudySupportStyle, policy: StudyKnowledgePolicy,
    onChange: (StudyLearnerLevel, StudySupportStyle, StudyKnowledgePolicy) -> Unit) {
    Text("Learner level")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StudyLearnerLevel.entries.forEach { item -> FilterChip(level == item, { onChange(item, style, policy) }, { Text(item.name.lowercase().replace('_', ' ')) }) }
    }
    Text("Support style")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StudySupportStyle.entries.forEach { item -> FilterChip(style == item, { onChange(level, item, policy) }, { Text(item.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
    }
    Text("Knowledge")
    StudyKnowledgePolicy.entries.forEach { item -> FilterChip(policy == item, { onChange(level, style, item) }, {
        Text(if (item == StudyKnowledgePolicy.SOURCE_ONLY) "Only my material" else "Material + general explanation")
    }) }
}
