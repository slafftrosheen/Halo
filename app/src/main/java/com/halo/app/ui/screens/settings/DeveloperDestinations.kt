package com.halo.app.ui.screens.settings

/** Shared by navigation requests and restored destinations. No lab opens implicitly. */
internal fun isDeveloperDestination(route: String): Boolean = route.substringBefore('?') in setOf(
    "runtime_status", "assistant_diagnostics", "workload_diagnostics",
    "vision_lab", "local_audio_lab", "settings/voice/speech-lab",
    "phone_control_sandbox", "automation_sandbox", "developer_check",
)
