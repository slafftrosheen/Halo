package com.halo.app.ui.shell

/** Exact internal destinations; never accepts arbitrary route strings or object IDs. */
object HaloScreenCatalog {
    val screens = linkedMapOf(
        "home" to "Home", "mini_halo" to "Mini Halo", "activity" to "Activity", "notebook" to "Workspace",
        "studio" to "Studio", "devices" to "Devices", "settings" to "Settings", "study" to "Study",
        "ai_settings" to "Intelligence", "voice_settings" to "Voice",
        "android_assistant" to "Android assistant", "permissions" to "Permissions",
        "skills" to "Skills", "runtime_status" to "Runtime", "system_check" to "System check",
        "integrations" to "Integrations", "automation" to "Automation", "notification_settings" to "Notifications",
        "storage_backup" to "Storage and backup", "discovery" to "Pair a device",
        "settings/voice/input" to "Speech input", "settings/voice/output" to "Speech output",
        "settings/voice/speech-lab" to "Speech Lab", "settings/intelligence/providers" to "Providers",
        "model_browser" to "Models", "phone_control_sandbox" to "Phone control sandbox",
    )
    const val EXTRA_SCREEN = "com.halo.app.extra.SCREEN"
}
