package com.halo.runtime.cognition.context

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** Regression sources added during the user-requested no-test coding pass; not executed. */
class WorkspaceContextSnapshotTest {
    private class Source : WorkspaceContextSource {
        var version: String? = "7"
        var mutateDuringRead = false
        var reads = 0
        var strictResolved = false
        override val resolvedTextRequiresVersion: Boolean get() = strictResolved
        override suspend fun content(includeMemories: Boolean) = emptyList<String>()
        override suspend fun versionFor(objectId: String) = version
        override suspend fun contentFor(objectId: String): String {
            reads++
            if (mutateDuringRead) version = "8"
            return "Synthetic exact source"
        }
    }

    @Test fun stableReadCarriesExactVersion() = runTest {
        val source = Source()
        assertEquals(WorkspaceContextEntry("owned-source", "Synthetic exact source", "7"), source.snapshot("owned-source", "7"))
    }

    @Test fun staleExpectedRevisionRejectsBeforeReadingContent() = runTest {
        val source = Source()
        assertNull(source.snapshot("owned-source", "6"))
        assertEquals(0, source.reads)
    }

    @Test fun mutationDuringSuspendedOwnerReadCannotBeBoundToOldVersion() = runTest {
        val source = Source().apply { mutateDuringRead = true }
        assertNull(source.snapshot("owned-source", "7"))
    }

    @Test fun legacyUnknownVersionIsNotInventedAndCannotSatisfyExpectedVersion() = runTest {
        val source = Source().apply { version = null }
        assertNull(source.snapshot("owned-source", "7"))
        assertNull(source.snapshot("owned-source")!!.version)
    }

    private suspend fun acquireExcerpt(source: Source, expected: String?) =
        com.halo.cognition.language.InteractionId("excerpt").let { id ->
            DefaultContextAcquisitionCoordinator(workspaceContext = source).acquire(
                ContextAcquisitionPlan(id, listOf(ContextNeed(ContextKind.WORKSPACE_REFERENCE,
                    com.halo.sdk.runtime.ContextRequirement.REQUIRED, ContextAffinity.EXPLICIT_REFERENCE))),
                ContextQuery(id, "question", activeWorkspaceObjectId = "owned-source",
                    workspaceVersion = expected, explicitWorkspaceText = "Selected excerpt"))
        }

    @Test fun canonicalExcerptRequiresCapturedRevisionAndRetainsOnlySelectedText() = runTest {
        val source = Source().apply { strictResolved = true }
        assertTrue(acquireExcerpt(source, null).candidates.isEmpty())
        assertTrue(acquireExcerpt(source, "6").candidates.isEmpty())
        val admitted = acquireExcerpt(source, "7").candidates.single()
        assertEquals("7", admitted.sourceVersion)
        assertEquals(ContextContent.Text("Selected excerpt"), admitted.content)
    }

    @Test fun oldUnversionedExcerptCannotBorrowNewCurrentRevision() = runTest {
        assertTrue(acquireExcerpt(Source(), null).candidates.isEmpty())
    }

    @Test fun deletedCanonicalExcerptCannotBeRevivedFromItsOldText() = runTest {
        val source = Source().apply { strictResolved = true; version = null }
        assertTrue(acquireExcerpt(source, "7").candidates.isEmpty())
        assertTrue(acquireExcerpt(source, null).candidates.isEmpty())
        assertEquals(0, source.reads)
    }
}
