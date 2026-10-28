package com.halo.app.data.procedure

import android.content.Context
import com.halo.runtime.cognition.procedures.LearnedProcedure
import com.halo.runtime.cognition.procedures.LearnedProcedureStore
import com.halo.runtime.cognition.procedures.ProcedureOrigin
import com.halo.runtime.cognition.procedures.ProcedureStep
import com.halo.runtime.cognition.procedures.ProcedureInput
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * MINING-P1-001 durable learned-procedure storage.
 *
 * Deliberately a small bounded document rather than a new database: learned procedures are few, and
 * this batch must not introduce a second persistence stack. Uses the same SharedPreferences pattern
 * the rest of the app already uses for small settings documents.
 *
 * Only explicitly reviewed templates and named-input definitions are stored. Values entered when
 * using a workflow are not written into its template. No utterance or memory is harvested here.
 */
class SharedPreferencesLearnedProcedureStore(
    context: Context,
    private val maxProcedures: Int = MAX_PROCEDURES,
) : LearnedProcedureStore {

    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    override suspend fun all(): List<LearnedProcedure> = mutex.withLock { readAll() }

    override suspend fun get(id: String): LearnedProcedure? = mutex.withLock {
        readAll().firstOrNull { it.id == id }
    }

    override suspend fun upsert(procedure: LearnedProcedure) = mutex.withLock {
        val existing = readAll()
        check(existing.any { it.id == procedure.id } || existing.size < maxProcedures) { "Workflow storage is full" }
        val current = existing.filterNot { it.id == procedure.id }.toMutableList()
        current.add(0, procedure)
        // Full storage refuses a new write; saving a recipe must never evict another reviewed one.
        writeAll(current)
    }

    override suspend fun delete(id: String) = mutex.withLock {
        writeAll(readAll().filterNot { it.id == id })
    }

    override suspend fun compareAndSet(expected: LearnedProcedure, replacement: LearnedProcedure?): Boolean = mutex.withLock {
        require(replacement == null || replacement.id == expected.id)
        val current = readAll()
        if (current.singleOrNull { it.id == expected.id } != expected) return@withLock false
        writeAll(current.mapNotNull { if (it.id == expected.id) replacement else it })
        true
    }

    override suspend fun recordUsage(id: String, usedAtMs: Long) = mutex.withLock {
        writeAll(
            readAll().map { procedure ->
                if (procedure.id != id) {
                    procedure
                } else {
                    procedure.copy(
                        usageCount = procedure.usageCount + 1,
                        lastUsedAt = usedAtMs,
                    )
                }
            }
        )
    }

    private fun readAll(): List<LearnedProcedure> {
        val raw = preferences.getString(KEY_PROCEDURES, null) ?: return emptyList()
        check(raw.length <= 1_000_000) { "Saved workflow data exceeds its supported size" }
        val array = JSONArray(raw)
        check(array.length() <= maxProcedures) { "Saved workflow collection exceeds its supported size" }
        return buildList {
            for (index in 0 until array.length()) {
                // Do not turn malformed storage into an empty collection and overwrite user data.
                add(requireNotNull(decode(array.getJSONObject(index))) { "Saved workflow data needs recovery" })
            }
        }.also { rows -> check(rows.map { it.id }.distinct().size == rows.size) { "Duplicate saved workflow identity" } }
    }

    private fun writeAll(procedures: List<LearnedProcedure>) {
        val array = JSONArray()
        procedures.forEach { array.put(encode(it)) }
        check(preferences.edit().putString(KEY_PROCEDURES, array.toString()).commit()) { "Could not persist reviewed workflow" }
    }

    private fun encode(procedure: LearnedProcedure): JSONObject {
        val steps = JSONArray()
        procedure.steps.forEach { step ->
            steps.put(JSONObject().put("capabilityId", step.capabilityId).put("intent", step.intent)
                .put("argumentsJson", step.argumentsJson ?: JSONObject.NULL)
                .put("inputBindings", JSONObject(step.inputBindings))
                .put("contractFingerprint", step.contractFingerprint ?: JSONObject.NULL))
        }
        return JSONObject()
            .put("id", procedure.id)
            .put("name", procedure.name)
            .put("description", procedure.description)
            .put("triggerPhrases", JSONArray(procedure.triggerPhrases))
            .put("steps", steps)
            .put("origin", procedure.origin.name)
            .put("createdAt", procedure.createdAt)
            .put("updatedAt", procedure.updatedAt)
            .put("lastUsedAt", procedure.lastUsedAt ?: -1L)
            .put("usageCount", procedure.usageCount)
            .put("enabled", procedure.enabled)
            .put("schemaVersion", procedure.schemaVersion)
            .put("inputs", JSONArray(procedure.inputs.map { JSONObject().put("name", it.name).put("description", it.description).put("valueType", it.valueType) }))
    }

    private fun decode(item: JSONObject): LearnedProcedure? {
        val id = item.optString("id").takeIf { it.isNotBlank() } ?: return null
        val rawSteps = item.optJSONArray("steps") ?: JSONArray()
        val steps = buildList {
            for (index in 0 until rawSteps.length()) {
                val step = rawSteps.getJSONObject(index)
                val capability = requireNotNull(step.optString("capabilityId").takeIf { it.isNotBlank() }) { "Invalid saved workflow step" }
                val bindings = step.optJSONObject("inputBindings") ?: JSONObject()
                add(ProcedureStep(capabilityId = capability, intent = step.optString("intent").take(300),
                    argumentsJson = step.optString("argumentsJson").takeIf { !step.isNull("argumentsJson") },
                    inputBindings = bindings.keys().asSequence().associateWith { bindings.getString(it) },
                    contractFingerprint = step.optString("contractFingerprint").takeIf { !step.isNull("contractFingerprint") }))
            }
        }
        val rawTriggers = item.optJSONArray("triggerPhrases") ?: JSONArray()
        val triggers = buildList {
            for (index in 0 until rawTriggers.length()) {
                rawTriggers.optString(index).takeIf { it.isNotBlank() }?.let { add(it.take(80)) }
            }
        }
        val lastUsed = item.optLong("lastUsedAt", -1L).takeIf { it >= 0L }
        return LearnedProcedure(
            id = id,
            name = item.optString("name").take(120),
            description = item.optString("description").take(400),
            triggerPhrases = triggers,
            steps = steps,
            origin = runCatching { ProcedureOrigin.valueOf(item.optString("origin")) }
                .getOrDefault(ProcedureOrigin.USER_CREATED),
            createdAt = item.optLong("createdAt"),
            updatedAt = item.optLong("updatedAt"),
            lastUsedAt = lastUsed,
            usageCount = item.optLong("usageCount"),
            enabled = item.optBoolean("enabled", true),
            inputs = item.optJSONArray("inputs")?.let { raw -> (0 until raw.length()).map {
                val input = raw.getJSONObject(it)
                ProcedureInput(input.getString("name"), input.getString("description"), input.optString("valueType", "string"))
            } }.orEmpty(),
            schemaVersion = item.optInt("schemaVersion", 1),
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "halo_learned_procedures"
        const val KEY_PROCEDURES = "procedures_v1"
        const val MAX_PROCEDURES = 50
    }
}
