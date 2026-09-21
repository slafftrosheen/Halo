package com.halo.app.di.tools

import com.halo.app.backup.BackupLocationState
import com.halo.app.backup.StorageAccessRepository
import com.halo.app.storage.StorageUsageRepository
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition

/** Read-only status. Deliberately never exposes a Vault URI or any user content. */
class StorageStatusToolExecutor(
    private val usageRepository: StorageUsageRepository,
    private val accessRepository: StorageAccessRepository,
) : ToolExecutor {
    override fun definitions() = listOf(
        ToolDefinition(
            id = "halo_storage_status",
            name = "halo_storage_status",
            description = "Reports local Halo storage sizes and whether a user-owned backup folder is configured.",
            parameters = mapOf(
                "type" to "object",
                "properties" to emptyMap<String, Any>(),
                "additionalProperties" to false,
            ),
        )
    )

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        usageRepository.refresh()
        accessRepository.validatePersistedAccess()
        val usage = usageRepository.usage.value
        val configured = accessRepository.state.value.backupLocation is BackupLocationState.Available
        return ToolExecutionResult.Success(
            toolCall.id,
            toolCall.functionName,
            "brainBytes=${usage.databaseBytes};workspaceBytes=${usage.workspaceBytes};modelBytes=${usage.modelBytes + usage.speechModelBytes};backupConfigured=$configured",
        )
    }
}
