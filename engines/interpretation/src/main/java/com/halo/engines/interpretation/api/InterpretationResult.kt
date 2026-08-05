package com.halo.engines.interpretation.api

sealed interface InterpretationResult {
    val interpretation: Interpretation?

    data object NoMeaning : InterpretationResult {
        override val interpretation: Interpretation? = null
    }

    data class PartialMeaning(
        override val interpretation: Interpretation
    ) : InterpretationResult

    data class CompleteMeaning(
        override val interpretation: Interpretation
    ) : InterpretationResult
}
