package com.halo.app.di.tools.host

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.halo.cognition.language.NativeDestination
import com.halo.app.nativeactions.NativeActivityRequests
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(application = android.app.Application::class)
class NativeDestinationToolsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    @Test fun `background actions and arbitrary destination extras fail closed`() = runTest {
        val tools = NativeDestinationTools(context) { false }
        assertEquals(ToolRejectionKind.CAPABILITY_UNAVAILABLE,
            (tools.execute(ToolCall("a", "halo_native_destination", mapOf("destination" to "camera"))) as ToolExecutionResult.Rejected).kind)
        assertEquals(ToolRejectionKind.INVALID_ARGUMENTS,
            (tools.execute(ToolCall("b", "halo_native_destination", mapOf("destination" to "camera", "component" to "bad"))) as ToolExecutionResult.Rejected).kind)
        assertEquals(ToolRejectionKind.INVALID_ARGUMENTS,
            (tools.execute(ToolCall("c", "halo_native_destination", mapOf("destination" to "intent://bad"))) as ToolExecutionResult.Rejected).kind)
    }
    @Test fun `document and folder intents use public user mediated contracts`() {
        val tools = NativeDestinationTools(context)
        val documents = tools.intentFor(NativeDestination.DOCUMENTS_PICKER)!!
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, documents.action)
        assertEquals("*/*", documents.type)
        assertTrue(documents.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
        assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, tools.intentFor(NativeDestination.FOLDER_PICKER)!!.action)
        assertNull(documents.component)
    }
    @Test fun `stop invalidates results and cannot resurrect a pending request`() = runTest {
        val (id, _) = NativeActivityRequests.create()
        NativeActivityRequests.cancelAll()
        NativeActivityRequests.complete(id, NativeActivityRequests.Receipt(true, "Saved"))
        assertFalse(NativeActivityRequests.active(id))
        assertNull(NativeActivityRequests.receipt(id))
        val result = NativeDestinationTools(context).execute(ToolCall("r", "halo_native_result", mapOf("requestId" to id))) as ToolExecutionResult.Success
        assertEquals(ActionTruth.UNKNOWN, result.actionTruth)
    }
    @Test fun `new handoffs always require approval and cannot replay`() {
        val definitions = NativeDestinationTools(context).definitions()
        val descriptor = com.halo.app.di.tools.HaloToolDescriptors.system(definitions.first())!!
        assertEquals(ConfirmationPolicy.ALWAYS, descriptor.security.confirmation)
        assertEquals(ReplaySafety.NEVER_REPLAY, descriptor.security.replaySafety)
    }
}
