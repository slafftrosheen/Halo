package com.halo.app.ui.screens.notebook

import android.app.KeyguardManager
import android.net.Uri
import com.halo.app.data.notebook.WorkspaceObjectLifecycle
import com.halo.app.data.notebook.WorkspaceObjectType
import com.halo.app.data.workspace.WorkspaceActionIds
import com.halo.app.data.workspace.WorkspaceDestination
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.organizer.PlannerCalendarPreferences
import com.halo.app.ui.everyday.EverydayRoutes
import kotlinx.coroutines.CancellationException

/** Resolves an exact, public Library ID locally. A link supplies no read or write authority. */
internal suspend fun resolveHaloLibraryItemRoute(id: String): String? {
    if (!WorkspaceActionIds.isValid(id) || id.startsWith("learning:")) return null
    val keyguard = Graph.applicationContext.getSystemService(KeyguardManager::class.java) ?: return null
    if (keyguard.isDeviceLocked) return null
    return try {
        val adult = Graph.kidsPreferences.adultActionRevision()
        val objectRef = Graph.workspaceUseCases.getObject(id)?.takeIf {
            it.lifecycle == WorkspaceObjectLifecycle.ACTIVE &&
                it.type !in setOf(WorkspaceObjectType.MEMORY, WorkspaceObjectType.CONVERSATION)
        }
        val study = Graph.appDatabase.studyDao()
        val card = study.getPracticeItem(id)?.takeIf {
            it.accepted && study.getSession(it.sessionId)?.let { session ->
                session.learnerLevel == "ADULT" && session.lifecycle in setOf("ACTIVE", "COMPLETED")
            } == true
        }
        val visibility = PlannerCalendarPreferences.read(Graph.storage)
        val plan = Graph.organizerRepository.get(id)?.takeIf { it.deletedAt == null && visibility.appearance(it.calendar).visible }
        val encoded = Uri.encode(id)
        val route = when {
            listOfNotNull(objectRef, card, plan).size != 1 -> null
            objectRef != null -> {
                val provenance = Graph.appDatabase.workspaceContextDao().provenanceFor(id)
                if (objectRef.type == WorkspaceObjectType.NOTE && provenance?.sourceId == LibraryPlaceOrigin) {
                    "maps?placeId=$encoded"
                } else {
                    val current = Graph.workspaceContinuity.describe(id)
                    Graph.workspaceContinuity.route(id, current.version, WorkspaceDestination.OPEN)
                }
            }
            card != null -> "notebook/study/${Uri.encode(card.sessionId)}?review=false&cardId=$encoded"
            else -> EverydayRoutes.organizer(id)
        }
        Graph.kidsPreferences.requireAdultAction(adult)
        route.takeUnless { keyguard.isDeviceLocked || plan != null && PlannerCalendarPreferences.read(Graph.storage) != visibility }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}
