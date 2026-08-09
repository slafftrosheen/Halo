package com.halo.drivers.heycyan.media

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File

/**
 * JSON-backed persistence for media inventory metadata and sync state.
 *
 * Stores the inventory as a JSON file in the app's private storage.
 * Each entry contains the filename, type, sync state, local path, and timestamps.
 */
class HeyCyanMediaStore(
    private val storageDir: File
) {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val listType = Types.newParameterizedType(
        List::class.java,
        MediaReferenceJson::class.java
    )
    private val adapter: JsonAdapter<List<MediaReferenceJson>> = moshi.adapter(listType)

    private val inventoryFile: File
        get() = File(storageDir, INVENTORY_FILENAME)

    init {
        storageDir.mkdirs()
    }

    /**
     * Loads the inventory from disk.
     */
    fun load(): List<MediaReference> {
        val file = inventoryFile
        if (!file.exists()) return emptyList()

        return try {
            val json = file.readText()
            val entries = adapter.fromJson(json) ?: emptyList()
            entries.map { it.toMediaReference() }
        } catch (e: Exception) {
            // Corrupt file — return empty and let next save overwrite
            emptyList()
        }
    }

    /**
     * Saves the entire inventory to disk.
     */
    fun save(items: List<MediaReference>) {
        val entries = items.map { MediaReferenceJson.from(it) }
        val json = adapter.toJson(entries)
        inventoryFile.writeText(json)
    }

    /**
     * Clears the persisted inventory.
     */
    fun clear() {
        inventoryFile.delete()
    }

    /**
     * JSON-serializable representation of a MediaReference.
     */
    data class MediaReferenceJson(
        val filename: String,
        val type: Int,
        val sizeBytes: Long?,
        val createdAt: Long?,
        val syncState: String,
        val localPath: String?,
        val thumbnailPath: String?,
        val downloadedAt: Long?
    ) {
        fun toMediaReference(): MediaReference {
            val mediaType = MediaType.fromValue(type)
            val state = try {
                SyncState.valueOf(syncState)
            } catch (e: IllegalArgumentException) {
                SyncState.NOT_SYNCED
            }

            return MediaReference(
                item = MediaItem(
                    filename = filename,
                    type = mediaType,
                    sizeBytes = sizeBytes,
                    createdAt = createdAt,
                    syncState = state
                ),
                localPath = localPath,
                thumbnailPath = thumbnailPath,
                downloadedAt = downloadedAt
            )
        }

        companion object {
            fun from(ref: MediaReference): MediaReferenceJson {
                return MediaReferenceJson(
                    filename = ref.item.filename,
                    type = ref.item.type.value,
                    sizeBytes = ref.item.sizeBytes,
                    createdAt = ref.item.createdAt,
                    syncState = ref.item.syncState.name,
                    localPath = ref.localPath,
                    thumbnailPath = ref.thumbnailPath,
                    downloadedAt = ref.downloadedAt
                )
            }
        }
    }

    companion object {
        private const val INVENTORY_FILENAME = "heycyan_media_inventory.json"
    }
}
