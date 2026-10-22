package com.halo.providers.litert.models

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class ModelArtifactFileLocksTest {
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
