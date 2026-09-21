package com.halo.app.data.workspace

import com.halo.app.data.notebook.NotebookDao
import com.halo.app.data.notebook.WorkspaceObjectEntity
import com.halo.app.data.notebook.WorkspaceObjectLifecycle
import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.notebook.WorkspaceObjectType
import com.halo.app.data.notebook.WorkspaceObjectSource
import com.halo.app.data.notebook.WorkspaceObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class WorkspaceQuery(
    val search: String = "",
    val types: Set<WorkspaceObjectType> = emptySet(),
    val lifecycle: WorkspaceObjectLifecycle = WorkspaceObjectLifecycle.ACTIVE,
    val inboxOnly: Boolean = false
)

interface WorkspaceRepository {
    fun observe(query: WorkspaceQuery = WorkspaceQuery()): Flow<List<WorkspaceObject>>
    suspend fun get(id: WorkspaceObjectId): WorkspaceObject?
    suspend fun updateTitle(id: WorkspaceObjectId, title: String)
    suspend fun archive(id: WorkspaceObjectId)
    suspend fun trash(id: WorkspaceObjectId)
    suspend fun restore(id: WorkspaceObjectId)
    suspend fun removeFromInbox(id: WorkspaceObjectId)
}

class RoomWorkspaceRepository(
    private val dao: NotebookDao,
    private val now: () -> Long = System::currentTimeMillis
) : WorkspaceRepository {

    override fun observe(query: WorkspaceQuery): Flow<List<WorkspaceObject>> {
        val searchPattern = if (query.search.isBlank()) "" else "%${query.search.trim().replace("%", "\\%")}%"
        return dao.observeWorkspaceObjects(
            searchPattern = searchPattern,
            lifecycle = query.lifecycle.name,
            inboxOnly = query.inboxOnly
        ).map { entities ->
            entities.map { entity ->
                val type = runCatching { WorkspaceObjectType.valueOf(entity.type) }.getOrDefault(WorkspaceObjectType.NOTE)
                val source = runCatching { WorkspaceObjectSource.valueOf(entity.source) }.getOrDefault(WorkspaceObjectSource.USER_CREATED)
                val lifecycle = runCatching { WorkspaceObjectLifecycle.valueOf(entity.lifecycle) }.getOrDefault(WorkspaceObjectLifecycle.ACTIVE)
                WorkspaceObject(
                    id = WorkspaceObjectId(entity.id),
                    type = type,
                    title = entity.title,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                    source = source,
                    lifecycle = lifecycle,
                    inInbox = entity.inInbox,
                    contentRef = entity.contentRef
                )
            }.filter { query.types.isEmpty() || it.type in query.types }
        }
    }

    override suspend fun get(id: WorkspaceObjectId): WorkspaceObject? {
        val entity = dao.getWorkspaceObject(id.value) ?: return null
        val type = runCatching { WorkspaceObjectType.valueOf(entity.type) }.getOrDefault(WorkspaceObjectType.NOTE)
        val source = runCatching { WorkspaceObjectSource.valueOf(entity.source) }.getOrDefault(WorkspaceObjectSource.USER_CREATED)
        val lifecycle = runCatching { WorkspaceObjectLifecycle.valueOf(entity.lifecycle) }.getOrDefault(WorkspaceObjectLifecycle.ACTIVE)
        return WorkspaceObject(
            id = WorkspaceObjectId(entity.id),
            type = type,
            title = entity.title,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            source = source,
            lifecycle = lifecycle,
            inInbox = entity.inInbox,
            contentRef = entity.contentRef
        )
    }

    override suspend fun updateTitle(id: WorkspaceObjectId, title: String) {
        dao.updateTitle(id.value, title.trim(), now())
    }

    override suspend fun archive(id: WorkspaceObjectId) {
        dao.setLifecycle(id.value, WorkspaceObjectLifecycle.ARCHIVED.name, now())
    }

    override suspend fun trash(id: WorkspaceObjectId) {
        dao.setLifecycle(id.value, WorkspaceObjectLifecycle.TRASHED.name, now())
    }

    override suspend fun restore(id: WorkspaceObjectId) {
        dao.setLifecycle(id.value, WorkspaceObjectLifecycle.ACTIVE.name, now())
    }

    override suspend fun removeFromInbox(id: WorkspaceObjectId) {
        dao.setInbox(id.value, false, now())
    }
}
