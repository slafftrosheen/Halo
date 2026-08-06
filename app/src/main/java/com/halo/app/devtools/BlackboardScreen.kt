package com.halo.app.devtools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.halo.app.ui.theme.HaloTextPrimary
import com.halo.app.ui.components.*
import com.halo.app.di.ManualDependencyGraph
import com.halo.icl.ontology.Goal
import com.halo.icl.ontology.Observation
import com.halo.icl.ontology.Thought

@Composable
fun BlackboardScreen() {
    val recentContext by ManualDependencyGraph.workingMemoryProcess.recentContext.collectAsState()
    
    val goals = recentContext.filterIsInstance<Goal>()
    val thoughts = recentContext.filterIsInstance<Thought>()
    val observations = recentContext.filterIsInstance<Observation>()
    
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HaloSection("GOALS (${goals.size})") {
                if (goals.isEmpty()) {
                    Text("No active goals", color = HaloTextPrimary, fontFamily = FontFamily.Monospace)
                } else {
                    goals.forEach { goal ->
                        GoalChip(goal = goal.desiredState, priority = "High")
                    }
                }
            }
        }
        item {
            HaloSection("THOUGHTS (${thoughts.size})") {
                if (thoughts.isEmpty()) {
                    Text("No recent thoughts", color = HaloTextPrimary, fontFamily = FontFamily.Monospace)
                } else {
                    thoughts.forEach { thought ->
                        MemoryNode(
                            title = thought.content,
                            type = "Inference",
                            confidence = thought.confidence.value,
                            timeAgo = "Just now"
                        )
                    }
                }
            }
        }
        item {
            HaloSection("OBSERVATIONS (${observations.size})") {
                if (observations.isEmpty()) {
                    Text("No recent observations", color = HaloTextPrimary, fontFamily = FontFamily.Monospace)
                } else {
                    observations.forEach { obs ->
                        MemoryNode(
                            title = obs.rawData.toString(),
                            type = obs.modality,
                            confidence = obs.confidence.value,
                            timeAgo = "Just now"
                        )
                    }
                }
            }
        }
    }
}
