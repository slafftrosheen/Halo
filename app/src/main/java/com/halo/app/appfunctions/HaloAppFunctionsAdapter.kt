package com.halo.app.appfunctions

import android.content.Context
import android.os.Build
import com.halo.app.data.memory.MemoryCandidate
import com.halo.app.data.memory.MemoryRepository
import com.halo.app.data.notebook.NotebookRepository
import com.halo.app.data.WorkspaceContextStore

/**
 * Optional adapter exposing safe Halo workflows to system assistants on Android 16+ (API 36+).
 * Halo's internal Skill/Tool model works portably on all Android versions.
 */
class HaloAppFunctionsAdapter(
    private val context: Context,
    private val notebookRepository: NotebookRepository,
    private val memoryRepository: MemoryRepository,
    private val workspaceContextStore: WorkspaceContextStore
) {
    fun isAppFunctionsSupported(): Boolean {
        return Build.VERSION.SDK_INT >= 36
    }

    suspend fun executeAppFunction(functionName: String, parameters: Map<String, Any?>): String {
        if (!isAppFunctionsSupported()) {
            return "AppFunctions requires Android 16+"
        }

        return when (functionName) {
            "CreateNotebookNote" -> {
                val body = parameters["body"]?.toString()?.trim().orEmpty()
                if (body.isBlank()) return "Missing note body"
                val note = notebookRepository.createNote(body)
                "Created note: ${note.id} '${note.title}'"
            }
            "AddToWorkingSet" -> {
                val id = parameters["objectId"]?.toString()?.trim().orEmpty()
                if (id.isBlank()) return "Missing objectId"
                workspaceContextStore.select(id)
                "Added $id to Working Set"
            }
            "ProposeHaloMemory" -> {
                val content = parameters["content"]?.toString()?.trim().orEmpty()
                if (content.isBlank()) return "Missing memory content"
                memoryRepository.promoteCandidate(MemoryCandidate(content))
                "Remembered fact: '$content'"
            }
            else -> "Unknown AppFunction: $functionName"
        }
    }
}
