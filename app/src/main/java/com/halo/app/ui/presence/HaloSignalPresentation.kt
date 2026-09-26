package com.halo.app.ui.presence

import androidx.compose.runtime.Immutable
import com.halo.app.ui.icons.HaloGlyph

enum class HaloSignalState {
    IDLE,
    LISTENING,
    CAPTURING,
    THINKING,
    ACTING,
    SPEAKING,
    WAITING,
    SUCCESS,
    WARNING,
    ERROR,
    OFFLINE,
    STOPPED,
}

@Immutable
data class HaloSignalPresentation(
    val state: HaloSignalState = HaloSignalState.IDLE,
    val glyph: HaloGlyph = HaloGlyph.HALO,
    val label: String = "Ready",
    val detail: String? = null,
    val progress: Float? = null,
    val activityLevel: Float = 0f,
    val reducedMotion: Boolean = false,
)
