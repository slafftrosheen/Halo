package com.halo.app.data.agent

import com.halo.runtime.cognition.apps.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class SemanticOperationJournalCodecTest {
    @Test fun receiptAndShapesRoundTripWithoutArgumentContents() {
        val operation = SemanticAppOperation("interaction", AppTarget.CurrentApp, SemanticActionId("prepare_message"),
            mapOf("body" to SemanticValue.Text("Do not persist this private message")))
        val receipt = SemanticOperationReceipt(operation.operationId, operation.action, AppOperationRoute.ANDROID_INTENT, "example.messages",
            DispatchTruth.DISPATCHED, EffectTruth.UNCONFIRMED, null, Instant.EPOCH, Instant.EPOCH.plusSeconds(1))
        val encoded = SemanticOperationCodec.encode(receipt, operation.argumentShapes())
        assertFalse(encoded.toString().contains("private message"))
        val decoded = SemanticOperationCodec.decode(encoded)
        assertEquals(receipt, decoded.first); assertEquals(operation.argumentShapes(), decoded.second)
    }
}
