package com.halo.app.di.tools

import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolDefinition

object StudySessionOperability {
    val reads = setOf("halo_study_current")
    val writes = setOf("halo_study_session_action")
    private val nullableText = mapOf("type" to listOf("string", "null"))
    private val ids = mapOf("type" to "array", "items" to mapOf("type" to "string", "maxLength" to 200))
    private val source = mapOf("type" to "object", "properties" to mapOf(
        "objectId" to mapOf("type" to "string", "maxLength" to 200), "sourceVersion" to nullableText,
        "title" to mapOf("type" to "string", "maxLength" to 200), "stale" to mapOf("type" to "boolean"),
        "kind" to mapOf("type" to "string", "enum" to listOf("TEXT", "PDF", "IMAGE", "UNAVAILABLE")),
        "pageCount" to mapOf("type" to listOf("integer", "null"), "minimum" to 1)),
        "required" to listOf("objectId", "sourceVersion", "title", "stale", "kind", "pageCount"), "additionalProperties" to false)
    fun forDefinition(definition: ToolDefinition): ToolOperability? {
        if (definition.name !in reads + writes) return null
        return ToolOperability(capabilityId = CapabilityId("halo.study.active"), owner = "ActiveStudyToolsOwner/StudyViewModel/StudyUseCases/StudyTutorGateway",
            operation = if (definition.name in reads) ToolOperation.QUERY else ToolOperation.EDIT,
            resultEncoding = ToolResultEncoding.JSON, resultSchema = mapOf("type" to "object", "properties" to mapOf(
                "sessionId" to mapOf("type" to "string", "maxLength" to 200),
                "sourceVersion" to mapOf("type" to "string", "pattern" to "^study-view:[a-f0-9]{64}$"),
                "requestId" to mapOf("type" to "string", "maxLength" to 200),
                "action" to mapOf("type" to "string", "enum" to StudySessionTools.actions.sorted()),
                "stage" to mapOf("type" to "string"), "busy" to mapOf("type" to "boolean"),
                "hasLearnerDraft" to mapOf("type" to "boolean"), "finished" to mapOf("type" to "boolean"),
                "reviewIntensity" to mapOf("type" to "string"),
                "sources" to mapOf("type" to "array", "maxItems" to 4, "items" to source),
                "sourceCandidates" to mapOf("type" to "array", "items" to source),
                "activeVisualId" to nullableText,
                "pdfPages" to mapOf("type" to "object", "additionalProperties" to mapOf("type" to "integer", "minimum" to 1)),
                "readerOpen" to mapOf("type" to "boolean"), "readerPlaying" to mapOf("type" to "boolean"),
                "attemptIds" to ids, "turnIds" to ids, "practiceIds" to ids,
                "latestTutorText" to nullableText, "notice" to nullableText,
                "exportedNoteId" to nullableText, "restartedSessionId" to nullableText,
                "collection" to mapOf("type" to "string", "enum" to listOf("sourceCandidates", "attemptIds", "turnIds", "practiceIds")),
                "offset" to mapOf("type" to "integer", "minimum" to 0),
                "totalCount" to mapOf("type" to "integer", "minimum" to 0),
                "nextOffset" to mapOf("type" to listOf("integer", "null"), "minimum" to 0),
                "truncated" to mapOf("type" to "boolean"),
                "collectionCounts" to mapOf("type" to "object", "properties" to
                    listOf("sourceCandidates", "attemptIds", "turnIds", "practiceIds").associateWith {
                        mapOf("type" to "integer", "minimum" to 0)
                    }, "additionalProperties" to false),
                "knowledgeProbeSkipped" to mapOf("type" to "boolean"),
                "journeyStage" to mapOf("type" to "string", "enum" to com.halo.app.ui.screens.study.LearningStage.entries.map { it.name }),
                "completedHints" to mapOf("type" to "integer", "minimum" to 0),
                "hasPriorKnowledge" to mapOf("type" to "boolean"),
                "hasSavedReflection" to mapOf("type" to "boolean"),
                "explanationDepth" to mapOf("type" to "string", "enum" to com.halo.app.study.StudyExplanationDepth.entries.map { it.name }),
                "status" to mapOf("type" to "string", "enum" to listOf("DISPATCHED")),
                "completionConfirmed" to mapOf("type" to "boolean")), "additionalProperties" to false),
            sourceVersionArguments = if (definition.name in writes) setOf("sourceVersion") else emptySet(),
            resultIdFields = setOf("sessionId"), limitations = setOf(
                "Requires one attached RESUMED native Study lesson; missing/ambiguous/stale/busy views reject. Pending drafts block actions except stop, skip_probe and explanation_depth, which preserve the draft. Depth is an explicit local presentation preference, not grading, learner-level inference or a generation request. Stored-data Study tools remain available separately.",
                "Native callbacks on Main retain source, learner authority, execution-choice, privacy, Kids, provider and spending gates; canonical reads run on IO.",
                "Every action consumes its exact source/UI token; queued dispatch is not saved work, grading, generated cards or success. NEVER_REPLAY uncertain actions.",
                "Read actual active/stored lesson IDs and content after dispatch; no human answer, card acceptance or solution consent may be inferred.",
                "knowledge_probe accepts the learner's literal starting description only during DISCOVER; explore_topic accepts their chosen topic (300 characters maximum); reflect saves their literal reflection; reflect_feedback requests tentative explain-back feedback; suggest_topics requests suggestions without selecting them. No action infers learner answers or mastery. Kids full solutions require two actual accepted hints for the current exercise and material.",
                "select_visual and select_pdf_page use observed source IDs and valid pages. read_source/read_reply and reader controls reuse the native reading owner and existing speech/privacy gates. Playback dispatch is not evidence of heard speech.",
                "Current metadata pages one collection within 3,800 characters. Empty unselected collections are not empty history; consult collectionCounts and nextOffset. Current sources remain complete; tutor text is an excerpt. Re-read after source/UI changes before acting.",
                "Returned lesson/source metadata and tutor text are personal Workspace data; no remote exposure/result transfer without existing category consent."))
    }
}
