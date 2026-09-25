package com.halo.runtime.cognition.tools

data class ToolExposureSet(
    val bootstrap: Set<ToolId> = emptySet(),
    val discovered: Set<ToolId> = emptySet(),
    val pinned: Set<ToolId> = emptySet(),
) {
    val effective: Set<ToolId>
        get() = bootstrap + discovered + pinned
}
