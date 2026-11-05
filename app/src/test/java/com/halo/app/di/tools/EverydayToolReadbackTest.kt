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
    @Test fun strictHostValidationAcceptsSmallAndPagedReadsAndSmallAndBoundedWriteReceipts() {
        val legacy = EverydayToolContracts.obj(mapOf("id" to EverydayToolContracts.text(200),
            "version" to EverydayToolContracts.number(1, Long.MAX_VALUE), "details" to EverydayToolContracts.text(20000)),
            listOf("id", "version", "details"))
        for (text in listOf("Owned text", "quoted\"\\\n😀".repeat(1000))) {
            val raw = JSONObject().put("id", "owned").put("version", 7).put("details", text)
            val read = EverydayToolReadback.project("halo_organizer_get", emptyMap(), raw, "scope")
            val receipt = EverydayToolReadback.receipt("halo_organizer_edit", raw)
            EverydayToolContracts.validate(EverydayResultContracts.value(read), EverydayToolReadback.schema(legacy))
            EverydayToolContracts.validate(EverydayResultContracts.value(receipt), EverydayToolReadback.receiptSchema(legacy))
            assertTrue(read.toString().length <= 4000); assertTrue(receipt.toString().length <= 4000)
            assertTrue(runCatching { EverydayToolContracts.validate(EverydayResultContracts.value(JSONObject(read.toString()).put("unexpected", true)), EverydayToolReadback.schema(legacy)) }.isFailure)
            assertTrue(runCatching { EverydayToolContracts.validate(EverydayResultContracts.value(JSONObject(receipt.toString()).put("unexpected", true)), EverydayToolReadback.receiptSchema(legacy)) }.isFailure)
        }
        assertTrue(runCatching { EverydayToolContracts.validate(emptyMap<String, Any>(), EverydayToolReadback.schema(legacy)) }.isFailure)
        assertTrue(runCatching { EverydayToolContracts.validate(emptyMap<String, Any>(), EverydayToolReadback.receiptSchema(legacy)) }.isFailure)
    }

}
