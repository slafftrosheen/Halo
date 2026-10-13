package com.halo.app.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.halo.app.di.ManualDependencyGraph
import com.halo.providers.litert.LiteRtLocalProvider
import com.halo.providers.litert.models.LiteRtModelCatalog
import com.halo.sdk.provider.SpeechRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit device acceptance against installed artifacts; no downloads or selection changes. */
@RunWith(AndroidJUnit4::class)
class LocalInferencePhysicalTest {
    @Test fun multilingualKokoroUpgradeSynthesizesEnglishAndChinese() = runBlocking {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val archive = java.io.File(context.cacheDir, "kokoro-multilingual-acceptance.tar.bz2")
        assertTrue("Stage the pinned multilingual acceptance archive before this test", archive.isFile)
        assertEquals(KokoroModelDownload.MULTI_BYTES, archive.length())
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        archive.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) { val count=input.read(buffer); if(count<0) break; digest.update(buffer,0,count) }
        }
        assertEquals(KokoroModelDownload.MULTI_HASH, digest.digest().joinToString("") { "%02x".format(it) })
        val zip = java.io.File(context.cacheDir, "kokoro-multilingual-acceptance.zip")
        val provider = ManualDependencyGraph.speechSynthesisRegistry.providers.filterIsInstance<KokoroSpeechProvider>().single()
        try {
            KokoroModelDownload.toImportZip(archive, zip)
            provider.importPack(android.net.Uri.fromFile(zip))
            assertTrue(provider.multilingualInstalled())
            for (request in listOf(SpeechRequest("This is Halo.", language="en", voiceId="3"),
                SpeechRequest("你好，这是语音测试。", language="zh", voiceId="45"))) {
                val pcm = provider.synthesizeToPcm(request)!!
                assertTrue(pcm.sampleRateHz > 0 && pcm.chunks.any { chunk -> chunk.any { it != 0.toByte() } })
            }
        } finally { zip.delete(); archive.delete() }
    }

    @Test fun kokoroInstalledPackSynthesizesEnglishPcm() = runBlocking {
        val provider = ManualDependencyGraph.speechSynthesisRegistry.providers.filterIsInstance<KokoroSpeechProvider>().single()
        assertTrue("Install a Kokoro pack before running device acceptance", provider.health())
        val pcm = provider.synthesizeToPcm(SpeechRequest(text = "This is Halo.", language = "en"))!!
        assertTrue(pcm.sampleRateHz > 0)
        assertTrue(pcm.chunks.any { chunk -> chunk.any { it != 0.toByte() } })
    }

    @Test fun qwenSmallCompletesGpuStabilityProbe() = runBlocking {
        val provider = ManualDependencyGraph.providerGraph.providers.inference as LiteRtLocalProvider
        assertTrue(provider.unloadLocalModel())
        val result = provider.gpuStabilityProbe(LiteRtModelCatalog.QWEN3_0_6B_DYNAMIC_INT4.id)
        assertTrue(result.summaryLine(), result.passed)
    }

    @Test fun qwenLargeCompletesGpuStabilityProbe() = runBlocking {
        val provider = ManualDependencyGraph.providerGraph.providers.inference as LiteRtLocalProvider
        assertTrue(provider.unloadLocalModel())
        val result = provider.gpuStabilityProbe(LiteRtModelCatalog.QWEN3_1_7B_DYNAMIC_INT4.id)
        assertTrue(result.summaryLine(), result.passed)
    }
}
