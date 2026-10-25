package com.halo.app.data.workspace

import com.halo.app.di.tools.WorkspaceToolExecutor
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolRejectionKind
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class WorkspaceCreateNoteToolTest {
    private fun executor(f: WorkspaceFileToolsFixture) = WorkspaceToolExecutor(f.notes, f.workspace, f.contextOwner())

    @Test fun explicitTitleAndLiteralBodyAreSavedIndependentlyWithActualIdReceipt() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val title = "HALO-AI-20261025-NOTE-C"
            val body = "Violet lanterns glow at dawn."
            val result = executor(f).execute(ToolCall("create", "halo_create_note", mapOf("body" to body, "title" to title)))
            assertTrue(result.toString(), result is ToolExecutionResult.Success)
            val saved = f.notes.observeNotes().first().single()
            val readback = requireNotNull(f.notes.getNote(saved.id))
            assertEquals(title, readback.title)
            assertEquals(body, readback.body)
            assertTrue((result as ToolExecutionResult.Success).output.contains(saved.id))
        }
    }

    @Test fun omittedTitleRetainsBodyDerivedTitleAndLegacyBodyTrimming() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val result = executor(f).execute(ToolCall("legacy", "halo_create_note", mapOf("body" to "  # First line\nLiteral **body**.  ")))
            assertTrue(result.toString(), result is ToolExecutionResult.Success)
            val saved = f.notes.observeNotes().first().single()
            assertEquals("First line", saved.title)
            assertEquals("# First line\nLiteral **body**.", saved.body)
        }
    }

    @Test fun titleAtExistingRenameLimitIsAcceptedWithoutTruncation() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val title = "T".repeat(120)
            val result = executor(f).execute(ToolCall("limit", "halo_create_note", mapOf("body" to "Body", "title" to title)))
            assertTrue(result.toString(), result is ToolExecutionResult.Success)
            assertEquals(title, f.notes.observeNotes().first().single().title)
        }
    }

    @Test fun invalidTitlesAndUnknownArgumentsCannotCreateAnyNote() = runBlocking {
        WorkspaceFileToolsFixture().use { f ->
            val tools = executor(f)
            val invalid = listOf(
                mapOf("body" to "Body", "title" to "T".repeat(121)),
                mapOf("body" to "Body", "title" to ""),
                mapOf("body" to "Body", "title" to " \n\t"),
                mapOf("body" to "Body", "title" to 42),
                mapOf("body" to "Body", "title" to listOf("Title")),
                mapOf("body" to "Body", "title" to "Title", "unexpected" to true),
                mapOf("body" to "Body", "unexpected" to true),
            )
            invalid.forEachIndexed { index, arguments ->
                val result = tools.execute(ToolCall("invalid-$index", "halo_create_note", arguments))
                assertTrue("Fixture $index: $result", result is ToolExecutionResult.Rejected)
                assertEquals(ToolRejectionKind.INVALID_ARGUMENTS, (result as ToolExecutionResult.Rejected).kind)
                assertTrue("Fixture $index must not create a note", f.notes.observeNotes().first().isEmpty())
            }
        }
    }

    @Test fun schemaExposesOptionalBoundedTitleWhileKeepingBodyRequired() {
        WorkspaceFileToolsFixture().use { f ->
            val schema = executor(f).definitions().single { it.name == "halo_create_note" }.parameters
            val properties = schema["properties"] as Map<*, *>
            val title = properties["title"] as? Map<*, *>
            assertNotNull("Named notes need an explicit title field", title)
            assertEquals("string", title!!["type"])
            assertEquals(1, title["minLength"])
            assertEquals(120, title["maxLength"])
            assertEquals(listOf("body"), schema["required"])
            assertEquals(false, schema["additionalProperties"])
        }
    }
}
