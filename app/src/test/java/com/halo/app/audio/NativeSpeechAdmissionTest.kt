package com.halo.app.audio

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.halo.runtime.kernel.resource.DefaultResourceLeaseManager
import com.halo.sdk.provider.SpeechRequest
import com.halo.sdk.provider.SegmentedPcmSpeechProvider
import com.halo.sdk.runtime.*
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class NativeSpeechAdmissionTest {
    private class Preferences : Storage {
        private val values = mutableMapOf<String, String>()
        override fun getString(key: String, defaultValue: String?) = values[key] ?: defaultValue
        override fun putString(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key]?.toBooleanStrictOrNull() ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value.toString() }
        override fun getInt(key: String, defaultValue: Int) = values[key]?.toIntOrNull() ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value.toString() }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }

    @Test fun cancelledKokoroAdmissionReleasesPartialComputeReservation() = checkCancelledAdmission(false)
    @Test fun cancelledSupertonicAdmissionReleasesPartialComputeReservation() = checkCancelledAdmission(true)

    private fun checkCancelledAdmission(supertonic: Boolean) = runBlocking {
        val root = java.nio.file.Files.createTempDirectory("halo-speech-admission-").toFile()
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun getNoBackupFilesDir() = File(root, "models").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        }
        val preferences = Preferences()
        val leases = DefaultResourceLeaseManager()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val resources = NativeResourceCoordinator(leases, scope)
        val entered = CompletableDeferred<Unit>()
        val unblock = CompletableDeferred<Unit>()
        val blocker = object : NativeResourceOwner {
            override val nativeResourceId = "fixture:independent-asr"
            override val nativeResourceKind = NativeResourceKind.HEAVY_ASR
            override val defaultResourcePolicy = WorkloadResourcePolicy(RuntimeWorkloadClass.ASR)
            override suspend fun releaseIfIdle(): Boolean {
                entered.complete(Unit)
                unblock.await()
                return false // Independent residency must survive the cancelled TTS request.
            }
        }
        var releaseJob: Job? = null
        var speechJob: Job? = null
        var provider: SegmentedPcmSpeechProvider? = null
        try {
            resources.updateBudget(NativeResourceBudget(8L shl 30, 8L shl 30))
            val request: SpeechRequest
            if (supertonic) {
                val speech = SupertonicSpeechProvider(context, preferences, resources)
                val archive = File(root, "fixture.zip")
                ZipOutputStream(archive.outputStream()).use { zip ->
                    SpeechPackRegistry.supertonic.requiredFiles.sorted().forEach { name ->
                        zip.putNextEntry(ZipEntry(name))
                        zip.write(ByteArray(if (name.endsWith(".onnx")) 4097 else 20) { 1 })
                        zip.closeEntry()
                    }
                }
                val hash = speech.importPack(Uri.fromFile(archive))
                provider = speech
                request = SpeechRequest("Fixture speech", modelId = hash, language = "en", voiceId = "0",
                    voiceSettings = mapOf("_supertonicPreview" to 1.0))
            } else {
                val hash = "a".repeat(64)
                val directory = File(context.noBackupFilesDir, "kokoro/$hash").apply { mkdirs() }
                listOf("model.onnx", "voices.bin", "tokens.txt").forEach { File(directory, it).writeText("fixture") }
                File(directory, "espeak-ng-data").mkdirs()
                preferences.putString("kokoro_pack_version", hash)
                provider = KokoroSpeechProvider(context, preferences, resources)
                request = SpeechRequest("Fixture speech", modelId = hash, language = "en", voiceId = "0")
            }
            val speech = requireNotNull(provider)
            val ownerId = if (supertonic) SupertonicSpeechProvider.ID else KokoroSpeechProvider.ID
            assertTrue(resources.acquire(blocker))
            resources.mark(blocker, NativeResourceState.RESIDENT, "Fixture idle")
            releaseJob = launch(Dispatchers.Default) { resources.releaseIdle("Block coordinator admission") }
            withTimeout(5_000) { entered.await() }
            speechJob = launch(Dispatchers.Default) { speech.synthesizeToPcm(request) }
            withTimeout(5_000) {
                leases.snapshotState.first { state -> state.active.any { it.holder.id == ownerId } }
            }
            speechJob.cancel()
            unblock.complete(Unit)
            withTimeout(5_000) { speechJob.join(); releaseJob.join() }
            assertTrue(speechJob.isCancelled)
            assertFalse(leases.snapshot().active.any { it.holder.id == ownerId })
            assertFalse(resources.snapshot.value.any { it.ownerId == ownerId && it.executionLeaseHeld })
            assertEquals(NativeResourceState.RESIDENT, resources.executionSnapshot(blocker)?.state)
            // Cancellation occurred while coordinator admission was blocked, before native initialization.
            assertFalse(resources.snapshot.value.any { it.ownerId == ownerId && it.state in
                setOf(NativeResourceState.LOADING, NativeResourceState.IN_USE) })
        } finally {
            unblock.complete(Unit)
            withContext(NonCancellable) {
                speechJob?.cancelAndJoin(); releaseJob?.cancelAndJoin()
                provider?.close(); resources.unregister(blocker)
            }
            scope.cancel()
            root.deleteRecursively()
        }
    }
}
