package com.halo.app.ui.screens.study

import android.app.Application
import com.halo.app.ai.IntentQualificationFixture.MemoryStorage
import com.halo.app.data.study.StudySessionRecord
import com.halo.sdk.runtime.study.StudyLanguagePreferences
import com.halo.sdk.runtime.study.StudySessionLifecycle
import com.halo.sdk.runtime.study.TutorStage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class StudyCourseOrderTest {
    private fun lesson(id: String, order: Long, completed: Boolean = false) = StudySessionRecord(
        id = id, goal = id, languages = StudyLanguagePreferences(), sourceObjectIds = emptyList(), sourceRevision = null,
        conversationId = null, runId = null, interactionId = null, tutorStage = TutorStage.ASK,
        lifecycle = if (completed) StudySessionLifecycle.COMPLETED else StudySessionLifecycle.ACTIVE,
        currentExerciseId = null, solutionRevealed = false, completedAtEpochMs = order.takeIf { completed },
        createdAtEpochMs = order, updatedAtEpochMs = order,
    )

    @Test fun orderingSurvivesReopeningAndCompletionComesFromSavedLessons() = runBlocking {
        val storage = MemoryStorage()
        val store = StudyCourseOrder(storage)
        val lessons = listOf(lesson("third", 3), lesson("first", 1, completed = true), lesson("second", 2))
        val before = store.outline(lessons, null)
        assertEquals(listOf("first", "second", "third"), before.lessons.map { it.id })
        assertEquals(listOf(StudyCourseProgress.DONE, StudyCourseProgress.NOW, StudyCourseProgress.LATER),
            before.lessons.map { it.progress })
        val reordered = store.move(before.version, "third", -1, null, {}) { lessons }
        assertEquals(listOf("first", "third", "second"), reordered.lessons.map { it.id })
        assertEquals(StudyCourseProgress.NOW, reordered.lessons[1].progress)
        val reopened = StudyCourseOrder(storage).outline(lessons, "second")
        assertEquals(reordered.version, reopened.version)
        assertEquals(reordered.lessons.map { it.id }, reopened.lessons.map { it.id })
        assertEquals(StudyCourseProgress.NOW, reopened.lessons.last().progress)
    }

    @Test fun staleApprovalCannotChangeCourseAfterAnotherReorder() = runBlocking<Unit> {
        val store = StudyCourseOrder(MemoryStorage())
        val lessons = listOf(lesson("first", 1), lesson("second", 2))
        val before = store.outline(lessons, null)
        val changed = store.move(before.version, "second", -1, null, {}) { lessons }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { store.move(before.version, "second", 1, null, {}) { lessons } }
        }
        assertEquals(changed, store.outline(lessons, null))
    }
}
