package com.halo.app.ui.workspace

import android.app.Application
import android.net.Uri
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.halo.app.R
import com.halo.app.ai.IntentQualificationFixture
import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.workspace.*
import com.halo.app.ui.theme.HostlessHaloTheme
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "en-rUS-w360dp-h800dp")
class WorkspaceExternalExportRenderingTest {
    @get:Rule val compose = createComposeRule()
    @Test fun verifiedCopyHasAnOutsideOpeningActionAndDurableReceipt() = show(false, false, 1f)
    @Test @Config(qualifiers = "ru-rRU-w360dp-h800dp") fun unknownCopyExplainsVerificationAt200Percent() = show(true, false, 2f)
    @Test @Config(qualifiers = "pl-rPL-w360dp-h800dp") fun cancelledPickerCanCloseAt200PercentWithoutWriting() = show(false, true, 2f)
    private fun show(unknown: Boolean, cancelled: Boolean, scale: Float) {
        val bytes = "Synthetic exported note".toByteArray()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val sourceUri = Uri.parse("content://halo/source")
        val source = WorkspaceFile(WorkspaceObjectId("synthetic-output"), sourceUri, "Field notes.txt", "text/plain", bytes.size.toLong(),
            FilePermissionMode.OWNED_COPY, ContentOwnership.HALO_OWNED, WorkspaceFileAvailability.OWNED_LOCAL, hash)
        var written: ByteArray? = null
        var writes = 0
        val exporter = WorkspaceExternalExport({ source }, { uri ->
            if (uri == sourceUri) ByteArrayInputStream(bytes)
            else if (unknown) throw SecurityException("Synthetic readback denial") else written?.let(::ByteArrayInputStream)
        }, {
            writes++
            object : ByteArrayOutputStream() { override fun close() { written = toByteArray(); super.close() } }
        }, { true })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val owner = WorkspaceExternalExportSession(IntentQualificationFixture.MemoryStorage(), exporter, scope) { true }
        try {
            runBlocking {
                owner.request(source); val picker = owner.picking()
                owner.picked(if (cancelled) null else Uri.parse("content://docs/output"), picker.pickerToken)
                withTimeout(5000) { while (owner.state.value?.busy == true) delay(10) }
            }
            var close = ""; var message = ""; var inspect = ""
            compose.setContent { HostlessHaloTheme(fontScale = scale) {
                close = stringResource(R.string.external_export_close)
                message = stringResource(if (cancelled) R.string.external_export_cancelled else if (unknown) R.string.external_export_unknown else R.string.external_export_verified)
                inspect = stringResource(if (unknown) R.string.external_export_inspect else R.string.external_export_open)
                WorkspaceExternalExportHost(owner, true)
            } }
            compose.onNodeWithText(message).assertExists()
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithText(message).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(scale, layouts.single().layoutInput.density.fontScale, 0.001f)
            compose.onRoot().captureRoboImage()
            if (!cancelled) compose.onNodeWithText(inspect).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(close).performClick()
            compose.waitUntil(5000) { owner.state.value == null }
            assertEquals(if (cancelled) 0 else 1, writes)
        } finally { scope.cancel() }
    }
}
