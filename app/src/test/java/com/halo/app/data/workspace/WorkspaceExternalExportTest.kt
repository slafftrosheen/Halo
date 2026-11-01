package com.halo.app.data.workspace

import android.net.Uri
import com.halo.app.ai.IntentQualificationFixture
import com.halo.app.data.notebook.WorkspaceObjectId
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WorkspaceExternalExportTest {
    private val bytes = "A chosen UTF-8 copy: āёжół".toByteArray()
    private val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private val sourceUri = Uri.parse("content://halo/source")
    private val target = Uri.parse("content://documents/selected-copy")
    private val source = WorkspaceFile(WorkspaceObjectId("export-source"), sourceUri, "Copy.txt", "text/plain", bytes.size.toLong(),
        FilePermissionMode.OWNED_COPY, ContentOwnership.HALO_OWNED, WorkspaceFileAvailability.OWNED_LOCAL, hash)
    private val version = WorkspaceFileVersion(source.objectId.value, hash)
    private var targetBytes: ByteArray? = null
    private var writes = 0
    private var allowed = true
    private var sourceCurrent = true
    private var verifyUnavailable = false
    private var corruptWrite = false
    private fun exporter() = WorkspaceExternalExport({ requested ->
        check(sourceCurrent && requested == version); source
    }, { uri ->
        if (uri == sourceUri) ByteArrayInputStream(bytes)
        else {
            if (verifyUnavailable) throw SecurityException("Provider read permission lost")
            targetBytes?.let(::ByteArrayInputStream)
        }
    }, {
        writes++
        object : ByteArrayOutputStream() {
            override fun close() { targetBytes = if (corruptWrite) byteArrayOf(1) else toByteArray(); super.close() }
        }
    }, { allowed })

    @Test fun successRequiresExactReadbackAndReturnsAReceipt() = runBlocking {
        val result = exporter().write(version, target)
        assertEquals(ExternalExportOutcome.VERIFIED, result.outcome)
        assertEquals(hash, result.sha256)
        assertArrayEquals(bytes, targetBytes)
        assertEquals(1, writes)
    }
    @Test fun sourceAndAuthorityFailuresNeverOpenTheOutput() = runBlocking {
        sourceCurrent = false
        assertEquals(ExternalExportOutcome.SOURCE_UNAVAILABLE, exporter().write(version, target).outcome)
        sourceCurrent = true; allowed = false
        assertNotEquals(ExternalExportOutcome.VERIFIED, exporter().write(version, target).outcome)
        allowed = true
        assertNotEquals(ExternalExportOutcome.VERIFIED, exporter().write(version, sourceUri).outcome)
        assertEquals(0, writes)
    }
    @Test fun missingReadbackStaysUnknownAndExplicitCheckNeverWritesAgain() = runBlocking {
        verifyUnavailable = true
        assertEquals(ExternalExportOutcome.UNKNOWN, exporter().write(version, target).outcome)
        assertEquals(1, writes)
        verifyUnavailable = false
        assertEquals(ExternalExportOutcome.VERIFIED, exporter().verify(version, target).outcome)
        assertEquals(1, writes)
    }
    @Test fun partialProviderOutputIsNotAnExportedCopy() = runBlocking {
        corruptWrite = true
        assertEquals(ExternalExportOutcome.WRITE_FAILED, exporter().write(version, target).outcome)
    }
    @Test fun cancelledPickerAndInterruptedJournalNeverReplayWrites() = runBlocking {
        val storage = IntentQualificationFixture.MemoryStorage()
        val session = WorkspaceExternalExportSession(storage, exporter(), this) { allowed }
        session.request(source); val picker = session.picking(); session.picked(null, picker.pickerToken)
        withTimeout(5000) { while (session.state.value?.phase != ExternalExportPhase.CANCELLED) delay(10) }
        assertEquals(0, writes)
        val raw = storage.getString("workspace.externalExport.receipt.v1")!!
        storage.putString("workspace.externalExport.receipt.v1", org.json.JSONObject(raw)
            .put("phase", "WRITING").put("destination", target.toString()).toString())
        val reopened = WorkspaceExternalExportSession(storage, exporter(), this) { allowed }
        reopened.load()
        assertEquals(ExternalExportPhase.UNKNOWN, reopened.state.value?.phase)
        assertEquals(target.toString(), reopened.state.value?.destination)
        assertEquals(0, writes)
        reopened.dismiss()
        assertNull(storage.getString("workspace.externalExport.receipt.v1"))
    }
    @Test fun restoredPickerResultLoadsItsJournalBeforeReceivingTheFile() = runBlocking {
        val storage = IntentQualificationFixture.MemoryStorage()
        val session = WorkspaceExternalExportSession(storage, exporter(), this) { allowed }
        session.request(source)
        val picker = session.picking()
        val restored = WorkspaceExternalExportSession(storage, exporter(), this) { allowed }
        restored.picked(target, picker.pickerToken)
        withTimeout(5000) { while (restored.state.value?.phase != ExternalExportPhase.VERIFIED) delay(10) }
        assertEquals(1, writes)
    }
    @Test fun duplicateAdmissionStaleResultAndCancelledRetryCannotEraseTheOriginalOutcome() = runBlocking {
        val storage = IntentQualificationFixture.MemoryStorage()
        val session = WorkspaceExternalExportSession(storage, exporter(), this) { allowed }
        session.request(source)
        val first = session.picking()
        assertTrue(runCatching { session.picking() }.isFailure)
        session.picked(null, "not-this-picker")
        delay(25)
        assertEquals(ExternalExportPhase.PICKER, session.state.value?.phase)
        verifyUnavailable = true
        session.picked(target, first.pickerToken)
        withTimeout(5000) { while (session.state.value?.phase != ExternalExportPhase.UNKNOWN) delay(10) }
        assertEquals(1, writes)
        val retry = session.picking()
        session.picked(null, retry.pickerToken)
        withTimeout(5000) { while (session.state.value?.pickerCancelled != true) delay(10) }
        assertEquals(ExternalExportPhase.UNKNOWN, session.state.value?.phase)
        assertEquals(target.toString(), session.state.value?.destination)
        assertEquals(1, writes)
        assertTrue(runCatching { session.request(source) }.isFailure)
    }
    @Test fun nonContentPickerResultProducesDismissibleFailureWithoutWriting() = runBlocking {
        val session = WorkspaceExternalExportSession(IntentQualificationFixture.MemoryStorage(), exporter(), this) { allowed }
        session.request(source)
        val picker = session.picking()
        session.picked(Uri.parse("file:///unexpected.txt"), picker.pickerToken)
        withTimeout(5000) { while (session.state.value?.phase != ExternalExportPhase.FAILED) delay(10) }
        assertEquals(0, writes)
        session.dismiss()
        assertNull(session.state.value)
    }
    @Test fun invalidPickerAndJournalFailureRetainADismissibleHonestOutcome() = runBlocking {
        val memory = IntentQualificationFixture.MemoryStorage()
        var fail = false
        val storage = object : com.halo.sdk.runtime.Storage by memory {
            override fun putString(key: String, value: String?) { check(!fail); memory.putString(key, value) }
        }
        val session = WorkspaceExternalExportSession(storage, exporter(), this) { allowed }
        session.request(source)
        val first = session.picking()
        fail = true
        session.picked(Uri.parse("file:///invalid"), first.pickerToken)
        withTimeout(5000) { while (session.state.value?.busy == true) delay(10) }
        assertEquals(ExternalExportPhase.FAILED, session.state.value?.phase)
        assertNull(session.state.value?.pickerToken)
        assertEquals(0, writes)
        fail = false; session.dismiss(); session.request(source)
        verifyUnavailable = true
        val retry = session.picking(); session.picked(target, retry.pickerToken)
        withTimeout(5000) { while (session.state.value?.phase != ExternalExportPhase.UNKNOWN) delay(10) }
        val another = session.picking(); fail = true
        session.picked(Uri.parse("file:///invalid"), another.pickerToken)
        withTimeout(5000) { while (session.state.value?.busy == true) delay(10) }
        assertEquals(ExternalExportPhase.UNKNOWN, session.state.value?.phase)
        assertEquals(target.toString(), session.state.value?.destination)
        assertEquals(1, writes)
        fail = false; session.dismiss()
    }

    @Test fun disposedHostBeforeSystemLaunchCannotStrandTheRetainedOwner() = runBlocking {
        val session = WorkspaceExternalExportSession(IntentQualificationFixture.MemoryStorage(), exporter(), this) { allowed }
        session.request(source)
        val admitted = session.picking()
        session.abandonUnlaunchedPicker() // Host rotation before its launch effect.
        withTimeout(5000) { while (session.state.value?.busy == true) delay(10) }
        assertEquals(ExternalExportPhase.CANCELLED, session.state.value?.phase)
        assertFalse(session.markPickerLaunched(admitted.pickerToken!!))
        assertEquals(0, writes)
        val launched = session.picking()
        assertTrue(session.markPickerLaunched(launched.pickerToken!!))
        session.abandonUnlaunchedPicker() // A real system picker survives host rotation.
        delay(50)
        assertEquals(ExternalExportPhase.PICKER, session.state.value?.phase)
        session.picked(null, launched.pickerToken)
        withTimeout(5000) { while (session.state.value?.busy == true) delay(10) }
        assertEquals(0, writes)
    }

}
