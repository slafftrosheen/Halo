package com.halo.app.data.memory

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.halo.runtime.cognition.memory.MemorySensitivity
import com.halo.runtime.cognition.memory.MemorySensitivityClassifier
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "memory_page_links",
    primaryKeys = ["sourceId", "targetId"],
    foreignKeys = [
        ForeignKey(entity = MemoryRecordEntity::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MemoryRecordEntity::class, parentColumns = ["id"], childColumns = ["targetId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("targetId")],
)
data class MemoryPageLinkEntity(val sourceId: String, val targetId: String)

@Entity(
    tableName = "memory_page_tags",
    primaryKeys = ["memoryId", "key"],
    foreignKeys = [
        ForeignKey(entity = MemoryRecordEntity::class, parentColumns = ["id"], childColumns = ["memoryId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("key")],
)
data class MemoryPageTagEntity(val memoryId: String, val key: String, val label: String)

@Dao
interface MemoryPagesDao {
    @Query("SELECT * FROM memory_page_links ORDER BY sourceId, targetId")
    fun observeLinks(): Flow<List<MemoryPageLinkEntity>>

    @Query("SELECT * FROM memory_page_tags ORDER BY memoryId, `key`")
    fun observeTags(): Flow<List<MemoryPageTagEntity>>

    @Query("SELECT * FROM memory_page_links WHERE sourceId = :id OR targetId = :id")
    suspend fun linksFor(id: String): List<MemoryPageLinkEntity>

    @Query("SELECT * FROM memory_page_tags WHERE memoryId = :id ORDER BY `key`")
    suspend fun tagsFor(id: String): List<MemoryPageTagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLink(link: MemoryPageLinkEntity): Long

    @Upsert
    suspend fun putTag(tag: MemoryPageTagEntity)

    @Query("DELETE FROM memory_page_links WHERE sourceId = :sourceId AND targetId = :targetId")
    suspend fun deleteLink(sourceId: String, targetId: String)

    @Query("DELETE FROM memory_page_links WHERE sourceId = :id OR targetId = :id")
    suspend fun deleteLinksFor(id: String)

    @Query("DELETE FROM memory_page_tags WHERE memoryId = :id AND `key` = :key")
    suspend fun deleteTag(id: String, key: String)

    @Query("DELETE FROM memory_page_tags WHERE memoryId = :id")
    suspend fun deleteTagsFor(id: String)
}

data class MemoryPageMetadata(
    val links: List<MemoryPageLinkEntity> = emptyList(),
    val tags: List<MemoryPageTagEntity> = emptyList(),
) {
    fun tagsOf(id: String) = tags.filter { it.memoryId == id }
}

data class MemoryPageNeighborhood(
    val distance: Map<String, Int>,
    val links: List<MemoryPageLinkEntity>,
    val truncated: Boolean,
)

internal fun MemoryRecord.isMemoryPage() =
    status == MemoryStatus.ACTIVE && scope != MemoryScope.CONVERSATION &&
        MemorySensitivityClassifier.classify(content) != MemorySensitivity.SECRET

internal fun memoryPageTag(text: String): Pair<String, String> {
    val label = Normalizer.normalize(text.trim().removePrefix("#").trim(), Normalizer.Form.NFKC)
        .replace(Regex(" +"), " ")
    require(label.length in 1..32 && label.any { it.isLetterOrDigit() })
    require(label.all { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' })
    return label.lowercase(Locale.ROOT) to label
}

internal fun memoryPageNeighborhood(
    center: String,
    links: List<MemoryPageLinkEntity>,
    allowedIds: Set<String>,
    depth: Int,
    limit: Int = 36,
): MemoryPageNeighborhood {
    require(depth in 1..3 && limit in 1..100)
    if (center !in allowedIds) return MemoryPageNeighborhood(emptyMap(), emptyList(), false)
    val visible = links.filter { it.sourceId in allowedIds && it.targetId in allowedIds }
    val incoming = visible.groupBy { it.targetId }
    val outgoing = visible.groupBy { it.sourceId }
    val distance = linkedMapOf(center to 0)
    val queue = ArrayDeque<String>().apply { add(center) }
    var truncated = false
    while (queue.isNotEmpty()) {
        val page = queue.removeFirst()
        val level = distance.getValue(page)
        if (level == depth) continue
        val neighbors = outgoing[page].orEmpty().map { it.targetId } + incoming[page].orEmpty().map { it.sourceId }
        for (neighbor in neighbors.distinct().sorted()) {
            if (neighbor in distance) continue
            if (distance.size == limit) {
                truncated = true
                continue
            }
            distance[neighbor] = level + 1
            queue.add(neighbor)
        }
    }
    return MemoryPageNeighborhood(distance, visible.filter { it.sourceId in distance && it.targetId in distance }, truncated)
}
