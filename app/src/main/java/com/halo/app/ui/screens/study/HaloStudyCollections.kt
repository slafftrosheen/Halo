package com.halo.app.ui.screens.study

import com.halo.app.data.study.StudySessionRecord
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.sdk.runtime.study.StudyLearnerLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine

internal data class HaloStudyCollection(val id: String, val title: String, val lessons: List<StudySessionRecord>)

/** Collections point at existing source material. Each Study lesson and progress record keeps its own identity. */
internal fun observeStudyCollections() = combine(
    Graph.notebookOrganizationRepository.observeCollections(),
    Graph.notebookOrganizationRepository.observeCollectionLinks(),
    Graph.studyUseCases.observeCourseSessions(),
) { collections, membership, lessons ->
    val authority = Graph.kidsPreferences.adultActionRevision()
    val adults = lessons.filter { it.learnerLevel == StudyLearnerLevel.ADULT }
    val order = Graph.studyLessonStore.course.outline(adults, activeId = null).lessons.map { it.id }
    val byId = adults.associateBy { it.id }
    val ordered = order.mapNotNull(byId::get)
    collections.mapNotNull { collection ->
        val sources = membership[collection.id].orEmpty()
        val inside = ordered.filter { lesson -> lesson.sourceObjectIds.any(sources::contains) }
        HaloStudyCollection(collection.id, collection.name, inside).takeIf { inside.isNotEmpty() }
    }.also { Graph.kidsPreferences.requireAdultAction(authority) }
}.catch { error ->
    if (error is CancellationException) throw error
    emit(emptyList())
}
