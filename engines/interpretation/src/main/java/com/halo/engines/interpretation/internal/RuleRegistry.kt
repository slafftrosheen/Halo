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

    fun ordered(): List<InterpretationRule> =
        rules.sortedByDescending { it.priority }
}
