package com.halo.app.ui.toolkit

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.SystemClock
import android.os.storage.StorageManager
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.OsConstants
import androidx.room.withTransaction
import com.halo.app.di.ManualDependencyGraph as Graph
import java.io.File
import java.io.FileNotFoundException
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking

/** A grant opens a seekable video only while its exact Album sources still belong to the public Library. */
internal object AlbumStoryShares {
    internal data class Grant(val video: AlbumStoryVideo, val endsAt: Long, val fileName: String, val length: Long)
    private val lock = Any()
    private val grants = linkedMapOf<String, Grant>()
    private val releaseAfterShare = mutableSetOf<File>()
    internal val handler by lazy { Handler(HandlerThread("HaloAlbumStoryShare").apply { start() }.looper) }
    private const val lifeMs = 15 * 60_000L

    fun grant(context: Context, video: AlbumStoryVideo): Uri = synchronized(lock) {
        check(grants.size < 12 && video.file.isFile)
        val id = UUID.randomUUID().toString()
        val fileName = video.draft.title.replace(Regex("[\\p{Cntrl}/\\\\]+"), " ").trim().ifBlank { "Album story" } + ".mp4"
        val uri = Uri.Builder().scheme("content").authority(context.packageName + ".album-story-shares")
            .appendPath(id).build()
        grants[id] = Grant(video, SystemClock.elapsedRealtime() + lifeMs, fileName, video.file.length())
        handler.postDelayed({ revoke(context.applicationContext, uri) }, lifeMs)
        uri
    }

    fun revoke(context: Context, uri: Uri) {
        val token = token(context, uri) ?: return
        synchronized(lock) {
            val file = grants.remove(token)?.video?.file
            if (file != null && grants.values.none { it.video.file == file } && releaseAfterShare.remove(file)) file.delete()
        }
        context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** The UI discards an unshared preview or releases it for the duration of an existing chooser. */
    fun release(file: File) = synchronized(lock) {
        if (grants.values.any { it.video.file == file }) releaseAfterShare.add(file) else file.delete()
    }

    fun remove(file: File) = synchronized(lock) {
        grants.entries.removeAll { it.value.video.file == file }
        releaseAfterShare.remove(file)
        file.delete()
    }

    /** A restored source never reopens a share grant that existed before the lock. */
    fun revokeForSources(context: Context, sources: Set<String>) {
        val revoked = synchronized(lock) {
            val matches = grants.filterValues { entry -> entry.video.draft.photos.any { it.id in sources } }
            matches.keys.forEach(grants::remove)
            matches.values.map { it.video.file }.distinct().forEach { file ->
                releaseAfterShare.remove(file)
                file.delete()
            }
            matches.keys.toList()
        }
        revoked.forEach { id ->
            context.revokeUriPermission(shareUri(context, id), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun lookup(context: Context, uri: Uri): Grant? = synchronized(lock) {
        token(context, uri)?.let(grants::get)?.takeIf { it.endsAt > SystemClock.elapsedRealtime() }
    }

    /** Older versions staged derivative MP4s beneath the general capture FileProvider. */
    fun purgeLegacyStage(context: Context) {
        val stage = File(context.filesDir, "workspace/owned/album-story-staging")
        stage.listFiles().orEmpty().filter { it.isFile && it.name.matches(Regex("story-[a-f0-9-]{36}\\.mp4")) }
            .forEach(File::delete)
    }

    private fun token(context: Context, uri: Uri): String? =
        uri.pathSegments.singleOrNull()?.takeIf {
            uri.scheme == "content" && uri.authority == context.packageName + ".album-story-shares" &&
                uri.query == null && uri.fragment == null && it.matches(Regex("[a-f0-9-]{36}"))
        }

    private fun shareUri(context: Context, id: String): Uri =
        Uri.Builder().scheme("content").authority(context.packageName + ".album-story-shares").appendPath(id).build()
}

/** The ordinary capture FileProvider cannot access these cache paths. */
class AlbumStoryShareProvider : ContentProvider() {
    override fun onCreate(): Boolean = true
    override fun getType(uri: Uri): String? = runCatching { access(uri) { "video/mp4" } }.getOrNull()

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?,
        sortOrder: String?): Cursor? = runCatching {
        access(uri) { grant ->
            val columns = projection?.map { it }?.toTypedArray() ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
            MatrixCursor(columns, 1).apply {
                addRow(columns.map { column -> when (column) {
                    OpenableColumns.DISPLAY_NAME -> grant.fileName
                    OpenableColumns.SIZE -> grant.length
                    else -> null
                } })
            }
        }
    }.getOrNull()

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r" || !readers.tryAcquire()) throw FileNotFoundException("Album story unavailable")
        var reader: StoryReader? = null
        try {
            val host = requireNotNull(context)
            val storage = requireNotNull(host.getSystemService(StorageManager::class.java))
            val handler = AlbumStoryShares.handler
            reader = access(uri) { grant -> StoryReader(uri, RandomAccessFile(grant.video.file, "r")) }
            return storage.openProxyFileDescriptor(ParcelFileDescriptor.MODE_READ_ONLY, reader, handler)
        } catch (_: Exception) {
            if (reader == null) readers.release() else reader.onRelease()
            throw FileNotFoundException("Album story unavailable")
        }
    }

    private inner class StoryReader(private val uri: Uri, private val file: RandomAccessFile) : ProxyFileDescriptorCallback() {
        private val released = AtomicBoolean()

        override fun onGetSize(): Long = checked { it.length }

        override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
            if (offset < 0 || size < 0) throw ErrnoException("albumStory", OsConstants.EINVAL)
            return checked { grant ->
                if (offset >= grant.length) 0 else {
                    file.seek(offset)
                    file.read(data, 0, minOf(size, data.size)).coerceAtLeast(0)
                }
            }
        }

        private fun <T> checked(use: (AlbumStoryShares.Grant) -> T): T = try {
            check(!released.get())
            access(uri, use)
        } catch (_: Exception) { throw ErrnoException("albumStory", OsConstants.ENOENT) }

        override fun onRelease() {
            if (released.compareAndSet(false, true)) {
                try { file.close() } finally { readers.release() }
            }
        }
    }

    private fun <T> access(uri: Uri, use: (AlbumStoryShares.Grant) -> T): T {
        val host = requireNotNull(context)
        val grant = requireNotNull(AlbumStoryShares.lookup(host, uri))
        return runBlocking {
            Graph.appDatabase.withTransaction {
                grant.video.draft.requireCurrentSources()
                check(AlbumStoryShares.lookup(host, uri) == grant && grant.video.file.length() == grant.length)
                use(grant)
            }
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private companion object { val readers = Semaphore(4) }
}
