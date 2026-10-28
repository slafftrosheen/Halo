package com.halo.app.di.tools

import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolDefinition

/** Saved templates are personal Workspace data, with ordinary per-step admission. */
object ProcedureOperability {
    fun forDefinition(definition: ToolDefinition): ToolOperability? {
        if (definition.name !in PersonalConfigurationToolAccess.procedures) return null
        val json = definition.name in setOf("halo_procedures_list", "halo_procedure_set_enabled", "halo_procedure_delete")
        return ToolOperability(capabilityId = CapabilityId("halo.procedures.manage"), owner = "LearnedProcedureStore",
            operation = when (definition.name) {
                "halo_save_procedure" -> ToolOperation.CREATE
                "halo_procedure_set_enabled", "halo_procedure_delete" -> ToolOperation.EDIT
                else -> ToolOperation.QUERY
            }, resultEncoding = if (json) ToolResultEncoding.JSON else ToolResultEncoding.TEXT,
            resultSchema = if (json) mapOf("type" to "object", "properties" to mapOf(
                "procedureId" to mapOf("type" to "string"), "sourceVersion" to mapOf("type" to listOf("string", "null")),
                "workflows" to mapOf("type" to "array"), "changed" to mapOf("type" to "boolean"),
                "executed" to mapOf("type" to "boolean"), "deleted" to mapOf("type" to "boolean")))
                else mapOf("type" to "string", "maxLength" to 4000),
            sourceVersionArguments = if (definition.name in setOf("halo_use_procedure", "halo_procedure_set_enabled", "halo_procedure_delete")) setOf("sourceVersion") else emptySet(),
            limitations = setOf("Saved workflows are reviewed data and never grant authority, unattended execution or rollback.",
                "Each actual step uses current source/schema, policy, permission and approval through the existing CognitiveSession.",
                "Use returns a bounded preview only. Unknown writes require reconciliation, never automatic replay."))
    }
}
