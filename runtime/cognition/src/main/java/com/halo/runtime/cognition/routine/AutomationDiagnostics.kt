package com.halo.runtime.cognition.routine

import kotlinx.coroutines.flow.MutableStateFlow

data class AutomationDiagnosticSnapshot(val enabled: Int = 0, val scheduled: Int = 0, val eventDriven: Int = 0,
    val blocked: Int = 0, val recentOccurrences: List<String> = emptyList())
object AutomationDiagnostics { val snapshot = MutableStateFlow(AutomationDiagnosticSnapshot()) }
