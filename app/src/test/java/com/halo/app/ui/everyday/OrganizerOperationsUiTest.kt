package com.halo.app.ui.everyday

import android.app.Application
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.notebook.*
import com.halo.app.everyday.organizer.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class OrganizerOperationsUiTest {
    private val task = OrganizerRecord("task", OrganizerKind.TASK, "Plan", createdAt = Instant.EPOCH)
    @Test fun orbChangesFromCreationToExactItemOperationsAndKeepsKidsReadOnly() {
        val top = organizerOrbActions(null, "TODAY", false)
        assertTrue(top.containsAll(listOf(OrganizerOrbAction.TASK, OrganizerOrbAction.EVENT, OrganizerOrbAction.TIMER, OrganizerOrbAction.ALARM, OrganizerOrbAction.BRIEF)))
        assertFalse(OrganizerOrbAction.DELEGATE in top)
        val human = organizerOrbActions(task, "TODAY", false)
        assertTrue(human.containsAll(listOf(OrganizerOrbAction.COMPLETE, OrganizerOrbAction.DELEGATE, OrganizerOrbAction.WATCH, OrganizerOrbAction.ATTACH)))
        assertFalse(OrganizerOrbAction.TASK in human); assertFalse(OrganizerOrbAction.SOURCES in human)
        val bound = task.copy(commitment = OrganizerCommitment(CommitmentOwner.HALO, execution = OrganizerExecutionBinding("plan", "routine")))
        val halo = organizerOrbActions(bound, "PLANNER", false)
        assertTrue(OrganizerOrbAction.STOP in halo); assertTrue(OrganizerOrbAction.TAKE_OVER in halo)
        assertFalse(OrganizerOrbAction.COMPLETE in halo); assertFalse(OrganizerOrbAction.EDIT in halo)
        assertTrue(organizerOrbActions(bound, "PLANNER", true).isEmpty())
        assertEquals(OrganizerOrbAction.CAPTURE, organizerOrbActions(null, "INBOX", false).first())
        assertEquals(OrganizerOrbAction.ROUTINE, organizerOrbActions(null, "ROUTINES", false).first())
    }
    @Test fun linksReturnToTheOwningInstrumentAndCannotOpenTrashedContent() {
        fun item(type: WorkspaceObjectType, ref: String = "content") = WorkspaceObject(WorkspaceObjectId("object:one"), type, "Object", 0, 0,
            WorkspaceObjectSource.USER_CREATED, WorkspaceObjectLifecycle.ACTIVE, false, ref)
        val note = item(WorkspaceObjectType.NOTE)
        assertEquals("notebook/note/object%3Aone", organizerWorkspaceRoute(note))
        assertEquals("notebook/memory/object%3Aone", organizerWorkspaceRoute(item(WorkspaceObjectType.MEMORY)))
        assertEquals("notebook/canvas/object%3Aone", organizerWorkspaceRoute(item(WorkspaceObjectType.CANVAS)))
        assertEquals("studio?projectId=object%3Aone", organizerWorkspaceRoute(item(WorkspaceObjectType.ARTIFACT, "studio:project")))
        assertEquals("notebook/canvas/object%3Aone", organizerWorkspaceRoute(item(WorkspaceObjectType.ARTIFACT, "canvas:map")))
        assertNull(organizerWorkspaceRoute(note.copy(lifecycle = WorkspaceObjectLifecycle.TRASHED)))
        assertNull(organizerWorkspaceRoute(note.copy(lifecycle = WorkspaceObjectLifecycle.ARCHIVED)))
    }
    @Test fun responsibilityRecoveryAndResearchControlsHaveReviewedHostCopy() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val keys = listOf("Shared", "Stopwatch", "Planner", "Capture to Inbox", "Delegate to Halo", "Take over", "Make shared",
            "Watch / monitor", "Needs your decision", "Review work plan", "Run reviewed plan now", "Recorded result",
            "Search / Research", "Research / Web", "This saved result is withheld because its source access changed. Review sources before reading it.")
        for (tag in listOf("ru", "lv", "pl")) {
            val resources = app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }).resources
            keys.forEach { key -> assertNotEquals("$tag: $key", key, com.halo.app.language.resolveUiText(resources, key)) }
        }
    }
}
