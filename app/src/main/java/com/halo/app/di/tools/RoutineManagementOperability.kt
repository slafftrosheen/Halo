package com.halo.app.di.tools

import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolDefinition

object RoutineManagementOperability {
    private val names = setOf("halo_routines_list", "halo_routine_get", "halo_routine_run_status",
        "halo_routine_set_enabled", "halo_routine_save_existing", "halo_routine_delete", "halo_routine_run_now")
    private val queries = setOf("halo_routines_list", "halo_routine_get", "halo_routine_run_status")
    private fun text(max: Int) = mapOf("type" to "string", "maxLength" to max)
    private fun nullable(value: Map<String, Any>) = mapOf("anyOf" to listOf(value, mapOf("type" to "null")))
    private val boolean = mapOf("type" to "boolean")
    private fun obj(properties: Map<String, Any>, required: Set<String> = properties.keys) = mapOf("type" to "object",
        "properties" to properties, "required" to required.toList(), "additionalProperties" to false)
    private val summary = mapOf("routineId" to text(80), "name" to text(120), "sourceVersion" to text(80),
        "editable" to boolean, "enabled" to nullable(boolean))
    fun forDefinition(definition: ToolDefinition): ToolOperability? {
        if (definition.name !in names) return null
        val schema = when (definition.name) {
            "halo_routines_list" -> obj(mapOf("routines" to mapOf("type" to "array", "maxItems" to 32, "items" to obj(summary)),
                "nextOffset" to nullable(mapOf("type" to "integer", "minimum" to 0, "maximum" to 10000))))
            "halo_routine_get" -> obj(summary + mapOf("sections" to obj(listOf("trigger", "conditions", "actions", "delivery").associateWith {
                mapOf("type" to "integer", "minimum" to 0, "maximum" to 90000) }),
                "section" to text(20), "offset" to mapOf("type" to "integer", "minimum" to 0, "maximum" to 90000),
                "text" to text(320), "totalChars" to mapOf("type" to "integer", "minimum" to 0, "maximum" to 90000),
                "nextOffset" to nullable(mapOf("type" to "integer", "minimum" to 0, "maximum" to 90000)),
                "minimumIntervalSeconds" to mapOf("type" to "integer", "minimum" to 0, "maximum" to 86400),
                "updatedAt" to text(64), "grantMatches" to boolean), summary.keys)
            "halo_routine_run_status" -> obj(mapOf("runId" to text(400), "routineId" to text(80), "recordedState" to text(40),
                "actionsCompleted" to mapOf("type" to "integer", "minimum" to 0, "maximum" to 10),
                "actionsTotal" to mapOf("type" to "integer", "minimum" to 0, "maximum" to 10), "finishedAt" to nullable(text(64))))
            else -> obj(mapOf("routineId" to text(80), "sourceVersion" to nullable(text(80)), "saved" to boolean,
                "scheduling" to text(40), "runId" to nullable(text(400)), "runRecorded" to boolean,
                "completionConfirmed" to boolean, "deliveryConfirmed" to boolean))
        }
        val fields = definition.parameters["properties"] as? Map<*, *> ?: emptyMap<Any, Any>()
        return ToolOperability(capabilityId = CapabilityId("halo.routines.manage"), owner = "RoutineManagementActions",
            operation = if (definition.name in queries) ToolOperation.QUERY else ToolOperation.EDIT,
            resultEncoding = ToolResultEncoding.JSON, resultSchema = schema,
            sourceVersionArguments = if ("sourceVersion" in fields) setOf("sourceVersion") else emptySet(),
            resultIdFields = when (definition.name) {
                "halo_routines_list" -> emptySet()
                "halo_routine_run_status" -> setOf("routineId", "runId")
                else -> setOf("routineId")
            }, limitations = setOf(
                "Current foreground grown-up guard and registry durable ALWAYS approval remain mandatory for mutations. Direct-user shortcut authority is not substituted for approval.",
                "Typed and STT-final share the same canonical owner; no new automation executor or constituent action approval exists here.",
                "Exact saved row/source tokens expire after edits, owner restart or a claimed manual run. NEVER_REPLAY mutations require fresh readback after uncertainty.",
                "Saved/scheduled/dispatched is not completed or delivered. Read the exact existing run receipt; unavailable/malformed sources cannot execute.",
                "OS trigger permissions, privacy/content policy, tool automation eligibility, resources, sensitive/external/paid actions and current grants are rechecked by RoutineExecutor."))
    }
}
