package com.halo.drivers.heycyan.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Maintains the local inventory of media files from the HeyCyan device.
 *
 * Tracks which files are known from the device manifest, their sync state,
 * and local file paths. Uses HeyCyanMediaStore for persistence.
 */
class HeyCyanMediaInventory(
    private val store: HeyCyanMediaStore
) {
    private val mutex = Mutex()

    private val _items = MutableStateFlow<List<MediaReference>>(emptyList())
    val items: StateFlow<List<MediaReference>> = _items.asStateFlow()

    /**
     * Loads the persisted inventory from disk.
     */
    suspend fun load() {
        mutex.withLock {
            _items.value = store.load()
        }
    }

    /**
     * Updates the inventory with a new manifest from the device.
     *
     * - New files are added with SyncState.NOT_SYNCED
     * - Existing files retain their current sync state
     * - Files not in the manifest are NOT removed (device may have deleted them)
     *
     * @param manifestFiles Filenames from the device manifest
     * @return List of newly discovered files
     */
    suspend fun updateFromManifest(manifestFiles: List<String>): List<MediaItem> {
        mutex.withLock {
            val existing = _items.value.associateBy { it.item.filename }
            val newItems = mutableListOf<MediaItem>()

            val updated = manifestFiles.map { filename ->
                val existingRef = existing[filename]
                if (existingRef != null) {
                    existingRef
                } else {
                    val item = MediaItem(
                        filename = filename,
                        type = MediaType.fromExtension(filename),
                        syncState = SyncState.NOT_SYNCED
                    )
                    newItems.add(item)
                    MediaReference(item = item)
                }
            }

            // Keep existing items not in manifest (already downloaded)
            val allItems = updated + existing.values.filter { ref ->
                manifestFiles.none { it == ref.item.filename }
            }

            _items.value = allItems
            store.save(allItems)

            return newItems
        }
    }

    /**
     * Updates the sync state of a specific item.
     */
    suspend fun updateSyncState(filename: String, state: SyncState, localPath: String? = null) {
        mutex.withLock {
            val updated = _items.value.map { ref ->
                if (ref.item.filename == filename) {
                    ref.copy(
                        item = ref.item.copy(syncState = state),
                        localPath = localPath ?: ref.localPath,
                        downloadedAt = if (state == SyncState.SYNCED) System.currentTimeMillis() else ref.downloadedAt
                    )
                } else {
                    ref
                }
            }
            _items.value = updated
            store.save(updated)
        }
    }

    /**
     * Returns items that need to be downloaded.
     */
    fun getUnsyncedItems(): List<MediaItem> {
        return _items.value
            .filter { it.item.syncState == SyncState.NOT_SYNCED || it.item.syncState == SyncState.FAILED }
            .map { it.item }
    }

    /**
     * Returns the MediaReference for a specific file.
     */
    fun getReference(filename: String): MediaReference? {
        return _items.value.firstOrNull { it.item.filename == filename }
    }

    /**
     * Checks whether a file already exists in the inventory.
     */
    fun isDuplicate(filename: String): Boolean {
        return _items.value.any { it.item.filename == filename && it.item.syncState == SyncState.SYNCED }
    }

    /**
     * Returns counts by media type.
     */
    fun countByType(): Map<MediaType, Int> {
        return _items.value.groupBy { it.item.type }.mapValues { it.value.size }
    }

    /**
     * Clears the inventory. Used during testing or full reset.
     */
    suspend fun clear() {
        mutex.withLock {
            _items.value = emptyList()
            store.clear()
        }
    }
}
