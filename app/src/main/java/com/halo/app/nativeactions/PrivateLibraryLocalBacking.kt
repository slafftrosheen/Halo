package com.halo.app.nativeactions

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.workspace.ContentOwnership
import com.halo.app.data.workspace.WorkspaceAssetEntity
import com.halo.app.data.workspace.WorkspaceFileAvailability
import com.halo.app.data.workspace.WorkspaceFileEntity
import java.io.File
import java.nio.file.Files
import org.json.JSONObject

/** Resolves only the two durable Halo FileProvider roots. A path is never ownership evidence by itself. */
internal class PrivateLibraryLocalBacking(context: Context, private val db: AppDatabase, private val managed: File) {
    private val context = context.applicationContext
    private val authority = "${context.packageName}.captures"
    private val captures = File(context.filesDir, "captures/images")

    data class Owner(val id: String, val assetId: String? = null, val independent: Boolean) {
        fun encode() = JSONObject().put("id", id).put("assetId", assetId).put("independent", independent)
        companion object {
            fun decode(data: JSONObject) = Owner(data.getString("id"),
                data.optString("assetId").takeIf(String::isNotBlank), data.getBoolean("independent"))
        }
    }

    fun isLocalUri(value: String): Boolean = Uri.parse(value).let { it.scheme == "content" && it.authority == authority }

    fun uri(path: File): Uri = FileProvider.getUriForFile(context, authority, path)

    /** Reference comparison never opens a URI or traverses a provider. */
    fun reference(value: String): File? {
        val parsed = Uri.parse(value)
        val path = when (parsed.scheme) {
            "content" -> {
                if (parsed.authority != authority) return null
                val segments = parsed.pathSegments
                val root = when (segments.firstOrNull()) {
                    "images" -> captures
                    "workspace-owned" -> managed
                    else -> return null
                }
                require(segments.size >= 2 && segments.drop(1).all(::safeSegment))
                segments.drop(1).fold(root) { dir, name -> File(dir, name) }
            }
            "file" -> parsed.path?.let(::File) ?: return null
            null -> File(value)
            else -> return null
        }
        if (!path.isAbsolute || path.toPath().any { !safeSegment(it.toString()) }) return null
        val candidate = path.canonicalFile
        val root = listOf(managed.canonicalFile, captures.canonicalFile).firstOrNull {
            candidate != it && candidate.toPath().startsWith(it.toPath())
        } ?: return null
        val relative = root.toPath().relativize(candidate.toPath())
        if (relative.nameCount !in 1..2 || root == captures.canonicalFile && relative.nameCount != 1) return null
        if (Files.isSymbolicLink(path.toPath()) || relative.nameCount == 2 &&
            Files.isSymbolicLink(File(root, relative.getName(0).toString()).toPath())) return null
        return candidate
    }

    fun exactUri(value: String): File {
        require(isLocalUri(value))
        val path = requireNotNull(reference(value))
        require(uri(path).toString() == value)
        return path
    }

    fun ownsFile(row: WorkspaceFileEntity, path: File): Boolean {
        if (row.ownership != ContentOwnership.HALO_OWNED.name || row.availability != WorkspaceFileAvailability.OWNED_LOCAL.name ||
            row.ownedCopyPath == null || !row.objectId.matches(workspaceId)) return false
        val directory = row.objectId.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(100)
        return path.parentFile == File(managed.canonicalFile, directory) &&
            reference(row.ownedCopyPath) == path && isLocalUri(row.uri) && exactUri(row.uri) == path
    }

    suspend fun ownNoteBacking(row: WorkspaceAssetEntity, path: File): Owner? {
        val header = db.notebookDao().getWorkspaceObject(row.objectId)
            ?.takeIf { it.type == "NOTE" && it.contentRef == "note:${it.id}" } ?: return null
        val original = when (row.storageType) {
            "MANAGED_FILE" -> {
                if (path.parentFile != managed.canonicalFile || !managedName.matches(path.name) ||
                    reference(row.pathOrUri) != path) return null
                path.name.contains(row.id) && row.id.matches(uuid)
            }
            "APP_OWNED" -> {
                if (path.parentFile != captures.canonicalFile || !captureName.matches(path.name) ||
                    !isLocalUri(row.pathOrUri) || exactUri(row.pathOrUri) != path) return null
                val receipt = db.workspaceContextDao().provenanceFor(row.objectId)?.let {
                    val meta = JSONObject(it.metadata)
                    meta.optString("captureId") == it.revision &&
                        meta.optString("sha256").matches(sha256) &&
                        meta.optString("mimeType") == row.mimeType && reference(meta.optString("localPath")) == path
                } == true
                header.source == "DEVICE_CAPTURE" && row.type == "IMAGE" && receipt
            }
            else -> return null
        }
        return Owner(row.objectId, row.id, original && header.lifecycle in setOf("ACTIVE", "ARCHIVED"))
    }

    suspend fun owner(path: File): Owner? {
        val files = db.workspaceFileDao().allFiles()
        val owned = files.filter { ownsFile(it, path) }
        require(owned.size <= 1) { "Several canonical Workspace files claim the same backing" }
        if (owned.isNotEmpty()) {
            val row = owned.single()
            val header = requireNotNull(db.notebookDao().getWorkspaceObject(row.objectId))
            require(header.type in setOf("FILE", "MEDIA") && header.contentRef == "file:${row.objectId}")
            return Owner(row.objectId, independent = header.lifecycle in setOf("ACTIVE", "ARCHIVED"))
        }
        val assets = db.workspaceAssetDao().privateAssetBackings().filter { reference(it.pathOrUri) == path }
        val noteOwners = assets.mapNotNull { ownNoteBacking(it, path) }.sortedWith(compareBy<Owner> { it.id }.thenBy { it.assetId })
        val independent = noteOwners.filter(Owner::independent)
        require(independent.map(Owner::id).distinct().size <= 1) { "Several Notes claim the original attachment" }
        return independent.firstOrNull() ?: noteOwners.firstOrNull()
    }

    suspend fun current(owner: Owner): File {
        val header = requireNotNull(db.notebookDao().getWorkspaceObject(owner.id))
        if (owner.assetId == null) {
            val file = requireNotNull(db.workspaceFileDao().getFile(owner.id))
            val path = requireNotNull(file.ownedCopyPath?.let(::reference))
            check(header.type in setOf("FILE", "MEDIA") && header.contentRef == "file:${owner.id}" && ownsFile(file, path))
            return path
        }
        val asset = requireNotNull(db.notebookPurgeDao().asset(owner.assetId))
        val path = requireNotNull(reference(asset.pathOrUri))
        check(asset.objectId == owner.id && ownNoteBacking(asset, path)?.assetId == owner.assetId)
        return path
    }

    private fun safeSegment(segment: String) = segment.isNotEmpty() && segment !in setOf(".", "..") &&
        '/' !in segment && '\\' !in segment && '\u0000' !in segment

    private companion object {
        val workspaceId = Regex("[A-Za-z0-9_:-][A-Za-z0-9_.:-]{0,159}")
        val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        val managedName = Regex("(?:asset_[0-9]+_${uuid.pattern}\\.bin|(?:edited|generated)_${uuid.pattern}\\.[a-zA-Z0-9]{1,12})")
        val captureName = Regex("${uuid.pattern}\\.jpg")
        val sha256 = Regex("[0-9a-f]{64}")
    }
}
