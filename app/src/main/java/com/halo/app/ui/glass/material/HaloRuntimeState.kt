package com.halo.app.ui.glass.material

/** Presentation phases projected from observed runtime state. Renderers own no lifecycle. */
enum class HaloRuntimeState {
    Idle, Listening, Wakeword, Capturing, Recognizing, Reasoning,
    Planning, Executing, Speaking, Recovering, Stopped
}
