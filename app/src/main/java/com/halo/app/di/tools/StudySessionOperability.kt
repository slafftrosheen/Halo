package com.halo.app.di.tools

import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolDefinition

object StudySessionOperability {
    val reads = setOf("halo_study_current")
    val writes = setOf("halo_study_session_action")
    fun forDefinition(definition: ToolDefinition): ToolOperability? {
        if (definition.name !in reads + writes) return null
        return ToolOperability(capabilityId = CapabilityId("halo.study.active"), owner = "ActiveStudyToolsOwner/StudyViewModel/StudyUseCases/StudyTutorGateway",
            operation = if (definition.name in reads) ToolOperation.QUERY else ToolOperation.EDIT,
            resultEncoding = ToolResultEncoding.JSON, resultSchema = mapOf("type" to "object", "properties" to mapOf(
                "sessionId" to mapOf("type" to "string", "maxLength" to 200),
                "sourceVersion" to mapOf("type" to "string", "pattern" to "^study-view:[a-f0-9]{64}$"),
                "requestId" to mapOf("type" to "string", "maxLength" to 200),
                "status" to mapOf("type" to "string", "enum" to listOf("DISPATCHED")),
                "completionConfirmed" to mapOf("type" to "boolean"))),
            sourceVersionArguments = if (definition.name in writes) setOf("sourceVersion") else emptySet(),
            resultIdFields = setOf("sessionId"), limitations = setOf(
                "Requires one attached RESUMED native Study lesson; missing/ambiguous/stale/pending-draft/busy views reject. Stored-data Study tools remain available separately.",
                "Native callbacks on Main retain source, learner authority, execution-choice, privacy, Kids, provider and spending gates; canonical reads run on IO.",
                "Every action consumes its exact source/UI token; queued dispatch is not saved work, grading, generated cards or success. NEVER_REPLAY uncertain actions.",
                "Read actual active/stored lesson IDs and content after dispatch; no human answer, card acceptance or solution consent may be inferred.",
                "Returned lesson/source metadata and tutor text are personal Workspace data; no remote exposure/result transfer without existing category consent."))
    }
}
