package com.halo.app.data.workspace

import com.halo.app.data.study.StudyRepository
import com.halo.app.study.*
import com.halo.sdk.runtime.study.StudyLanguagePreferences
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class WorkspaceStudyContinuityTest {
    @Test fun staleHandoffDoesNotCreateAStudySessionButExactCurrentSourceDoes() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val source = f.text.create("Source", WorkspaceTextFormat.MARKDOWN, "# Original source")
            val resolver = WorkspaceStudySourceResolver(f.context, f.documents, f.workspace, f.files, noPdf())
            val study = StudyUseCases(StudyRepository(f.database, f.database.studyDao()), sourceResolver = resolver)
            val changed = f.text.replace(source.id, source.revision, "# Changed source")
            val stale = study.startMaterial(listOf(source.id), StudyLanguagePreferences(), "Goal", expectedVersions = mapOf(source.id to source.revision.toString()))
            assertEquals(StudyUnavailableReason.SOURCE_CHANGED, (stale as StudyStartResult.Unavailable).reason)
            assertTrue(study.observeActiveSessions().first().isEmpty())
            val started = study.startMaterial(listOf(source.id), StudyLanguagePreferences(), "Goal", expectedVersions = mapOf(source.id to changed.revision.toString())) as StudyStartResult.Started
            assertEquals(changed.revision.toString(), study.getSession(started.session.id)!!.sources.single().revision)
        }
    }
    @Test fun trashedHandoffCannotBeSilentlyTurnedIntoAGeneralKnowledgeLesson() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val source = f.text.create("Source", WorkspaceTextFormat.TXT, "Original")
            val study = StudyUseCases(StudyRepository(f.database, f.database.studyDao()),
                sourceResolver = WorkspaceStudySourceResolver(f.context, f.documents, f.workspace, f.files, noPdf()))
            f.workspace.trash(com.halo.app.data.notebook.WorkspaceObjectId(source.id))
            val result = study.startMaterial(listOf(source.id), StudyLanguagePreferences(), "Goal",
                policy = com.halo.sdk.runtime.study.StudyKnowledgePolicy.SOURCE_PLUS_GENERAL,
                expectedVersions = mapOf(source.id to source.revision.toString()))
            assertEquals(StudyUnavailableReason.SOURCE_CHANGED, (result as StudyStartResult.Unavailable).reason)
            assertTrue(study.observeActiveSessions().first().isEmpty())
        }
    }
    @Test fun exactHandoffCannotBypassAnUnavailableSourceResolver() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val source = f.text.create("Source", WorkspaceTextFormat.TXT, "Original")
            val study = StudyUseCases(StudyRepository(f.database, f.database.studyDao()))
            val result = study.startMaterial(listOf(source.id), StudyLanguagePreferences(), "Goal",
                expectedVersions = mapOf(source.id to source.revision.toString()))
            assertEquals(StudyUnavailableReason.SOURCE_STORE_UNAVAILABLE, (result as StudyStartResult.Unavailable).reason)
            assertTrue(study.observeActiveSessions().first().isEmpty())
        }
    }

}
