package com.halo.app.ui.state

import com.halo.sdk.runtime.NativeResourceKind
import com.halo.sdk.runtime.NativeResourceState

/** Human-readable projection of the coordinator's heavyweight resource kinds. */
fun NativeResourceKind.label(): String = when (this) {
    NativeResourceKind.HEAVY_LLM -> "LLM"
    NativeResourceKind.HEAVY_ASR -> "ASR"
    NativeResourceKind.HEAVY_TTS -> "TTS"
}

/** Human-readable projection of the native residency lifecycle. */
fun NativeResourceState.label(): String = when (this) {
    NativeResourceState.RELEASED -> "Released"
    NativeResourceState.ACQUIRING -> "Acquiring"
    NativeResourceState.LOADING -> "Loading"
    NativeResourceState.RESIDENT -> "Resident"
    NativeResourceState.IN_USE -> "In use"
}
