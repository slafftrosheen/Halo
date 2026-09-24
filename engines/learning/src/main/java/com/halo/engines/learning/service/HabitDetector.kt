package com.halo.engines.learning.service

import com.halo.runtime.cognition.learning.Habit
import com.halo.runtime.cognition.learning.LearningContext

class HabitDetector {
    fun detect(context: LearningContext): Habit? {
        if (context.execution?.success != true) return null
        val properties = context.environment.properties
        val timeOfDay = properties["timeOfDay"]?.toString()?.takeIf { it.isNotBlank() }
        val location = properties["location"]?.toString()?.takeIf { it.isNotBlank() }
        val actions = when (val raw = properties["recentActions"]) {
            is Iterable<*> -> raw.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
            is Array<*> -> raw.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
            else -> emptyList()
        }.distinct().take(5)
        if (actions.isEmpty() || (timeOfDay == null && location == null)) return null
        val trigger = buildMap {
            timeOfDay?.let { put("timeOfDay", it) }
            location?.let { put("location", it) }
        }
        val evidence = context.memories.count { it.frequency > 1 }
        val confidence = (0.5f + evidence.coerceAtMost(5) * 0.08f).coerceAtMost(0.9f)
        return Habit(
            name = trigger.values.joinToString(" ") + " routine",
            triggerContext = trigger,
            predictedActions = actions,
            confidence = confidence
        )
    }
}
