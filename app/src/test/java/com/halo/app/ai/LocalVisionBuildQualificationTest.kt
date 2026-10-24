package com.halo.app.ai

import android.app.Application
import com.halo.providers.litert.*
import com.halo.providers.litert.runtime.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class LocalVisionBuildQualificationTest {
    @Test fun matchingBuildCanReopenProofButReplacementRequiresNewProbe() = runTest {
        val f = IntentQualificationFixture(backgroundScope)
        f.inventory.artifact = f.artifact.copy(supportsVision = true)
        val key = requireNotNull(f.provider.visionQualificationKeyForModel(f.artifact.id))
        f.storage.putString("litert.local.vision.probe.v2.$key", "TEXT_CPU;IMAGE_CPU")
        assertEquals(VisionQualification.PROVEN, f.provider.visionQualificationFor(f.artifact))
        val same = LiteRtLocalProvider(f.context, inventoryRepository = f.inventory, storage = f.storage,
            modelSession = LocalModelSession({ f.runtime }, backgroundScope))
        assertEquals(key, same.visionQualificationKeyForModel(f.artifact.id))
        assertEquals(VisionQualification.PROVEN, same.visionQualificationFor(f.artifact))
        f.packageRevision(2000)
        val replaced = LiteRtLocalProvider(f.context, inventoryRepository = f.inventory, storage = f.storage,
            modelSession = LocalModelSession({ f.runtime }, backgroundScope))
        assertNotEquals(key, replaced.visionQualificationKeyForModel(f.artifact.id))
        assertEquals(VisionQualification.PROBE_REQUIRED, replaced.visionQualificationFor(f.artifact))
        assertEquals("TEXT_CPU;IMAGE_CPU", f.storage.getString("litert.local.vision.probe.v2.$key"))
    }

    @Test fun legacyProofAndMissingInstallationIdentityCannotAdmitVision() = runTest {
        val f = IntentQualificationFixture(backgroundScope)
        f.inventory.artifact = f.artifact.copy(supportsVision = true)
        val legacy = VisionQualificationKey.of(requireNotNull(f.artifact.sha256), LiteRtLocalProvider.RUNTIME_VERSION)
        f.storage.putString("litert.local.vision.probe.v1.$legacy", "GPU")
        assertEquals(VisionQualification.PROBE_REQUIRED, f.provider.visionQualificationFor(f.artifact))
        f.packageRevision(0)
        val unknown = LiteRtLocalProvider(f.context, inventoryRepository = f.inventory, storage = f.storage,
            modelSession = LocalModelSession({ f.runtime }, backgroundScope))
        assertNull(unknown.visionQualificationKeyForModel(f.artifact.id))
        assertEquals(VisionQualification.PROBE_REQUIRED, unknown.visionQualificationFor(f.artifact))
        assertEquals("GPU", f.storage.getString("litert.local.vision.probe.v1.$legacy"))
    }

    @Test fun installedBuildIdentityIsStableBoundedAndUnavailableWhenMetadataIsMissing() = runTest {
        val f = IntentQualificationFixture(backgroundScope)
        val identity = requireNotNull(LiteRtExecutionBuildIdentity.current(f.context))
        assertTrue(identity.matches(Regex("[a-f0-9]{64}")))
        assertEquals(identity, LiteRtExecutionBuildIdentity.current(f.context))
        f.packageRevision(2000)
        assertNotEquals(identity, LiteRtExecutionBuildIdentity.current(f.context))
        f.packageRevision(0)
        assertNull(LiteRtExecutionBuildIdentity.current(f.context))
    }
}
