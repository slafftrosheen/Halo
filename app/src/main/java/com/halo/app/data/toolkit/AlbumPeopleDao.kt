package com.halo.app.data.toolkit

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** Public, explicitly named Album person. Match references move with this row when it is made private. */
@Entity(tableName = "album_people", indices = [Index("normalizedName", unique = true), Index("referenceId", unique = true)])
data class AlbumPersonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val referenceId: String,
    val referenceVersion: String,
    val sortOrder: Int,
    val matchesJson: String,
)

@Entity(tableName = "album_people_migration")
data class AlbumPeopleMigrationEntity(@PrimaryKey val id: Int, val phase: Int)

@Dao
interface AlbumPeopleDao {
    @Query("SELECT * FROM album_people ORDER BY sortOrder, id")
    suspend fun people(): List<AlbumPersonEntity>

    @Query("SELECT * FROM album_people ORDER BY sortOrder, id")
    fun observePeople(): Flow<List<AlbumPersonEntity>>

    @Query("SELECT * FROM album_people WHERE id = :id")
    suspend fun person(id: String): AlbumPersonEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPeople(rows: List<AlbumPersonEntity>)

    @Query("DELETE FROM album_people WHERE id = :id")
    suspend fun deletePerson(id: String): Int

    @Query("DELETE FROM album_people")
    suspend fun deletePeople()

    @Query("SELECT phase FROM album_people_migration WHERE id = 1")
    suspend fun migrationPhase(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun writeMigrationPhase(row: AlbumPeopleMigrationEntity)
}

internal fun createAlbumPeopleTables(db: SupportSQLiteDatabase) {
    db.execSQL("""CREATE TABLE IF NOT EXISTS album_people (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL,
        normalizedName TEXT NOT NULL, referenceId TEXT NOT NULL, referenceVersion TEXT NOT NULL,
        sortOrder INTEGER NOT NULL, matchesJson TEXT NOT NULL)""")
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_album_people_normalizedName ON album_people(normalizedName)")
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_album_people_referenceId ON album_people(referenceId)")
    db.execSQL("CREATE TABLE IF NOT EXISTS album_people_migration (id INTEGER NOT NULL PRIMARY KEY, phase INTEGER NOT NULL)")
}
