package com.halo.app.ui.shell

/** Exact internal destinations; never accepts arbitrary route strings or object IDs. */
object HaloScreenCatalog {
    val screens = linkedMapOf(
        "home" to "Home", "screen" to "Screen", "mini_halo" to "Mini Halo", "activity" to "Activity", "notebook" to "Workspace",
        "workspace_handoff" to "Continue with this item", "studio" to "Studio", "file_tools" to "File tools", "devices" to "Devices", "settings" to "Settings", "study" to "Study",
        "ai_settings" to "Intelligence", "voice_settings" to "Voice",
        "android_assistant" to "Android assistant", "permissions" to "Permissions",
        "skills" to "Skills", "runtime_status" to "Runtime", "system_check" to "System check",
        "integrations" to "Integrations", "automation" to "Automation", "notification_settings" to "Notifications",
        "storage_backup" to "Storage and backup", "discovery" to "Pair a device",
        "settings/voice/input" to "Speech input", "settings/voice/output" to "Speech output",
        "settings/voice/speech-lab" to "Speech Lab", "settings/intelligence/providers" to "Providers",
        "notebook/conversations" to "Conversations", "notebook/memory" to "Memory",
        "notifications" to "Notifications", "advanced_settings" to "Advanced",
        "settings/intelligence/context" to "Context and privacy",
        "settings/intelligence/on-device" to "On this phone", "settings/intelligence/roles" to "Model roles",
        "settings/intelligence/usage" to "Usage", "settings/shared-requests" to "Shared requests",
        "model_browser" to "Models", "phone_control_sandbox" to "Phone control sandbox",
    )
    const val EXTRA_SCREEN = "com.halo.app.extra.SCREEN"
}
