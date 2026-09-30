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

    /**
     * A read that reaches outside Halo's own storage. Reading is still safe to
     * replay, but the user's stored approval policy decides whether it runs
     * unattended, because the read itself leaves the device or exposes another
     * app's content.
     */
    private fun readWithConsent(id: String) = ToolSecurityPolicy(
        risk = ToolRiskClass.READ_ONLY,
        confirmation = ConfirmationPolicy.USER_POLICY,
        replaySafety = ReplaySafety.SAFE,
    )

    private fun reversible(replay: ReplaySafety = ReplaySafety.SAFE) = ToolSecurityPolicy(
        risk = ToolRiskClass.REVERSIBLE,
        confirmation = ConfirmationPolicy.USER_POLICY,
        replaySafety = replay,
    )

    private fun sensitive(replay: ReplaySafety = ReplaySafety.NEVER_REPLAY) = ToolSecurityPolicy(
        risk = ToolRiskClass.SENSITIVE,
        confirmation = ConfirmationPolicy.USER_POLICY,
        replaySafety = replay,
    )

    private fun external(replay: ReplaySafety = ReplaySafety.NEVER_REPLAY) = ToolSecurityPolicy(
        risk = ToolRiskClass.EXTERNAL_SIDE_EFFECT,
        confirmation = ConfirmationPolicy.USER_POLICY,
        replaySafety = replay,
    )

    // ---- runtime / workspace ----------------------------------------------

    /**
     * Single-tool kinds claim exactly their own definition. Without this guard a kind that
     * cannot find its tool described whatever it was handed, using its own id and category.
     */
    fun currentTime(definition: ToolDefinition): ToolDescriptor? {
        if (definition.name != "halo_current_time") return null
        return descriptor(
            id = "halo_current_time",
            definition = definition,
            category = ToolCategory.INFORMATION,
            security = readOnly("halo_current_time"),
            keywords = setOf("time", "clock", "date", "timezone"),
        )
    }

    fun storageStatus(definition: ToolDefinition): ToolDescriptor? {
        if (definition.name != "halo_storage_status") return null
        return descriptor(
            id = "halo_storage_status",
            definition = definition,
            category = ToolCategory.SYSTEM,
            security = readOnly("halo_storage_status"),
            keywords = setOf("storage", "disk", "backup"),
        )
    }

    /**
     * MINING-P2-001 presentation.
     *
     * READ_ONLY by construction: a presentation is a view. It cannot dispatch an Intent, run a tool,
     * grant an approval, change settings, create a routine, send a message or delete memory. It is
     * BOOTSTRAP so a small model can reach for it without a discovery round trip.
     */
    fun presentation(definition: ToolDefinition): ToolDescriptor? {
        if (definition.name != "halo_present") return null
        return descriptor(
            id = "halo_present",
            definition = definition,
            category = ToolCategory.INFORMATION,
            security = readOnly("halo_present"),
            keywords = setOf("present", "show", "card", "compare", "summary", "chart", "display"),
            visibility = ToolVisibility.BOOTSTRAP,
            presentation = ToolPresentation(title = "Present information", actionVerb = "Present"),
        )
    }

    /**
     * MINING-P1-001 model-callable routine creation.
     *
     * Persisting a routine is a durable side effect on the user's device, so it is SENSITIVE with
     * `ConfirmationPolicy.ALWAYS`: the existing durable approval surface is what asks the user.
     */
    fun routineCreation(definition: ToolDefinition): ToolDescriptor? {
        if (definition.name != "halo_create_routine") return null
        return descriptor(
            id = "halo_create_routine",
            definition = definition,
            category = ToolCategory.AUTOMATION,
            security = ToolSecurityPolicy(
                risk = ToolRiskClass.SENSITIVE,
                confirmation = ConfirmationPolicy.ALWAYS,
                replaySafety = ReplaySafety.NEVER_REPLAY,
            ),
            keywords = setOf("routine", "schedule", "automation", "daily", "remind"),
            presentation = ToolPresentation(
                title = "Create routine",
                actionVerb = "Create routine",
                targetArgument = "name",
            ),
        )
    }

    /**
     * MINING-P1-001 learned procedures.
     *
     * A procedure is a recipe over *existing* capabilities. It never grants new authority: using one
     * only returns instructions, and every step still runs through the ordinary tool policy.
     */
    fun procedure(definition: ToolDefinition): ToolDescriptor? = when (definition.name) {
        "halo_propose_procedure" -> descriptor(
            id = "halo_propose_procedure",
            definition = definition,
            category = ToolCategory.AUTOMATION,
            security = readOnly("halo_propose_procedure"),
            keywords = setOf("procedure", "routine", "recipe", "learn"),
        )
        "halo_save_procedure" -> descriptor(
            id = "halo_save_procedure",
            definition = definition,
            category = ToolCategory.AUTOMATION,
            security = ToolSecurityPolicy(
                risk = ToolRiskClass.SENSITIVE,
                confirmation = ConfirmationPolicy.ALWAYS,
                replaySafety = ReplaySafety.NEVER_REPLAY,
            ),
            keywords = setOf("procedure", "save", "remember"),
            presentation = ToolPresentation(title = "Save procedure", actionVerb = "Save"),
        )
        "halo_use_procedure" -> descriptor(
            id = "halo_use_procedure",
            definition = definition,
            category = ToolCategory.INFORMATION,
            security = readOnly("halo_use_procedure"),
            keywords = setOf("procedure", "recipe", "steps", "run"),
        )
        else -> null
    }

    /**
     * MINING-P1-001 notification reply.
     *
     * Sending a reply is an external side effect on another app and on a human conversation, so it
     * is SENSITIVE with `ConfirmationPolicy.ALWAYS`: it is never run unattended, and the ephemeral
     * approval-grant layer explicitly refuses to reuse an approval for it.
     */
    fun notificationReply(definition: ToolDefinition): ToolDescriptor? = when (definition.name) {
        "halo_reply_notification" -> descriptor(
            id = "halo_reply_notification",
            definition = definition,
            category = ToolCategory.COMMUNICATION,
            security = ToolSecurityPolicy(
                risk = ToolRiskClass.SENSITIVE,
                confirmation = ConfirmationPolicy.ALWAYS,
                replaySafety = ReplaySafety.NEVER_REPLAY,
            ),
            keywords = setOf("notification", "reply", "message", "respond"),
            presentation = ToolPresentation(
                title = "Reply to notification",
                actionVerb = "Reply",
                sensitiveArguments = setOf("replyText"),
            ),
        )
        "halo_replyable_notifications" -> descriptor(
            id = "halo_replyable_notifications",
            definition = definition,
            category = ToolCategory.INFORMATION,
            security = readOnly("halo_replyable_notifications"),
            keywords = setOf("notification", "reply", "inbox"),
        )
        else -> null
    }

    fun notificationDigest(definition: ToolDefinition): ToolDescriptor? {
        if (definition.name != "halo_notification_digest") return null
        return descriptor(
            id = "halo_notification_digest",
            definition = definition,
            category = ToolCategory.INFORMATION,
            security = readOnly("halo_notification_digest"),
            keywords = setOf("notification", "digest", "summary"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.notification.read"))),
        )
    }

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
        else -> null
    }

    fun file(definition: ToolDefinition): ToolDescriptor? = when (definition.name) {
        "halo_search_files" -> descriptor(definition.name, definition, ToolCategory.FILES,
            readOnly(definition.name), keywords = setOf("file", "search", "document"))
        "halo_get_file_metadata" -> descriptor(definition.name, definition, ToolCategory.FILES,
            readOnly(definition.name), keywords = setOf("file", "metadata"))
        "halo_read_text_file" -> descriptor(definition.name, definition, ToolCategory.FILES,
            readOnly(definition.name), keywords = setOf("file", "read", "text"))
        else -> null
    }

    /**
     * RUNTIME-056: integrity measurement over text the user supplied or a file already
     * imported into the workspace. A pure read, so it is READ_ONLY and safely replayable.
     */
    fun integrity(definition: ToolDefinition): ToolDescriptor? = when (definition.name) {
        "halo_checksum" -> descriptor(definition.name, definition, ToolCategory.FILES,
            readOnly(definition.name),
            keywords = setOf("checksum", "hash", "sha", "md5", "verify", "integrity"))
        else -> null
    }

    fun memory(definition: ToolDefinition): ToolDescriptor? = when (definition.name) {
        "halo_search_memory" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            readOnly(definition.name), keywords = setOf("memory", "recall", "fact"))
        "halo_propose_memory" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            reversible(ReplaySafety.IDEMPOTENT_WITH_KEY), keywords = setOf("memory", "save", "remember"),
            presentation = ToolPresentation(title = "Save a memory", actionVerb = "Remember",
                targetArgument = "content"))
        else -> null
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
        else -> null
    }

    fun device(definition: ToolDefinition): ToolDescriptor? = when (definition.name) {
        "halo_volume" -> descriptor(definition.name, definition, ToolCategory.DEVICE_CONTROL,
            reversible(), keywords = setOf("volume", "sound", "louder", "quieter"))
        "halo_flashlight" -> descriptor(definition.name, definition, ToolCategory.DEVICE_CONTROL,
            reversible(), keywords = setOf("flashlight", "torch", "light"))
        "halo_ringer" -> descriptor(definition.name, definition, ToolCategory.DEVICE_CONTROL,
            reversible(), keywords = setOf("ringer", "silent", "vibrate", "sound mode", "mute"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.ringer.control"))))
        "halo_brightness" -> descriptor(definition.name, definition, ToolCategory.DEVICE_CONTROL,
            reversible(), keywords = setOf("brightness", "screen", "dim", "display"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.screen.brightness"))))
        else -> null
    }

    /**
     * RUNTIME-056: observation-only host facts. Nothing here changes device state,
     * so every entry is READ_ONLY with no confirmation and safe replay.
     */
    fun deviceInsight(definition: ToolDefinition): ToolDescriptor? = when (definition.name) {
        "halo_device_info" -> descriptor(definition.name, definition, ToolCategory.SYSTEM,
            readOnly(definition.name),
            keywords = setOf("device", "phone", "model", "android version", "hardware", "specs"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.device.inspect"))))
        "halo_battery_status" -> descriptor(definition.name, definition, ToolCategory.SYSTEM,
            readOnly(definition.name),
            keywords = setOf("battery", "charge", "charging", "power", "percentage"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.battery.read"))))
        "halo_network_status" -> descriptor(definition.name, definition, ToolCategory.SYSTEM,
            readOnly(definition.name),
            keywords = setOf("network", "wifi", "signal", "internet", "connection", "ip"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.network.read"))))
        else -> null
    }

    /**
     * RUNTIME-056: the clipboard belongs to whatever app the user copied from.
     * Reading it is SENSITIVE; writing it is an ordinary reversible action.
     */
    fun clipboard(definition: ToolDefinition): ToolDescriptor? = when (definition.name) {
        "halo_clipboard_read" -> descriptor(definition.name, definition, ToolCategory.INFORMATION,
            sensitive(ReplaySafety.NEVER_REPLAY),
            keywords = setOf("clipboard", "copied", "paste", "copied text"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.clipboard.read"))),
            presentation = ToolPresentation(
                title = "Read the clipboard",
                actionVerb = "Read clipboard",
                sensitiveArguments = setOf("text"),
            ))
        "halo_clipboard_write" -> descriptor(definition.name, definition, ToolCategory.DEVICE_CONTROL,
            reversible(ReplaySafety.IDEMPOTENT_WITH_KEY),
            keywords = setOf("clipboard", "copy", "paste"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.clipboard.write"))),
            presentation = ToolPresentation(
                title = "Copy text to the clipboard",
                actionVerb = "Copy",
                targetArgument = "text",
                sensitiveArguments = setOf("text"),
            ))
        else -> null
    }

    /**
     * RUNTIME-056: reading a web page for the user. The request leaves the device,
     * so the user's stored approval policy decides whether it runs unattended even
     * though nothing local changes.
     */
    fun webInsight(definition: ToolDefinition): ToolDescriptor? = when (definition.name) {
        "halo_fetch_url" -> descriptor(definition.name, definition, ToolCategory.INFORMATION,
            ToolSecurityPolicy(
                risk = ToolRiskClass.READ_ONLY,
                confirmation = ConfirmationPolicy.USER_POLICY,
                replaySafety = ReplaySafety.SAFE,
            ),
            keywords = setOf("web", "page", "read", "article", "http", "fetch", "link", "summarize"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.web.fetch"))),
            execution = com.halo.runtime.cognition.tools.ToolExecutionPolicy(timeoutMs = 15_000L),
            presentation = ToolPresentation(title = "Read a web page"))
        else -> null
    }

    fun alarm(definition: ToolDefinition): ToolDescriptor? = when (definition.name) {
        "halo_set_alarm" -> descriptor(definition.name, definition, ToolCategory.AUTOMATION,
            external(ReplaySafety.RECONCILE_BEFORE_RETRY), keywords = setOf("alarm", "wake", "set"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.alarm.set"))))
        "halo_timer" -> descriptor(definition.name, definition, ToolCategory.AUTOMATION,
            external(ReplaySafety.RECONCILE_BEFORE_RETRY),
            keywords = setOf("timer", "countdown", "minutes", "seconds"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.timer.set"))))
        else -> null
    }

    fun communication(definition: ToolDefinition): ToolDescriptor? = when (definition.name) {
        "halo_find_contact" -> descriptor(definition.name, definition, ToolCategory.COMMUNICATION,
            readOnly(definition.name), keywords = setOf("contact", "phone", "find"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.contacts.read"))))
        "halo_dial_contact" -> descriptor(definition.name, definition, ToolCategory.COMMUNICATION,
            external(ReplaySafety.RECONCILE_BEFORE_RETRY), keywords = setOf("call", "dial", "phone"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.dialer.open"))),
            presentation = ToolPresentation(title = "Start a call", actionVerb = "Call", targetArgument = "name",
                sensitiveArguments = setOf("phone", "number")))
        else -> null
    }

    fun media(definition: ToolDefinition): ToolDescriptor? = when (definition.name) {
        "halo_media_status" -> descriptor(definition.name, definition, ToolCategory.MEDIA,
            readOnly(definition.name), keywords = setOf("music", "playing", "media"))
        "halo_media_control" -> descriptor(definition.name, definition, ToolCategory.MEDIA,
            reversible(ReplaySafety.IDEMPOTENT_WITH_KEY), keywords = setOf("play", "pause", "next", "previous"))
        else -> null
    }

    fun navigation(definition: ToolDefinition): ToolDescriptor? = when (definition.name) {
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
        else -> null
    }

    fun system(definition: ToolDefinition): ToolDescriptor? = when (definition.name) {
        "halo_open_settings" -> descriptor(definition.name, definition, ToolCategory.HOST_CONTROL,
            external(ReplaySafety.RECONCILE_BEFORE_RETRY),
            keywords = setOf(
                "settings", "wifi", "bluetooth", "battery", "accessibility", "developer options",
                "language", "storage", "security", "data usage", "nfc", "vpn", "cast",
            ),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.settings.open"))))
        "halo_share_text" -> descriptor(definition.name, definition, ToolCategory.COMMUNICATION,
            external(ReplaySafety.RECONCILE_BEFORE_RETRY), keywords = setOf("share", "send", "text"),
            presentation = ToolPresentation(title = "Share text", actionVerb = "Share", targetArgument = "text"))
        else -> null
    }

    /**
     * AGENTIC-001: loading a skill is guidance and exposure planning, not a side
     * effect. It must be callable without an approval prompt or the agent loop
     * deadlocks on its own bootstrap step.
     */
    fun useSkill(definition: ToolDefinition): ToolDescriptor? {
        if (definition.name != "halo_use_skill") return null
        return descriptor(
            id = definition.id,
            definition = definition,
            category = ToolCategory.INFORMATION,
            security = readOnly(definition.id),
            keywords = setOf("skill", "guidance", "enable", "instructions"),
            visibility = ToolVisibility.BOOTSTRAP,
        )
    }

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
        else -> null
    }

    // ---- descriptor id assignment for a whole executor ---------------------

    /**
     * RUNTIME-056 correction. Each executor kind now claims an exact definition or
     * returns null.
     *
     * The previous version let every kind fall through to a default descriptor, so
     * `ToolRegistry`'s first-wins catalogue stored whichever kind happened to be
     * listed first in the composition root for *every* host tool. Android settings,
     * volume, media, alarms, contacts and the whole accessibility family were all
     * catalogued as `NAVIGATION` + `EXTERNAL_SIDE_EFFECT` + `NEVER_REPLAY`, which
     * also meant a descriptor declaring `ConfirmationPolicy.ALWAYS` (for example
     * `halo_ui_set_text`) lost its mandatory confirmation to a user policy that may
     * be `ALWAYS_ALLOW`. An unclaimed definition now falls back to the registry's
     * own conservative descriptor instead of a plausible-looking wrong one.
     */
    fun forExecutor(executorKind: ExecutorKind, definition: ToolDefinition): ToolDescriptor? =
        when (executorKind) {
            ExecutorKind.CURRENT_TIME -> currentTime(definition)
            ExecutorKind.STORAGE_STATUS -> storageStatus(definition)
            ExecutorKind.NOTIFICATION_DIGEST -> notificationDigest(definition)
            ExecutorKind.WORKSPACE -> workspace(definition)
            ExecutorKind.FILE -> file(definition)
            ExecutorKind.INTEGRITY -> integrity(definition)
            ExecutorKind.MEMORY -> memory(definition)
            ExecutorKind.APPLICATION -> application(definition)
            ExecutorKind.DEVICE -> device(definition)
            ExecutorKind.DEVICE_INSIGHT -> deviceInsight(definition)
            ExecutorKind.CLIPBOARD -> clipboard(definition)
            ExecutorKind.WEB_INSIGHT -> webInsight(definition)
            ExecutorKind.ALARM -> alarm(definition)
            ExecutorKind.COMMUNICATION -> communication(definition)
            ExecutorKind.MEDIA -> media(definition)
            ExecutorKind.NAVIGATION -> navigation(definition)
            ExecutorKind.SYSTEM -> system(definition)
            ExecutorKind.ACCESSIBILITY -> accessibility(definition)
            ExecutorKind.USE_SKILL -> useSkill(definition)
            ExecutorKind.NOTIFICATION_REPLY -> notificationReply(definition)
            ExecutorKind.ROUTINE -> routineCreation(definition)
            ExecutorKind.PROCEDURE -> procedure(definition)
            ExecutorKind.PRESENTATION -> presentation(definition)
        }

    enum class ExecutorKind {
        CURRENT_TIME,
        STORAGE_STATUS,
        NOTIFICATION_DIGEST,
        WORKSPACE,
        FILE,
        INTEGRITY,
        MEMORY,
        APPLICATION,
        DEVICE,
        DEVICE_INSIGHT,
        CLIPBOARD,
        WEB_INSIGHT,
        ALARM,
        COMMUNICATION,
        MEDIA,
        NAVIGATION,
        SYSTEM,
        ACCESSIBILITY,
        USE_SKILL,
        NOTIFICATION_REPLY,
        ROUTINE,
        PROCEDURE,
        PRESENTATION,
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
