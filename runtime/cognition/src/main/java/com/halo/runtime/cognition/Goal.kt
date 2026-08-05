package com.halo.runtime.cognition

import kotlin.time.Duration

sealed interface Goal

data class AnswerQuestion(val question: String) : Goal
data class Navigate(val destination: String) : Goal
data class OpenApplication(val packageName: String) : Goal
data class ObserveEnvironment(val duration: Duration) : Goal
