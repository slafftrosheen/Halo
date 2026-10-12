package com.halo.app.ui.components

import com.halo.app.language.uiText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.theme.*

@Composable
fun EnginePulse(
    name: String,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PulseIndicator(
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            size = 8.dp,
            isPulsing = isActive
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(name, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun CognitiveFlow(stages: List<String>, activeStageIndex: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        stages.forEachIndexed { index, stage ->
            val isActive = index == activeStageIndex
            val isCompleted = index < activeStageIndex

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else if (isCompleted) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.background,
                            CircleShape
                        )
                        .border(
                            1.dp,
                            if (isActive || isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(HaloIcons.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    } else {
                        Text(
                            "${index + 1}",
                            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stage,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                )
            }
            if (index < stages.size - 1) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
    }
}

@Composable
fun MemoryNode(
    title: String,
    type: String,
    confidence: Float,
    timeAgo: String
) {
    HaloCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(HaloIcons.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            }
            Text(timeAgo, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HaloTag(type)
            HaloTag("Conf: ${(confidence * 100).toInt()}%")
        }
    }
}

@Composable
fun GoalChip(goal: String, priority: String) {
    val (color, bgColor) = when (priority.lowercase()) {
        "high" -> HaloWarning to HaloWarning.copy(alpha = 0.1f)
        "critical" -> HaloDanger to HaloDanger.copy(alpha = 0.1f)
        else -> HaloInformation to HaloInformation.copy(alpha = 0.1f)
    }
    
    Row(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(16.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(HaloIcons.Eye, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(goal, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlackboardGraph(nodes: List<String>) {
    // A simplified visual representation of interconnected blackboard concepts
    HaloCard(modifier = Modifier.fillMaxWidth()) {
        Text(uiText("Active Context"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            nodes.forEach { node ->
                HaloTag(node)
            }
        }
    }
}
