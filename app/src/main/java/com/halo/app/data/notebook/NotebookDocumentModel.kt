package com.halo.app.data.notebook

/**
 * Typed block kinds supported by the canonical notebook document.
 *
 * Values are persisted by name. Unknown/legacy stored values decode to [PARAGRAPH] through
 * [fromStored] so an older or newer writer can never make a document unreadable: the block text is
 * always preserved even when the type is not understood.
 */
enum class NoteBlockType {
    PARAGRAPH,
    HEADING,
    HEADING_2,
    HEADING_3,
    BULLET_LIST,
    NUMBERED_LIST,
    CHECKLIST,
    QUOTE,
    CODE,
    DIVIDER,
    REFERENCE;

    val isList: Boolean
        get() = this == BULLET_LIST || this == NUMBERED_LIST || this == CHECKLIST

    val isHeading: Boolean
        get() = this == HEADING || this == HEADING_2 || this == HEADING_3

    /** Heading depth for outline rendering; 0 for non-headings. */
    val headingLevel: Int
        get() = when (this) {
            HEADING -> 1
            HEADING_2 -> 2
            HEADING_3 -> 3
            else -> 0
        }

    /** Next heading depth for promote/demote operations, clamped to the supported range. */
    fun withHeadingLevel(level: Int): NoteBlockType = when (level.coerceIn(1, 3)) {
        1 -> HEADING
        2 -> HEADING_2
        else -> HEADING_3
    }

    val markdownMarker: String
        get() = when (this) {
            HEADING -> "#"
            HEADING_2 -> "##"
            HEADING_3 -> "###"
            BULLET_LIST -> "-"
            NUMBERED_LIST -> "1."
            CHECKLIST -> "- [ ]"
            QUOTE -> ">"
            CODE -> "```"
            DIVIDER -> "---"
            else -> ""
        }

    val label: String
        get() = when (this) {
            PARAGRAPH -> "Paragraph"
            HEADING, HEADING_2, HEADING_3 -> "Heading ${headingLevel}"
            BULLET_LIST -> "Bulleted list"
            NUMBERED_LIST -> "Numbered list"
            CHECKLIST -> "Checklist"
            QUOTE -> "Quote"
            CODE -> "Code"
            DIVIDER -> "Divider"
            REFERENCE -> "Reference"
        }

    companion object {
        fun fromStored(value: String?): NoteBlockType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: PARAGRAPH
    }
}

/**
 * One ordered, stable-identity block in a notebook document.
 *
 * [position] is a derived index (the repository re-sequences on every commit); [id] is the durable
 * anchor that selections, proposals and recovery drafts point at.
 */
data class NoteBlock(
    val id: String,
    val position: Int,
    val type: NoteBlockType,
    val text: String,
    val checked: Boolean = false,
    val indent: Int = 0,
    val refObjectId: String? = null,
    val language: String? = null,
) {
    fun withPosition(position: Int): NoteBlock = copy(position = position)

    /**
     * True when this block claims to be a reference but has no usable target: either the object id
     * is missing (legacy `[[object-id]]` placeholder) or the editor knows the target was deleted.
     */
    fun needsRelink(knownObjectIds: Set<String>? = null): Boolean {
        if (type != NoteBlockType.REFERENCE) return false
        val target = refObjectId ?: referenceTarget(text, refObjectId)
        if (target.isNullOrBlank()) return true
        return knownObjectIds != null && target !in knownObjectIds
    }
}

/** A snapshot of one note document with the committed revision it was loaded at. */
data class NotebookDocument(
    val noteId: String,
    val title: String,
    val revision: Long,
    val blocks: List<NoteBlock>,
    val lifecycle: WorkspaceLifecycle,
    val updatedAt: Long,
    val createdAt: Long,
    val schemaVersion: Int = SCHEMA_VERSION,
    /** True when [title] is still the automatic projection of the body, not a user decision. */
    val titleIsDerived: Boolean = true,
) {
    val isWritable: Boolean get() = lifecycle != WorkspaceLifecycle.TRASHED

    companion object {
        /** Version of the canonical document shape. Bumped when block semantics change. */
        const val SCHEMA_VERSION = 2
    }
}

/** A selection anchored to a stable block id plus text offsets, never to a raw index. */
data class DocumentSelection(
    val blockId: String,
    val start: Int,
    val end: Int,
) {
    val isEmpty: Boolean get() = start >= end

    fun normalized(length: Int): DocumentSelection {
        val safeStart = start.coerceIn(0, length)
        val safeEnd = end.coerceIn(0, length)
        return if (safeStart <= safeEnd) copy(start = safeStart, end = safeEnd)
        else copy(start = safeEnd, end = safeStart)
    }
}

