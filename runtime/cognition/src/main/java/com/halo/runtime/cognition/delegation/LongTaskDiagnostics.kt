package com.halo.runtime.cognition.delegation

/** Process-local projections of durable facts; no objectives or source contents. */
data class DelegationDiagnostics(val childCount: Int = 0, val activeChildren: Int = 0,
    val maximumDepth: Int = 0, val lastRole: String? = null, val lastProvider: String? = null,
    val lastModel: String? = null, val lastStatus: String? = null, val evidenceCount: Int = 0)
object LongTaskDiagnostics {
    val delegation = kotlinx.coroutines.flow.MutableStateFlow(DelegationDiagnostics())
    val payloads = kotlinx.coroutines.flow.MutableStateFlow<com.halo.runtime.cognition.context.PayloadStoreStats?>(null)
}
