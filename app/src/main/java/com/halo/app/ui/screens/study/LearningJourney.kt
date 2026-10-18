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
)

/** Extracted text is a draft until the learner confirms it; confidence is never fabricated. */
data class HomeworkExercise(
    val id: String,
    val text: String,
    val sourceObjectId: String? = null,
    val locator: String? = null,
    val uncertainty: String? = null,
    val reviewed: Boolean = false,
)
