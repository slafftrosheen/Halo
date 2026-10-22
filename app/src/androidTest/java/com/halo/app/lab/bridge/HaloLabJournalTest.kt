package com.halo.app.lab.bridge

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class HaloLabJournalTest {
    @Test fun acceptanceIsIdempotentAndTerminalAttemptCannotBeOverwritten() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("halo_lab_bridge_v1.db")
        val journal = HaloLabJournal(context)
        try {
            val id = UUID.randomUUID().toString()
            val requestHash = "a".repeat(64)
            assertEquals("CREATED", journal.accept(id, requestHash, "model-a", "b".repeat(64)))
            assertEquals("EXISTING", journal.accept(id, requestHash, "model-a", "b".repeat(64)))
            assertEquals("REQUEST_ID_CONFLICT", journal.accept(id, "c".repeat(64), "model-a", "b".repeat(64)))

            val terminal = JSONObject().put("status", "TERMINAL").put("outcome", "PASS")
                .put("validity", "VALID").put("modelId", "model-a").put("artifactBytes", 12L)
                .put("artifactSha256", "b".repeat(64))
            assertTrue(journal.terminal(id, terminal))
            assertFalse(journal.terminal(id, JSONObject().put("status", "TERMINAL").put("outcome", "FAIL")))

            val events = journal.readEvents(id, 0L, 64)
            assertEquals(2, events.length())
            val envelope = events.getJSONObject(1)
            val payload = envelope.getJSONObject("payload")
            assertEquals("PASSED", payload.getString("outcome"))
            assertEquals("VALID", payload.getString("validity"))
            assertNotNull(envelope.optString("payloadSha256").takeIf { it.isNotBlank() })
            val canonical = "{\"kind\":\"ATTEMPT_TERMINAL\",\"attemptId\":\"$id\",\"stage\":null," +
                "\"eventCode\":\"TERMINAL\",\"outcome\":\"PASSED\",\"validity\":\"VALID\"," +
                "\"failureCode\":null,\"failureDomain\":null,\"stopDisposition\":null,\"workerInstanceId\":null," +
                "\"artifactId\":\"model-a\",\"artifactBytes\":12,\"artifactSha256\":\"${"b".repeat(64)}\",\"unavailableReason\":null}"
            val expected = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            assertEquals(expected, envelope.getString("payloadSha256"))
        } finally {
            journal.close()
            context.deleteDatabase("halo_lab_bridge_v1.db")
        }
    }
}
