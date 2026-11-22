package com.halo.app.ui.toolkit

import android.app.KeyguardManager
import android.content.Context
import android.net.Uri
import com.halo.app.capture.glassesPhotoOrigins
import com.halo.app.data.notebook.WorkspaceObjectLifecycle
import com.halo.app.data.toolkit.HaloMapCoordinate
import com.halo.app.data.workspace.ContentOwnership
import com.halo.app.data.workspace.WorkspaceFileAvailability
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.sdk.runtime.embodiment.DeviceKind
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class HaloMapPhoto(val id: String, val title: String, val uri: Uri, val coordinate: HaloMapCoordinate)

/** EXIF points live only in the current map session. Original bytes and Workspace IDs remain canonical. */
internal fun observeHaloMapPhotos(context: Context, allow: () -> Boolean): Flow<List<HaloMapPhoto>> =
    combine(
        Graph.appDatabase.workspaceContextDao().observeLibraryCaptureOrigins(),
        HaloAlbumIndex(context).observeLocalPhotoIds(),
        Graph.workspaceFileRepository.observeFiles(),
        Graph.notebookAssetRepository.observeImageAttachments(),
        Graph.workspaceRepository.observe(),
    ) { origins, album, files, attachments, workspace ->
        withContext(Dispatchers.IO) {
            val family = Graph.kidsPreferences
            val approved = family.adultActionRevision()
            val privacyVersion = Graph.privacyModes.revision
            val pause = Graph.haloPause
            val pauseVersion = pause.state.value.version
            val lock = context.getSystemService(KeyguardManager::class.java)
            fun authorized() = allow() && lock?.isDeviceLocked == false && !pause.isPaused() &&
                pause.state.value.version == pauseVersion && Graph.privacyModes.revision == privacyVersion &&
                runCatching { family.requireAdultAction(approved) }.isSuccess
            if (!authorized()) return@withContext emptyList()

            val active = workspace.filter { it.lifecycle == WorkspaceObjectLifecycle.ACTIVE }.associateBy { it.id.value }
            val devices = if (origins.any { it.sourceId != "phone" }) Graph.deviceRegistry.getSavedDevices().asSequence()
                .filter { it.descriptor.kind.name.endsWith("_GLASSES") || it.descriptor.kind == DeviceKind.BRILLIANT_FRAME }
                .map { it.identity.deviceId }.toSet() else emptySet()
            val glasses = glassesPhotoOrigins(origins, devices)
            val glassesIds = glasses.mapTo(hashSetOf()) { it.objectId }
            val fileImages = files.asSequence().filter { file ->
                val id = file.objectId.value
                id in active && (id in album || id in glassesIds) && file.ownership == ContentOwnership.HALO_OWNED &&
                    file.availability == WorkspaceFileAvailability.OWNED_LOCAL && file.mimeType.startsWith("image/")
            }.associate { it.objectId.value to it.uri }
            val originals = glasses.asSequence().filter { it.objectId in active }.mapNotNull { capture ->
                val path = runCatching { JSONObject(capture.metadata).getString("localPath") }.getOrNull()
                    ?: return@mapNotNull null
                val ownedUri = runCatching { captureFileUri(context, File(path)) }.getOrNull()
                    ?: return@mapNotNull null
                val attachment = attachments.firstOrNull { it.noteId == capture.objectId && it.storageType == "APP_OWNED" &&
                    it.pathOrUri == ownedUri.toString() } ?: return@mapNotNull null
                capture.objectId to Uri.parse(attachment.pathOrUri)
            }.toMap()
            val points = mutableListOf<HaloMapPhoto>()
            for ((id, uri) in (fileImages + originals).entries.sortedByDescending { active[it.key]?.updatedAt }.take(250)) {
                currentCoroutineContext().ensureActive()
                if (!authorized()) return@withContext emptyList()
                if (uri.scheme != "content" || uri.authority != context.packageName + ".captures") continue
                val point = readAlbumExif(context, uri)?.latLong?.let {
                    runCatching { HaloMapCoordinate(it[0], it[1]) }.getOrNull()
                } ?: continue
                points += HaloMapPhoto(id, requireNotNull(active[id]).title, uri, point)
            }
            if (authorized()) points else emptyList()
        }
    }
