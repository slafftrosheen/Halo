package com.halo.providers.litert.runtime

import com.halo.sdk.provider.RuntimeGenerationConfig
import org.junit.Assert.*
import org.junit.Test

class LiteRtMultimodalInputTest {
    @Test fun cpuOnlyVisionMetadataSelectsCpuEncoderIndependentlyOfText() {
        val selected = LiteRtModelPreflight.selectVisionBackend(setOf(com.google.ai.edge.litertlm.BackendType.CPU))
        assertEquals(com.halo.sdk.provider.LocalComputeBackend.CPU, selected)
        val config = LiteRtEngineConfigFactory.buildEngineConfig("model", com.halo.sdk.provider.LocalComputeBackend.GPU,
            4096, java.io.File("cache"), visionEnabled = true, cpuThreads = 2, visionBackend = selected)
        assertTrue(config.backend is com.google.ai.edge.litertlm.Backend.GPU)
        assertEquals(2, (config.visionBackend as com.google.ai.edge.litertlm.Backend.CPU).threadCount)
        assertEquals(com.halo.sdk.provider.LocalComputeBackend.GPU,
            LiteRtModelPreflight.selectVisionBackend(setOf(com.google.ai.edge.litertlm.BackendType.CPU, com.google.ai.edge.litertlm.BackendType.GPU)))
        assertEquals(com.halo.sdk.provider.LocalComputeBackend.GPU, LiteRtModelPreflight.selectVisionBackend(emptySet()))
    }
    @Test fun audioDurationFailsClosedWhenUnknownOrAboveThePublishedLimit() {
        LiteRtAudioInput.validateDuration(30_000)
        assertThrows(IllegalArgumentException::class.java) { LiteRtAudioInput.validateDuration(30_001) }
        assertThrows(IllegalArgumentException::class.java) { LiteRtAudioInput.validateDuration(null) }
        assertThrows(IllegalArgumentException::class.java) { LiteRtAudioInput.validateDuration(0) }
    }
    @Test fun encoderConfigurationKeepsTextLoadsSmallAndWiresCpuThreads() {
        val plain = LiteRtEngineConfigFactory.buildEngineConfig("model", com.halo.sdk.provider.LocalComputeBackend.CPU,
            4096, java.io.File("cache"), cpuThreads = 3)
        assertNull(plain.audioBackend)
        assertNull(plain.visionBackend)
        assertEquals(3, (plain.backend as com.google.ai.edge.litertlm.Backend.CPU).threadCount)
        val media = LiteRtEngineConfigFactory.buildEngineConfig("model", com.halo.sdk.provider.LocalComputeBackend.GPU,
            4096, java.io.File("cache"), visionEnabled = true, audioEnabled = true, cpuThreads = 2)
        assertTrue(media.visionBackend is com.google.ai.edge.litertlm.Backend.GPU)
        assertEquals(2, (media.audioBackend as com.google.ai.edge.litertlm.Backend.CPU).threadCount)
        assertEquals(1, media.maxNumImages)
    }
    @Test fun sdkContentsIncludeMediaInTheDocumentedOrder() {
        val config = RuntimeGenerationConfig("Describe", imageBytes = byteArrayOf(1), audioBytes = byteArrayOf(2))
        val parts = LiteRtMultimodalInput.contents(config).contents
        assertEquals(3, parts.size)
        assertArrayEquals(config.imageBytes, (parts[0] as com.google.ai.edge.litertlm.Content.ImageBytes).bytes)
        assertArrayEquals(config.audioBytes, (parts[1] as com.google.ai.edge.litertlm.Content.AudioBytes).bytes)
        assertTrue(parts[2] is com.google.ai.edge.litertlm.Content.Text)
    }
    @Test fun minimumRuntimeVersionsAreComparedNumericallyAndUnknownVersionsFailClosed() {
        assertTrue(LiteRtModelPreflight.versionSupported("0.17.1", "0.18.0"))
        assertTrue(LiteRtModelPreflight.versionSupported("0.18.0", "0.18.0"))
        assertFalse(LiteRtModelPreflight.versionSupported("0.19.0", "0.18.0"))
        assertFalse(LiteRtModelPreflight.versionSupported("unknown", "0.18.0"))
    }
    @Test fun rejectsUnsupportedEmptyAndOversizedMediaBeforeNativeAllocation() {
        val audio = RuntimeGenerationConfig("Transcribe", audioBytes = byteArrayOf(1))
        assertThrows(IllegalArgumentException::class.java) { LiteRtMultimodalInput.validate(audio, false, false) }
        assertThrows(IllegalArgumentException::class.java) { LiteRtMultimodalInput.validate(audio.copy(audioBytes = byteArrayOf()), false, true) }
        assertThrows(IllegalArgumentException::class.java) { LiteRtMultimodalInput.validate(audio.copy(audioBytes = ByteArray(LiteRtMultimodalInput.MAX_AUDIO_BYTES + 1)), false, true) }
        assertThrows(IllegalArgumentException::class.java) { LiteRtMultimodalInput.validate(audio.copy(imageBytes = byteArrayOf(1)), false, true) }
        LiteRtMultimodalInput.validate(audio.copy(imageBytes = byteArrayOf(1)), true, true)
    }

    @Test fun configurationIdentityIncludesInstructionsAndAudioContents() {
        val config = RuntimeGenerationConfig("Transcribe", systemInstruction = "Only English", audioBytes = byteArrayOf(1, 2))
        assertEquals(config, config.copy(audioBytes = byteArrayOf(1, 2)))
        assertEquals(config.hashCode(), config.copy(audioBytes = byteArrayOf(1, 2)).hashCode())
        assertNotEquals(config, config.copy(systemInstruction = "Only Latvian"))
        assertNotEquals(config, config.copy(audioBytes = byteArrayOf(2, 1)))
        assertNotEquals(config, config.copy(audioBytes = null))
    }

    @Test fun onlyAllocationFailuresPermitContextRecovery() {
        assertTrue(LiteRtEngineConfigFactory.isMemoryAllocationFailure(OutOfMemoryError()))
        assertTrue(LiteRtEngineConfigFactory.isMemoryAllocationFailure(IllegalStateException("Failed to allocate tensor")))
        assertFalse(LiteRtEngineConfigFactory.isMemoryAllocationFailure(IllegalStateException("Invalid memory mapped model metadata")))
        assertFalse(LiteRtEngineConfigFactory.isMemoryAllocationFailure(IllegalStateException("Insufficient permissions")))
    }
}
