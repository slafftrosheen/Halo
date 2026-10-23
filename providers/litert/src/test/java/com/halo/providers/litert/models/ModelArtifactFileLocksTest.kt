package com.halo.providers.litert.models

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class ModelArtifactFileLocksTest {
    @Test fun leasesCanBeClosedAfterCoroutineThreadHandoffWithoutLeakingPins() {
        val root = Files.createTempDirectory("halo-cross-thread-lease").toFile()
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val read = requireNotNull(ModelArtifactFileLocks.acquireRead(root, "model"))
            executor.submit { read.close(); read.close() }.get()
            val write = requireNotNull(ModelArtifactFileLocks.tryAcquireWrite(root, "model"))
            assertNull(ModelArtifactFileLocks.acquireRead(root, "model"))
            assertNull(ModelArtifactFileLocks.tryAcquireWrite(root, "model"))
            executor.submit { write.close(); write.close() }.get()
            requireNotNull(ModelArtifactFileLocks.acquireRead(root, "model")).close()
            requireNotNull(ModelArtifactFileLocks.tryAcquireWrite(root, "model")).close()
        } finally { executor.shutdownNow(); root.deleteRecursively() }
    }
    @Test fun readerPinBlocksManagerMutationAndReleasesAfterClose() {
        val root = Files.createTempDirectory("halo-artifact-lock-test").toFile()
        try {
            val reader = ModelArtifactFileLocks.acquireRead(root, "model-a")
            assertNotNull(reader)
            assertNull(ModelArtifactFileLocks.tryAcquireWrite(root, "model-a"))
            val unrelatedWriter = ModelArtifactFileLocks.tryAcquireWrite(root, "model-b")
            assertNotNull(unrelatedWriter)
            unrelatedWriter!!.close()
            reader!!.close()
            val writer = ModelArtifactFileLocks.tryAcquireWrite(root, "model-a")
            assertNotNull(writer)
            writer!!.close()
        } finally {
            root.deleteRecursively()
        }
    }
}
