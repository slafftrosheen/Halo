package com.halo.app.data.notebook

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Entity(tableName = "notebook_collections", indices = [Index(value = ["name"], unique = true)])
data class NotebookCollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "notebook_collection_members",
    primaryKeys = ["collectionId", "objectId"],
    foreignKeys = [
        ForeignKey(
            entity = NotebookCollectionEntity::class,
            parentColumns = ["id"], childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = WorkspaceObjectEntity::class,
            parentColumns = ["id"], childColumns = ["objectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("collectionId"), Index("objectId")],
)
data class NotebookCollectionMemberEntity(
    val collectionId: String,
    val objectId: String,
    val addedAtEpochMs: Long,
)

@Entity(
    tableName = "notebook_favorites",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceObjectEntity::class,
            parentColumns = ["id"], childColumns = ["objectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class NotebookFavoriteEntity(
    @PrimaryKey val objectId: String,
    val addedAtEpochMs: Long,
)

data class NotebookCollection(val id: String, val name: String, val sortOrder: Int)

/** Exact private compare state; never serialized to assistant/provider output. */
data class NotebookOrganizationSnapshot(
    val objectId: String?, val collectionId: String?, val objectRow: WorkspaceObjectEntity?,
    val favorite: NotebookFavoriteEntity?, val collections: List<NotebookCollectionEntity>,
    val members: List<NotebookCollectionMemberEntity>,
)
sealed interface NotebookOrganizationChange {
    data class Favorite(val favorite: Boolean) : NotebookOrganizationChange
    data class Membership(val included: Boolean) : NotebookOrganizationChange
    data class CreateCollection(val name: String) : NotebookOrganizationChange
    data object DeleteCollection : NotebookOrganizationChange
}
data class NotebookOrganizationReceipt(val operation: String, val objectId: String?, val collectionId: String?, val value: Boolean?)

@Dao
interface NotebookOrganizationDao {
    @Query("SELECT * FROM workspace_objects WHERE id = :id")
    suspend fun guardedObject(id: String): WorkspaceObjectEntity?
    @Query("SELECT * FROM notebook_favorites WHERE objectId = :id")
    suspend fun guardedFavorite(id: String): NotebookFavoriteEntity?
    @Query("SELECT * FROM notebook_collections ORDER BY id")
    suspend fun guardedCollections(): List<NotebookCollectionEntity>
    @Query("SELECT * FROM notebook_collection_members WHERE collectionId = :id ORDER BY objectId")
    suspend fun guardedMembers(id: String): List<NotebookCollectionMemberEntity>

    @androidx.room.Transaction
    suspend fun captureGuarded(objectId: String?, collectionId: String?) = NotebookOrganizationSnapshot(
        objectId, collectionId, objectId?.let { guardedObject(it) }, objectId?.let { guardedFavorite(it) },
        guardedCollections(), collectionId?.let { guardedMembers(it) }.orEmpty())

    /** Existing tables/owners only. Exact data comparison and write share one Room transaction. */
    @androidx.room.Transaction
    suspend fun applyGuarded(expected: NotebookOrganizationSnapshot, change: NotebookOrganizationChange,
        timestamp: Long, newId: String, isStillAdmitted: suspend () -> Boolean): NotebookOrganizationReceipt? {
        if (captureGuarded(expected.objectId, expected.collectionId) != expected) return null
        if (!isStillAdmitted()) return null
        return when (change) {
            is NotebookOrganizationChange.Favorite -> {
                val row = expected.objectRow?.takeIf { it.lifecycle != "TRASHED" } ?: return null
                if (change.favorite) addFavorite(NotebookFavoriteEntity(row.id, timestamp)) else removeFavorite(row.id)
                check(isFavorite(row.id) == change.favorite)
                NotebookOrganizationReceipt("favorite", row.id, null, change.favorite)
            }
            is NotebookOrganizationChange.Membership -> {
                val row = expected.objectRow?.takeIf { it.lifecycle != "TRASHED" } ?: return null
                val collection = expected.collections.find { it.id == expected.collectionId } ?: return null
                if (change.included) addMember(NotebookCollectionMemberEntity(collection.id, row.id, timestamp))
                else removeMember(collection.id, row.id)
                check(guardedMembers(collection.id).any { it.objectId == row.id } == change.included)
                NotebookOrganizationReceipt("membership", row.id, collection.id, change.included)
            }
            is NotebookOrganizationChange.CreateCollection -> {
                val name = change.name.trim()
                if (name.isEmpty() || name.length > 60 || expected.collections.any { it.name == name }) return null
                insertCollection(NotebookCollectionEntity(newId, name, nextCollectionPosition(), timestamp, timestamp))
                check(guardedCollections().any { it.id == newId })
                NotebookOrganizationReceipt("create_collection", null, newId, null)
            }
            NotebookOrganizationChange.DeleteCollection -> {
                val id = expected.collectionId ?: return null
                if (expected.collections.none { it.id == id }) return null
                check(deleteCollection(id) == 1)
                check(guardedCollections().none { it.id == id })
                NotebookOrganizationReceipt("delete_collection", null, id, null)
            }
        }
    }

    @Query("SELECT * FROM notebook_collections ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    fun observeCollections(): Flow<List<NotebookCollectionEntity>>

    @Query("SELECT objectId FROM notebook_favorites")
    fun observeFavoriteIds(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM notebook_favorites WHERE objectId = :objectId)")
    suspend fun isFavorite(objectId: String): Boolean

    @Query("SELECT objectId FROM notebook_collection_members WHERE collectionId = :collectionId")
    fun observeMemberIds(collectionId: String): Flow<List<String>>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM notebook_collections")
    suspend fun nextCollectionPosition(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCollection(entity: NotebookCollectionEntity)

    @Query("DELETE FROM notebook_collections WHERE id = :collectionId")
    suspend fun deleteCollection(collectionId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addMember(entity: NotebookCollectionMemberEntity)

    @Query("DELETE FROM notebook_collection_members WHERE collectionId = :collectionId AND objectId = :objectId")
    suspend fun removeMember(collectionId: String, objectId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(entity: NotebookFavoriteEntity)

    @Query("DELETE FROM notebook_favorites WHERE objectId = :objectId")
    suspend fun removeFavorite(objectId: String): Int
}

class NotebookOrganizationRepository(
    private val dao: NotebookOrganizationDao,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val favoriteMutationMutex = Mutex()

    suspend fun captureGuarded(objectId: String?, collectionId: String?) = dao.captureGuarded(objectId, collectionId)
    suspend fun applyGuarded(expected: NotebookOrganizationSnapshot, change: NotebookOrganizationChange,
        isStillAdmitted: suspend () -> Boolean): NotebookOrganizationReceipt? = favoriteMutationMutex.withLock {
        dao.applyGuarded(expected, change, now(), newId(), isStillAdmitted)
    }

    fun observeCollections(): Flow<List<NotebookCollection>> =
        dao.observeCollections().map { rows ->
            rows.map { NotebookCollection(it.id, it.name, it.sortOrder) }
        }

    fun observeFavorites(): Flow<Set<String>> =
        dao.observeFavoriteIds().map { it.toSet() }

    fun observeMembers(collectionId: String): Flow<Set<String>> =
        dao.observeMemberIds(collectionId).map { it.toSet() }

    suspend fun createCollection(name: String): NotebookCollection? {
        val cleanName = name.trim().take(60)
        if (cleanName.isEmpty()) return null
        val timestamp = now()
        val entity = NotebookCollectionEntity(
            id = newId(), name = cleanName, sortOrder = dao.nextCollectionPosition(),
            createdAtEpochMs = timestamp, updatedAtEpochMs = timestamp,
        )
        return runCatching { dao.insertCollection(entity) }
            .map { NotebookCollection(entity.id, entity.name, entity.sortOrder) }
            .getOrNull()
    }

    suspend fun deleteCollection(collectionId: String): Boolean = dao.deleteCollection(collectionId) == 1

    suspend fun setMembership(collectionId: String, objectId: String, included: Boolean): Boolean {
        return if (included) {
            dao.addMember(NotebookCollectionMemberEntity(collectionId, objectId, now()))
            true
        } else {
            dao.removeMember(collectionId, objectId) == 1
        }
    }

    suspend fun setFavorite(objectId: String, favorite: Boolean): Boolean {
        return if (favorite) {
            dao.addFavorite(NotebookFavoriteEntity(objectId, now()))
            true
        } else {
            dao.removeFavorite(objectId) == 1
        }
    }

    /** Serialize read-modify-write so rapid taps cannot apply the same stale UI state twice. */
    suspend fun toggleFavorite(objectId: String): Boolean = favoriteMutationMutex.withLock {
        setFavorite(objectId, !dao.isFavorite(objectId))
    }
}
