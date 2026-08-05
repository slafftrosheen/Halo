package com.halo.engines.interpretation.internal

import com.halo.engines.interpretation.api.InterpretationRule
import java.util.concurrent.CopyOnWriteArrayList

class RuleRegistry(
    initialRules: Collection<InterpretationRule> = emptyList()
) {
    private val rules = CopyOnWriteArrayList(initialRules)

    fun register(rule: InterpretationRule) {
        rules += rule
    }

    fun unregister(rule: InterpretationRule) {
        rules -= rule
    }

    val size: Int
        get() = rules.size

    fun ordered(): List<InterpretationRule> =
        rules.sortedWith(
            compareByDescending<InterpretationRule> { it.priority }
                .thenBy { it::class.java.name }
        )
}
