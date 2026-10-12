package com.halo.app.ui.screens.study

import com.halo.app.language.uiText

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
    onChange: (StudyLearnerLevel, StudySupportStyle, StudyKnowledgePolicy) -> Unit,
    showStyle: Boolean = true,
    showKnowledgePolicy: Boolean = true,
) {
    Text(uiText("How detailed should explanations be?"))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StudyLearnerLevel.entries.forEach { item -> FilterChip(level == item, { onChange(item, style, policy) }, { Text(uiText(item.friendlyLabel())) }) }
    }
    if (showStyle) {
        Text(uiText("How would you like to learn?"))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StudySupportStyle.entries.forEach { item ->
                FilterChip(style == item, { onChange(level, item, policy) }, {
                    Text(uiText(when (item) {
                        StudySupportStyle.GUIDED -> "Guide me"
                        StudySupportStyle.PRACTICE -> "Let me practise"
                        StudySupportStyle.EXPLAIN -> "Explain it"
                    }))
                })
            }
        }
    }
    if (showKnowledgePolicy) {
        Text(uiText("What can Halo use?"))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StudyKnowledgePolicy.entries.forEach { item ->
                FilterChip(policy == item, { onChange(level, style, item) }, {
                    Text(uiText(if (item == StudyKnowledgePolicy.SOURCE_ONLY) "Only my material" else "Material + general explanation"))
                })
            }
        }
    }
}

internal fun StudyLearnerLevel.friendlyLabel(): String = when(this) {
    StudyLearnerLevel.EARLY_PRIMARY -> "First school years"
    StudyLearnerLevel.PRIMARY -> "Primary school"
    StudyLearnerLevel.LOWER_SECONDARY -> "Middle school"
    StudyLearnerLevel.UPPER_SECONDARY -> "High school"
    StudyLearnerLevel.ADULT -> "College / adult"
}
