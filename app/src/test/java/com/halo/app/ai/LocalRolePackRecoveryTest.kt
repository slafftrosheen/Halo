package com.halo.app.ai

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.halo.providers.litert.models.*
import com.halo.providers.sherpa.stt.*
import com.halo.sdk.runtime.Storage
import com.halo.app.di.tools.LocalModelSetupToolExecutor
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.sdk.provider.ToolCall
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LocalRolePackRecoveryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun missingRecordIsIdleWithoutInventedOwnershipOrRecoveryAction() = runTest {
        val f = Fixture(backgroundScope)
        val owner = f.owner()
        assertEquals(LocalRolePackInstallState(), owner.state.value)
        assertFalse(owner.request(LocalRolePackCommand.KEEP_FILES, "NONE"))
        assertEquals(0, f.storage.removes)
    }

    @Test fun malformedAndOversizedRecordsSurviveBothDirectAndTypedMutationAttempts() = runTest {
        for (raw in listOf("unreadable", "\t".repeat(16001), "")) {
            val f = Fixture(backgroundScope); f.storage.putString(KEY, raw)
            val owner = f.owner()
            assertRecovery(owner)
            assertFalse(owner.request(LocalRolePackCommand.START_OR_RESUME, "NONE"))
            assertFalse(owner.request(LocalRolePackCommand.ROLLBACK, "NONE"))
            owner.startOrResume(); owner.rollback(); runCurrent()
            assertEquals(raw, f.storage.getString(KEY))
            assertEquals(0, f.speech.calls); assertEquals(0, f.downloads)
            assertEquals(0, f.storage.removes)
            assertRecovery(owner)
        }
    }

    @Test fun keepFilesClearsOnlyTheObservedRecordAndPreservesFilesAndChoices() = runTest {
        val f = Fixture(backgroundScope); f.storage.putString(KEY, "unreadable")
        f.storage.putString("user.choice", "retained")
        val file = File(f.root, "untouched.bin").apply { writeText("retained file") }
        val owner = f.owner()
        val tools = LocalModelSetupToolExecutor(owner)
        val status = tools.execute(ToolCall("status", "halo_local_setup_status", emptyMap())) as ToolExecutionResult.Success
        val json = org.json.JSONObject(status.output)
        assertTrue(json.getBoolean("hasSetupRecord")); assertTrue(json.getBoolean("recoveryRequired"))
        assertTrue(json.getBoolean("canKeepFiles")); assertFalse(json.getBoolean("canRollback"))
        assertFalse(json.getBoolean("canResume")); assertFalse(json.getBoolean("qualificationGranted"))
        val dispatched = tools.execute(ToolCall("keep", "halo_local_setup_keep_files", mapOf("transactionId" to "NONE"))) as ToolExecutionResult.Success
        assertEquals(ActionTruth.DISPATCHED, dispatched.actionTruth)
        runCurrent()
        assertEquals(LocalRolePackInstallState(), owner.state.value)
        assertNull(f.storage.getString(KEY)); assertEquals(1, f.storage.removes)
        assertEquals("retained", f.storage.getString("user.choice"))
        assertEquals("retained file", file.readText())
        assertEquals(0, f.speech.calls); assertEquals(0, f.downloads)
        assertFalse(owner.request(LocalRolePackCommand.KEEP_FILES, "NONE"))
    }

    @Test fun failedStorageRemovalRetainsRecoveryAndAllowsAnExplicitRetry() = runTest {
        val f = Fixture(backgroundScope); f.storage.putString(KEY, "unreadable")
        val owner = f.owner(); f.storage.failRemove = true
        owner.keepDownloadedFiles(); runCurrent()
        assertRecovery(owner); assertEquals("unreadable", f.storage.getString(KEY))
        f.storage.failRemove = false
        owner.keepDownloadedFiles(); runCurrent()
        assertNull(f.storage.getString(KEY)); assertEquals(LocalRolePackInstallState(), owner.state.value)
        assertEquals(0, f.speech.calls); assertEquals(0, f.downloads)
    }

    @Test fun queuedClearCannotRemoveAReplacedRecordEvenWithSameNoneToken() = runTest {
        val f = Fixture(backgroundScope); f.storage.putString(KEY, "first")
        val owner = f.owner()
        assertTrue(owner.request(LocalRolePackCommand.KEEP_FILES, "NONE"))
        f.storage.putString(KEY, "replacement")
        runCurrent()
        assertEquals("replacement", f.storage.getString(KEY)); assertEquals(0, f.storage.removes)
        assertRecovery(owner)
        assertFalse(owner.request(LocalRolePackCommand.KEEP_FILES, "NONE"))
    }

    @Test fun malformedUnicodeCannotAliasAnotherObservedRecord() = runTest {
        val f = Fixture(backgroundScope); f.storage.putString(KEY, "bad\uD800")
        val owner = f.owner(); owner.keepDownloadedFiles()
        f.storage.putString(KEY, "bad\uD801"); runCurrent()
        assertEquals("bad\uD801", f.storage.getString(KEY)); assertEquals(0, f.storage.removes)
    }

    @Test fun legacyAndChangedPlanRecordsOfferKeepOnlyWithoutNewOwnership() = runTest {
        val f = Fixture(backgroundScope)
        val initial = f.owner()
        val method = LocalRolePackInstaller::class.java.getDeclaredMethod("currentPlanFingerprint").apply { isAccessible = true }
        val plan = method.invoke(initial) as String
        val baseline = LocalRolePackJournal(LocalRolePackPhase.PAUSED, LocalRolePackStage.SPEECH_OUTPUT,
            true, true, plan, true, true, emptySet(), transactionId = TOKEN)
        for (journal in listOf(baseline.copy(transactionId = null), baseline.copy(planFingerprint = "f".repeat(64)))) {
            val raw = journal.encode(); f.storage.putString(KEY, raw)
            val owner = f.owner(); assertRecovery(owner)
            assertEquals(journal.transactionId, owner.state.value.transactionId)
            val token = journal.transactionId ?: "NONE"
            assertFalse(owner.request(LocalRolePackCommand.START_OR_RESUME, token))
            assertFalse(owner.request(LocalRolePackCommand.ROLLBACK, token))
            owner.startOrResume(); owner.rollback(); runCurrent()
            assertEquals(raw, f.storage.getString(KEY))
            assertTrue(owner.request(LocalRolePackCommand.KEEP_FILES, token)); runCurrent()
            assertNull(f.storage.getString(KEY))
        }
        assertEquals(0, f.speech.calls); assertEquals(0, f.downloads)
    }

    @Test fun validInterruptedRecordRetainsItsTokenAndResumableBaseline() = runTest {
        val f = Fixture(backgroundScope)
        val method = LocalRolePackInstaller::class.java.getDeclaredMethod("currentPlanFingerprint").apply { isAccessible = true }
        val plan = method.invoke(f.owner()) as String
        val record = LocalRolePackJournal(LocalRolePackPhase.INSTALLING, LocalRolePackStage.SPEECH_ACTIVITY,
            true, true, plan, true, false, setOf("a".repeat(64)), transactionId = TOKEN)
        f.storage.putString(KEY, record.encode())
        val owner = f.owner()
        assertEquals(TOKEN, owner.state.value.transactionId)
        assertTrue(owner.state.value.canResume); assertTrue(owner.state.value.canRollback)
        assertTrue(owner.state.value.canKeepFiles); assertFalse(owner.state.value.recoveryRequired)
        val saved = requireNotNull(LocalRolePackJournal.decode(f.storage.getString(KEY)))
        assertEquals(record.copy(phase = LocalRolePackPhase.PAUSED, message = saved.message), saved)
        assertFalse(owner.request(LocalRolePackCommand.KEEP_FILES, "NONE"))
        assertTrue(owner.request(LocalRolePackCommand.KEEP_FILES, TOKEN)); runCurrent()
        assertNull(f.storage.getString(KEY))
    }

    @Test fun failedRecoveryWritePreservesDurableRecordAndOffersKeepOnly() = runTest {
        val f = Fixture(backgroundScope)
        val method = LocalRolePackInstaller::class.java.getDeclaredMethod("currentPlanFingerprint").apply { isAccessible = true }
        val plan = method.invoke(f.owner()) as String
        val record = LocalRolePackJournal(LocalRolePackPhase.INSTALLING, LocalRolePackStage.INTENT,
            true, true, plan, true, true, emptySet(), transactionId = TOKEN).encode()
        f.storage.putString(KEY, record); f.storage.failWrite = true
        val owner = f.owner(); assertRecovery(owner)
        assertEquals(record, f.storage.getString(KEY))
        f.storage.failWrite = false
        assertTrue(owner.request(LocalRolePackCommand.KEEP_FILES, TOKEN)); runCurrent()
        assertNull(f.storage.getString(KEY))
        assertEquals(0, f.speech.calls); assertEquals(0, f.downloads)
    }

    @Test fun queuedKeepCannotClearOrOverwriteANewerCanonicalReceiptWithTheSameToken() = runTest {
        for (typed in listOf(false, true)) {
            val f = Fixture(backgroundScope)
            val record = LocalRolePackJournal(LocalRolePackPhase.PAUSED, LocalRolePackStage.INTENT,
                true, true, f.plan(), true, true, emptySet(), transactionId = TOKEN).encode()
            f.storage.putString(KEY, record)
            val owner = f.owner()
            owner.startOrResume()
            if (typed) assertTrue(owner.request(LocalRolePackCommand.KEEP_FILES, TOKEN))
            else owner.keepDownloadedFiles()
            runCurrent()
            // Unavailable TTS causes the real resume to persist FAILED under the SAME token.
            val latest = requireNotNull(LocalRolePackJournal.decode(f.storage.getString(KEY)))
            assertEquals(TOKEN, latest.transactionId); assertEquals(LocalRolePackPhase.FAILED, latest.phase)
            assertNotEquals(record, f.storage.getString(KEY)); assertEquals(0, f.storage.removes)
            assertEquals(LocalRolePackPhase.FAILED, owner.state.value.phase)
            assertFalse(owner.state.value.recoveryRequired)
        }
    }

    @Test fun failedTerminalWriteDoesNotLeaveACompletedJobPermanentlyBusy() = runTest {
        val f = Fixture(backgroundScope)
        val owner = f.owner()
        f.storage.afterWrite = { key -> if (key == KEY) f.storage.failWrite = true }
        owner.startOrResume()
        withContext(Dispatchers.Default) {
            withTimeout(5000) { owner.state.first { it.recoveryRequired } }
        }
        assertRecovery(owner)
        val durable = requireNotNull(LocalRolePackJournal.decode(f.storage.getString(KEY)))
        assertEquals(LocalRolePackPhase.PREFLIGHT, durable.phase)
        assertFalse(owner.request(LocalRolePackCommand.START_OR_RESUME, requireNotNull(durable.transactionId)))
        assertEquals(0, f.downloads)
        f.storage.failWrite = false; f.storage.afterWrite = {}
        assertTrue(owner.request(LocalRolePackCommand.KEEP_FILES, requireNotNull(durable.transactionId)))
        runCurrent()
        assertEquals(LocalRolePackInstallState(), owner.state.value)
        assertNull(f.storage.getString(KEY))
    }

    @Test fun failedClearOfAKnownReceiptExposesVisibleKeepOnlyRecovery() = runTest {
        val f = Fixture(backgroundScope)
        f.storage.putString(KEY, LocalRolePackJournal(LocalRolePackPhase.PAUSED, LocalRolePackStage.INTENT,
            true, true, f.plan(), true, true, emptySet(), transactionId = TOKEN).encode())
        val owner = f.owner(); f.storage.failRemove = true
        owner.keepDownloadedFiles(); runCurrent()
        assertRecovery(owner)
        assertTrue(requireNotNull(owner.state.value.message).contains("could not be cleared"))
        f.storage.failRemove = false
        assertTrue(owner.request(LocalRolePackCommand.KEEP_FILES, TOKEN)); runCurrent()
        assertNull(f.storage.getString(KEY))
    }

    @Test fun failedRollbackAdmissionWriteNeverStartsRemovalOrLeavesBusyState() = runTest {
        val f = Fixture(backgroundScope)
        val raw = LocalRolePackJournal(LocalRolePackPhase.PAUSED, LocalRolePackStage.SPEECH_RECOGNITION,
            true, true, f.plan(), false, true, emptySet(), transactionId = TOKEN).encode()
        f.storage.putString(KEY, raw)
        val owner = f.owner(); f.storage.failWrite = true
        owner.rollback(); runCurrent()
        assertRecovery(owner); assertEquals(raw, f.storage.getString(KEY))
        assertEquals(0, f.speech.calls); assertEquals(0, f.downloads)
    }

    @Test fun failedRollbackTerminalWriteRetainsDurableReceiptAndKeepOnlyRecovery() = runTest {
        val f = Fixture(backgroundScope)
        f.storage.putString(KEY, LocalRolePackJournal(LocalRolePackPhase.PAUSED, LocalRolePackStage.SPEECH_RECOGNITION,
            true, true, f.plan(), false, true, emptySet(), transactionId = TOKEN).encode())
        val owner = f.owner()
        f.storage.afterWrite = { key -> if (key == KEY) f.storage.failWrite = true }
        owner.rollback(); runCurrent()
        assertRecovery(owner)
        val durable = requireNotNull(LocalRolePackJournal.decode(f.storage.getString(KEY)))
        assertEquals(LocalRolePackPhase.ROLLING_BACK, durable.phase); assertEquals(TOKEN, durable.transactionId)
        assertEquals(1, f.speech.calls) // Fake owned removal fails; no physical file is touched.
        assertEquals(0, f.downloads)
    }

    @Test fun failedNewTransactionCannotMakeAPreviousCompletedPackRollbackEligible() = runTest {
        val f = Fixture(backgroundScope)
        val raw = LocalRolePackJournal(LocalRolePackPhase.COMPLETE, LocalRolePackStage.DONE,
            false, false, f.plan(), false, false, emptySet(), transactionId = TOKEN).encode()
        f.storage.putString(KEY, raw)
        val owner = f.owner(); f.storage.failWrite = true
        owner.startOrResume()
        withContext(Dispatchers.Default) { withTimeout(5000) { owner.state.first { it.recoveryRequired } } }
        assertRecovery(owner)
        assertEquals(raw, f.storage.getString(KEY))
        assertEquals(0, f.downloads)
        assertFalse(owner.request(LocalRolePackCommand.ROLLBACK, TOKEN))
    }

    @Test fun sourceReviewIsExactPersistentAndRequiredBeforeAnyNewAcquisition() = runTest {
        val f = Fixture(backgroundScope, reviewed = false)
        val owner = f.owner()
        assertFalse(owner.sourceReviewAcknowledged)
        assertFalse(owner.request(LocalRolePackCommand.START_OR_RESUME, "NONE"))
        owner.startOrResume(); runCurrent()
        assertEquals(LOCAL_ROLE_PACK_REVIEW_REQUIRED, owner.state.value.message)
        assertNull(f.storage.getString(KEY)); assertEquals(0, f.downloads)
        assertFalse(owner.acknowledgeSourceReview("f".repeat(64), true))
        assertFalse(owner.sourceReviewAcknowledged)
        assertTrue(owner.acknowledgeSourceReview(PrivateOfflinePack.fingerprint, true))
        assertTrue(f.owner().sourceReviewAcknowledged)
        assertTrue(owner.acknowledgeSourceReview(PrivateOfflinePack.fingerprint, false))
        assertFalse(f.owner().sourceReviewAcknowledged)
        assertEquals(0, f.downloads)
    }

    private fun assertRecovery(owner: LocalRolePackInstaller) {
        val value = owner.state.value
        assertEquals(LocalRolePackPhase.ROLLBACK_FAILED, value.phase)
        assertTrue(value.hasSetupRecord); assertTrue(value.recoveryRequired); assertTrue(value.canKeepFiles)
        assertFalse(value.canResume); assertFalse(value.canRollback); assertFalse(value.busy)
    }

    private inner class Fixture(private val scope: CoroutineScope, reviewed: Boolean = true) {
        val root = temporary.newFolder()
        val storage = RecordStorage()
        val speech = Speech()
        var downloads = 0
        private val manager = LiteRtModelManager(ApplicationProvider.getApplicationContext(), File(root, "models"),
            downloader = object : LiteRtModelDownloader {
                override suspend fun download(artifact: LiteRtCatalogModel, destinationPart: File,
                    onProgress: (DownloadProgress) -> Unit, resumeFrom: Long): DownloadResult {
                    downloads++; error("Unexpected acquisition")
                }
            })
        init {
            if (reviewed) check(owner().acknowledgeSourceReview(PrivateOfflinePack.fingerprint, true))
        }
        fun owner() = LocalRolePackInstaller(storage, manager, speech, File(root, "speech"), null, scope)
        fun plan(): String = LocalRolePackInstaller::class.java.getDeclaredMethod("currentPlanFingerprint")
            .apply { isAccessible = true }.invoke(owner()) as String
    }

    private class Speech : SpeechModelInventoryRepository {
        override val state = MutableStateFlow(SpeechModelInventoryState(loading = false))
        var calls = 0
        override suspend fun refresh(languageMode: String?) { calls++ }
        override suspend fun install(modelId: String, onProgress: (SpeechModelDownloadProgress) -> Unit): Result<Unit> { calls++; error("Unexpected acquisition") }
        override suspend fun remove(modelId: String): Result<Unit> { calls++; error("Unexpected removal") }
        override suspend fun removeOwned(modelId: String, owner: String): Result<Unit> { calls++; error("Unexpected removal") }
    }

    private class RecordStorage : Storage {
        val values = mutableMapOf<String, Any>()
        var removes = 0
        var failRemove = false
        var failWrite = false
        var afterWrite: (String) -> Unit = {}
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) {
            check(!failWrite)
            if (value == null) values.remove(key) else values[key] = value
            afterWrite(key)
        }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { check(!failRemove); removes++; values.remove(key) }
        override fun clear() { values.clear() }
    }

    private companion object {
        const val KEY = "local_role_pack_transaction_v1"
        const val TOKEN = "b88fe002-14e0-48dc-85bd-6dbcd038f709"
    }
}
