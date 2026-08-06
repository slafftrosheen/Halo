package com.halo.engines.interpretation.internal

import com.halo.engines.interpretation.api.InterpretationRule
import java.util.concurrent.CopyOnWriteArrayList

class RuleRegistry {
    private val hardRules = CopyOnWriteArrayList<InterpretationRule>()
    private val contextRules = CopyOnWriteArrayList<InterpretationRule>()
    private val semanticRules = CopyOnWriteArrayList<InterpretationRule>()

    fun registerHardRule(rule: InterpretationRule) {
        hardRules += rule
    }

    fun registerContextRule(rule: InterpretationRule) {
        contextRules += rule
    }

    fun registerSemanticRule(rule: InterpretationRule) {
        semanticRules += rule
    }

    fun unregister(rule: InterpretationRule) {
        hardRules -= rule
        contextRules -= rule
        semanticRules -= rule
    }

    val size: Int
        get() = hardRules.size + contextRules.size + semanticRules.size

    fun getHardRules(): List<InterpretationRule> = hardRules.sortedWithPriority()
    fun getContextRules(): List<InterpretationRule> = contextRules.sortedWithPriority()
    fun getSemanticRules(): List<InterpretationRule> = semanticRules.sortedWithPriority()

    private fun List<InterpretationRule>.sortedWithPriority(): List<InterpretationRule> =
        sortedWith(
            compareByDescending<InterpretationRule> { it.priority }
                .thenBy { it::class.java.name }
        )
}
