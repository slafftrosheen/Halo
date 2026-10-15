package com.halo.providers.litert

import com.halo.providers.litert.models.LiteRtUserTuning

/** Transient UI result. Never contains the prompt and is never written to diagnostic exports. */
data class InstalledModelComparison(
    val modelId: String,
    val sha256: String,
    val atMs: Long,
    val tuning: LiteRtUserTuning,
    val output: String,
    val loadMs: Long,
    val firstTokenMs: Long,
    val estimatedTokensPerSecond: Double,
    val effectiveContext: Int?,
    val effectiveBackend: String,
)
