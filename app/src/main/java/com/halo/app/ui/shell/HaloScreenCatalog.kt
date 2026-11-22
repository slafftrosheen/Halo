package com.halo.app.ui.shell

/** Exact internal destinations; never accepts arbitrary route strings or object IDs. */
object HaloScreenCatalog {
    val screens = linkedMapOf(
        "home" to "Home", "mini_halo" to "Mini Halo", "activity" to "Activity", "notebook" to "Library",
        "home/today" to "Today", "home/tools" to "Tools", "home/reads" to "Reads", "search" to "Search",
        "maps" to "halo maps", "calc" to "halo calc", "measure" to "halo measure",
        "screen/organizer" to "Organizer", "screen/clocks" to "World clocks", "screen/brief" to "Your day in Halo", "screen/sources" to "Sources",
        "workspace_handoff" to "Continue with this item", "studio" to "HaloStudio", "file_tools" to "HaloNotes", "canvases" to "HaloCanvas", "devices" to "Devices", "settings" to "Settings", "study" to "HaloStudy",
        "scan" to "halo scan", "planner" to "halo planner", "record" to "halo record", "sheets" to "halo sheets",
        "translate" to "Translate", "magnifier" to "Magnifier", "quick_note" to "Quick note", "routines" to "Routines",
        "focus" to "Focus", "bedside" to "Bedside", "album" to "halo album", "vault" to "halo vault",
        "family" to "Family space", "checkin" to "Morning check-in", "emergency" to "Emergency info",
        "private_chat" to "Private chat",
        "pause" to "Pause halo", "follow_me" to "Follow me",
        "on_move" to "On the move", "winddown" to "Wind down", "at_a_glance" to "halo at a glance",
        "ai_settings" to "Intelligence", "voice_settings" to "Voice",
        "android_assistant" to "Android assistant", "permissions" to "Permissions",
        "skills" to "Skills", "runtime_status" to "Runtime", "system_check" to "System check",
        "integrations" to "Integrations", "automation" to "Automation", "notification_settings" to "Notifications",
        "storage_backup" to "Storage and backup", "discovery" to "Pair a device",
        "settings/voice/input" to "Speech input", "settings/voice/output" to "Speech output",
        "settings/voice/speech-lab" to "Speech Lab", "settings/intelligence/providers" to "Providers",
        "notebook/conversations" to "Conversations", "notebook/memory" to "Memory", "memory_learning" to "Memory and learning",
        "notifications" to "Notifications", "advanced_settings" to "Advanced",
        "settings/intelligence/context" to "Context and privacy",
        "settings/intelligence/on-device" to "On this phone", "settings/intelligence/roles" to "Model roles",
        "settings/intelligence/usage" to "Usage", "settings/shared-requests" to "Shared requests",
        "model_browser" to "Models", "phone_control_sandbox" to "Phone control sandbox",
    )
    const val EXTRA_SCREEN = "com.halo.app.extra.SCREEN"
}
