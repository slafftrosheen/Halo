package com.halo.app.ui.screens.home

import com.halo.sdk.runtime.Storage
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ComposerDraftStoreTest {
    private class MemoryStorage : Storage {
        private val values = ConcurrentHashMap<String, Any>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() = values.clear()
    }
    private class ReverseDispatcher : CoroutineDispatcher() {
        private val pending = mutableListOf<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { pending += block }
        fun drain() { while (pending.isNotEmpty()) pending.removeAt(pending.lastIndex).run() }
    }

    @Test fun newOwnerRestoresEachConversationWithoutChangingProviderSettings() = runBlocking {
        val storage = MemoryStorage().apply { putString("active_ai_model", "free-test-model") }
        val scope = CoroutineScope(SupervisorJob())
        try {
            val owner = ComposerDraftStore(storage, scope)
            owner.save("first", "First unsent message").join()
            owner.save("second", "Second unsent message").join()
            val recreated = ComposerDraftStore(storage, scope)
            assertEquals("First unsent message", recreated.load("first"))
            assertEquals("Second unsent message", recreated.load("second"))
            assertEquals("free-test-model", storage.getString("active_ai_model"))
        } finally { scope.cancel() }
    }

    @Test fun delayedOlderWriteCannotReplaceTheNewestDraft() = runBlocking {
        val storage = MemoryStorage()
        val scope = CoroutineScope(SupervisorJob())
        val dispatcher = ReverseDispatcher()
        try {
            val owner = ComposerDraftStore(storage, scope, dispatcher)
            owner.save("chat", "Earlier text")
            owner.save("chat", "Latest complete text")
            dispatcher.drain()
            assertEquals("Latest complete text", ComposerDraftStore(storage, scope).load("chat"))
        } finally { scope.cancel() }
    }

    @Test fun clearingDraftCannotResurrectItAfterRestart() = runBlocking {
        val storage = MemoryStorage()
        val scope = CoroutineScope(SupervisorJob())
        try {
            val owner = ComposerDraftStore(storage, scope)
            owner.save("chat", "Unsent private fixture").join()
            assertEquals("Unsent private fixture", owner.load("chat"))
            owner.save("chat", "").join()
            assertEquals("", ComposerDraftStore(storage, scope).load("chat"))
        } finally { scope.cancel() }
    }

    @Test fun acceptedClearRequiresLiteralAndRevisionAndRecoveryPreservesLaterEdits() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        try {
            val owner = ComposerDraftStore(MemoryStorage(), scope)
            owner.save("chat", "Question").join()
            val submitted = requireNotNull(owner.current("chat"))
            owner.save("chat", "Other").join(); owner.save("chat", "Question").join()
            assertNull(owner.replaceIfCurrent("chat", submitted.revision, submitted.text, ""))
            val current = requireNotNull(owner.current("chat"))
            val cleared = requireNotNull(owner.replaceIfCurrent("chat", current.revision, current.text, ""))
            owner.save("chat", "New edit").join()
            assertNull(owner.replaceIfCurrent("chat", cleared, "", current.text))
            assertEquals("New edit", owner.load("chat"))
        } finally { scope.cancel() }
    }
    @Test fun oversizedDraftRemainsWholeInMemoryWithoutOverwritingSavedText() = runBlocking {
        val storage = MemoryStorage(); val scope = CoroutineScope(SupervisorJob())
        try {
            val owner = ComposerDraftStore(storage, scope)
            owner.save("chat", "Previous durable text").join()
            val long = "x".repeat(100_001)
            owner.save("chat", long).join()
            assertEquals(long, owner.current("chat")!!.text)
            assertEquals(ComposerDraftFailure.TOO_LARGE, owner.current("chat")!!.failure)
            assertFalse(owner.current("chat")!!.saved)
            assertEquals("Previous durable text", ComposerDraftStore(storage, scope).load("chat"))
        } finally { scope.cancel() }
    }
    @Test fun unreadableSnapshotIsPreservedUntilExplicitReplacement() = runBlocking {
        val storage = MemoryStorage().apply { putString(ComposerDraftStore.STORAGE_KEY, "unreadable original bytes") }
        val scope = CoroutineScope(SupervisorJob())
        try {
            val owner = ComposerDraftStore(storage, scope)
            owner.load("chat")
            assertEquals(ComposerDraftFailure.READ, owner.current("chat")!!.failure)
            owner.save("chat", "Explicit new draft").join()
            assertEquals("unreadable original bytes", storage.getString(ComposerDraftStore.STORAGE_KEY))
            owner.replaceUnreadable("chat").join()
            assertEquals("Explicit new draft", ComposerDraftStore(storage, scope).load("chat"))
        } finally { scope.cancel() }
    }
    @Test fun temporaryMigrationDoesNotReplaceExistingTargetAndDeletionUsesExactPredicate() = runBlocking {
        val scope = CoroutineScope(SupervisorJob()); val storage = MemoryStorage()
        try {
            val owner = ComposerDraftStore(storage, scope)
            owner.save("", "Startup").join(); owner.save("existing", "Keep this").join()
            owner.migrateTemporary("existing").join()
            assertEquals("Startup", owner.load("")); assertEquals("Keep this", owner.load("existing"))
            owner.migrateTemporary("new").join()
            assertEquals("", owner.load("")); assertEquals("Startup", owner.load("new"))
            owner.pruneDeleted { it == "new" }
            owner.save("new", "Late stale editor write").join()
            val restarted = ComposerDraftStore(storage, scope)
            assertEquals("", restarted.load("new")); assertEquals("Keep this", restarted.load("existing"))
        } finally { scope.cancel() }
    }
    @Test fun delayedDiskLoadCannotOverwriteTypingAndSavedWaitsForCommit() = runBlocking {
        val storage = MemoryStorage(); val scope = CoroutineScope(SupervisorJob())
        val entered = java.util.concurrent.CountDownLatch(1); val release = java.util.concurrent.CountDownLatch(1)
        try {
            ComposerDraftStore(storage, scope).save("chat", "Old durable draft").join()
            val blocked = object : Storage by storage {
                override fun getString(key: String, defaultValue: String?): String? {
                    val old = storage.getString(key, defaultValue)
                    entered.countDown()
                    check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    return old
                }
            }
            val owner = ComposerDraftStore(blocked, scope)
            val reading = async(Dispatchers.IO) { owner.load("chat") }
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            val writing = owner.save("chat", "New typing while loading")
            assertFalse(owner.current("chat")!!.saved)
            release.countDown(); reading.await(); writing.join()
            assertEquals("New typing while loading", owner.current("chat")!!.text)
            assertTrue(owner.current("chat")!!.saved)
            assertEquals("New typing while loading", ComposerDraftStore(storage, scope).load("chat"))
        } finally { release.countDown(); scope.cancel() }
    }
    @Test fun writeFailureKeepsTextAndPreviousDurableCopyUntilExplicitRetrySucceeds() = runBlocking {
        val storage = MemoryStorage(); val scope = CoroutineScope(SupervisorJob())
        var fail = true
        try {
            ComposerDraftStore(storage, scope).save("chat", "Durable before failure").join()
            val failing = object : Storage by storage {
                override fun putString(key: String, value: String?) { if (fail) throw java.io.IOException("fixture disk failure"); storage.putString(key, value) }
            }
            val owner = ComposerDraftStore(failing, scope)
            owner.save("chat", "Keep editable text").join()
            assertEquals("Keep editable text", owner.current("chat")!!.text)
            assertEquals(ComposerDraftFailure.WRITE, owner.current("chat")!!.failure)
            assertFalse(owner.current("chat")!!.saved)
            assertEquals("Durable before failure", ComposerDraftStore(storage, scope).load("chat"))
            fail = false; owner.retry("chat").join()
            assertTrue(owner.current("chat")!!.saved)
            assertEquals("Keep editable text", ComposerDraftStore(storage, scope).load("chat"))
        } finally { scope.cancel() }
    }
    @Test fun temporaryMoveRejectsANewerEmptyTargetRevisionAndKeepsOversizedTextEditable() = runBlocking {
        val scope = CoroutineScope(SupervisorJob()); val storage = MemoryStorage()
        try {
            val owner = ComposerDraftStore(storage, scope)
            owner.save("", "Startup text").join()
            owner.save("target", "").join()
            owner.migrateTemporary("target", expectedTargetRevision = 0L).join()
            assertEquals("", owner.load("target")); assertEquals("Startup text", owner.load(""))
            val long = "x".repeat(100_001)
            owner.save("", long).join()
            owner.migrateTemporary("other").join()
            assertEquals(long, owner.current("other")!!.text)
            assertEquals(ComposerDraftFailure.TOO_LARGE, owner.current("other")!!.failure)
            assertFalse(owner.current("other")!!.saved)
        } finally { scope.cancel() }
    }
    @Test fun deletionCleanupFailureIsObservableAndRetryRemovesOnlyDeletedDraft() = runBlocking {
        val storage = MemoryStorage(); val scope = CoroutineScope(SupervisorJob())
        var fail = false
        try {
            val failing = object : Storage by storage {
                override fun putString(key: String, value: String?) { if (fail) throw java.io.IOException(); storage.putString(key, value) }
            }
            val owner = ComposerDraftStore(failing, scope)
            owner.save("deleted", "Remove this").join(); owner.save("keep", "Keep this").join()
            fail = true; owner.pruneDeleted { it == "deleted" }
            assertTrue(owner.cleanupFailure.value)
            owner.save("deleted", "Queued stale editor text").join()
            fail = false; owner.pruneDeleted { it == "deleted" }
            assertFalse(owner.cleanupFailure.value)
            val restarted = ComposerDraftStore(storage, scope)
            assertEquals("", restarted.load("deleted")); assertEquals("Keep this", restarted.load("keep"))
        } finally { scope.cancel() }
    }
    @Test fun canonicalRestoreCreatesNewIdentityWithoutRevivingDeletedDraftOrOldClear() = runBlocking {
        val storage = MemoryStorage(); val scope = CoroutineScope(SupervisorJob())
        var available = true
        try {
            val owner = ComposerDraftStore(storage, scope, canPersistConversation = { available })
            owner.save("chat", "Deleted content").join()
            val old = requireNotNull(owner.current("chat"))
            available = false; owner.pruneDeleted { it == "chat" }
            val deletedRevision = owner.current("chat")!!.revision
            available = true; assertEquals("", owner.load("chat"))
            assertTrue(owner.current("chat")!!.revision > deletedRevision)
            assertNull(owner.replaceIfCurrent("chat", old.revision, old.text, ""))
            owner.save("chat", "Fresh restored editor input").join()
            assertEquals("Fresh restored editor input", ComposerDraftStore(storage, scope).load("chat"))
        } finally { scope.cancel() }
    }
    @Test fun postRestoreTypingRacingExistenceCheckKeepsItsTextAndRevision() = runBlocking {
        val storage = MemoryStorage(); val scope = CoroutineScope(SupervisorJob())
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var checkingRestore = false
        try {
            val owner = ComposerDraftStore(storage, scope, canPersistConversation = {
                if (checkingRestore) { entered.complete(Unit); release.await() }
                true
            })
            owner.save("chat", "Before delete").join()
            owner.pruneDeleted { it == "chat" }
            checkingRestore = true
            val reading = async(Dispatchers.IO) { owner.load("chat") }
            withTimeout(5000) { entered.await() }
            val writing = owner.save("chat", "New typing after restore")
            val typedRevision = owner.current("chat")!!.revision
            release.complete(Unit); reading.await(); writing.join()
            assertEquals(typedRevision, owner.current("chat")!!.revision)
            assertEquals("New typing after restore", owner.current("chat")!!.text)
            assertTrue(owner.current("chat")!!.saved)
            assertEquals("New typing after restore", ComposerDraftStore(storage, scope).load("chat"))
        } finally { release.complete(Unit); scope.cancel() }
    }
    @Test fun renderedConversationCannotSubmitIntoDifferentCanonicalChatEvenWithIdenticalText() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        try {
            val owner = ComposerDraftStore(MemoryStorage(), scope)
            owner.save("old", "Same question").join(); owner.save("new", "Same question").join()
            val old = owner.current("old")!!
            assertNull(owner.resolveSubmission("old", old.revision, old.text, "new", null))
            assertNotNull(owner.resolveSubmission("old", old.revision, old.text, "old", null))
            owner.save("old", "New edit").join()
            assertNull(owner.resolveSubmission("old", old.revision, old.text, "old", null))
        } finally { scope.cancel() }
    }
    @Test fun startupSubmissionNeedsAnchorAndAcceptedClearFollowsOnlyExactMoveReceipt() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        try {
            val owner = ComposerDraftStore(MemoryStorage(), scope)
            owner.save("", "  Startup question  ").join()
            val rendered = owner.current("")!!
            assertNull(owner.resolveSubmission("", rendered.revision, rendered.text, "chat", null))
            assertNotNull(owner.resolveSubmission("", rendered.revision, rendered.text, "chat", "chat"))
            owner.migrateTemporary("chat", rendered.revision, 0L).join()
            val moved = requireNotNull(owner.resolveSubmission("", rendered.revision, rendered.text, "chat", "chat"))
            assertEquals("chat", moved.conversationId)
            assertNull(owner.resolveSubmission("", rendered.revision, rendered.text, "other", "other"))
            val cleared = owner.replaceIfCurrent(moved.conversationId, moved.revision, moved.literal, "")
            assertNotNull(cleared)
            owner.save("chat", "A newer edit").join()
            assertNull(owner.replaceIfCurrent("chat", cleared!!, "", rendered.text))
            assertNull(owner.resolveIdentity("", rendered.revision, rendered.text))
        } finally { scope.cancel() }
    }
    @Test fun blockedCleanupCommitCannotEraseRestoredTypingAheadOfQueuedLoadAndSave() =
        blockedCleanupRestore(failCleanup = false)

    @Test fun blockedCleanupFailureRevokesOldIdentityAndPreservesRestoredTyping() =
        blockedCleanupRestore(failCleanup = true)

    private fun blockedCleanupRestore(failCleanup: Boolean) = runBlocking {
        val storage = MemoryStorage(); val scope = CoroutineScope(SupervisorJob())
        val available = java.util.concurrent.atomic.AtomicBoolean(true)
        val blockNext = java.util.concurrent.atomic.AtomicBoolean(false)
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        try {
            val blocked = object : Storage by storage {
                override fun putString(key: String, value: String?) {
                    if (blockNext.compareAndSet(true, false)) {
                        entered.countDown()
                        check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                        if (failCleanup) throw java.io.IOException("fixture cleanup failure")
                    }
                    storage.putString(key, value)
                }
            }
            // Unconfined makes the load queue on the held mutex before save, without sleeps.
            val owner = ComposerDraftStore(blocked, scope, Dispatchers.Unconfined, canPersistConversation = { available.get() })
            owner.save("chat", "Deleted draft").join()
            val deleted = owner.current("chat")!!
            available.set(false); blockNext.set(true)
            val cleanup = async(Dispatchers.IO) { owner.pruneDeleted { !available.get() } }
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            available.set(true)
            val loading = async(start = CoroutineStart.UNDISPATCHED) { owner.load("chat") }
            val writing = owner.save("chat", "Fresh typing in restored conversation")
            val typedRevision = owner.current("chat")!!.revision
            // Revocation must be independent of whether the encrypted cleanup write succeeds.
            assertNull(owner.resolveIdentity("chat", deleted.revision, deleted.text))
            release.countDown(); cleanup.await(); loading.await(); writing.join()
            assertEquals("Fresh typing in restored conversation", owner.current("chat")!!.text)
            assertEquals(typedRevision, owner.current("chat")!!.revision)
            assertTrue(owner.current("chat")!!.saved)
            assertEquals("Fresh typing in restored conversation", ComposerDraftStore(storage, scope).load("chat"))
            assertNull(owner.replaceIfCurrent("chat", deleted.revision, deleted.text, ""))
            owner.pruneDeleted { !available.get() }
            assertFalse(owner.cleanupFailure.value)
        } finally { release.countDown(); scope.cancel() }
    }
}
