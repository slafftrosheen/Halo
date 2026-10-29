package com.halo.app.data.memory

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.halo.app.di.ManualDependencyGraph
import com.halo.runtime.cognition.context.MemoryContextItem
import com.halo.runtime.cognition.context.MemoryRecallQuery
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

/** Reads the installed production owner. Native UI alone creates, corrects and forgets the QA fact. */
@RunWith(AndroidJUnit4::class)
class MemoryContextCheckpointPhysicalTest {
    private enum class Checkpoint { ORIGINAL, CORRECTED, FORGOTTEN }

    @Test fun verifyProductionRecallCheckpoint() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val runId = requireNotNull(args.getString("runId")) { "runId required" }
        require(runId.matches(Regex("[A-Za-z0-9._-]{1,80}"))) { "Invalid runId" }
        val checkpoint = Checkpoint.valueOf(requireNotNull(args.getString("checkpoint")))
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "memory-context-qa-$runId")
        val retained = File(directory, "retained-private.json")
        val source = ManualDependencyGraph.memoryContextSource
        check(ManualDependencyGraph.workspaceContext.useMemories.value) { "Existing memory context opt-in required" }
        val query = MemoryRecallQuery(text = PREFIX, kinds = setOf("FACT"), scopes = setOf("GLOBAL"),
            limit = 5, targetIsLocal = true)
        val fresh = source.recall(query)
        val qa = fresh.filter { it.content.startsWith(PREFIX) }
        val state = if (checkpoint == Checkpoint.ORIGINAL) {
            check(!retained.exists()) { "Use a fresh runId for the original checkpoint" }
            JSONObject().put("runId", runId)
        } else JSONObject(retained.readText()).also { check(it.getString("runId") == runId) }
        val report = JSONObject().put("kind", "LOCAL_PRODUCTION_RECALL_NOT_MODEL_INFLUENCE")
            .put("runId", runId).put("checkpoint", checkpoint.name)
            .put("atMs", System.currentTimeMillis()).put("targetIsLocal", true)
        when (checkpoint) {
            Checkpoint.ORIGINAL -> {
                val hit = qa.single()
                check(hit.content == ORIGINAL && hit.kind == "FACT" && hit.scope == "GLOBAL") { "Original QA fact mismatch" }
                check(source.dereference(hit, query) == hit) { "Original QA hit is not current" }
                state.put("original", encode(hit))
                report.put("current", receipt(hit))
            }
            Checkpoint.CORRECTED -> {
                val original = decode(state.getJSONObject("original"), ORIGINAL)
                check(source.dereference(original, query) == null) { "Old QA hit survived correction" }
                val hit = qa.single()
                check(hit.id == original.id && hit.content == CORRECTED && hit.kind == "FACT" && hit.scope == "GLOBAL") {
                    "Corrected QA identity or content mismatch"
                }
                check(hit.version != original.version) { "Correction did not change context version" }
                check(source.dereference(hit, query) == hit) { "Corrected QA hit is not current" }
                state.put("corrected", encode(hit))
                report.put("current", receipt(hit)).put("originalRejected", true)
            }
            Checkpoint.FORGOTTEN -> {
                val original = decode(state.getJSONObject("original"), ORIGINAL)
                val corrected = decode(state.getJSONObject("corrected"), CORRECTED)
                check(original.id == corrected.id) { "Retained QA identity mismatch" }
                check(source.dereference(original, query) == null && source.dereference(corrected, query) == null) {
                    "Retained QA hit survived Forget"
                }
                check(qa.isEmpty() && fresh.none { it.id == original.id }) { "Forgotten QA fact remains in recall" }
                report.put("forgotten", receipt(corrected)).put("originalRejected", true)
                    .put("correctedRejected", true).put("freshQaAbsent", true)
            }
        }
        check(ManualDependencyGraph.workspaceContext.useMemories.value) { "Memory context setting changed during checkpoint" }
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create private QA report directory" }
        if (checkpoint != Checkpoint.FORGOTTEN) retained.writeText(state.toString())
        File(directory, "${checkpoint.name.lowercase()}-result.json").writeText(report.put("result", "PASS").toString(2))
    }

    private fun encode(item: MemoryContextItem): JSONObject {
        check(!item.version.isNullOrBlank()) { "QA context version missing" }
        return JSONObject().put("id", item.id).put("content", item.content).put("kind", item.kind)
            .put("scope", item.scope).put("relevance", item.relevance.toDouble())
            .put("provenance", item.provenance ?: JSONObject.NULL).put("version", item.version)
    }

    private fun decode(json: JSONObject, expected: String): MemoryContextItem {
        check(json.getString("content") == expected && json.getString("kind") == "FACT" && json.getString("scope") == "GLOBAL") {
            "Retained file is not the exact owned QA fact"
        }
        return MemoryContextItem(id = json.getString("id"), content = expected, kind = "FACT", scope = "GLOBAL",
            relevance = json.getDouble("relevance").toFloat(),
            provenance = if (json.isNull("provenance")) null else json.getString("provenance"),
            version = json.getString("version").also { check(it.isNotBlank()) })
    }

    private fun receipt(item: MemoryContextItem) = JSONObject().put("id", item.id).put("version", item.version)
        .put("contentSha256", MessageDigest.getInstance("SHA-256").digest(item.content.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) })

    private companion object {
        const val PREFIX = "HALO-QA-20261029 test token"
        const val ORIGINAL = "$PREFIX is copper."
        const val CORRECTED = "$PREFIX is silver."
    }
}
