package com.halo.drivers.heycyan.mock

import com.halo.drivers.heycyan.media.HeyCyanMedia
import com.halo.drivers.heycyan.media.MediaDownloadProgress
import com.halo.drivers.heycyan.media.MediaDownloadResult
import com.halo.drivers.heycyan.media.MediaIntegrity
import com.halo.drivers.heycyan.media.MediaLocation
import com.halo.drivers.heycyan.media.repository.FileHeyCyanMediaRepository
import com.halo.drivers.heycyan.media.repository.HeyCyanMediaIdentityResolver
import com.halo.drivers.heycyan.media.repository.HeyCyanMediaType
import com.halo.drivers.heycyan.media.repository.MediaWriteSession
import com.halo.drivers.heycyan.media.repository.StoredHeyCyanMedia
import com.halo.drivers.heycyan.media.repository.toMediaType
import com.halo.drivers.heycyan.media.sync.MediaDownloadStrategy
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory [FileHeyCyanMediaRepository] substitute for unit tests.
 *
 * Stores committed [StoredHeyCyanMedia] records in a mutable in-memory map.
 * Eliminates all filesystem interaction — tests run without temp directories.
 *
 * Usage:
 * ```kotlin
 * val repo = MockMediaRepository()
 * repo.seed(StoredHeyCyanMedia(...)) // pre-populate with existing items
 * ```
 */
class MockMediaRepository(
    private val identityResolver: HeyCyanMediaIdentityResolver = DefaultMockIdentityResolver(),
) : FileHeyCyanMediaRepository(
    // FileHeyCyanMediaRepository requires a real File, but we never use it in tests
    // since we override all operations in the subclass.
    storageDir = createTempDir("heycyan_mock_repo"),
    identityResolver = DefaultMockIdentityResolver(),
) {
    private val mutex = Mutex()
    private val store = mutableMapOf<String, StoredHeyCyanMedia>()

    // Track write operations for assertions
    val committedItems: MutableList<HeyCyanMedia> = mutableListOf()
    val abortedItems: MutableList<HeyCyanMedia> = mutableListOf()

    /** Pre-populate the repository with already-downloaded items. */
    fun seed(vararg items: StoredHeyCyanMedia) {
        items.forEach { store[it.identity] = it }
    }

    fun seed(items: List<StoredHeyCyanMedia>) {
        items.forEach { store[it.identity] = it }
    }

    override suspend fun list(): List<StoredHeyCyanMedia> = mutex.withLock {
        store.values.toList()
    }

    override suspend fun find(identity: String): StoredHeyCyanMedia? = mutex.withLock {
        store[identity]
    }

    override suspend fun beginWrite(media: HeyCyanMedia): MediaWriteSession {
        val identity = identityResolver.identity(media)
        return MockMediaWriteSession(identity = identity, media = media)
    }

    override suspend fun commit(session: MediaWriteSession, sha256: String?): Result<StoredHeyCyanMedia> {
        val mockSession = session as MockMediaWriteSession
        val record = StoredHeyCyanMedia(
            identity = mockSession.identity,
            deviceId = null,
            remoteId = mockSession.media.id,
            filename = mockSession.media.filename,
            mediaType = mockSession.media.toMediaType(),
            localPath = "/mock/${mockSession.media.filename}",
            sizeBytes = mockSession.media.sizeBytes ?: 0L,
            createdAtMillis = mockSession.media.createdAt,
            downloadedAtMillis = System.currentTimeMillis(),
            sha256 = sha256,
        )
        mutex.withLock { store[mockSession.identity] = record }
        committedItems.add(mockSession.media)
        return Result.success(record)
    }

    override suspend fun abort(session: MediaWriteSession) {
        val mockSession = session as? MockMediaWriteSession ?: return
        abortedItems.add(mockSession.media)
    }

    fun reset() {
        store.clear()
        committedItems.clear()
        abortedItems.clear()
    }

    private class MockMediaWriteSession(
        override val identity: String,
        override val media: HeyCyanMedia,
    ) : MediaWriteSession {
        override suspend fun write(bytes: ByteArray, offset: Int, length: Int) = Unit
        override suspend fun flush() = Unit
        override suspend fun close() = Unit
    }
}

/**
 * Identity resolver for mock: uses `media.filename` as the stable identity key.
 */
private class DefaultMockIdentityResolver : HeyCyanMediaIdentityResolver {
    override fun identity(media: HeyCyanMedia): String = media.filename
}

/**
 * In-memory [MediaDownloadStrategy] that records calls without any I/O.
 *
 * By default succeeds. Can be configured to fail on a specific item.
 */
class MockDownloadStrategy(
    var behaviour: Behaviour = Behaviour.Succeed,
) : MediaDownloadStrategy {

    val downloadedItems: MutableList<HeyCyanMedia> = mutableListOf()

    override suspend fun download(
        media: HeyCyanMedia,
        repository: FileHeyCyanMediaRepository,
        progress: (MediaDownloadProgress) -> Unit,
    ): Result<MediaDownloadResult> {
        downloadedItems.add(media)

        return when (behaviour) {
            Behaviour.Succeed -> {
                val bytes = media.sizeBytes ?: 1024L
                progress(MediaDownloadProgress(bytesReceived = bytes, totalBytes = bytes))
                repository.commit(
                    session = repository.beginWrite(media),
                    sha256 = "mock-sha256",
                )
                Result.success(
                    MediaDownloadResult(
                        media = media,
                        bytesWritten = bytes,
                        destination = MediaLocation("/mock/${media.filename}"),
                        integrity = MediaIntegrity(
                            expectedBytes = bytes,
                            actualBytes = bytes,
                            sha256 = "mock-sha256",
                        ),
                    )
                )
            }
            is Behaviour.Fail -> Result.failure((behaviour as Behaviour.Fail).cause)
        }
    }

    sealed class Behaviour {
        data object Succeed : Behaviour()
        data class Fail(val cause: Throwable = RuntimeException("Mock download failed")) : Behaviour()
    }
}