data class DocumentOutlineEntry(
    val blockId: String,
    val level: Int,
    val title: String,
    val position: Int,
)

/** Why a write was refused before it could touch stored content. */
enum class DocumentRejection {
    /** The note object does not exist (deleted or never created). */
    MISSING,

    /** The note exists but is in trash; repository-level guard, not only a UI state. */
    TRASHED,
}

/** Result of a revision-checked document commit. */
sealed interface CommitOutcome {
    data class Committed(
        val revision: Long,
        val updatedAt: Long,
        val acknowledgedDraftSequence: Long,
    ) : CommitOutcome

    /** Another writer committed first. Local content is returned so the caller can keep it. */
    data class Conflict(
        val storedRevision: Long,
    ) : CommitOutcome

    data class Rejected(val reason: DocumentRejection) : CommitOutcome

    data class Failed(val message: String) : CommitOutcome
}

/** Durable recovery draft: structure, base revision and edit sequence. */
data class RecoveryDraft(
    val noteId: String,
    val blocks: List<NoteBlock>,
    val baseRevision: Long,
    val sequence: Long,
    val updatedAt: Long,
) {
    /** Only true when the draft is a *newer* unsaved state than the committed document. */
    fun supersedes(committedRevision: Long, committedUpdatedAt: Long): Boolean =
        baseRevision >= committedRevision && updatedAt >= committedUpdatedAt
}

/** Metadata observed after a successful commit; surfaced to the editor status bar. */
data class DocumentSaveMetadata(
    val revision: Long,
    val updatedAt: Long,
    val acknowledgedDraftSequence: Long,
)

/** A late AI proposal bound to the exact revision and selection it was generated from. */
data class DocumentProposal(
    val id: String,
    val operation: DocumentOperation,
    val baseRevision: Long,
    val blockId: String?,
    val selectionStart: Int,
    val selectionEnd: Int,
    val content: String,
    val generationId: String,
    val sourceObjectIds: List<String> = emptyList(),
    val createdAt: Long = 0,
) {
    /** Rejected as soon as the document moved past the revision the proposal was built from. */
    fun isStale(currentRevision: Long): Boolean = baseRevision != currentRevision
}

enum class DocumentOperation {
    SUMMARIZE,
    REWRITE,
    EXPLAIN,
    TRANSLATE,
    TURN_INTO_TASKS,
    CONTINUE,
    OUTLINE,
    STUDY_QUESTIONS,
    ;

    val label: String
        get() = when (this) {
            SUMMARIZE -> "Summarize"
            REWRITE -> "Rewrite"
            EXPLAIN -> "Explain"
            TRANSLATE -> "Translate"
            TURN_INTO_TASKS -> "Turn into tasks"
            CONTINUE -> "Continue writing"
            OUTLINE -> "Outline"
            STUDY_QUESTIONS -> "Practice questions"
        }

    /** How an accepted result lands in the document. */
    val placement: ProposalPlacement
        get() = when (this) {
            REWRITE -> ProposalPlacement.REPLACE_SELECTION
            TURN_INTO_TASKS -> ProposalPlacement.REPLACE_SELECTION
            SUMMARIZE, EXPLAIN, TRANSLATE, STUDY_QUESTIONS -> ProposalPlacement.INSERT_BELOW
            CONTINUE -> ProposalPlacement.APPEND_AT_END
            OUTLINE -> ProposalPlacement.INSERT_BELOW
        }
}

enum class ProposalPlacement { REPLACE_SELECTION, INSERT_BELOW, APPEND_AT_END }

/** Outline derived purely from the current block list. */
fun List<NoteBlock>.outline(): List<DocumentOutlineEntry> = filter { it.type.isHeading }
    .map { block ->
        DocumentOutlineEntry(
            blockId = block.id,
            level = block.type.headingLevel,
            title = block.text.ifBlank { "Untitled heading" },
            position = block.position,
        )
    }

/** Resolve a reference target that may have been stored as a bare `[[id]]` placeholder. */
fun referenceTarget(text: String, refObjectId: String?): String? =
    refObjectId?.takeIf { it.isNotBlank() }
        ?: Regex("^\\[\\[([^\\]|]+)(?:\\|.*)?\\]\\]$").find(text.trim())?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotEmpty() }
