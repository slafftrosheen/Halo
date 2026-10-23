package com.halo.app.ai

import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

data class RemoteActivityReceipt(
    val id: String,
    val atMs: Long,
    val providerId: String,
    val operation: RemoteOperation,
    val categories: Set<RemoteDataCategory>,
    val outcome: RemoteDispatchOutcome,
    val providerReportedCostUsd: String?,
)

/** Bounded local metadata only; callers cannot supply a URL, payload or free-form error. */
class RemoteActivityHistory(private val storage: Storage, private val now: () -> Long = System::currentTimeMillis) {
    private val current = MutableStateFlow(read())
    val receipts = current.asStateFlow()
    @Synchronized fun record(request: RemoteDispatchRequest, outcome: RemoteDispatchOutcome, reportedCost: String? = null): String {
        require(request.providerId.matches(Regex("[a-zA-Z0-9_.-]{1,80}")))
        val cost = normalizedCost(reportedCost)
        val id = java.util.UUID.randomUUID().toString()
        val next = (listOf(RemoteActivityReceipt(id, now(), request.providerId, request.operation,
            request.categories.toSet(), outcome, cost)) + current.value).take(200)
        persist(next)
        current.value = next
        return id
    }
    @Synchronized fun finish(id: String, outcome: RemoteDispatchOutcome, reportedCost: String?) {
        val cost = normalizedCost(reportedCost)
        if (current.value.none { it.id == id }) return // Clearing history also clears in-flight entries.
        val next = current.value.map { if (it.id == id) it.copy(outcome = outcome, providerReportedCostUsd = it.providerReportedCostUsd ?: cost) else it }
        persist(next); current.value = next
    }
    @Synchronized fun clear() { persist(emptyList()); current.value = emptyList() }
    @Synchronized fun reportProviderCost(id: String, cost: String) {
        val row = current.value.firstOrNull { it.id == id } ?: return
        if (row.providerReportedCostUsd != null) return
        finish(id, row.outcome, cost)
    }
    private fun persist(rows: List<RemoteActivityReceipt>) {
        storage.putString(KEY, JSONArray().also { array -> rows.forEach { row ->
            array.put(JSONObject().put("id", row.id).put("at", row.atMs).put("provider", row.providerId)
                .put("operation", row.operation.name).put("categories", JSONArray(row.categories.map { it.name }))
                .put("outcome", row.outcome.name).put("cost", row.providerReportedCostUsd ?: JSONObject.NULL))
        } }.toString())
    }
    private fun read(): List<RemoteActivityReceipt> = try {
        val raw = storage.getString(KEY)
        if (raw == null || raw.length > 200000) emptyList() else JSONArray(raw).let { array ->
            require(array.length() <= 200)
            (0 until array.length()).map { index ->
                val row = array.getJSONObject(index)
                val provider = row.getString("provider").also { require(it.matches(Regex("[a-zA-Z0-9_.-]{1,80}"))) }
                val categories = row.getJSONArray("categories").let { values ->
                    (0 until values.length()).map { RemoteDataCategory.valueOf(values.getString(it)) }.toSet()
                }
                val id = row.getString("id").also { require(runCatching { java.util.UUID.fromString(it) }.isSuccess) }
                RemoteActivityReceipt(id, row.getLong("at"), provider, RemoteOperation.valueOf(row.getString("operation")),
                    categories, RemoteDispatchOutcome.valueOf(row.getString("outcome")),
                    if (row.isNull("cost")) null else row.getString("cost").also {
                        require(normalizedCost(it) != null)
                    })
            }
        }
    } catch (_: Exception) { emptyList() }
    companion object { private const val KEY = "remote_activity_metadata_v1" }
}

private fun normalizedCost(raw: String?): String? = raw?.takeIf { it.length <= 40 }?.let {
    runCatching {
        val amount = BigDecimal(it)
        if (amount.signum() < 0 || amount.scale() !in -9..18 || amount.precision() > 24 || amount > BigDecimal("1000000000")) null
        else amount.stripTrailingZeros().toPlainString().takeIf { value -> value.length <= 40 }
    }.getOrNull()
}
