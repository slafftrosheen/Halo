package com.halo.app.di.tools

import com.halo.runtime.cognition.tools.CapabilityId
import com.halo.runtime.cognition.tools.ConfirmationPolicy
import com.halo.runtime.cognition.tools.ReplaySafety
import com.halo.runtime.cognition.tools.ToolAutomationMetadata
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
        requirements = requirements + WorkspaceOperabilityContracts.forDefinition(definition)?.let { setOf(ToolRequirement.Capability(it.capabilityId)) }.orEmpty(),
        security = security,
        execution = execution,
        presentation = presentation,
        operability = WorkspaceOperabilityContracts.forDefinition(definition),
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
        "halo_open_notification" -> descriptor(definition.name, definition, ToolCategory.HOST_CONTROL,
            ToolSecurityPolicy(ToolRiskClass.SENSITIVE, ConfirmationPolicy.ALWAYS, ReplaySafety.NEVER_REPLAY),
            keywords = setOf("notification", "open"), requirements = setOf(ToolRequirement.Capability(CapabilityId("host.notification.live"))))
        "halo_list_notifications" -> descriptor(definition.name, definition, ToolCategory.INFORMATION,
            ToolSecurityPolicy.READ_ONLY, keywords = setOf("notification", "list", "inbox"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.notification.live"))))
        "halo_dismiss_notification" -> descriptor(definition.name, definition, ToolCategory.COMMUNICATION,
            ToolSecurityPolicy(ToolRiskClass.SENSITIVE, ConfirmationPolicy.ALWAYS, ReplaySafety.NEVER_REPLAY),
            keywords = setOf("notification", "dismiss"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.notification.live"))),
            presentation = ToolPresentation(title = "Dismiss notification", actionVerb = "Dismiss"))
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
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.notification.live"))),
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
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.notification.live"))),
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
        ).copy(automation = ToolAutomationMetadata(com.halo.runtime.cognition.routine.AutomationPermission.ALLOWED_WITH_ROUTINE_GRANT, true, true))
    }

    fun routineMessage(definition: ToolDefinition): ToolDescriptor? {
        if (definition.name != "halo_routine_message") return null
        return descriptor(definition.name, definition, ToolCategory.INFORMATION, readOnly(definition.name),
            keywords = setOf("routine", "message", "alert"),
            presentation = ToolPresentation(title = "Show message", actionVerb = "Show message"))
            .copy(automation = ToolAutomationMetadata(
                com.halo.runtime.cognition.routine.AutomationPermission.ALLOWED_WITH_ROUTINE_GRANT,
                backgroundCompatible = true, argumentsPersistable = true, requiresModel = false))
    }

    fun modelSetup(definition: ToolDefinition): ToolDescriptor? {
        val operation = when (definition.name) {
            "halo_local_setup_status" -> com.halo.runtime.cognition.tools.ToolOperation.QUERY
            "halo_local_setup_start" -> com.halo.runtime.cognition.tools.ToolOperation.CREATE
            "halo_local_setup_pause", "halo_local_setup_keep_files", "halo_local_setup_rollback" -> com.halo.runtime.cognition.tools.ToolOperation.EDIT
            else -> return null
        }
        val policy = when (definition.name) {
            "halo_local_setup_status" -> ToolSecurityPolicy.READ_ONLY
            "halo_local_setup_rollback" -> ToolSecurityPolicy.DESTRUCTIVE
            "halo_local_setup_pause" -> ToolSecurityPolicy(ToolRiskClass.REVERSIBLE, ConfirmationPolicy.NONE, ReplaySafety.NEVER_REPLAY)
            "halo_local_setup_start" -> sensitive(ReplaySafety.NEVER_REPLAY)
            else -> reversible(ReplaySafety.NEVER_REPLAY)
        }
        return descriptor(definition.name, definition, ToolCategory.SYSTEM, policy,
            keywords = setOf("model", "models", "setup", "starter", "download", "listening", "voice", "local", "pause", "rollback"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.models.manage"))),
            presentation = ToolPresentation(title = "Local model setup", actionVerb = if (definition.name == "halo_local_setup_rollback") "Remove setup downloads" else "Manage setup"))
            .copy(operability = com.halo.runtime.cognition.tools.ToolOperability(capabilityId = CapabilityId("host.models.manage"),
                owner = "LocalRolePackInstaller", operation = operation, resultEncoding = com.halo.runtime.cognition.tools.ToolResultEncoding.JSON,
                resultSchema = mapOf("type" to "object", "properties" to mapOf("transactionId" to mapOf("type" to "string"),
                    "phase" to mapOf("type" to "string"), "stage" to mapOf("type" to "string"), "state" to mapOf("type" to "string"),
                    "available" to mapOf("type" to "boolean"), "busy" to mapOf("type" to "boolean"),
                    "canResume" to mapOf("type" to "boolean"), "canRollback" to mapOf("type" to "boolean"),
                    "hasSetupRecord" to mapOf("type" to "boolean"), "recoveryRequired" to mapOf("type" to "boolean"),
                    "canKeepFiles" to mapOf("type" to "boolean")), "required" to listOf("transactionId"), "additionalProperties" to true),
                sourceVersionArguments = if (definition.name == "halo_local_setup_status") emptySet() else setOf("transactionId"),
                resultIdFields = setOf("transactionId"), limitations = setOf("Dispatch only; poll canonical status. No role selection or qualification.",
                    "Same semantic owner for typed and voice-final requests; real speech/device/provider acceptance is separate.")))
    }

    // ---- workspace / notebook / memory ------------------------------------

    fun workspace(definition: ToolDefinition) = when (definition.name) {
        ContextReceiptTools.NAME -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            readOnly(definition.name), keywords = setOf("sources", "context", "used", "history", "receipt"),
            presentation = ToolPresentation(title = "Inspect saved sources", actionVerb = "Read"))
            .copy(operability = ContextReceiptTools.operability())
        "halo_ink_status", "halo_ink_document", "halo_ink_selection" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            readOnly(definition.name), keywords = setOf("pen", "drawing", "tablet", "ink", "canvas"),
            execution = com.halo.runtime.cognition.tools.ToolExecutionPolicy(maxOutputChars = 48000))
        "halo_ink_start" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            ToolSecurityPolicy(risk = ToolRiskClass.SENSITIVE, confirmation = ConfirmationPolicy.ALWAYS,
                replaySafety = ReplaySafety.NEVER_REPLAY), keywords = setOf("pen", "drawing", "tablet", "ink", "canvas"),
            presentation = ToolPresentation(title = "Draw together", actionVerb = "Start"))
        "halo_ink_stop" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            reversible(ReplaySafety.SAFE), keywords = setOf("pen", "drawing", "tablet", "ink", "stop"),
            presentation = ToolPresentation(title = "Stop drawing together", actionVerb = "Stop"))
        "halo_canvas_delete", "halo_canvas_ungroup", "halo_canvas_disconnect" -> descriptor(
            definition.name, definition, ToolCategory.WORKSPACE, ToolSecurityPolicy.DESTRUCTIVE,
            execution = com.halo.runtime.cognition.tools.ToolExecutionPolicy(maxOutputChars = 16000),
            keywords = setOf("canvas", "board", "card", "group", "connection", "remove", "delete", "ungroup"),
            presentation = ToolPresentation(title = when (definition.name) {
                "halo_canvas_delete" -> "Remove cards"
                "halo_canvas_ungroup" -> "Ungroup"
                else -> "Remove connection"
            }, actionVerb = "Remove"))
        "halo_canvas_rename", "halo_canvas_add_group", "halo_canvas_tag", "halo_canvas_duplicate", "halo_canvas_arrange" -> descriptor(
            definition.name, definition, ToolCategory.WORKSPACE, reversible(ReplaySafety.NEVER_REPLAY),
            execution = com.halo.runtime.cognition.tools.ToolExecutionPolicy(maxOutputChars = 16000),
            keywords = setOf("canvas", "board", "card", "rename", "group", "tag", "duplicate", "arrange", "layer"),
            presentation = ToolPresentation(title = when (definition.name) {
                "halo_canvas_rename" -> "Rename canvas"
                "halo_canvas_add_group" -> "Add group"
                "halo_canvas_duplicate" -> "Duplicate"
                else -> "Edit"
            }, actionVerb = "Edit"))
        "halo_canvas_clear_sketch" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE, ToolSecurityPolicy.DESTRUCTIVE,
            keywords = setOf("canvas", "sketch", "drawing", "clear", "erase", "strokes"),
            presentation = ToolPresentation(title = "Clear sketch", actionVerb = "Clear"))
        "halo_canvas_current" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE, readOnly(definition.name),
            keywords = setOf("canvas", "current", "this", "selected", "selection", "board", "cards"),
            execution = com.halo.runtime.cognition.tools.ToolExecutionPolicy(maxOutputChars = 16000))
        "halo_canvas_get" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE, readOnly(definition.name),
            keywords = setOf("canvas", "board", "cards", "read", "drawing"),
            execution = com.halo.runtime.cognition.tools.ToolExecutionPolicy(maxOutputChars = 24000))
        "halo_canvas_create", "halo_canvas_add_text", "halo_canvas_edit_text", "halo_canvas_move", "halo_canvas_connect" -> descriptor(
            definition.name, definition, ToolCategory.WORKSPACE, reversible(ReplaySafety.NEVER_REPLAY),
            dependencies = if (definition.name == "halo_canvas_edit_text")
                setOf(ToolId("halo_canvas_current"), ToolId("halo_canvas_get")) else emptySet(),
            keywords = setOf("canvas", "board", "card", "text", "move", "connect", "drawing"),
            presentation = ToolPresentation(title = "Edit Canvas", actionVerb = "Edit"))
        "halo_workspace_describe", "halo_workspace_canvases", "halo_workspace_handoff_status" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            readOnly(definition.name), keywords = setOf("workspace", "source", "history", "continue", "canvas"))
        "halo_workspace_open_with", "halo_workspace_insert", "halo_workspace_select" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            reversible(ReplaySafety.NEVER_REPLAY), keywords = setOf("workspace", "continue", "context", "studio", "study", "canvas"),
            presentation = ToolPresentation(title = "Continue with this item", actionVerb = "Continue"))
        "halo_studio_list", "halo_studio_get", "halo_studio_current", "halo_studio_status" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            readOnly(definition.name), keywords = setOf("studio", "image", "drawing", "layers", "project"),
            presentation = ToolPresentation(title = "Studio", actionVerb = "Open"))
        "halo_studio_create", "halo_studio_edit", "halo_studio_save", "halo_studio_export", "halo_studio_insert", "halo_studio_open", "halo_studio_ai_draft" -> descriptor(
            definition.name, definition, ToolCategory.WORKSPACE, reversible(ReplaySafety.NEVER_REPLAY),
            keywords = setOf("studio", "image", "drawing", "layers", "project", "export", "canvas"),
            presentation = ToolPresentation(title = "Studio", actionVerb = "Edit"))
        "halo_get_workspace_object" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            readOnly(definition.name), keywords = setOf("note", "workspace", "read"))
        "halo_create_note", "halo_create_empty_note" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            reversible(ReplaySafety.NEVER_REPLAY), keywords = setOf("note", "create", "write"),
            presentation = ToolPresentation(title = "Create a note", actionVerb = "Create note"))
        "halo_search_notebook" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            readOnly(definition.name), keywords = setOf("note", "search", "find"))
        "halo_save_screen_to_notebook" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            reversible(ReplaySafety.NEVER_REPLAY), keywords = setOf("screen", "capture", "note"))
        "halo_note_attachments" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            readOnly(definition.name), keywords = setOf("note", "notebook", "attachment", "photo", "image", "list"),
            execution = com.halo.runtime.cognition.tools.ToolExecutionPolicy(maxOutputChars = 24000))
        "halo_note_reuse_image" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            reversible(ReplaySafety.NEVER_REPLAY), keywords = setOf("note", "attachment", "image", "photo", "reuse", "copy"),
            presentation = ToolPresentation(title = "Notebook", actionVerb = "Save"),
            execution = com.halo.runtime.cognition.tools.ToolExecutionPolicy(maxOutputChars = 24000))
        "halo_note_unlink_attachment" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            ToolSecurityPolicy.DESTRUCTIVE, keywords = setOf("note", "attachment", "remove", "unlink"),
            presentation = ToolPresentation(title = "Notebook", actionVerb = "Remove"),
            execution = com.halo.runtime.cognition.tools.ToolExecutionPolicy(maxOutputChars = 24000))
        "halo_note_get", "halo_note_read_block", "halo_note_purge_status" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            readOnly(definition.name), keywords = setOf("note", "notebook", "read", "block"),
            execution = com.halo.runtime.cognition.tools.ToolExecutionPolicy(maxOutputChars = 24000))
        "halo_note_replace_content" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            ToolSecurityPolicy.DESTRUCTIVE, keywords = setOf("note", "notebook", "replace", "source"),
            presentation = ToolPresentation(title = "Notebook", actionVerb = "Save"),
            execution = com.halo.runtime.cognition.tools.ToolExecutionPolicy(maxOutputChars = 24000))
        "halo_note_delete_block", "halo_note_trash", "halo_note_purge", "halo_note_purge_retry" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            ToolSecurityPolicy.DESTRUCTIVE, keywords = setOf("note", "notebook", "delete", "replace", "trash"),
            presentation = ToolPresentation(title = "Notebook", actionVerb = "Delete"),
            execution = com.halo.runtime.cognition.tools.ToolExecutionPolicy(maxOutputChars = 24000))
        "halo_note_rename", "halo_note_insert_block", "halo_note_edit_block", "halo_note_move_block", "halo_note_indent_block",
        "halo_note_split_block", "halo_note_merge_block", "halo_note_duplicate_block", "halo_note_insert_reference", "halo_note_set_reference",
        "halo_note_archive", "halo_note_restore", "halo_note_remove_from_inbox" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            reversible(ReplaySafety.NEVER_REPLAY), keywords = setOf("note", "notebook", "edit", "block", "save", "archive", "restore"),
            presentation = ToolPresentation(title = "Notebook", actionVerb = "Save"),
            execution = com.halo.runtime.cognition.tools.ToolExecutionPolicy(maxOutputChars = 24000))
        "halo_study_sessions", "halo_study_state", "halo_study_card_get" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            readOnly(definition.name), keywords = setOf("study", "session", "practice", "card", "review", "learn"),
            execution = com.halo.runtime.cognition.tools.ToolExecutionPolicy(maxOutputChars = 24000))
        "halo_study_delete", "halo_study_card_delete" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            ToolSecurityPolicy.DESTRUCTIVE, keywords = setOf("study", "session", "practice", "card", "delete", "remove"),
            presentation = ToolPresentation(title = "Study", actionVerb = "Delete"))
        "halo_study_select_exercise", "halo_study_submit_attempt", "halo_study_set_preferences", "halo_study_complete",
        "halo_study_card_create", "halo_study_card_edit", "halo_study_card_accept", "halo_study_card_review" -> descriptor(
            definition.name, definition, ToolCategory.WORKSPACE, reversible(ReplaySafety.NEVER_REPLAY),
            keywords = setOf("study", "session", "practice", "card", "attempt", "review", "learn", "finish"),
            execution = com.halo.runtime.cognition.tools.ToolExecutionPolicy(maxOutputChars = 24000),
            presentation = ToolPresentation(title = "Study", actionVerb = "Save"))
        "halo_study_with_halo" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            reversible(ReplaySafety.NEVER_REPLAY),
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
        "halo_file_tool_capabilities", "halo_text_get", "halo_file_batch_status", "halo_pdf_page_info", "halo_pdf_annotation_get" -> descriptor(definition.name, definition, ToolCategory.FILES,
            readOnly(definition.name), keywords = setOf("file", "document", "csv", "pdf", "image"))
        "halo_file_tools_open", "halo_text_create", "halo_text_import", "halo_text_edit", "halo_text_export", "halo_image_transform", "halo_image_batch", "halo_image_collage", "halo_pdf_extract_page_image", "halo_pdf_annotation_create", "halo_pdf_annotation_edit", "halo_pdf_annotation_export_page_image", "halo_file_attach_canvas" -> descriptor(definition.name, definition, ToolCategory.FILES,
            reversible(ReplaySafety.NEVER_REPLAY), keywords = setOf("file", "edit", "export", "csv", "pdf", "image"),
            presentation = ToolPresentation(title = "Edit local files", actionVerb = "Apply"))
        "halo_file_handoff" -> descriptor(definition.name, definition, ToolCategory.FILES,
            ToolSecurityPolicy(ToolRiskClass.SENSITIVE, ConfirmationPolicy.ALWAYS, ReplaySafety.NEVER_REPLAY),
            keywords = setOf("file", "pdf", "document", "attachment", "share", "view"))
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
        "halo_search_memory", "halo_read_memory", "halo_read_working_memory" -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            readOnly(definition.name), keywords = setOf("memory", "recall", "fact"))
        "halo_set_working_memory" -> descriptor(definition.name,definition,ToolCategory.WORKSPACE,
            reversible(ReplaySafety.IDEMPOTENT_WITH_KEY),keywords=setOf("memory","working","temporary"))
        "halo_propose_memory" -> descriptor(definition.name,definition,ToolCategory.WORKSPACE,
            reversible(ReplaySafety.NEVER_REPLAY),keywords=setOf("memory","suggest"),
            presentation=ToolPresentation(title="Suggest a memory",actionVerb="Suggest",targetArgument="content"))
        "halo_remember", "halo_update_memory", "halo_forget_memory" -> descriptor(definition.name,definition,ToolCategory.WORKSPACE,
            ToolSecurityPolicy(if(definition.name == "halo_forget_memory") ToolRiskClass.DESTRUCTIVE else ToolRiskClass.REVERSIBLE,ConfirmationPolicy.ALWAYS,ReplaySafety.NEVER_REPLAY),
            keywords=setOf("memory","remember","forget","correct"),presentation=ToolPresentation(title="Change personal memory",actionVerb="Review",targetArgument="content"))
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
            reversible(), keywords = setOf("flashlight", "torch", "light"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.flashlight.control"))))
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

    fun calendar(definition: ToolDefinition): ToolDescriptor? = when (definition.name) {
        "halo_calendar_editor" -> descriptor(definition.name, definition, ToolCategory.AUTOMATION,
            ToolSecurityPolicy(ToolRiskClass.EXTERNAL_SIDE_EFFECT, ConfirmationPolicy.ALWAYS, ReplaySafety.NEVER_REPLAY),
            keywords = setOf("calendar", "event", "editor", "schedule"),
            presentation = ToolPresentation(title = "Open calendar event editor", actionVerb = "Review",
                sensitiveArguments = setOf("title", "startMs", "endMs")))
        "halo_calendar_upcoming" -> descriptor(definition.name, definition, ToolCategory.INFORMATION,
            ToolSecurityPolicy.READ_ONLY, keywords = setOf("calendar", "upcoming", "events"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.calendar.read"))))
        "halo_calendar_create" -> descriptor(definition.name, definition, ToolCategory.AUTOMATION,
            ToolSecurityPolicy(ToolRiskClass.EXTERNAL_SIDE_EFFECT, ConfirmationPolicy.ALWAYS, ReplaySafety.NEVER_REPLAY),
            keywords = setOf("calendar", "event", "schedule"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.calendar.write"))),
            presentation = ToolPresentation(title = "Create calendar event", actionVerb = "Create", targetArgument = "title"))
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
        "halo_clock_view" -> descriptor(definition.name, definition, ToolCategory.HOST_CONTROL,
            external(ReplaySafety.RECONCILE_BEFORE_RETRY), keywords = setOf("clock", "alarm", "timer", "show", "open"),
            presentation = ToolPresentation(title = "Open clock page", actionVerb = "Show"))
        "halo_clock_modify" -> descriptor(definition.name, definition, ToolCategory.AUTOMATION,
            ToolSecurityPolicy(ToolRiskClass.EXTERNAL_SIDE_EFFECT, ConfirmationPolicy.ALWAYS, ReplaySafety.NEVER_REPLAY),
            keywords = setOf("clock", "alarm", "timer", "dismiss", "snooze"),
            presentation = ToolPresentation(title = "Change clock alarm or timer", actionVerb = "Review"))
        else -> null
    }

    fun followups(definition: ToolDefinition): ToolDescriptor? = when (definition.name) {
        "halo_create_watch" -> descriptor(definition.name, definition, ToolCategory.AUTOMATION, ToolSecurityPolicy.READ_ONLY,
            keywords = setOf("followup", "watch", "notify", "wait", "when"))
        "halo_list_watches" -> descriptor(definition.name, definition, ToolCategory.AUTOMATION, ToolSecurityPolicy.READ_ONLY,
            keywords = setOf("followup", "watch", "list", "active"))
        "halo_cancel_watch" -> descriptor(definition.name, definition, ToolCategory.AUTOMATION, ToolSecurityPolicy.READ_ONLY,
            keywords = setOf("followup", "watch", "cancel", "stop"))
        else -> null
    }

    fun appSemantic(definition: ToolDefinition): ToolDescriptor? = when (definition.name) {
        "halo_current_app_capabilities" -> descriptor(definition.name, definition, ToolCategory.HOST_CONTROL, ToolSecurityPolicy.READ_ONLY,
            keywords = setOf("current app", "app actions"))
        "halo_current_app_action" -> descriptor(definition.name, definition, ToolCategory.HOST_CONTROL,
            ToolSecurityPolicy(ToolRiskClass.SENSITIVE, ConfirmationPolicy.ALWAYS, ReplaySafety.NEVER_REPLAY),
            keywords = setOf("archive", "mark unread", "current app"), presentation = ToolPresentation(title = "Use current app"))
        else -> null
    }

    fun communication(definition: ToolDefinition): ToolDescriptor? = when (definition.name) {
        "halo_contact_editor" -> descriptor(definition.name, definition, ToolCategory.COMMUNICATION,
            ToolSecurityPolicy(ToolRiskClass.EXTERNAL_SIDE_EFFECT, ConfirmationPolicy.ALWAYS, ReplaySafety.NEVER_REPLAY),
            keywords = setOf("contact", "create", "edit", "address book"),
            presentation = ToolPresentation(title = "Open contact editor", actionVerb = "Review",
                sensitiveArguments = setOf("name", "phone", "email")))
        "halo_compose_sms", "halo_compose_email", "halo_compose_message" -> descriptor(definition.name, definition, ToolCategory.COMMUNICATION,
            ToolSecurityPolicy.EXTERNAL.copy(confirmation = ConfirmationPolicy.ALWAYS), keywords = setOf("text", "message", "email", "compose"),
            presentation = ToolPresentation(title = "Open message composer", sensitiveArguments = setOf("recipient", "body")))
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
        "halo_search" -> descriptor(definition.name, definition, ToolCategory.NAVIGATION,
            external(ReplaySafety.RECONCILE_BEFORE_RETRY), keywords = setOf("search", "web", "internet"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.web.search"))))
        else -> null
    }

    private fun navigationOperability(operation: com.halo.runtime.cognition.tools.ToolOperation) =
        com.halo.runtime.cognition.tools.ToolOperability(
            capabilityId = CapabilityId("host.assistant.screen"),
            owner = "HaloScreenCatalog/HaloScreenLauncher",
            operation = operation,
            resultEncoding = com.halo.runtime.cognition.tools.ToolResultEncoding.TEXT,
            resultSchema = mapOf("type" to "string", "maxLength" to 24000),
            limitations = setOf("Parameter-free destinations only; object routes require their domain owner.",
                "Dispatch does not verify rendering or grant permissions; never replay an uncertain launch."),
        )

    fun assistant(definition: ToolDefinition): ToolDescriptor? = when (definition.name) {
        in com.halo.app.di.tools.host.NotebookLibraryTools.NAMES ->
            com.halo.app.di.tools.host.NotebookLibraryTools.descriptor(definition)
        in com.halo.app.di.tools.host.NotebookOrganizationTools.NAMES ->
            com.halo.app.di.tools.host.NotebookOrganizationTools.descriptor(definition)
        in com.halo.app.di.tools.host.SavedChatTools.names -> descriptor(definition.name, definition, ToolCategory.WORKSPACE,
            ToolSecurityPolicy(
                if (definition.name in setOf("halo_saved_chat_trash", "halo_saved_chat_restore")) ToolRiskClass.DESTRUCTIVE else ToolRiskClass.SENSITIVE,
                ConfirmationPolicy.ALWAYS, ReplaySafety.NEVER_REPLAY),
            keywords = setOf("saved", "chat", "conversation", "history", "trash", "restore"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.saved_chats"))),
            presentation = ToolPresentation("Saved chats", actionVerb = "Review", targetArgument = "conversationId",
                sensitiveArguments = setOf("query")))
            .copy(operability = com.halo.app.di.tools.host.SavedChatTools.operability(definition.name),
                execution = com.halo.runtime.cognition.tools.ToolExecutionPolicy(maxOutputChars = 24000))
        com.halo.app.di.tools.host.CurrentScreenTools.NAME -> descriptor(definition.name, definition, ToolCategory.INFORMATION,
            readOnly(definition.name), keywords = setOf("current", "screen", "selection", "page", "context"),
                requirements = setOf(ToolRequirement.Capability(CapabilityId("host.assistant.current_screen"))))
            .copy(operability = com.halo.app.di.tools.host.CurrentScreenTools.operability(),
                execution = com.halo.runtime.cognition.tools.ToolExecutionPolicy(maxOutputChars = 48000))

        "halo_list_screens" -> descriptor(definition.name, definition, ToolCategory.INFORMATION,
            readOnly(definition.name), keywords = setOf("halo", "screens", "destinations"))
            .copy(operability = navigationOperability(com.halo.runtime.cognition.tools.ToolOperation.QUERY))
        "halo_get_preferences" -> descriptor(definition.name, definition, ToolCategory.INFORMATION,
            readOnly(definition.name), keywords = setOf("halo", "screens", "preferences", "appearance", "font", "start page"))
            .withAppPreferences(definition)
        "halo_set_preferences" -> descriptor(definition.name, definition, ToolCategory.HOST_CONTROL,
            reversible(ReplaySafety.NEVER_REPLAY), keywords = setOf("halo", "theme", "dark", "light", "font", "start page"))
            .withAppPreferences(definition)
        "halo_get_mode_profile", "halo_edit_mode_profile" -> descriptor(definition.name, definition,
            if (definition.name == "halo_get_mode_profile") ToolCategory.INFORMATION else ToolCategory.HOST_CONTROL,
            if (definition.name == "halo_get_mode_profile") readOnly(definition.name)
                else ToolSecurityPolicy(ToolRiskClass.SENSITIVE, ConfirmationPolicy.ALWAYS, ReplaySafety.NEVER_REPLAY),
            keywords = setOf("halo", "mode", "profile", "privacy", "routing", "speech"),
            presentation = ToolPresentation(title = if (definition.name == "halo_get_mode_profile") "Read mode profile" else "Edit mode profile",
                actionVerb = if (definition.name == "halo_get_mode_profile") "Read" else "Save", targetArgument = "mode"))
            .withAppPreferences(definition)
        "halo_open_screen" -> descriptor(definition.name, definition, ToolCategory.HOST_CONTROL,
            external(ReplaySafety.NEVER_REPLAY), keywords = setOf("halo", "navigate", "page", "screen"))
            .copy(operability = navigationOperability(com.halo.runtime.cognition.tools.ToolOperation.PRESENT))
        "halo_read_aloud" -> descriptor(definition.name, definition, ToolCategory.MEDIA,
            external(ReplaySafety.NEVER_REPLAY), keywords = setOf("speech", "read aloud", "voice"),
            presentation = ToolPresentation(title = "Read text aloud", actionVerb = "Read", sensitiveArguments = setOf("text")))
        "halo_list_app_actions" -> descriptor(definition.name, definition, ToolCategory.INFORMATION,
            readWithConsent(definition.name), keywords = setOf("native", "assistant", "app actions"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.assistant.native"))))
        "halo_perform_app_action" -> descriptor(definition.name, definition, ToolCategory.HOST_CONTROL,
            ToolSecurityPolicy(risk = ToolRiskClass.SENSITIVE, confirmation = ConfirmationPolicy.ALWAYS, replaySafety = ReplaySafety.NEVER_REPLAY),
            keywords = setOf("native", "assistant", "app actions"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.assistant.native"))))
        "halo_stop_reading" -> descriptor(definition.name, definition, ToolCategory.MEDIA,
            reversible(ReplaySafety.SAFE), keywords = setOf("speech", "stop reading", "stop voice"))
        else -> interfaceConfiguration(definition)
    }

    private fun ToolDescriptor.withAppPreferences(definition: ToolDefinition): ToolDescriptor {
        val contract = requireNotNull(com.halo.app.di.tools.host.AppPreferenceOperability.forName(definition.name))
        return copy(operability = contract, requirements = requirements + ToolRequirement.Capability(contract.capabilityId),
            execution = execution.copy(maxOutputChars = 24000))
    }

    private fun interfaceConfiguration(definition: ToolDefinition): ToolDescriptor? {
        val contract = ScreenOperabilityContracts.forDefinition(definition)
            ?: DeviceNotificationOperability.forDefinition(definition)
            ?: com.halo.app.di.tools.host.VoiceConfigurationTools.operability(definition.name)
            ?: LearningConfigurationTools.operability(definition.name)
            ?: RoutineManagementOperability.forDefinition(definition)
            ?: ProviderConfigurationOperability.forDefinition(definition)
            ?: McpSettingsOperability.forDefinition(definition)
            ?: LocalModelRoleOperability.forDefinition(definition)
            ?: StudySessionOperability.forDefinition(definition)
            ?: CanvasWorkflowOperability.forDefinition(definition)
            ?: SpeechInventoryOperability.forDefinition(definition) ?: return null
        val query = contract.operation == com.halo.runtime.cognition.tools.ToolOperation.QUERY
        val screen = definition.name in ScreenOperabilityContracts.readTools + ScreenOperabilityContracts.mutationTools
        val always = !query && (!screen || definition.name == "halo_screen_settings" || definition.name == "halo_screen_remove_preset")
        val security = when {
            query -> readOnly(definition.name)
            always -> ToolSecurityPolicy(
                when (definition.name) {
                    "halo_saved_device_forget", "halo_screen_remove_preset", "halo_routine_delete" -> ToolRiskClass.DESTRUCTIVE
                    "halo_routine_run_now", "halo_provider_refresh_models", "halo_provider_test_connection", "halo_mcp_refresh_tools" -> ToolRiskClass.EXTERNAL_SIDE_EFFECT
                    else -> ToolRiskClass.SENSITIVE
                },
                ConfirmationPolicy.ALWAYS, ReplaySafety.NEVER_REPLAY)
            else -> reversible(ReplaySafety.NEVER_REPLAY)
        }
        val title = when (definition.name) {
            "halo_mcp_servers" -> "List configured tool servers"
            "halo_mcp_server_state" -> "Read tool server settings"
            "halo_mcp_server_set_enabled" -> "Change tool server availability"
            "halo_mcp_tool_set_enabled" -> "Change server tool availability"
            "halo_mcp_refresh_tools" -> "Refresh connected server tools"
            "halo_local_role_state" -> "Read local model roles"
            "halo_local_role_select" -> "Change a local model role"
            "halo_screen_state" -> "Read Screen layout"
            "halo_screen_preview" -> "Preview Screen layout"
            "halo_screen_apply" -> "Apply Screen layout"
            "halo_screen_settings" -> "Change Screen settings"
            "halo_screen_undo" -> "Undo Screen layout change"
            "halo_screen_save_preset" -> "Save Screen preset"
            "halo_screen_remove_preset" -> "Remove Screen preset"
            "halo_saved_devices" -> "List saved devices"
            "halo_saved_device_get" -> "Read saved device"
            "halo_device_preferences" -> "Read device preferences"
            "halo_device_thumbnail_quality" -> "Change device image quality"
            "halo_saved_device_retry" -> "Retry saved device"
            "halo_saved_device_connect" -> "Connect saved device"
            "halo_saved_device_disconnect" -> "Disconnect saved device"
            "halo_saved_device_autoconnect" -> "Change automatic device connection"
            "halo_saved_device_forget" -> "Forget saved device"
            "halo_notification_settings_apps" -> "List notification settings apps"
            "halo_notification_policy_get" -> "Read app notification policy"
            "halo_notification_policy_set" -> "Change app notification policy"
            "halo_digest_delivery_get" -> "Read digest delivery settings"
            "halo_digest_delivery_set" -> "Change digest delivery settings"
            "halo_get_voice_configuration" -> "Read voice settings"
            "halo_set_voice_configuration" -> "Change voice settings"
            "halo_get_learning_configuration" -> "Read learning settings"
            "halo_set_learning_configuration" -> "Change learning settings"
            "halo_routines_list" -> "List saved routines"
            "halo_routine_get" -> "Read saved routine"
            "halo_routine_run_status" -> "Read routine progress"
            "halo_routine_set_enabled" -> "Change routine scheduling"
            "halo_routine_save_existing" -> "Save routine changes"
            "halo_routine_delete" -> "Delete saved routine"
            "halo_routine_run_now" -> "Run saved routine"
            "halo_provider_catalog" -> "List answer providers"
            "halo_provider_state" -> "Read provider readiness"
            "halo_provider_refresh_models" -> "Refresh provider model list"
            "halo_provider_test_connection" -> "Check provider connection"
            "halo_study_current" -> "Read active study lesson"
            "halo_study_session_action" -> "Change active study lesson"
            "halo_canvas_workflow_status" -> "Read Canvas review status"
            "halo_canvas_image_palette" -> "Save image color palette"
            "halo_canvas_transcribe_file" -> "Prepare local transcription for review"
            "halo_canvas_save_reviewed_text" -> "Save reviewed Canvas text"
            "halo_canvas_edit_reviewed_text" -> "Edit Canvas review draft"
            "halo_canvas_discard_reviewed_text" -> "Discard Canvas review draft"
            "halo_canvas_export_note" -> "Export Canvas to a note"
            "halo_speech_inventory" -> "List local speech models"
            "halo_speech_model_action" -> "Manage local speech model"
            else -> return null
        }
        return descriptor(definition.name, definition, if (query) ToolCategory.INFORMATION else ToolCategory.HOST_CONTROL,
            security, keywords = if (screen) setOf("screen", "layout", "card", "preset", "typography")
                else setOf("saved device", "connection", "notification", "digest", "preferences", "voice", "learning", "routine", "provider", "study", "canvas", "review draft", "speech model"),
            requirements = setOf(ToolRequirement.Capability(contract.capabilityId)),
            execution = com.halo.runtime.cognition.tools.ToolExecutionPolicy(
                maxOutputChars = if (contract.capabilityId.value == "host.voice.configuration") 12000 else 24000),
            presentation = ToolPresentation(title = title, actionVerb = if (query) "Read" else "Apply",
                targetArgument = when {
                    definition.name.startsWith("halo_saved_device_") -> "deviceId"
                    definition.name.startsWith("halo_notification_policy_") -> "packageName"
                    else -> null
                }, sensitiveArguments = setOf("settings", "topics", "name", "text", "sourceVersions", "trigger", "conditions", "actions", "delivery", "patch")))
            .copy(operability = contract)
    }

    fun system(definition: ToolDefinition): ToolDescriptor? = when (definition.name) {
        "halo_google_mail" -> descriptor(definition.name, definition, ToolCategory.INFORMATION,
            ToolSecurityPolicy(ToolRiskClass.SENSITIVE, ConfirmationPolicy.ALWAYS, ReplaySafety.NEVER_REPLAY),
            keywords = setOf("gmail", "email", "inbox", "search", "message"),
            presentation = ToolPresentation(title = "Read Gmail", actionVerb = "Authorize read-only access", sensitiveArguments = setOf("query", "messageId")))
        "halo_native_map" -> descriptor(definition.name, definition, ToolCategory.NAVIGATION,
            ToolSecurityPolicy(ToolRiskClass.EXTERNAL_SIDE_EFFECT, ConfirmationPolicy.ALWAYS, ReplaySafety.NEVER_REPLAY),
            keywords = setOf("map", "coordinates", "directions", "walking", "driving", "transit", "cycling"))
        "halo_native_global" -> descriptor(definition.name, definition, ToolCategory.HOST_CONTROL,
            ToolSecurityPolicy(ToolRiskClass.EXTERNAL_SIDE_EFFECT, ConfirmationPolicy.ALWAYS, ReplaySafety.NEVER_REPLAY),
            keywords = setOf("recents", "notifications", "quick settings", "lock", "screenshot", "split screen"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.screen.control"))))
        "halo_native_shortcut" -> descriptor(definition.name, definition, ToolCategory.HOST_CONTROL,
            ToolSecurityPolicy(ToolRiskClass.EXTERNAL_SIDE_EFFECT, ConfirmationPolicy.ALWAYS, ReplaySafety.NEVER_REPLAY),
            keywords = setOf("shortcut", "launcher", "pin", "home"))
        "halo_native_observation" -> descriptor(definition.name, definition, ToolCategory.INFORMATION,
            sensitive(ReplaySafety.SAFE), keywords = setOf("location", "sensor", "light", "network", "speed"))
        "halo_native_target" -> descriptor(definition.name, definition, ToolCategory.HOST_CONTROL,
            ToolSecurityPolicy(ToolRiskClass.SENSITIVE, ConfirmationPolicy.ALWAYS, ReplaySafety.NEVER_REPLAY),
            keywords = setOf("app", "uninstall", "contact", "calendar", "event"))
        "halo_native_result" -> descriptor(definition.name, definition, ToolCategory.INFORMATION,
            readOnly(definition.name), keywords = setOf("picker", "result", "receipt"))
        "halo_native_destination" -> descriptor(definition.name, definition, ToolCategory.HOST_CONTROL,
            ToolSecurityPolicy(ToolRiskClass.EXTERNAL_SIDE_EFFECT, ConfirmationPolicy.ALWAYS, ReplaySafety.NEVER_REPLAY),
            keywords = setOf("camera", "gallery", "document", "folder", "contact", "record", "bluetooth"))
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
        "halo_ui_wait" -> descriptor(definition.name, definition, ToolCategory.PERCEPTION,
            readOnly(definition.name), keywords = setOf("wait", "screen", "observe"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.screen.observe"))))
        "halo_ui_set_checked" -> descriptor(definition.name, definition, ToolCategory.HOST_CONTROL,
            ToolSecurityPolicy(risk = ToolRiskClass.REVERSIBLE, confirmation = ConfirmationPolicy.USER_POLICY,
                replaySafety = ReplaySafety.NEVER_REPLAY), keywords = setOf("toggle", "switch", "checkbox", "screen"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.screen.control"))),
            presentation = ToolPresentation(title = "Change a screen toggle", actionVerb = "Set checked state"))
        "halo_ui_click" -> descriptor(definition.name, definition, ToolCategory.HOST_CONTROL,
            ToolSecurityPolicy(
                risk = ToolRiskClass.EXTERNAL_SIDE_EFFECT,
                confirmation = ConfirmationPolicy.ALWAYS,
                replaySafety = ReplaySafety.NEVER_REPLAY,
            ), keywords = setOf("click", "tap", "ui"),
            requirements = setOf(ToolRequirement.Capability(CapabilityId("host.screen.control"))))
        "halo_ui_scroll", "halo_ui_search" -> descriptor(definition.name, definition, ToolCategory.HOST_CONTROL,
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
            ), keywords = setOf("back", "home", "global", "recents", "notifications", "quick settings", "lock", "screenshot", "menu", "dpad"),
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
            ExecutorKind.ROUTINE_MESSAGE -> routineMessage(definition)
            ExecutorKind.WORKSPACE -> workspace(definition)
            ExecutorKind.MODEL_SETUP -> modelSetup(definition)
            ExecutorKind.FILE -> file(definition)
            ExecutorKind.INTEGRITY -> integrity(definition)
            ExecutorKind.MEMORY -> memory(definition)
            ExecutorKind.APPLICATION -> application(definition)
            ExecutorKind.DEVICE -> device(definition)
            ExecutorKind.DEVICE_INSIGHT -> deviceInsight(definition)
            ExecutorKind.CLIPBOARD -> clipboard(definition)
            ExecutorKind.WEB_INSIGHT -> webInsight(definition)
            ExecutorKind.ALARM -> alarm(definition)
            ExecutorKind.CALENDAR -> calendar(definition)
            ExecutorKind.APP_SEMANTIC -> appSemantic(definition)
            ExecutorKind.COMMUNICATION -> communication(definition)
            ExecutorKind.MEDIA -> media(definition)
            ExecutorKind.NAVIGATION -> navigation(definition)
            ExecutorKind.SYSTEM -> system(definition)
            ExecutorKind.ASSISTANT -> assistant(definition)
            ExecutorKind.ACCESSIBILITY -> accessibility(definition)
            ExecutorKind.USE_SKILL -> useSkill(definition)
            ExecutorKind.NOTIFICATION_REPLY -> notificationReply(definition)
            ExecutorKind.ROUTINE -> routineCreation(definition)
            ExecutorKind.PROCEDURE -> procedure(definition)
            ExecutorKind.PRESENTATION -> presentation(definition)
            ExecutorKind.FOLLOWUP -> followups(definition)
        }

    enum class ExecutorKind {
        CURRENT_TIME,
        STORAGE_STATUS,
        NOTIFICATION_DIGEST,
        ROUTINE_MESSAGE,
        WORKSPACE,
        MODEL_SETUP,
        FILE,
        INTEGRITY,
        MEMORY,
        APPLICATION,
        DEVICE,
        DEVICE_INSIGHT,
        CLIPBOARD,
        WEB_INSIGHT,
        ALARM,
        CALENDAR,
        APP_SEMANTIC,
        COMMUNICATION,
        MEDIA,
        NAVIGATION,
        SYSTEM,
        ASSISTANT,
        ACCESSIBILITY,
        USE_SKILL,
        NOTIFICATION_REPLY,
        ROUTINE,
        PROCEDURE,
        PRESENTATION,
        FOLLOWUP,
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
