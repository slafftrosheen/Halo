package com.halo.app.di.tools

import com.halo.runtime.cognition.tools.CapabilityId
import com.halo.runtime.cognition.tools.ConfirmationPolicy
import com.halo.runtime.cognition.tools.ReplaySafety
import com.halo.runtime.cognition.tools.ToolCategory
import com.halo.runtime.cognition.tools.ToolDescriptor
import com.halo.runtime.cognition.tools.ToolId
import com.halo.runtime.cognition.tools.ToolPresentation
import com.halo.runtime.cognition.tools.ToolRequirement
import com.halo.runtime.cognition.tools.ToolRiskClass
import com.halo.runtime.cognition.tools.ToolSecurityPolicy
import com.halo.runtime.cognition.tools.ToolVisibility
import com.halo.sdk.provider.ToolDefinition

/**
 * RUNTIME-017 host adapter: declares portable Runtime descriptors for every
 * production tool executor registered in the phone composition root.
 *
 * These descriptors are the safety/execution truth for each tool. Android remains
 * the executor; Runtime policy derives exclusively from this metadata, never from
 * tool-name string patterns.
 */
object HaloToolDescriptors {

    // ---- helpers -----------------------------------------------------------

    private fun descriptor(
        id: String,
        definition: ToolDefinition,
        category: ToolCategory,
        security: ToolSecurityPolicy,
        keywords: Set<String> = emptySet(),
        visibility: ToolVisibility = ToolVisibility.DISCOVERABLE,
        requirements: Set<ToolRequirement> = emptySet(),
        dependencies: Set<ToolId> = emptySet(),
        execution: com.halo.runtime.cognition.tools.ToolExecutionPolicy =
            com.halo.runtime.cognition.tools.ToolExecutionPolicy(),
        presentation: ToolPresentation? = null,
    ) = ToolDescriptor(
        id = ToolId(id),
        definition = definition,
        category = category,
        keywords = keywords,
        visibility = visibility,
        dependencies = dependencies,
        requirements = requirements,
        security = security,
        execution = execution,
        presentation = presentation,
    )

    private fun readOnly(id: String) = ToolSecurityPolicy.READ_ONLY.copy(
        confirmation = ConfirmationPolicy.NONE,
    )

    private fun reversible(replay: ReplaySafety = ReplaySafety.SAFE) = ToolSecurityPolicy(
        risk = ToolRiskClass.REVERSIBLE,
        confirmation = ConfirmationPolicy.USER_POLICY,
        replaySafety = replay,
    )

    private fun external(replay: ReplaySafety = ReplaySafety.NEVER_REPLAY) = ToolSecurityPolicy(
        risk = ToolRiskClass.EXTERNAL_SIDE_EFFECT,
        confirmation = ConfirmationPolicy.USER_POLICY,
        replaySafety = replay,
    )

    // ---- runtime / workspace ----------------------------------------------

    fun currentTime(definition: ToolDefinition) = descriptor(
        id = "halo_current_time",
        definition = definition,
        category = ToolCategory.INFORMATION,
        security = readOnly("halo_current_time"),
        keywords = setOf("time", "clock", "date", "timezone"),
    )

    fun storageStatus(definition: ToolDefinition) = descriptor(
        id = "halo_storage_status",
        definition = definition,
        category = ToolCategory.SYSTEM,
        security = readOnly("halo_storage_status"),
        keywords = setOf("storage", "disk", "backup"),
    )

    fun notificationDigest(definition: ToolDefinition) = descriptor(
        id = "halo_notification_digest",
        definition = definition,
        category = ToolCategory.INFORMATION,
        security = readOnly("halo_notification_digest"),
        keywords = setOf("notification", "digest", "summary"),
        requirements = setOf(ToolRequirement.Capability(CapabilityId("host.notification.read"))),
    )

    // ---- workspace / notebook / memory ------------------------------------

