package com.halo.app.data.knowledge

import androidx.room.*
import com.halo.app.data.everyday.CollectorSourceEntity
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "reference_libraries", foreignKeys = [ForeignKey(entity = CollectorSourceEntity::class,
    parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE)])
data class ReferenceLibraryEntity(@PrimaryKey val sourceId: String, val generation: String?, val title: String,
    val language: String, val fingerprint: String?, val importedAt: Long?, val entryCount: Int, val origin: String = "USER_IMPORT")

@Entity(tableName = "reference_entries", foreignKeys = [ForeignKey(entity = CollectorSourceEntity::class,
    parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["sourceId", "generation", "entryId"], unique = true), Index(value = ["sourceId", "generation", "ordinal"])])
data class ReferenceEntryEntity(@PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rowid") val rowId: Long = 0,
    val sourceId: String, val generation: String, val ordinal: Int, val entryId: String,
    val title: String, val text: String, val language: String, val url: String?, val publisher: String,
    val revision: String?, val license: String, val updatedAt: String?, val format: String, val imagesJson: String = "[]")

@Fts4(contentEntity = ReferenceEntryEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "reference_entries_fts")
data class ReferenceEntryFts(val title: String, val text: String)

/** URI stays private. Tools receive the job ID/token, never a path supplied by a model. */
@Entity(tableName = "reference_import_jobs", indices = [Index(value = ["sourceId"])])
data class ReferenceImportJobEntity(@PrimaryKey val id: String, val sourceId: String, val sourceVersion: Long?,
    val uri: String, val fileName: String, val title: String, val state: String, val reviewToken: String,
    val fingerprint: String, val inputBytes: Long, val processed: Int, val generation: String,
    val previewJson: String, val message: String?, val createdAt: Long, val updatedAt: Long, val origin: String = "USER_IMPORT")

@Dao
interface ReferenceDao {
    @Query("SELECT uri FROM reference_import_jobs") suspend fun inputHandles(): List<String>
    @Query("SELECT * FROM reference_import_jobs WHERE sourceId=:id") suspend fun jobsForSource(id: String): List<ReferenceImportJobEntity>
    @Query("SELECT * FROM material_images WHERE id=:id") suspend fun image(id: String): MaterialImageEntity?
    @Query("SELECT * FROM material_images ORDER BY savedAt, id") suspend fun images(): List<MaterialImageEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putImage(image: MaterialImageEntity)
    @Query("DELETE FROM material_images WHERE id=:id") suspend fun removeImage(id: String)

    @Query("SELECT * FROM reference_libraries ORDER BY title, sourceId") fun observeLibraries(): Flow<List<ReferenceLibraryEntity>>
    @Query("SELECT * FROM reference_import_jobs ORDER BY updatedAt DESC") fun observeJobs(): Flow<List<ReferenceImportJobEntity>>
    @Query("SELECT * FROM reference_libraries ORDER BY sourceId") suspend fun libraries(): List<ReferenceLibraryEntity>
    @Query("SELECT * FROM reference_libraries WHERE sourceId=:id") suspend fun library(id: String): ReferenceLibraryEntity?
    @Query("SELECT * FROM reference_libraries WHERE fingerprint=:fingerprint LIMIT 1") suspend fun byFingerprint(fingerprint: String): ReferenceLibraryEntity?
    @Query("SELECT * FROM reference_import_jobs ORDER BY updatedAt DESC, id LIMIT 20") suspend fun recentJobs(): List<ReferenceImportJobEntity>
    @Query("SELECT * FROM reference_import_jobs WHERE id=:id") suspend fun job(id: String): ReferenceImportJobEntity?
    @Query("SELECT * FROM reference_import_jobs WHERE id=:id") fun jobNow(id: String): ReferenceImportJobEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putLibrary(value: ReferenceLibraryEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putJob(value: ReferenceImportJobEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun putJobNow(value: ReferenceImportJobEntity)
    @Insert fun insertEntries(values: List<ReferenceEntryEntity>)
    @Query("SELECT version FROM collector_sources WHERE id=:id") fun sourceVersionNow(id: String): Long?
    @Query("DELETE FROM reference_entries WHERE sourceId=:id AND generation!=:keep") suspend fun removeOtherGenerations(id: String, keep: String)
    @Query("DELETE FROM reference_entries WHERE sourceId=:id AND generation=:generation") suspend fun removeGeneration(id: String, generation: String)
    @Query("UPDATE reference_import_jobs SET state='INTERRUPTED', message='Import interrupted; review and resume', updatedAt=:now WHERE state IN ('QUEUED','IMPORTING','INDEXING')") suspend fun recover(now: Long)
    @Query("SELECT e.* FROM reference_entries e JOIN reference_entries_fts ON e.rowid=reference_entries_fts.rowid JOIN reference_libraries l ON l.sourceId=e.sourceId AND l.generation=e.generation WHERE reference_entries_fts MATCH :terms AND e.sourceId IN (:sourceIds) ORDER BY CASE WHEN e.title LIKE :titlePrefix THEN 0 ELSE 1 END, e.sourceId, e.ordinal LIMIT :limit OFFSET :offset")
    suspend fun search(terms: String, titlePrefix: String, sourceIds: List<String>, offset: Int, limit: Int): List<ReferenceEntryEntity>
    @Query("SELECT * FROM reference_entries WHERE sourceId=:sourceId AND generation=:generation AND entryId=:entryId")
    suspend fun entry(sourceId: String, generation: String, entryId: String): ReferenceEntryEntity?
    @Query("SELECT COUNT(*) FROM reference_entries WHERE sourceId=:sourceId AND generation=:generation") suspend fun count(sourceId: String, generation: String): Int
}
