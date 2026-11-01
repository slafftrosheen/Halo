package com.halo.app.data.workspace

import android.content.ContentResolver
import android.net.Uri
import com.halo.app.data.notebook.WorkspaceObjectId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
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
) {
    constructor(versions: WorkspaceFileVersions, resolver: ContentResolver, allowed: () -> Boolean) : this(
        versions::current, resolver::openInputStream, { resolver.openOutputStream(it, "wt") }, allowed)

    suspend fun write(source: WorkspaceFileVersion, destination: Uri): ExternalExportReceipt = withContext(Dispatchers.IO) {
        var writing = false
        var verifying = false
        try {
            check(allowed())
            require(destination.scheme == "content")
            val file = current(source)
            require(file.objectId == WorkspaceObjectId(source.objectId) && file.uri != destination)
            require(file.ownership == ContentOwnership.HALO_OWNED && file.sha256 != null)
            // Validate all source bytes before touching the chosen output. Never silently export a newer version.
            val bytes = requireNotNull(input(file.uri)).use { readBounded(it) }
            require(digest(bytes) == file.sha256)
            require(current(source) == file)
            check(allowed())
            coroutineContext.ensureActive()
            writing = true // Opening may create/truncate even when the provider throws.
            requireNotNull(output(destination)).use { stream ->
                var offset = 0
                while (offset < bytes.size) {
                    coroutineContext.ensureActive()
                    check(allowed())
                    val count = minOf(DEFAULT_BUFFER_SIZE, bytes.size - offset)
                    stream.write(bytes, offset, count)
                    offset += count
                }
                stream.flush()
            }
            verifying = true
            val written = requireNotNull(input(destination)).use { readBounded(it) }
            if (written.size != bytes.size || digest(written) != file.sha256)
                ExternalExportReceipt(ExternalExportOutcome.WRITE_FAILED)
            else ExternalExportReceipt(ExternalExportOutcome.VERIFIED, bytes.size.toLong(), file.sha256)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: SecurityException) { ExternalExportReceipt(if (writing) ExternalExportOutcome.UNKNOWN else ExternalExportOutcome.ACCESS_DENIED) }
        catch (_: Exception) { ExternalExportReceipt(when {
            verifying -> ExternalExportOutcome.UNKNOWN
            writing -> ExternalExportOutcome.WRITE_FAILED
            else -> ExternalExportOutcome.SOURCE_UNAVAILABLE
        }) }
    }

    /** Read-only recovery: verifying a previously chosen URI never replays a write. */
    suspend fun verify(source: WorkspaceFileVersion, destination: Uri): ExternalExportReceipt = withContext(Dispatchers.IO) {
        try {
            check(allowed())
            require(destination.scheme == "content")
            val file = current(source)
            require(file.sha256 != null && file.ownership == ContentOwnership.HALO_OWNED)
            val bytes = requireNotNull(input(destination)).use { readBounded(it) }
            if (digest(bytes) == file.sha256) ExternalExportReceipt(ExternalExportOutcome.VERIFIED, bytes.size.toLong(), file.sha256)
            else ExternalExportReceipt(ExternalExportOutcome.WRITE_FAILED)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { ExternalExportReceipt(ExternalExportOutcome.UNKNOWN) }
    }

    private suspend fun readBounded(stream: InputStream): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            coroutineContext.ensureActive()
            val count = stream.read(chunk)
            if (count < 0) break
            require(buffer.size() + count <= 20_000_000)
            buffer.write(chunk, 0, count)
        }
        return buffer.toByteArray()
    }
    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