    fun workspace(definition: ToolDefinition) = when (definition.name) {
        "halo_get_workspace_object" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            readOnly(definition.name), keywords = setOf("note", "workspace", "read"))
        "halo_create_note" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            reversible(ReplaySafety.IDEMPOTENT_WITH_KEY), keywords = setOf("note", "create", "write"),
            presentation = ToolPresentation(title = "Create a note", actionVerb = "Create note"))
        "halo_search_notebook" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            readOnly(definition.name), keywords = setOf("note", "search", "find"))
        "halo_save_screen_to_notebook" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            reversible(ReplaySafety.IDEMPOTENT_WITH_KEY), keywords = setOf("screen", "capture", "note"))
        "halo_study_with_halo" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            reversible(ReplaySafety.IDEMPOTENT_WITH_KEY),
            keywords = setOf("study", "tutor", "learn"),
            presentation = ToolPresentation(title = "Start a study session", actionVerb = "Start study"))
        "halo_get_study_session" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            readOnly(definition.name), keywords = setOf("study", "session", "progress", "mastery"))
        "halo_add_to_working_set" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            reversible(), keywords = setOf("working", "set", "context"))
        "halo_remove_from_working_set" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            reversible(), keywords = setOf("working", "set", "context"))
        else -> descriptor(definition.name, definition, ToolCategory.WORKSPACE, external())
    }

    fun file(definition: ToolDefinition) = when (definition.name) {
        "halo_search_files" -> descriptor(definition.name, definition, ToolCategory.FILES,
            readOnly(definition.name), keywords = setOf("file", "search", "document"))
        "halo_get_file_metadata" -> descriptor(definition.name, definition, ToolCategory.FILES,
            readOnly(definition.name), keywords = setOf("file", "metadata"))
        "halo_read_text_file" -> descriptor(definition.name, definition, ToolCategory.FILES,
            readOnly(definition.name), keywords = setOf("file", "read", "text"))
        else -> descriptor(definition.name, definition, ToolCategory.FILES, external())
    }

    fun memory(definition: ToolDefinition) = when (definition.name) {
        "halo_search_memory" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            readOnly(definition.name), keywords = setOf("memory", "recall", "fact"))
        "halo_propose_memory" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            reversible(ReplaySafety.IDEMPOTENT_WITH_KEY), keywords = setOf("memory", "save", "remember"),
            presentation = ToolPresentation(title = "Save a memory", actionVerb = "Remember",
                targetArgument = "content"))
        else -> descriptor(definition.name, definition, ToolCategory.WORKSPACE, external())
    }

    // ---- host control (Android adapter) ------------------------------------

    fun application(definition: ToolDefinition) = when (definition.name) {
        "halo_list_apps" -> descriptor(definition.name, definition, ToolCategory.SYSTEM,
            readOnly(definition.name), keywords = setOf("app", "list", "installed"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.application.enumerate"))))
        "halo_open_app" -> descriptor(definition.name, definition, ToolCategory.HOST_CONTROL,
            external(ReplaySafety.RECONCILE_BEFORE_RETRY), keywords = setOf("app", "open", "launch"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.application.launch"))),
            presentation = ToolPresentation(title = "Open an app", actionVerb = "Open app"))
        else -> descriptor(definition.name, definition, ToolCategory.HOST_CONTROL, external())
    }

    fun device(definition: ToolDefinition) = when (definition.name) {
        "halo_volume" -> descriptor(definition.name, definition, ToolCategory.DEVICE_CONTROL,
            reversible(), keywords = setOf("volume", "sound", "louder", "quieter"))
        "halo_flashlight" -> descriptor(definition.name, definition, ToolCategory.DEVICE_CONTROL,
            reversible(), keywords = setOf("flashlight", "torch", "light"))
        else -> descriptor(definition.name, definition, ToolCategory.DEVICE_CONTROL, external())
    }

    fun alarm(definition: ToolDefinition) = when (definition.name) {
        "halo_set_alarm" -> descriptor(definition.name, definition, ToolCategory.AUTOMATION,
            external(ReplaySafety.RECONCILE_BEFORE_RETRY), keywords = setOf("alarm", "wake", "set"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.alarm.set"))))
        else -> descriptor(definition.name, definition, ToolCategory.AUTOMATION, external())
    }

    fun communication(definition: ToolDefinition) = when (definition.name) {
        "halo_find_contact" -> descriptor(definition.name, definition, ToolCategory.COMMUNICATION,
            readOnly(definition.name), keywords = setOf("contact", "phone", "find"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.contacts.read"))))
        "halo_dial_contact" -> descriptor(definition.name, definition, ToolCategory.COMMUNICATION,
            external(ReplaySafety.RECONCILE_BEFORE_RETRY), keywords = setOf("call", "dial", "phone"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.dialer.open"))),
            presentation = ToolPresentation(title = "Start a call", actionVerb = "Call", targetArgument = "name",
                sensitiveArguments = setOf("phone", "number")))
        else -> descriptor(definition.name, definition, ToolCategory.COMMUNICATION, external())
    }

    fun media(definition: ToolDefinition) = when (definition.name) {
        "halo_media_status" -> descriptor(definition.name, definition, ToolCategory.MEDIA,
            readOnly(definition.name), keywords = setOf("music", "playing", "media"))
        "halo_media_control" -> descriptor(definition.name, definition, ToolCategory.MEDIA,
            reversible(ReplaySafety.IDEMPOTENT_WITH_KEY), keywords = setOf("play", "pause", "next", "previous"))
        else -> descriptor(definition.name, definition, ToolCategory.MEDIA, external())
    }

    fun navigation(definition: ToolDefinition) = when (definition.name) {
        "halo_navigation" -> descriptor(definition.name, definition, ToolCategory.NAVIGATION,
            external(ReplaySafety.RECONCILE_BEFORE_RETRY), keywords = setOf("navigate", "directions", "maps"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.navigation.open"))),
            presentation = ToolPresentation(title = "Start navigation", actionVerb = "Navigate to"))
        "halo_browser" -> descriptor(definition.name, definition, ToolCategory.NAVIGATION,
            external(ReplaySafety.RECONCILE_BEFORE_RETRY), keywords = setOf("web", "browser", "open", "url"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.web.open"))),
            presentation = ToolPresentation(title = "Open a web page", actionVerb = "Open"))
        "halo_search" -> descriptor(definition.name, definition, ToolCategory.INFORMATION,
            external(ReplaySafety.RECONCILE_BEFORE_RETRY), keywords = setOf("search", "web", "internet"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.web.search"))))
        else -> descriptor(definition.name, definition, ToolCategory.NAVIGATION, external())
    }

    fun system(definition: ToolDefinition) = when (definition.name) {
        "halo_open_settings" -> descriptor(definition.name, definition, ToolCategory.HOST_CONTROL,
            external(ReplaySafety.RECONCILE_BEFORE_RETRY), keywords = setOf("settings", "wifi", "bluetooth"))
        "halo_share_text" -> descriptor(definition.name, definition, ToolCategory.COMMUNICATION,
            external(ReplaySafety.RECONCILE_BEFORE_RETRY), keywords = setOf("share", "send", "text"),
            presentation = ToolPresentation(title = "Share text", actionVerb = "Share", targetArgument = "text"))
        else -> descriptor(definition.name, definition, ToolCategory.HOST_CONTROL, external())
    }

    /**
     * AGENTIC-001: loading a skill is guidance and exposure planning, not a side
     * effect. It must be callable without an approval prompt or the agent loop
     * deadlocks on its own bootstrap step.
     */
    fun useSkill(definition: ToolDefinition) = descriptor(
        id = definition.id,
        definition = definition,
        category = ToolCategory.INFORMATION,
        security = readOnly(definition.id),
        keywords = setOf("skill", "guidance", "enable", "instructions"),
        visibility = ToolVisibility.BOOTSTRAP,
    )

    fun accessibility(definition: ToolDefinition) = when (definition.name) {
        "halo_read_screen" -> descriptor(definition.name, definition, ToolCategory.PERCEPTION,
            readOnly(definition.name), keywords = setOf("screen", "read", "observe", "accessibility"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.screen.observe"))))
        "halo_find_on_screen" -> descriptor(definition.name, definition, ToolCategory.PERCEPTION,
            readOnly(definition.name), keywords = setOf("screen", "find", "search", "accessibility"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.screen.observe"))))
        "halo_ui_click" -> descriptor(definition.name, definition, ToolCategory.HOST_CONTROL,
            ToolSecurityPolicy(
                risk = ToolRiskClass.EXTERNAL_SIDE_EFFECT,
                confirmation = ConfirmationPolicy.ALWAYS,
                replaySafety = ReplaySafety.NEVER_REPLAY,
            ), keywords = setOf("click", "tap", "ui"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.screen.control"))))
        "halo_ui_scroll" -> descriptor(definition.name, definition, ToolCategory.HOST_CONTROL,
            ToolSecurityPolicy(
                risk = ToolRiskClass.EXTERNAL_SIDE_EFFECT,
                confirmation = ConfirmationPolicy.ALWAYS,
                replaySafety = ReplaySafety.NEVER_REPLAY,
            ), keywords = setOf("scroll", "ui"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.screen.control"))))
        "halo_ui_set_text" -> descriptor(definition.name, definition, ToolCategory.HOST_CONTROL,
            ToolSecurityPolicy(
                risk = ToolRiskClass.SENSITIVE,
                confirmation = ConfirmationPolicy.ALWAYS,
                replaySafety = ReplaySafety.NEVER_REPLAY,
            ), keywords = setOf("type", "text", "input", "ui"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.screen.control"))),
            presentation = ToolPresentation(
                title = "Enter text on screen",
                actionVerb = "Enter text",
                sensitiveArguments = setOf("text"),
            ))
        "halo_ui_global_action" -> descriptor(definition.name, definition, ToolCategory.HOST_CONTROL,
            ToolSecurityPolicy(
                risk = ToolRiskClass.EXTERNAL_SIDE_EFFECT,
                confirmation = ConfirmationPolicy.ALWAYS,
                replaySafety = ReplaySafety.NEVER_REPLAY,
            ), keywords = setOf("back", "home", "global"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.screen.control"))))
        else -> descriptor(definition.name, definition, ToolCategory.HOST_CONTROL, external())
    }

    // ---- descriptor id assignment for a whole executor ---------------------

    fun forExecutor(executorKind: ExecutorKind, definition: ToolDefinition): ToolDescriptor =
        when (executorKind) {
            ExecutorKind.CURRENT_TIME -> currentTime(definition)
            ExecutorKind.STORAGE_STATUS -> storageStatus(definition)
            ExecutorKind.NOTIFICATION_DIGEST -> notificationDigest(definition)
            ExecutorKind.WORKSPACE -> workspace(definition)
            ExecutorKind.FILE -> file(definition)
            ExecutorKind.MEMORY -> memory(definition)
            ExecutorKind.APPLICATION -> application(definition)
            ExecutorKind.DEVICE -> device(definition)
            ExecutorKind.ALARM -> alarm(definition)
            ExecutorKind.COMMUNICATION -> communication(definition)
            ExecutorKind.MEDIA -> media(definition)
            ExecutorKind.NAVIGATION -> navigation(definition)
            ExecutorKind.SYSTEM -> system(definition)
            ExecutorKind.ACCESSIBILITY -> accessibility(definition)
            ExecutorKind.USE_SKILL -> useSkill(definition)
        }

    enum class ExecutorKind {
        CURRENT_TIME,
        STORAGE_STATUS,
        NOTIFICATION_DIGEST,
        WORKSPACE,
        FILE,
        MEMORY,
        APPLICATION,
        DEVICE,
        ALARM,
        COMMUNICATION,
        MEDIA,
        NAVIGATION,
        SYSTEM,
        ACCESSIBILITY,
        USE_SKILL,
    }

    /** Discovery meta-tool is always part of bounded bootstrap exposure. */
    fun discoveryMeta(definition: ToolDefinition) = descriptor(
        id = definition.id,
        definition = definition,
        category = ToolCategory.SYSTEM,
        security = readOnly(definition.id),
        visibility = ToolVisibility.BOOTSTRAP,
    )
}
