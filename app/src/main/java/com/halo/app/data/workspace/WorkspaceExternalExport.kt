package com.halo.app.data.workspace

import android.content.ContentResolver
import android.net.Uri
import com.halo.app.data.notebook.WorkspaceObjectId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

enum class ExternalExportOutcome { VERIFIED, SOURCE_UNAVAILABLE, ACCESS_DENIED, WRITE_FAILED, UNKNOWN }
data class ExternalExportReceipt(val outcome: ExternalExportOutcome, val bytes: Long? = null, val sha256: String? = null)

/** Transport only. Source authority remains with Workspace; the system picker owns destination choice. */
class WorkspaceExternalExport(
    private val current: suspend (WorkspaceFileVersion) -> WorkspaceFile,
    private val input: (Uri) -> InputStream?,
    private val output: (Uri) -> OutputStream?,
    private val allowed: () -> Boolean,
    private val snapshotFile: () -> File = { java.nio.file.Files.createTempFile("halo-export-", ".tmp").toFile() },
    private val maxBytes: Long = 256L * 1024 * 1024,
) {
    constructor(versions: WorkspaceFileVersions, resolver: ContentResolver, allowed: () -> Boolean, cacheDir: File) : this(
        versions::current, resolver::openInputStream, { resolver.openOutputStream(it, "wt") }, allowed,
        snapshotFile = { File(cacheDir, "workspace-export").also { check(it.exists() || it.mkdirs()) }
            .let { File.createTempFile("snapshot-", ".tmp", it) } })

    suspend fun write(source: WorkspaceFileVersion, destination: Uri): ExternalExportReceipt = withContext(Dispatchers.IO) {
        var writing = false
        var verifying = false
        var snapshot: File? = null
        try {
            check(allowed())
            require(destination.scheme == "content")
            val file = current(source)
            require(file.objectId == WorkspaceObjectId(source.objectId) && file.uri != destination)
            require(file.ownership == ContentOwnership.HALO_OWNED && file.sha256 != null)
            // Stage a bounded immutable snapshot before touching the chosen output.
            // Large video files never require a whole-file ByteArray, and an aliased
            // provider destination cannot truncate the source before it has been copied.
            val staged = snapshotFile().also { snapshot = it }
            val original = FileOutputStream(staged).use { sink ->
                requireNotNull(input(file.uri)).use { stream -> digestStream(stream, sink) }.also { sink.flush(); sink.fd.sync() }
            }
            require(original.sha256 == file.sha256 && (file.sizeBytes == null || original.bytes == file.sizeBytes))
            require(current(source) == file)
            check(allowed()); coroutineContext.ensureActive()
            writing = true // Opening may create/truncate even when the provider throws.
            val copied = requireNotNull(output(destination)).use { sink ->
                staged.inputStream().use { stream -> digestStream(stream, sink) }.also { sink.flush() }
            }
            if (copied != original) return@withContext ExternalExportReceipt(ExternalExportOutcome.WRITE_FAILED)
            verifying = true
            val written = requireNotNull(input(destination)).use { digestStream(it) }
            if (written != original) ExternalExportReceipt(ExternalExportOutcome.WRITE_FAILED)
            else ExternalExportReceipt(ExternalExportOutcome.VERIFIED, original.bytes, original.sha256)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: SecurityException) { ExternalExportReceipt(if (writing) ExternalExportOutcome.UNKNOWN else ExternalExportOutcome.ACCESS_DENIED) }
        catch (_: Exception) { ExternalExportReceipt(when {
            verifying -> ExternalExportOutcome.UNKNOWN
            writing -> ExternalExportOutcome.WRITE_FAILED
            else -> ExternalExportOutcome.SOURCE_UNAVAILABLE
        }) }
        finally { snapshot?.delete() }
    }

    /** Read-only recovery: verifying a previously chosen URI never replays a write. */
    suspend fun verify(source: WorkspaceFileVersion, destination: Uri): ExternalExportReceipt = withContext(Dispatchers.IO) {
        try {
            check(allowed())
            require(destination.scheme == "content")
            val file = current(source)
            require(file.sha256 != null && file.ownership == ContentOwnership.HALO_OWNED)
            val read = requireNotNull(input(destination)).use { digestStream(it) }
            if (read.sha256 == file.sha256 && (file.sizeBytes == null || read.bytes == file.sizeBytes))
                ExternalExportReceipt(ExternalExportOutcome.VERIFIED, read.bytes, file.sha256)
            else ExternalExportReceipt(ExternalExportOutcome.WRITE_FAILED)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { ExternalExportReceipt(ExternalExportOutcome.UNKNOWN) }
    }

    private data class Digest(val bytes: Long, val sha256: String)
    private suspend fun digestStream(stream: InputStream, output: OutputStream? = null): Digest {
        val digest = MessageDigest.getInstance("SHA-256")
        val chunk = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            coroutineContext.ensureActive(); check(allowed())
            val count = stream.read(chunk)
            if (count < 0) break
            require(total <= maxBytes - count) { "File exceeds export limit" }
            output?.write(chunk, 0, count)
            digest.update(chunk, 0, count); total += count
        }
        return Digest(total, digest.digest().joinToString("") { "%02x".format(it) })
    }
}
