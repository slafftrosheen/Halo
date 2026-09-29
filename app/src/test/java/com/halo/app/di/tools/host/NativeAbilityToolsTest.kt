package com.halo.app.di.tools.host

import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.workspace.WorkspaceFile
import com.halo.app.data.workspace.WorkspaceFileRepository
import com.halo.app.data.notebook.WorkspaceObject
import com.halo.app.data.workspace.WorkspaceQuery
import com.halo.app.data.workspace.WorkspaceRepository
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolRejectionKind
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * RUNTIME-056 regression tests for the native ability batch.
 *
 * These pin the contracts that are easy to lose silently: that a read-only action never
 * invents a value the platform did not report, that checksum results are real digests when
 * compared with published test vectors, and that the web reader refuses local targets
 * before it ever opens a socket.
 */
@RunWith(RobolectricTestRunner::class)
class NativeAbilityToolsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // ---- device insight ----------------------------------------------------

    @Test
    fun `device info describes this device and rejects arguments`() = runTest {
        val tools = DeviceInsightTools(context)
        assertTrue(tools.definitions().map { it.name }.contains("halo_device_info"))

        val ok = tools.execute(ToolCall("c1", "halo_device_info", emptyMap()))
        assertTrue(ok is ToolExecutionResult.Success)
        val output = (ok as ToolExecutionResult.Success).output
        assertTrue(output.contains("sdk_int="))
        assertTrue(output.contains("model="))

        val rejected = tools.execute(ToolCall("c2", "halo_device_info", mapOf("level" to 1)))
        assertTrue(rejected is ToolExecutionResult.Rejected)
        assertEquals(
            ToolRejectionKind.INVALID_ARGUMENTS,
            (rejected as ToolExecutionResult.Rejected).kind,
        )
    }

    @Test
    fun `battery status never invents a level`() = runTest {
        val result = DeviceInsightTools(context).execute(ToolCall("c1", "halo_battery_status", emptyMap()))
        when (result) {
            is ToolExecutionResult.Success -> {
                val output = result.output
                assertTrue("a reported battery answer must state its level", output.contains("level_percent="))
                assertTrue(output.contains("charging_state="))
            }
            is ToolExecutionResult.Failed -> assertEquals("BATTERY_UNAVAILABLE", result.code)
            else -> throw AssertionError("unexpected battery result: $result")
        }
    }

    @Test
    fun `network status reports transports without claiming connectivity`() = runTest {
        val result = DeviceInsightTools(context).execute(ToolCall("c1", "halo_network_status", emptyMap()))
        assertTrue(result is ToolExecutionResult.Success)
        val output = (result as ToolExecutionResult.Success).output
        assertTrue(output.contains("transports="))
        assertTrue(output.contains("connected="))
        assertTrue(output.contains("validated_internet="))
    }

    // ---- clipboard ---------------------------------------------------------

    @Test
    fun `clipboard write then read round-trips and frames content as untrusted`() = runTest {
        val tools = ClipboardTools(context)

        val write = tools.execute(ToolCall("c1", "halo_clipboard_write", mapOf("text" to "hello halo")))
        assertTrue(write is ToolExecutionResult.Success)
        assertEquals(ActionTruth.CONFIRMED, (write as ToolExecutionResult.Success).actionTruth)

        val read = tools.execute(ToolCall("c2", "halo_clipboard_read", emptyMap()))
        assertTrue(read is ToolExecutionResult.Success)
        val output = (read as ToolExecutionResult.Success).output
        assertTrue(output.contains("UNTRUSTED_CLIPBOARD_CONTENT"))
        assertTrue(output.contains("hello halo"))
        assertTrue(output.contains("END_UNTRUSTED_CLIPBOARD_CONTENT"))
    }

    @Test
    fun `clipboard write rejects empty and oversized payloads`() = runTest {
        val tools = ClipboardTools(context)
        val empty = tools.execute(ToolCall("c1", "halo_clipboard_write", mapOf("text" to "")))
        assertTrue(empty is ToolExecutionResult.Rejected)

        val oversized = tools.execute(
            ToolCall("c2", "halo_clipboard_write", mapOf("text" to "x".repeat(4_001))),
        )
        assertTrue(oversized is ToolExecutionResult.Rejected)
        assertEquals(
            ToolRejectionKind.INVALID_ARGUMENTS,
            (oversized as ToolExecutionResult.Rejected).kind,
        )
    }

    @Test
    fun `clipboard read fails honestly when the clipboard holds nothing Halo can read`() = runTest {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.clearPrimaryClip()

        val result = ClipboardTools(context).execute(ToolCall("c1", "halo_clipboard_read", emptyMap()))
        assertTrue(result is ToolExecutionResult.Failed)
        val failure = result as ToolExecutionResult.Failed
        assertEquals("CLIPBOARD_NOT_READABLE", failure.code)
        assertFalse("a missing clipboard is not a retry loop", failure.hint.isNullOrBlank())
    }

    // ---- checksum ----------------------------------------------------------

    @Test
    fun `checksum matches published test vectors`() = runTest {
        val tools = IntegrityTools(context, NoFiles, NoWorkspace)

        val md5 = tools.execute(
            ToolCall("c1", "halo_checksum", mapOf("algorithm" to "md5", "source" to "text", "text" to "abc")),
        )
        assertTrue(md5 is ToolExecutionResult.Success)
        assertTrue((md5 as ToolExecutionResult.Success).output.contains("900150983cd24fb0d6963f7d28e17f72"))

        val sha256 = tools.execute(
            ToolCall("c2", "halo_checksum", mapOf("algorithm" to "sha256", "source" to "text", "text" to "abc")),
        )
        assertTrue(sha256 is ToolExecutionResult.Success)
        assertTrue(
            (sha256 as ToolExecutionResult.Success).output
                .contains("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"),
        )

        assertEquals(0, NoFiles.getFileCalls)
    }

    @Test
    fun `a checksum mismatch is a measurement, not a tool failure`() = runTest {
        val result = IntegrityTools(context, NoFiles, NoWorkspace).execute(
            ToolCall(
                "c1",
                "halo_checksum",
                mapOf(
                    "algorithm" to "md5",
                    "source" to "text",
                    "text" to "abc",
                    "expected" to "00000000000000000000000000000000",
                ),
            ),
        )
        assertTrue(result is ToolExecutionResult.Success)
        val success = result as ToolExecutionResult.Success
        assertTrue(success.output.contains("matched=false"))
        assertEquals(ActionTruth.CONFIRMED, success.actionTruth)
    }

    @Test
    fun `checksum rejects an unsupported algorithm by name`() = runTest {
        val result = IntegrityTools(context, NoFiles, NoWorkspace).execute(
            ToolCall("c1", "halo_checksum", mapOf("algorithm" to "crc32", "source" to "text", "text" to "abc")),
        )
        assertTrue(result is ToolExecutionResult.Rejected)
        val rejection = result as ToolExecutionResult.Rejected
        assertTrue(rejection.reason.contains("md5"))
        assertEquals(ToolRejectionKind.INVALID_ARGUMENTS, rejection.kind)
    }

    // ---- web reading -------------------------------------------------------

    @Test
    fun `web reader refuses local and non-web targets before any request`() = runTest {
        val tools = WebInsightTools()

        val fileScheme = tools.execute(ToolCall("c1", "halo_fetch_url", mapOf("url" to "file:///etc/hosts")))
        assertTrue(fileScheme is ToolExecutionResult.Rejected)

        listOf(
            "http://localhost/admin",
            "http://127.0.0.1:8080/",
            "http://10.0.0.5/",
            "http://192.168.1.1/",
            "http://169.254.169.254/latest/meta-data/",
            "http://router.local/",
        ).forEachIndexed { index, url ->
            val result = tools.execute(ToolCall("c$index", "halo_fetch_url", mapOf("url" to url)))
            assertTrue("expected refusal for $url", result is ToolExecutionResult.Rejected)
            assertEquals(
                "refusal for $url must not be reported as a bad argument",
                ToolRejectionKind.UNSUPPORTED_OPERATION,
                (result as ToolExecutionResult.Rejected).kind,
            )
        }
    }

    @Test
    fun `web reader rejects a relative url and an unknown mode`() = runTest {
        val tools = WebInsightTools()
        assertTrue(
            tools.execute(ToolCall("c1", "halo_fetch_url", mapOf("url" to "example.com")))
                is ToolExecutionResult.Rejected,
        )
        assertTrue(
            tools.execute(
                ToolCall("c2", "halo_fetch_url", mapOf("url" to "https://example.com", "mode" to "screenshot")),
            ) is ToolExecutionResult.Rejected,
        )
    }

    @Test
    fun `web reader refuses a hostname that resolves inside the local network`() = runTest {
        val tools = WebInsightTools(hostResolver = { listOf("192.168.0.10") })
        val result = tools.execute(ToolCall("c1", "halo_fetch_url", mapOf("url" to "https://example.com")))
        assertTrue(result is ToolExecutionResult.Rejected)
        assertEquals(
            ToolRejectionKind.UNSUPPORTED_OPERATION,
            (result as ToolExecutionResult.Rejected).kind,
        )
    }

    // ---- device controls and timers ---------------------------------------

    @Test
    fun `ringer rejects an unsupported mode`() = runTest {
        val result = DeviceTools(context).execute(ToolCall("c1", "halo_ringer", mapOf("mode" to "loud")))
        assertTrue(result is ToolExecutionResult.Rejected)
    }

    @Test
    fun `brightness reports the missing system-settings grant instead of failing silently`() = runTest {
        val result = DeviceTools(context).execute(ToolCall("c1", "halo_brightness", mapOf("level" to 42)))
        when (result) {
            is ToolExecutionResult.Failed -> {
                assertEquals("WRITE_SETTINGS_REQUIRED", result.code)
                assertTrue(result.hint.orEmpty().contains("write_settings"))
            }
            // A Robolectric device may grant the write; then the action must confirm or dispatch.
            is ToolExecutionResult.Success -> assertTrue(
                result.output.contains("requested_percent=42"),
            )
            else -> throw AssertionError("unexpected brightness result: $result")
        }
    }

    @Test
    fun `timer rejects a duration outside the supported range`() = runTest {
        val tooLong = AlarmTools(context).execute(ToolCall("c1", "halo_timer", mapOf("seconds" to 200_000)))
        assertTrue(tooLong is ToolExecutionResult.Rejected)

        val zero = AlarmTools(context).execute(ToolCall("c2", "halo_timer", mapOf("seconds" to 0)))
        assertTrue(zero is ToolExecutionResult.Rejected)
    }

    @Test
    fun `settings reports an unavailable section as a permanent failure, not a bad argument`() = runTest {
        // Robolectric resolves no settings activities, so any named section is unavailable here.
        val result = SystemTools(context).execute(
            ToolCall("c1", "halo_open_settings", mapOf("section" to "developer")),
        )
        when (result) {
            is ToolExecutionResult.Failed -> {
                assertEquals("SETTINGS_SECTION_UNAVAILABLE", result.code)
                assertTrue(result.message.contains("developer"))
            }
            is ToolExecutionResult.Rejected -> assertEquals(
                "an unknown name is a bad argument; a known but absent screen is not",
                ToolRejectionKind.INVALID_ARGUMENTS,
                result.kind,
            )
            else -> throw AssertionError("unexpected settings result: $result")
        }
    }

    private object NoFiles : WorkspaceFileRepository {
        var getFileCalls = 0
        override suspend fun importFile(
            uri: android.net.Uri,
            displayName: String?,
            mimeType: String?,
            sizeBytes: Long?,
            source: com.halo.app.data.notebook.WorkspaceObjectSource,
        ): WorkspaceFile = error("not used")

        override suspend fun getFile(objectId: WorkspaceObjectId): WorkspaceFile? {
            getFileCalls++
            return null
        }

        override fun observeFiles(): Flow<List<WorkspaceFile>> = flowOf(emptyList())
        override suspend fun keepLocalCopy(objectId: WorkspaceObjectId): WorkspaceFile = error("not used")
        override suspend fun relink(objectId: WorkspaceObjectId, uri: android.net.Uri): WorkspaceFile = error("not used")
        override suspend fun reconcileAvailability() = Unit
    }

    private object NoWorkspace : WorkspaceRepository {
        override fun observe(query: WorkspaceQuery): Flow<List<WorkspaceObject>> = flowOf(emptyList())
        override suspend fun get(id: WorkspaceObjectId): WorkspaceObject? = null
        override suspend fun updateTitle(id: WorkspaceObjectId, title: String) = Unit
        override suspend fun archive(id: WorkspaceObjectId) = Unit
        override suspend fun trash(id: WorkspaceObjectId) = Unit
        override suspend fun restore(id: WorkspaceObjectId) = Unit
        override suspend fun removeFromInbox(id: WorkspaceObjectId) = Unit
    }
}
