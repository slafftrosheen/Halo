package com.halo.app.ui.everyday

import android.net.Uri
import com.halo.app.data.notebook.*

/** Only references an instrument's canonical object; availability is checked again before navigation. */
internal fun organizerWorkspaceRoute(item: WorkspaceObject): String? {
    if (item.lifecycle != WorkspaceObjectLifecycle.ACTIVE) return null
    val id = Uri.encode(item.id.value)
    return when (item.type) {
        WorkspaceObjectType.NOTE -> if (item.contentRef.startsWith("file-tools:")) "file_tools?objectId=$id" else "notebook/note/$id"
        WorkspaceObjectType.CANVAS -> "notebook/canvas/$id"
        WorkspaceObjectType.MEMORY -> "notebook/memory/$id"
        WorkspaceObjectType.CONVERSATION -> "notebook/conversation/$id"
        WorkspaceObjectType.ARTIFACT -> when {
            item.contentRef.startsWith("studio:") -> "studio?projectId=$id"
            item.contentRef.startsWith("canvas:") -> "notebook/canvas/$id"
            else -> "notebook/file/$id"
        }
        else -> "notebook/file/$id"
    }
}
