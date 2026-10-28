package com.halo.app.data.procedure

import com.halo.runtime.cognition.procedures.ProcedureInput
import com.halo.runtime.cognition.procedures.ProcedureStep
import com.halo.runtime.cognition.tools.ToolDescriptor
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Bounded data templates over existing registry contracts. This owner never executes a step. */
object ProcedureContracts {
    const val MAX_INPUTS = 8
    const val MAX_ARGUMENT_CHARS = 6_000
    private val inputName = Regex("[a-z][a-zA-Z0-9_]{0,39}")

    fun inputs(raw: Any?): List<ProcedureInput> {
        if (raw == null) return emptyList()
        val array = array(raw)
        require(array.length() <= MAX_INPUTS) { "Too many named inputs" }
        val result = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            require(item.keys().asSequence().all { it in setOf("name", "description", "valueType") }) { "Invalid input fields" }
            val name = requireNotNull(item.get("name") as? String) { "Input name must be text" }
            val description = requireNotNull(item.get("description") as? String) { "Input description must be text" }
            require(inputName.matches(name) && description.length in 1..160) { "Invalid named input" }
            val type = if (item.has("valueType")) item.get("valueType") as? String else "string"
            require(type in setOf("string", "integer", "boolean")) { "Unsupported input type" }
            ProcedureInput(name, description, requireNotNull(type))
        }
        require(result.map { it.name }.distinct().size == result.size) { "Duplicate named input" }
        return result
    }

    fun step(item: JSONObject, descriptor: ToolDescriptor?): ProcedureStep {
        require(item.keys().asSequence().all { it in setOf("capabilityId", "intent", "arguments", "inputBindings") })
        val capability = requireNotNull(item.get("capabilityId") as? String) { "Missing capability" }
        require(capability.length in 1..120)
        val intent = if (item.has("intent")) requireNotNull(item.get("intent") as? String) { "Intent must be text" } else ""
        require(intent.length <= 300)
        val arguments = if (item.has("arguments")) item.getJSONObject("arguments") else null
        val bindingsObject = if (item.has("inputBindings")) item.getJSONObject("inputBindings") else JSONObject()
        require(bindingsObject.length() <= 32)
        val bindings = bindingsObject.keys().asSequence().associateWith { key ->
            require(key.length in 1..120)
            val name = requireNotNull(bindingsObject.get(key) as? String) { "Input binding must name an input" }
            require(inputName.matches(name))
            name
        }
        require(arguments != null || bindings.isEmpty()) { "Bindings need a typed argument template" }
        if (arguments == null) return ProcedureStep(capability, intent)
        require(descriptor != null && descriptor.name == capability) { "Typed action contract unavailable" }
        require(bindings.keys.none(arguments::has)) { "An argument cannot be both literal and bound" }
        val json = canonical(arguments)
        require(json.length <= MAX_ARGUMENT_CHARS) { "Argument template too large" }
        objectMap(arguments) // The ToolCall SDK requires non-null argument values.
        return ProcedureStep(capability, intent, json, bindings, fingerprint(descriptor))
    }

    fun bind(step: ProcedureStep, inputs: Map<String, String>, descriptor: ToolDescriptor?, declarations: List<ProcedureInput> = emptyList()): Map<String, Any>? {
        val argumentsJson = step.argumentsJson ?: return null
        require(descriptor != null && step.contractFingerprint == fingerprint(descriptor)) {
            "Saved action contract changed. Review a new workflow."
        }
        require(argumentsJson.length <= MAX_ARGUMENT_CHARS)
        val args = JSONObject(argumentsJson)
        step.inputBindings.forEach { (argument, input) ->
            val raw = requireNotNull(inputs[input])
            val type = declarations.singleOrNull { it.name == input }?.valueType ?: "string"
            val value: Any = when (type) {
                "string" -> raw
                "integer" -> requireNotNull(raw.toLongOrNull()) { "Expected a whole number input" }
                "boolean" -> requireNotNull(raw.toBooleanStrictOrNull()) { "Expected true or false input" }
                else -> throw IllegalArgumentException("Unsupported input type")
            }
            args.put(argument, value)
        }
        require(args.toString().length <= MAX_ARGUMENT_CHARS) { "Bound arguments too large" }
        return objectMap(args)
    }

    fun literals(step: ProcedureStep): Map<String, Any>? = step.argumentsJson?.let {
        require(it.length <= MAX_ARGUMENT_CHARS)
        objectMap(JSONObject(it))
    }

    fun inputTypes(step: ProcedureStep, declarations: List<ProcedureInput>): Map<String, String> =
        step.inputBindings.mapValues { (_, name) ->
            requireNotNull(declarations.singleOrNull { it.name == name }) { "Unknown named input" }.valueType
        }

    fun fingerprint(descriptor: ToolDescriptor): String {
        val value = JSONObject()
            .put("id", descriptor.id.value).put("name", descriptor.name)
            .put("description", descriptor.definition.description)
            .put("schema", JSONObject(descriptor.definition.parameters))
            .put("owner", descriptor.operability?.owner ?: "")
            .put("contractVersion", descriptor.operability?.version ?: 0)
            .put("capability", descriptor.operability?.capabilityId?.value ?: "")
            .put("sourceGuards", JSONArray(descriptor.operability?.sourceVersionArguments?.sorted().orEmpty()))
            .put("risk", descriptor.security.risk.name).put("confirmation", descriptor.security.confirmation.name)
            .put("replay", descriptor.security.replaySafety.name)
            .put("execution", descriptor.execution.toString())
            .put("requirements", JSONArray(descriptor.requirements.map { it.toString() }.sorted()))
            .put("dependencies", JSONArray(descriptor.dependencies.map { it.value }.sorted()))
            .put("automation", descriptor.automation.toString())
            .put("resources", JSONArray(descriptor.resources.map { it.toString() }.sorted()))
            .put("reviewArgument", descriptor.presentation?.reviewArgument ?: "")
            .put("presentation", descriptor.presentation?.toString() ?: "")
            .put("resultSchema", JSONObject(descriptor.operability?.resultSchema.orEmpty()))
        return MessageDigest.getInstance("SHA-256").digest(canonical(value).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun version(procedure: com.halo.runtime.cognition.procedures.LearnedProcedure): String {
        val value = JSONObject().put("id", procedure.id).put("name", procedure.name)
            .put("description", procedure.description).put("enabled", procedure.enabled)
            .put("schemaVersion", procedure.schemaVersion).put("updatedAt", procedure.updatedAt)
            .put("triggers", JSONArray(procedure.triggerPhrases))
            .put("inputs", JSONArray(procedure.inputs.map { JSONObject().put("name", it.name).put("description", it.description).put("valueType", it.valueType) }))
            .put("steps", JSONArray(procedure.steps.map { JSONObject().put("capabilityId", it.capabilityId)
                .put("intent", it.intent).put("argumentsJson", it.argumentsJson ?: JSONObject.NULL)
                .put("inputBindings", JSONObject(it.inputBindings)).put("contract", it.contractFingerprint ?: JSONObject.NULL) }))
        return MessageDigest.getInstance("SHA-256").digest(canonical(value).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun array(raw: Any): JSONArray = when (raw) {
        is JSONArray -> raw
        is List<*> -> JSONArray(raw)
        else -> throw IllegalArgumentException("Expected an array")
    }

    private fun canonical(value: Any?, depth: Int = 0): String {
        require(depth <= 10) { "Argument nesting too deep" }
        return when (value) {
            null, JSONObject.NULL -> "null"
            is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") {
                JSONObject.quote(it) + ":" + canonical(value.get(it), depth + 1)
            }
            is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it), depth + 1) }
            is String -> JSONObject.quote(value)
            is Boolean -> value.toString()
            is Number -> JSONObject.numberToString(value)
            else -> throw IllegalArgumentException("Unsupported argument value")
        }
    }

    private fun objectMap(value: JSONObject): Map<String, Any> = value.keys().asSequence().associateWith { unwrap(value.get(it)) }
    private fun unwrap(value: Any?): Any = when (value) {
        null, JSONObject.NULL -> throw IllegalArgumentException("Omit optional arguments instead of binding null")
        is JSONObject -> objectMap(value)
        is JSONArray -> (0 until value.length()).map { unwrap(value.get(it)) }
        else -> value
    }
}
