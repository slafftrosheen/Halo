package com.halo.drivers.heycyan.media.sync

import com.halo.drivers.heycyan.media.HeyCyanMedia
import com.halo.drivers.heycyan.media.repository.HeyCyanMediaIdentityResolver
import com.halo.drivers.heycyan.media.repository.StoredHeyCyanMedia

/**
 * Result of comparing remote manifest media items against local repository records.
 */
data class HeyCyanMediaDiff(
    val newItems: List<HeyCyanMedia>,
    val alreadyPresent: List<HeyCyanMedia>,
    val changedItems: List<HeyCyanMedia>,
    val localOnly: List<StoredHeyCyanMedia>,
)

/**
 * Differ component that classifies media items into new, existing, changed, and local-only.
 */
class HeyCyanMediaDiffer(
    private val identityResolver: HeyCyanMediaIdentityResolver,
) {
    fun diff(
        remote: List<HeyCyanMedia>,
        local: List<StoredHeyCyanMedia>,
    ): HeyCyanMediaDiff {
        val localById = local.associateBy { it.identity }
        val remoteIds = remote.map { identityResolver.identity(it) }.toSet()

        val newItems = mutableListOf<HeyCyanMedia>()
        val existing = mutableListOf<HeyCyanMedia>()
        val changed = mutableListOf<HeyCyanMedia>()

        for (media in remote) {
            val id = identityResolver.identity(media)
            val localItem = localById[id]

            if (localItem == null) {
                newItems.add(media)
                continue
            }

            if (hasChanged(media, localItem)) {
                changed.add(media)
            } else {
                existing.add(media)
            }
        }

        val localOnly = local.filter { it.identity !in remoteIds }

        return HeyCyanMediaDiff(
            newItems = newItems,
            alreadyPresent = existing,
            changedItems = changed,
            localOnly = localOnly,
        )
    }

    private fun hasChanged(remote: HeyCyanMedia, local: StoredHeyCyanMedia): Boolean {
        val remoteSize = remote.sizeBytes
        return remoteSize != null && remoteSize != local.sizeBytes
    }
}

/**
 * Policy governing media synchronization filtering.
 */
enum class MediaSyncPolicy {
    NEW_ONLY,
    NEW_AND_CHANGED,
    FORCE_REDOWNLOAD,
}

/**
 * Request payload for configuring media synchronization.
 */
data class MediaSyncRequest(
    val policy: MediaSyncPolicy = MediaSyncPolicy.NEW_AND_CHANGED,
    val mediaTypes: Set<com.halo.drivers.heycyan.media.repository.HeyCyanMediaType> =
        com.halo.drivers.heycyan.media.repository.HeyCyanMediaType.entries.toSet(),
)
