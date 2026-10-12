package com.halo.app.di.tools.host

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NavigationToolsTest {
    private val app: Context = ApplicationProvider.getApplicationContext()

    @Test fun noAndroidActivityNeverProducesFalseDispatchSuccess() = runTest {
        val unavailable = object : ContextWrapper(app) {
            override fun startActivity(intent: Intent) {
                throw ActivityNotFoundException("No handler")
            }
        }
        val tools = NavigationTools(unavailable) { true }
        for (call in listOf(
            ToolCall("nav", "halo_navigation", mapOf("destination" to "Riga")),
            ToolCall("web", "halo_browser", mapOf("url" to "https://example.org")),
            ToolCall("search", "halo_search", mapOf("query" to "Halo project"))
        )) {
            val result = tools.execute(call)
            assertFalse("No handler cannot be recorded as successful dispatch: ${call.functionName}",
                result is ToolExecutionResult.Success)
        }
    }

    @Test fun searchFallsBackToEscapedBrowserUrlAndReportsDispatchOnly() = runTest {
        var opened: Intent? = null
        val context = object : ContextWrapper(app) {
            override fun startActivity(intent: Intent) {
                if (intent.action == Intent.ACTION_WEB_SEARCH) throw ActivityNotFoundException("No search handler")
                opened = intent
            }
        }
        val result = NavigationTools(context) { true }.execute(
            ToolCall("search", "halo_search", mapOf("query" to "rock & roll"))
        )
        assertTrue(result is ToolExecutionResult.Success)
        assertEquals(ActionTruth.DISPATCHED, (result as ToolExecutionResult.Success).actionTruth)
        assertEquals(Intent.ACTION_VIEW, opened?.action)
        assertEquals("https", opened?.data?.scheme)
        assertEquals("duckduckgo.com", opened?.data?.host)
        assertEquals("rock & roll", opened?.data?.getQueryParameter("q"))
    }

    @Test fun invalidWebRequestsCannotEscapeToAnExternalIntent() = runTest {
        var launches = 0
        val context = object : ContextWrapper(app) {
            override fun startActivity(intent: Intent) { launches++ }
        }
        val tools = NavigationTools(context) { true }
        for (url in listOf("file:///etc/passwd", "javascript:alert(1)", "https:///invalid", "")) {
            val result = tools.execute(ToolCall("web", "halo_browser", mapOf("url" to url)))
            assertTrue("Invalid URL: $url", result is ToolExecutionResult.Rejected)
        }
        assertEquals(0, launches)
    }

    @Test fun backgroundWithoutForegroundAuthorityDoesNotLaunch() = runTest {
        var launches = 0
        val context = object : ContextWrapper(app) {
            override fun startActivity(intent: Intent) { launches++ }
        }
        val result = NavigationTools(context) { false }.execute(
            ToolCall("search", "halo_search", mapOf("query" to "weather"))
        )
        assertTrue(result is ToolExecutionResult.Rejected)
        assertEquals(0, launches)
    }
}
