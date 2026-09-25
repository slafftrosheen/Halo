package com.halo.sdk.runtime.study

enum class TutorStage {
    ASK,
    HINT,
    EXPLAIN,
    ANALOGY,
    CHECK_ATTEMPT,
    PARTIAL_SOLUTION,
    SOLUTION
}

data class StudySession(
    val id: String,
    val assignmentLanguage: String?,
    val explanationLanguage: String?,
    val sourceObjectIds: List<String>,
    val interactionId: String?,
    val tutorStage: TutorStage,
)
