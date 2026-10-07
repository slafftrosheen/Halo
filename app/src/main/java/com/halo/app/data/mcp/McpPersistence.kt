package com.halo.app.data.mcp

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Index
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * MINING-P1-001 MCP capability fabric persistence.
 *
 * Harvested conceptually from Brownie's `McpServerStore` / `SkillStore`; the trust model is Halo's
 * own. A configuration row never contains a credential — only a `credentialRef` pointing into the
 * existing SecretStore.
 */

enum class McpAuthKind {
    NONE,

    /** Static bearer token resolved from SecretStore by [credentialRef]. */
    BEARER,
}

/**
 * Trust is about how much the user vouches for the server's *identity*, never about permitting tool
 * execution. Execution always goes through Halo policy.
 */
enum class McpTrustLevel {
    UNTRUSTED,
    USER_APPROVED,
    LOCAL_TRUSTED,
}

enum class McpConnectionState {
    UNKNOWN,
    DISABLED,
    CONNECTING,
    CONNECTED,
    ERROR,
}

@Entity(tableName = "mcp_servers")
data class McpServerEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val endpoint: String,
    val enabled: Boolean,
    val authKind: String,
    val credentialRef: String?,
    val trust: String,
    val createdAt: Long,
    val updatedAt: Long,
    val protocolVersion: String?,
    val lastConnectedAt: Long?,
    val lastError: String?,
)

@Entity(tableName = "mcp_tools", indices = [Index(value = ["serverId"])])
data class McpToolEntity(
    /** Halo namespace id: `mcp.<serverId>.<remoteToolName>`, sanitized. */
    @PrimaryKey val id: String,
    val serverId: String,
    /** The name the server actually published. Kept separate so it is never lost. */
    val remoteName: String,
    val displayName: String,
    val description: String,
    val inputSchemaJson: String,
    /**
     * Halo-owned risk classification. Defaults to SENSITIVE: a remote server may describe its tool,
     * it may never declare itself safe.
     */
    val risk: String,
    val enabled: Boolean,
    val lastRefreshedAt: Long,
)

@Dao
interface McpDao {
    @Query("SELECT * FROM mcp_servers ORDER BY displayName COLLATE NOCASE")
    fun observeServers(): Flow<List<McpServerEntity>>

    @Query("SELECT * FROM mcp_servers WHERE id = :id LIMIT 1")
    suspend fun getServer(id: String): McpServerEntity?

    @Query("SELECT * FROM mcp_servers ORDER BY displayName COLLATE NOCASE")
    suspend fun allServers(): List<McpServerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertServer(server: McpServerEntity)

    @Query("DELETE FROM mcp_servers WHERE id = :id")
    suspend fun deleteServer(id: String)

    @Query("SELECT * FROM mcp_tools WHERE serverId = :serverId ORDER BY displayName COLLATE NOCASE")
    fun observeTools(serverId: String): Flow<List<McpToolEntity>>

    @Query("SELECT * FROM mcp_tools ORDER BY displayName COLLATE NOCASE")
    fun observeAllTools(): Flow<List<McpToolEntity>>

    @Query("SELECT * FROM mcp_tools WHERE enabled = 1")
    suspend fun enabledTools(): List<McpToolEntity>

    @Query("SELECT * FROM mcp_tools WHERE serverId = :serverId")
    suspend fun toolsForServer(serverId: String): List<McpToolEntity>

    @Query("SELECT * FROM mcp_tools WHERE id = :id LIMIT 1")
    suspend fun tool(id: String): McpToolEntity?

    @Query("SELECT COUNT(*) FROM mcp_tools WHERE enabled = 1")
    suspend fun enabledToolCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTools(tools: List<McpToolEntity>)

    @Query("DELETE FROM mcp_tools WHERE serverId = :serverId")
    suspend fun deleteTools(serverId: String)

    @Transaction
    suspend fun replaceTools(serverId: String, tools: List<McpToolEntity>) {
        deleteTools(serverId)
        upsertTools(tools)
    }

    @Query("UPDATE mcp_tools SET enabled = :enabled WHERE id = :id")
    suspend fun setToolEnabled(id: String, enabled: Boolean)
}
