package com.halo.app.devtools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.ui.theme.HaloTextSecondary
import com.halo.runtime.cognition.blackboard.BlackboardQuery
import com.halo.runtime.cognition.blackboard.DecisionPayload
import com.halo.runtime.cognition.blackboard.GoalPayload
import com.halo.runtime.cognition.blackboard.Interpretation
import com.halo.runtime.cognition.blackboard.InterpretationPayload
import com.halo.runtime.cognition.blackboard.Knowledge
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun KnowledgeScreen() {
    val knowledge = rememberKnowledgeHistory()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(knowledge.asReversed()) { item ->
            ConsoleSection(item.payload.javaClass.simpleName) {
                ConsoleRow("Source", item.source.name)
                ConsoleRow("Confidence", item.confidence.overall.asPercent())
                ConsoleRow("Created", item.createdAt.toString())
            }
        }
    }
}

@Composable
fun InterpretationScreen() {
    val interpretations = rememberKnowledgeHistory()
        .mapNotNull { (it.payload as? InterpretationPayload)?.interpretation }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (interpretations.isEmpty()) {
            item {
                EmptyCognitiveState("No interpretations published yet.")
            }
        }
        items(interpretations.asReversed()) { interpretation ->
            InterpretationDetails(interpretation)
        }
    }
}

@Composable
fun ReasoningScreen() {
    val knowledge = rememberKnowledgeHistory()
    val latestGoal = knowledge
        .asReversed()
        .firstNotNullOfOrNull { (it.payload as? GoalPayload)?.goal }
    val latestInterpretation = knowledge
        .asReversed()
        .firstNotNullOfOrNull { (it.payload as? InterpretationPayload)?.interpretation }
    val latestDecision = knowledge
        .asReversed()
        .firstNotNullOfOrNull { (it.payload as? DecisionPayload)?.decision }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ConsoleSection("Reasoning") {
                ConsoleRow("Current Goal", latestGoal?.toString() ?: "None")
                ConsoleRow(
                    "Alternatives",
                    latestInterpretation
                        ?.candidates
                        ?.joinToString { "${it.name} (${it.confidence.asPercent()})" }
                        ?: "None"
                )
                ConsoleRow("Decision", latestDecision?.toString() ?: "None")
                ConsoleRow(
                    "Explanation",
                    if (latestDecision == null) {
                        "No decision knowledge published."
                    } else {
                        "Latest decision is sourced from the Blackboard."
                    }
                )
            }
        }
    }
}

@Composable
private fun InterpretationDetails(
    interpretation: Interpretation
) {
    ConsoleSection("Interpretation") {
        ConsoleRow(
            "Entities",
            interpretation.entities.joinToString {
                when (it) {
                    is com.halo.runtime.cognition.blackboard.PersonEntity -> "Person: ${it.name}"
                    is com.halo.runtime.cognition.blackboard.LocationEntity -> "Location: ${it.location}"
                    is com.halo.runtime.cognition.blackboard.TimeEntity -> "Time: ${it.expression}"
                    is com.halo.runtime.cognition.blackboard.ObjectEntity -> "Object: ${it.name}"
                    is com.halo.runtime.cognition.blackboard.ApplicationEntity -> "Application: ${it.name}"
                    is com.halo.runtime.cognition.blackboard.DeviceEntity -> "Device: ${it.name}"
                    else -> it.toString()
                }
            }
        )
        ConsoleRow(
            "Candidates",
            interpretation.candidates.joinToString {
                "${it.name} (${it.confidence.asPercent()})"
            }
        )
        ConsoleRow(
            "Slots",
            interpretation.slots.joinToString {
                "${it.key}: ${it.value}"
            }
        )
        ConsoleRow("Confidence", interpretation.confidence.overall.asPercent())
    }
}

@Composable
private fun EmptyCognitiveState(message: String) {
    Column {
        Text(
            text = message,
            color = HaloTextSecondary,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun rememberKnowledgeHistory(): List<Knowledge> {
    val blackboard = ManualDependencyGraph.blackboard
    var knowledge by remember { mutableStateOf(emptyList<Knowledge>()) }

    LaunchedEffect(blackboard) {
        val updates = Channel<Knowledge>(Channel.BUFFERED)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            blackboard.stream().collect(updates::send)
        }

        try {
            knowledge = blackboard.query(BlackboardQuery())
            for (next in updates) {
                if (knowledge.none { it.id == next.id }) {
                    knowledge = (knowledge + next).takeLast(512)
                }
            }
        } finally {
            collector.cancel()
            updates.close()
        }
    }

    return knowledge
}

private fun Float.asPercent(): String = "%.1f%%".format(this * 100f)
