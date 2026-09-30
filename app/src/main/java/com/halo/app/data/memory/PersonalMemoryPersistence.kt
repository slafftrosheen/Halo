package com.halo.app.data.memory

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "personal_episodes")
data class EpisodeEntity(@PrimaryKey val id: String, val runId: String, val json: String, val endedAt: Long,
    val importance: Int = 0, val usageCount: Long = 0, val lastReferencedAt: Long? = null)
@Entity(tableName = "reflection_states")
data class ReflectionStateEntity(@PrimaryKey val id: String, val policyVersion: Int, val status: String, val mode: String, val updatedAt: Long)
@Entity(tableName = "personal_suggestions", indices = [Index(value = ["fingerprint"], unique = true)])
data class PersonalSuggestionEntity(@PrimaryKey val id: String, val fingerprint: String, val kind: String, val json: String,
    val state: String, val createdAt: Long, val updatedAt: Long, val evidenceCount: Int, val dismissedAt: Long? = null, val neverAgain: Boolean = false)
@Dao interface PersonalMemoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun episode(value: EpisodeEntity): Long
    @Query("SELECT * FROM personal_episodes ORDER BY endedAt DESC LIMIT 30") fun episodes(): Flow<List<EpisodeEntity>>
    @Query("SELECT * FROM personal_episodes WHERE endedAt >= :since ORDER BY endedAt DESC LIMIT 30") suspend fun recent(since: Long): List<EpisodeEntity>
    @Query("SELECT * FROM personal_episodes WHERE id = :id") suspend fun episodeById(id: String): EpisodeEntity?
    @Query("UPDATE personal_episodes SET usageCount = MIN(usageCount + 1,1000000), lastReferencedAt = :at WHERE id = :id") suspend fun usedEpisode(id: String, at: Long)
    @Query("DELETE FROM personal_episodes WHERE endedAt < :before AND importance = 0 AND usageCount = 0") suspend fun pruneEpisodes(before: Long)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun claim(value: ReflectionStateEntity): Long
    @Query("SELECT * FROM reflection_states WHERE id = :id") suspend fun reflection(id: String): ReflectionStateEntity?
    @Query("UPDATE reflection_states SET status = :status, mode = :mode, updatedAt = :at WHERE id = :id") suspend fun status(id: String,status: String,mode: String,at: Long)
    @Query("SELECT * FROM reflection_states ORDER BY updatedAt DESC LIMIT 20") fun reflections(): Flow<List<ReflectionStateEntity>>
    @Query("SELECT * FROM personal_suggestions WHERE state = 'PENDING' ORDER BY createdAt DESC LIMIT 20") fun pending(): Flow<List<PersonalSuggestionEntity>>
    @Query("SELECT * FROM personal_suggestions WHERE id = :id") suspend fun suggestionById(id: String): PersonalSuggestionEntity?
    @Query("SELECT * FROM personal_suggestions WHERE fingerprint = :fingerprint") suspend fun fingerprint(fingerprint: String): PersonalSuggestionEntity?
    @Query("SELECT COUNT(*) FROM personal_suggestions WHERE state = 'PENDING'") suspend fun pendingCount(): Int
    @Query("SELECT COUNT(*) FROM personal_suggestions WHERE createdAt >= :since") suspend fun newCount(since: Long): Int
    @Upsert suspend fun put(value: PersonalSuggestionEntity)
    @Query("UPDATE personal_suggestions SET state = :state, updatedAt = :at, dismissedAt = :dismissedAt, neverAgain = :neverAgain WHERE id = :id") suspend fun decide(id: String,state: String,at: Long,dismissedAt: Long?,neverAgain: Boolean)
    @Query("SELECT * FROM personal_suggestions WHERE state = 'PENDING' AND createdAt < :before LIMIT 20") suspend fun expiring(before: Long): List<PersonalSuggestionEntity>
    @Query("SELECT * FROM personal_suggestions WHERE state != 'PENDING' ORDER BY updatedAt DESC LIMIT 1000") suspend fun durableDecisions(): List<PersonalSuggestionEntity>
    @Query("SELECT * FROM personal_episodes WHERE importance > 0 ORDER BY endedAt DESC LIMIT 1000") suspend fun savedEpisodes(): List<EpisodeEntity>
    @Query("SELECT COUNT(*) FROM personal_episodes") suspend fun episodeCount(): Int

    @Query("UPDATE personal_episodes SET importance = 1 WHERE id = :id") suspend fun saveEpisode(id: String)
    @Query("DELETE FROM personal_episodes") suspend fun clearEpisodes()
    @Query("DELETE FROM personal_suggestions") suspend fun clearSuggestions()
    @Query("DELETE FROM reflection_states") suspend fun clearReflections()

    @Query("UPDATE reflection_states SET status = 'SKIPPED' WHERE status = 'PENDING'") suspend fun interruptReflections()
    @Query("SELECT * FROM reflection_states ORDER BY updatedAt DESC LIMIT 1000") suspend fun reflectionHistory(): List<ReflectionStateEntity>
}
