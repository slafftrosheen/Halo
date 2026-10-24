package com.halo.app.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.halo.app.di.tools.LocalModelSetupToolExecutor
import com.halo.host.android.AndroidStorage
import com.halo.providers.litert.models.*
import com.halo.providers.sherpa.stt.*
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.sdk.provider.ToolCall
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated committed Android storage and owner reconstruction; never OS process death or pack qualification. */
@RunWith(AndroidJUnit4::class)
class LocalRolePackRecoveryDeviceTest {
    @Test fun damagedRecordCanBeRecoveredWithoutAcquisitionOrFileRemoval() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val args = InstrumentationRegistry.getArguments()
        val runId = requireNotNull(args.getString("runId"))
        val sourceStamp = requireNotNull(args.getString("sourceStamp"))
        val name = "setup-recovery-fixture-${UUID.randomUUID()}"
        val root = File(context.cacheDir, name).apply { check(mkdirs()) }
        val preferencesFile = File(context.applicationInfo.dataDir, "shared_prefs/$name.xml")
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        var downloads = 0
        val speech = object : SpeechModelInventoryRepository {
            override val state = MutableStateFlow(SpeechModelInventoryState(loading = false))
            var calls = 0
            override suspend fun refresh(languageMode: String?) { calls++; error("No acquisition allowed") }
            override suspend fun install(modelId: String, onProgress: (SpeechModelDownloadProgress) -> Unit): Result<Unit> { calls++; error("No acquisition allowed") }
            override suspend fun remove(modelId: String): Result<Unit> { calls++; error("No removal allowed") }
            override suspend fun removeOwned(modelId: String, owner: String): Result<Unit> { calls++; error("No removal allowed") }
        }
        val manager = LiteRtModelManager(context, File(root, "models"), downloader = object : LiteRtModelDownloader {
            override suspend fun download(artifact: LiteRtCatalogModel, destinationPart: File,
                onProgress: (DownloadProgress) -> Unit, resumeFrom: Long): DownloadResult {
                downloads++; error("No acquisition allowed")
            }
        })
        fun open() = LocalRolePackInstaller(AndroidStorage(context, name), manager, speech, File(root, "speech"), null, scope)
        try {
            val storage = AndroidStorage(context, name)
            storage.putString(KEY, "unreadable fixture record")
            storage.putString("fixture.choice", "retained")
            val retainedFile = File(root, "retained.bin").apply { writeText("fixture file") }
            val first = open()
            assertTrue(first.state.value.recoveryRequired)
            assertFalse(first.request(LocalRolePackCommand.START_OR_RESUME, "NONE"))
            assertFalse(first.request(LocalRolePackCommand.ROLLBACK, "NONE"))
            first.startOrResume(); first.rollback()
            assertEquals("unreadable fixture record", AndroidStorage(context, name).getString(KEY))
            assertTrue(preferencesFile.readText().contains("name=\"$KEY\""))

            val recreated = open()
            assertTrue(recreated.state.value.recoveryRequired); assertTrue(recreated.state.value.canKeepFiles)
            val tools = LocalModelSetupToolExecutor(recreated)
            val dispatched = tools.execute(ToolCall("fixture-keep", "halo_local_setup_keep_files", mapOf("transactionId" to "NONE"))) as ToolExecutionResult.Success
            assertEquals(ActionTruth.DISPATCHED, dispatched.actionTruth)
            withTimeout(10_000) { recreated.state.first { !it.hasSetupRecord } }
            assertEquals(LocalRolePackInstallState(), open().state.value)
            assertNull(AndroidStorage(context, name).getString(KEY))
            assertFalse(preferencesFile.readText().contains("name=\"$KEY\""))
            assertEquals("retained", AndroidStorage(context, name).getString("fixture.choice"))
            assertEquals("fixture file", retainedFile.readText())
            assertEquals(0, speech.calls); assertEquals(0, downloads)
            // Await only this manager's empty startup scan before deleting its fixture directory.
            withTimeout(10_000) { manager.inventoryState.first { it is LocalModelInventoryState.Ready } }
            val row = JSONObject().put("scope", "MODEL_SETUP_SHARED_PREFS_OWNER_RECREATION")
                .put("runId", runId).put("sourceStamp", sourceStamp).put("durableRecordCleared", true)
                .put("retainedFilesAndChoices", true).put("acquisitionCalls", downloads + speech.calls)
                .put("fixtureUsesGlobalSetupOrSelection", false).put("osProcessDeath", "NOT_RUN")
                .put("modelQualification", "NOT_TESTED").put("actualSpokenRecognition", "NOT_RUN")
            File(context.filesDir, "model-setup-recovery-acceptance.jsonl").appendText(row.toString() + "\n")
            println("MODEL_SETUP_RECOVERY run=$runId recordCleared=true retained=true acquisitionCalls=0")
        } finally {
            job.cancelAndJoin()
            context.deleteSharedPreferences(name)
            check(root.canonicalFile.parentFile == context.cacheDir.canonicalFile)
            root.deleteRecursively()
        }
    }

    private companion object { const val KEY = "local_role_pack_transaction_v1" }
}
