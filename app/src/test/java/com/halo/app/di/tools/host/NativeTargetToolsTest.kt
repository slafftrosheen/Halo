package com.halo.app.di.tools.host

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.halo.sdk.provider.ToolCall
import com.halo.runtime.cognition.tools.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class NativeTargetToolsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    @Test fun `target actions reject caller supplied URIs and components`() = runTest {
        val tools = NativeTargetTools(context)
        val result = tools.execute(ToolCall("x", "halo_native_target", mapOf("action" to "app_info", "packageName" to "com.example.app", "uri" to "file:///private")))
        assertEquals(ToolRejectionKind.INVALID_ARGUMENTS, (result as ToolExecutionResult.Rejected).kind)
    }
    @Test fun `contact number never silently removes control strings`() = runTest {
        val tools = NativeTargetTools(context)
        listOf("123&recipient=other", "*#21#", "abc123", "12").forEach { number ->
            val result = tools.execute(ToolCall("x", "halo_native_target", mapOf("action" to "contact_from_number", "phoneNumber" to number)))
            assertTrue(number, result is ToolExecutionResult.Rejected)
        }
    }
    @Test fun `map coordinates reject nonfinite or out of range values`() {
        val tools = NativeMapTools(context)
        listOf(Double.NaN, Double.POSITIVE_INFINITY, 91.0).forEach { latitude ->
            try { tools.intentFor(mapOf("action" to "coordinates", "latitude" to latitude, "longitude" to 24.0)); fail("Invalid latitude accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun `directions use a fixed documented origin and encoded destination`() {
        val intent = NativeMapTools(context).intentFor(mapOf("action" to "directions", "destination" to "Riga & travelmode=driving", "mode" to "walking"))
        assertEquals("www.google.com", intent.data!!.host)
        assertEquals("walking", intent.data!!.getQueryParameter("travelmode"))
        assertEquals("Riga & travelmode=driving", intent.data!!.getQueryParameter("destination"))
    }
    @Test fun `global actions require a current manual accessibility owner`() = runTest {
        val result = NativeGlobalTools().execute(ToolCall("x", "halo_native_global", mapOf("action" to "take_screenshot")))
        assertEquals(ToolRejectionKind.PERMISSION_REQUIRED, (result as ToolExecutionResult.Rejected).kind)
    }
    @Test fun `incoming share refuses file URIs`() {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND)
            .putExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri.parse("file:///private/token"))
        assertTrue(com.halo.app.data.workspace.SendIntentHandler.sharedUris(intent).isEmpty())
    }
}
