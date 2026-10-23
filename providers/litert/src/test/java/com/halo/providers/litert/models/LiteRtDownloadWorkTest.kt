package com.halo.providers.litert.models
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.work.NetworkType
@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE,sdk=[33])
class LiteRtDownloadWorkTest {
    @Test fun `owned work persists exact receipt identity and scoped cancellation tag`() {
        val owner = "b88fe002-14e0-48dc-85bd-6dbcd038f709"
        val model = LiteRtModelCatalog.QWEN3_0_6B_DYNAMIC_INT4
        val request = LiteRtDownloadWorker.request(model, owner)
        assertEquals(setOf("modelId", "acquisitionOwner"), request.workSpec.input.keyValueMap.keys)
        assertEquals(owner, request.workSpec.input.getString("acquisitionOwner"))
        assertTrue(request.tags.contains("halo-model-owner:$owner:${model.id}"))
    }
    @Test fun `malformed ownership cannot enter persistent work`() {
        assertThrows(IllegalArgumentException::class.java) {
            LiteRtDownloadWorker.request(LiteRtModelCatalog.QWEN3_0_6B_DYNAMIC_INT4, "../another-download")
        }
    }
    @Test fun `persistent work carries only catalog identity and requires a network`() {
        val model=LiteRtModelCatalog.QWEN3_0_6B_DYNAMIC_INT4
        val request=LiteRtDownloadWorker.request(model)
        assertEquals(setOf("modelId"),request.workSpec.input.keyValueMap.keys)
        assertEquals(model.id,request.workSpec.input.getString("modelId"))
        assertEquals(NetworkType.CONNECTED,request.workSpec.constraints.requiredNetworkType)
        assertTrue(request.tags.contains("halo-model:${model.id}"))
    }
    @Test fun `gated credentials cannot be queued persistently`() {
        assertThrows(IllegalArgumentException::class.java) { LiteRtDownloadWorker.request(LiteRtModelCatalog.GEMMA3_1B_IT_INT4) }
    }
}
