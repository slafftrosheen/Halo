package com.halo.app.di.tools.host

import android.content.Context
import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.notebook.WorkspaceObjectLifecycle
import com.halo.app.data.workspace.WorkspaceFileAvailability
import com.halo.app.data.workspace.WorkspaceFileRepository
import com.halo.app.data.workspace.WorkspaceRepository
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.runtime.cognition.tools.ToolFailureKind
import com.halo.runtime.cognition.tools.ToolRejectionKind
import com.halo.runtime.cognition.tools.ToolResultEnvelope
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * RUNTIME-056 native ability batch, harvested from the OneBox `checksum_tool`.
 *
 * Scoped deliberately to what Halo already grants: the user's own text, or a file the
 * user imported into the workspace. There is no arbitrary-path mode, because Halo has
 * no general filesystem capability and adding one here would create a second, weaker
 * data-access route around the workspace import flow.
 */
class IntegrityTools(
    private val context: Context,
    private val files: WorkspaceFileRepository,
    private val workspace: WorkspaceRepository,
) : ToolExecutor {

    override fun definitions(): List<ToolDefinition> = listOf(
        ToolDefinition(
            id = "halo_checksum",
            name = "halo_checksum",
            description = "Compute a MD5, SHA-1 or SHA-256 checksum of user-provided text or of a file already imported " +
                "into the workspace, and optionally compare it with an expected value.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "algorithm" to mapOf("type" to "string", "enum" to ALGORITHMS),
                    "source" to mapOf("type" to "string", "enum" to listOf("text", "file")),
                    "text" to mapOf("type" to "string", "maxLength" to MAX_TEXT_CHARS),
                    "objectId" to mapOf("type" to "string", "maxLength" to 500),
                    "expected" to mapOf("type" to "string", "maxLength" to 128),
                ),
                "required" to listOf("algorithm", "source"),
                "additionalProperties" to false,
            ),
        ),
    )

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String, kind: ToolRejectionKind) =
            ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason, kind)

        if (toolCall.functionName != "halo_checksum") return reject("Unknown tool", ToolRejectionKind.TOOL_UNKNOWN)

        val algorithm = (toolCall.arguments["algorithm"] as? String)?.lowercase()
            ?: return reject("algorithm is required", ToolRejectionKind.INVALID_ARGUMENTS)
        if (algorithm !in ALGORITHMS) {
            return reject("Unsupported algorithm; use ${ALGORITHMS.joinToString(", ")}", ToolRejectionKind.INVALID_ARGUMENTS)
        }
        val source = (toolCall.arguments["source"] as? String)?.lowercase()
            ?: return reject("source is required", ToolRejectionKind.INVALID_ARGUMENTS)
        val expected = (toolCall.arguments["expected"] as? String)?.trim()?.takeIf { it.isNotEmpty() }

        return when (source) {
            "text" -> {
                val text = toolCall.arguments["text"] as? String
                    ?: return reject("text is required when source=text", ToolRejectionKind.INVALID_ARGUMENTS)
                if (text.length > MAX_TEXT_CHARS) {
                    return reject("Text exceeds $MAX_TEXT_CHARS characters", ToolRejectionKind.INVALID_ARGUMENTS)
                }
                respond(toolCall, algorithm, digestOf(text.toByteArray(Charsets.UTF_8), algorithm), text.length.toLong(), expected)
            }

            "file" -> {
                val objectId = toolCall.arguments["objectId"] as? String
                    ?: return reject("objectId is required when source=file", ToolRejectionKind.INVALID_ARGUMENTS)
                checksumFile(toolCall, algorithm, objectId, expected)
            }

            else -> reject("Unsupported source; use text or file", ToolRejectionKind.INVALID_ARGUMENTS)
        }
    }

    private suspend fun checksumFile(
        toolCall: ToolCall,
        algorithm: String,
        objectId: String,
        expected: String?,
    ): ToolExecutionResult = withContext(Dispatchers.IO) {
        fun reject(reason: String, kind: ToolRejectionKind) =
            ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason, kind)

        val id = WorkspaceObjectId(objectId)
        val item = workspace.get(id)
            ?: return@withContext reject("Workspace object not found; search it in Workspace first", ToolRejectionKind.INVALID_ARGUMENTS)
        if (item.lifecycle == WorkspaceObjectLifecycle.TRASHED) {
            return@withContext reject("That file is in the trash", ToolRejectionKind.INVALID_ARGUMENTS)
        }
        val file = files.getFile(id)
            ?: return@withContext reject("Imported file not found for $objectId", ToolRejectionKind.INVALID_ARGUMENTS)
        if (file.availability !in setOf(WorkspaceFileAvailability.AVAILABLE, WorkspaceFileAvailability.OWNED_LOCAL)) {
            return@withContext reject("File is unavailable; relink it in Workspace", ToolRejectionKind.CAPABILITY_UNAVAILABLE)
        }
        if (file.sizeBytes != null && file.sizeBytes > MAX_FILE_BYTES) {
            return@withContext reject(
                "File is larger than the ${MAX_FILE_BYTES / MEGABYTE} MB checksum limit",
                ToolRejectionKind.UNSUPPORTED_OPERATION,
            )
        }

        try {
            val digest = MessageDigest.getInstance(algorithm)
            var bytes = 0L
            context.contentResolver.openInputStream(file.uri)?.use { input ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    bytes += read
                    if (bytes > MAX_FILE_BYTES) {
                        return@withContext reject(
                            "File is larger than the ${MAX_FILE_BYTES / MEGABYTE} MB checksum limit",
                            ToolRejectionKind.UNSUPPORTED_OPERATION,
                        )
                    }
                    digest.update(buffer, 0, read)
                }
            } ?: return@withContext reject("File cannot be opened", ToolRejectionKind.CAPABILITY_UNAVAILABLE)

            respond(toolCall, algorithm, digest.digest().toHex(), bytes, expected)
        } catch (error: SecurityException) {
            reject("Document access expired; relink the file in Workspace", ToolRejectionKind.PERMISSION_REQUIRED)
        } catch (error: java.io.IOException) {
            ToolExecutionResult.Failed(
                toolCallId = toolCall.id,
                toolId = toolCall.functionName,
                message = error.message ?: "Unable to read the imported file",
                failureKind = ToolFailureKind.TRANSIENT,
                code = "FILE_READ_FAILED",
            )
        }
    }

    /**
     * A mismatch is a successful measurement with a negative answer, not a tool
     * failure: the model asked a question and got the truth.
     */
    private fun respond(
        toolCall: ToolCall,
        algorithm: String,
        checksum: String,
        byteCount: Long,
        expected: String?,
    ): ToolExecutionResult {
        val matched = expected?.let { it.equals(checksum, ignoreCase = true) }
        val details = buildString {
            appendLine("algorithm=$algorithm")
            appendLine("checksum=$checksum")
            appendLine("bytes=$byteCount")
            if (matched != null) {
                appendLine("expected=$expected")
                append("matched=").append(matched)
            }
        }
        return ToolExecutionResult.Success(
            toolCallId = toolCall.id,
            toolId = toolCall.functionName,
            output = ToolResultEnvelope.success(actionTruth = ActionTruth.CONFIRMED, details = details),
            actionTruth = ActionTruth.CONFIRMED,
        )
    }

    private fun digestOf(bytes: ByteArray, algorithm: String): String =
        MessageDigest.getInstance(algorithm).digest(bytes).toHex()

    /** Raw lowercase hex would be ambiguous when a caller echoes it back; `:`-free is the common form. */
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        val ALGORITHMS = listOf("md5", "sha1", "sha256")
        const val MAX_TEXT_CHARS = 20_000
        const val MAX_FILE_BYTES = 256L * 1024 * 1024
        const val BUFFER_BYTES = 64 * 1024
        const val MEGABYTE = 1024L * 1024L
    }
}
