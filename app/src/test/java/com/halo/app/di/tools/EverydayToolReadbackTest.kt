package com.halo.app.di.tools

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [33], application = android.app.Application::class)
class EverydayToolReadbackTest {
    @Test fun escapedLongRecordReassemblesWithoutTruncationOrMixedRevisions() {
        val text = ("\\\"\n\u0001😀".repeat(1500))
        val result = JSONObject().put("id", "owned").put("version", 7).put("record", JSONObject().put("details", text))
        val first = EverydayToolReadback.project("halo_organizer_get", mapOf("id" to "owned"), result, "authority-1")
        assertTrue(first.toString().length <= 4000)
        val snapshot = first.getString("snapshot")
        var offset = 0
        val complete = StringBuilder()
        do {
            val page = EverydayToolReadback.project("halo_organizer_get", mapOf("id" to "owned", "view" to "text", "path" to listOf("record", "details"), "readOffset" to offset, "snapshot" to snapshot), result, "authority-1")
            assertTrue(page.toString().length <= 4000)
            complete.append(page.getString("text"))
            offset = if (page.isNull("nextReadOffset")) -1 else page.getInt("nextReadOffset")
        } while (offset >= 0)
        assertEquals(text, complete.toString())
        assertTrue(runCatching { EverydayToolReadback.project("halo_organizer_get", mapOf("snapshot" to snapshot), result, "revoked") }.isFailure)
    }
    @Test fun smallResultRetainsItsOriginalShapeAndNewSnapshot() {
        val result = JSONObject().put("sources", org.json.JSONArray())
        val value = EverydayToolReadback.project("halo_source_query", emptyMap(), result, "same")
        assertEquals(0, value.getJSONArray("sources").length())
        assertEquals(64, value.getString("snapshot").length)
    }
    @Test fun explicitPathAndOffsetsRequireAReviewedSnapshot() {
        assertTrue(runCatching { EverydayToolReadback.project("halo_source_get", mapOf("path" to listOf("content")), JSONObject(), "epoch") }.isFailure)
    }
}
