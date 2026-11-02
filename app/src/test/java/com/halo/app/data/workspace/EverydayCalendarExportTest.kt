package com.halo.app.data.workspace

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class EverydayCalendarExportTest {
    private val bytes = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nEND:VCALENDAR\r\n".toByteArray()

    @Test fun rejectedPreparationRollsBackManifestFileRowsAndOwnedBytesAtEveryBoundary() = runBlocking {
        for (boundary in 1..3) WorkspaceFileToolsFixture().use { f ->
            var validations = 0
            val result = runCatching {
                f.files.createManagedFileWithPreparedSource("Halo-calendar.ics", "text/calendar", bytes, "{}",
                    validateSource = { if (++validations == boundary) error("Reviewed source changed") },
                    prepareSource = { f.notes.createNote(title = "Calendar manifest").id })
            }
            assertTrue("boundary $boundary", result.isFailure)
            assertTrue(f.workspace.observe().first().isEmpty())
            assertTrue(f.root.walkTopDown().none { it.isFile })
        }
    }

    @Test fun cancellationAfterPreparingManifestDoesNotPublishAnOrphan() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            var validations = 0
            val failure = runCatching {
                f.files.createManagedFileWithPreparedSource("Halo-calendar.ics", "text/calendar", bytes, "{}",
                    validateSource = { if (++validations == 2) throw CancellationException("Parent authority revoked") },
                    prepareSource = { f.notes.createNote(title = "Calendar manifest").id })
            }.exceptionOrNull()
            assertTrue(failure is CancellationException)
            assertTrue(f.workspace.observe().first().isEmpty())
            assertTrue(f.root.walkTopDown().none { it.isFile })
        }
    }

    @Test fun successfulPreparationPublishesExactBytesAndOneManifestTogether() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            var validations = 0
            val file = f.files.createManagedFileWithPreparedSource("Halo-calendar.ics", "text/calendar", bytes, "{}",
                validateSource = { validations++ }, prepareSource = { f.notes.createNote(title = "Calendar manifest").id })
            assertArrayEquals(bytes, f.bytes(file))
            assertEquals(2, f.workspace.observe().first().size)
            assertEquals(3, validations)
        }
    }
}
