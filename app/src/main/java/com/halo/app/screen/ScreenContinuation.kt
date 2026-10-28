package com.halo.app.screen

import com.halo.app.data.notebook.WorkspaceObject
import com.halo.app.data.notebook.WorkspaceObjectLifecycle
import com.halo.app.data.study.StudySessionRecord
import com.halo.app.data.workspace.WorkspaceRepository
import com.halo.app.data.routine.RoomRoutineRepository
import com.halo.app.data.watch.RoomContinuationWatchRepository
import com.halo.app.study.StudyUseCases
import com.halo.sdk.runtime.study.StudySessionLifecycle
import com.halo.runtime.cognition.routine.RoutineId
import com.halo.runtime.cognition.watch.ContinuationWatch

/** Ephemeral review identity, never another copy of a document or a conversation owner. */
internal sealed interface ScreenContinuation {
    data class Workspace(val observed: WorkspaceObject) : ScreenContinuation
    data class Study(val observed: StudySessionRecord) : ScreenContinuation
    data class Routine(val id: String, val revision: String) : ScreenContinuation
    data class FollowUp(val observed: ContinuationWatch) : ScreenContinuation
}

/** Resolve against the existing owner immediately before navigation. Deleted, archived and
 * changed sources require a fresh card review rather than silently opening a different version.
 */
internal suspend fun ScreenContinuation.isCurrent(
    workspace: WorkspaceRepository,
    study: StudyUseCases,
    routines: RoomRoutineRepository,
    followUps: RoomContinuationWatchRepository,
): Boolean = when (this) {
    is ScreenContinuation.Workspace -> observed.lifecycle == WorkspaceObjectLifecycle.ACTIVE &&
        workspace.get(observed.id) == observed
    is ScreenContinuation.Study -> observed.lifecycle == StudySessionLifecycle.ACTIVE &&
        study.getSession(observed.id) == observed
    is ScreenContinuation.Routine -> routines.managementGet(RoutineId(id))?.routine?.updatedAt?.toString() == revision
    is ScreenContinuation.FollowUp -> followUps.findById(observed.id) == observed
}
