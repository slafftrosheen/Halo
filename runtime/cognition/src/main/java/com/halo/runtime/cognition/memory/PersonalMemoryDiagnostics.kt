package com.halo.runtime.cognition.memory

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Aggregate metadata only. Never retain personal text, prompts, evidence bodies or tool arguments. */
data class PersonalMemoryDiagnosticSnapshot(val pendingByKind: Map<String,Int> = emptyMap(),
    val recentEpisodeCount: Int = 0, val lastReflectionStatus: String = "Not observed",val lastReflectionMode: String = "NONE")
object PersonalMemoryDiagnostics {
    val snapshot=MutableStateFlow(PersonalMemoryDiagnosticSnapshot())
    fun pending(kinds: List<String>) { snapshot.update { it.copy(pendingByKind=kinds.take(20).groupingBy { kind -> kind }.eachCount()) } }
    fun episodes(count: Int) { snapshot.update { it.copy(recentEpisodeCount=count.coerceIn(0,30)) } }
    fun reflection(status: ReflectionStatus,mode: ReflectionMode) { snapshot.update { it.copy(lastReflectionStatus=status.name,lastReflectionMode=mode.name) } }
}
