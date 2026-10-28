package com.halo.app.ui.screens.study

/** Shared Kids/Study journey state; assessment remains owned by stored attempts and feedback. */
enum class LearningStage { START, DISCOVER, EXPLAIN, EXPLORE, PRACTICE, REFLECT, CONTINUE }
data class LearningJourney(
    val stage: LearningStage = LearningStage.DISCOVER,
    val priorKnowledge: String = "",
    val branches: List<String> = emptyList(),
    val currentBranch: String? = null,
    val reflection: String = "",
    val exercises: List<HomeworkExercise> = emptyList(),
    val concepts: List<LearningConcept> = emptyList(),
    val explainBack: ExplainBackReview? = null,
    /** Explicit learner choice; never inferred from an empty answer or model response. */
    val probeSkipped: Boolean = false,
    val explanationDepth: com.halo.app.study.StudyExplanationDepth = com.halo.app.study.StudyExplanationDepth.BALANCED,
    /** One reusable artifact per journey; revision prevents overwriting later Notebook edits. */
    val summaryNoteId: String? = null,
    val summaryNoteRevision: Long? = null,
)

/** Extracted text is a draft until the learner confirms it; confidence is never fabricated. */
data class HomeworkExercise(
    val id: String,
    val text: String,
    val sourceObjectId: String? = null,
    val locator: String? = null,
    val uncertainty: String? = null,
    val reviewed: Boolean = false,
    val finished: Boolean = false,
)

/** Explored means an explanation arrived; it never means mastery. */
data class LearningConcept(val id: String, val title: String, val parentId: String? = null, val explored: Boolean = false)
data class ExplainBackObservation(val quote: String, val suggestion: String)
data class ExplainBackReview(val transcript: String, val observations: List<ExplainBackObservation>, val questions: List<String>)
