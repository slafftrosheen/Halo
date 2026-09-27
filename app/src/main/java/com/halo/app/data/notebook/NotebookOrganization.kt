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

@Dao
interface NotebookOrganizationDao {
    @Query("SELECT * FROM notebook_collections ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    fun observeCollections(): Flow<List<NotebookCollectionEntity>>

    @Query("SELECT objectId FROM notebook_favorites")
    fun observeFavoriteIds(): Flow<List<String>>

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
}
