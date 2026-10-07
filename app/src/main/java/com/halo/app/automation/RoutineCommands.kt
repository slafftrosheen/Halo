package com.halo.app.automation

import com.halo.runtime.cognition.routine.*
import java.time.Instant

/** User commands use saved definitions and the existing occurrence executor exclusively. */
class RoutineCommands(
    private val repository: RoutineRepository,
    private val scheduler: RoutineScheduler,
    private val executeSaved: suspend (RoutineId, Instant, Boolean) -> String?,
) {
    suspend fun delete(id: RoutineId) {
        repository.delete(id)
        scheduler.cancel(id)
    }

    suspend fun runNow(id: RoutineId): String? {
        val saved = requireNotNull(repository.get(id)) { "Save and review this automation first" }
        require(saved.grantDigest != null && saved.grantDigest == RoutineArguments.grant(saved)) {
            "Review this automation before running it"
        }
        return executeSaved(saved.id, Instant.now(), true)
    }
}

/** Same explicit opt-in contract used by the runtime executor and the editor catalogue. */
fun routineActionDescriptors(registry: com.halo.runtime.cognition.tools.ToolRegistry) = registry.descriptors().filter {
    it.automation.permission == AutomationPermission.ALLOWED_WITH_ROUTINE_GRANT &&
        it.automation.argumentsPersistable && it.automation.backgroundCompatible
}
