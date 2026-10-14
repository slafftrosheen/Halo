package com.halo.app.di.tools.host

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolRejectionKind
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(application = android.app.Application::class)
class AlarmToolsTest {
    private val tools = AlarmTools(ApplicationProvider.getApplicationContext<Context>())

    @Test fun `repeating days belong to alarm schema and never timer schema`() {
        val definitions = tools.definitions().associateBy { it.name }
        assertTrue((definitions.getValue("halo_set_alarm").parameters["properties"] as Map<*, *>).containsKey("repeatDays"))
        assertFalse((definitions.getValue("halo_timer").parameters["properties"] as Map<*, *>).containsKey("repeatDays"))
    }

    @Test fun `fractional nonfinite and overflowing timer durations cannot reach Android`() = runTest {
        listOf(1.5, Double.NaN, Double.POSITIVE_INFINITY, Long.MAX_VALUE).forEach { seconds ->
            val result = tools.execute(ToolCall("timer", "halo_timer", mapOf("seconds" to seconds)))
            assertEquals(ToolRejectionKind.INVALID_ARGUMENTS, (result as ToolExecutionResult.Rejected).kind)
        }
    }

    @Test fun `timer cannot accept alarm repetition or silently truncate labels`() = runTest {
        listOf(mapOf("seconds" to 60, "repeatDays" to listOf(2)),
            mapOf("seconds" to 60, "label" to "x".repeat(81))).forEach { args ->
            assertEquals(ToolRejectionKind.INVALID_ARGUMENTS,
                (tools.execute(ToolCall("timer", "halo_timer", args)) as ToolExecutionResult.Rejected).kind)
        }
    }

    @Test fun `numerically duplicate repeat days cannot bypass uniqueness`() = runTest {
        val result = tools.execute(ToolCall("alarm", "halo_set_alarm", mapOf("timeExpression" to "7 AM", "repeatDays" to listOf(2, 2L))))
        assertEquals(ToolRejectionKind.INVALID_ARGUMENTS, (result as ToolExecutionResult.Rejected).kind)
    }
}
