package com.halo.app.online

import com.halo.sdk.runtime.Storage
import java.math.BigDecimal
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID

enum class OnlineProblem {
    CONSENT_REQUIRED, CONSENT_EXPIRED, BUDGET_REACHED, INVALID_AMOUNT, LEDGER_UNAVAILABLE,
    LOCAL_ONLY, CHILD_RESTRICTED, KEY_REQUIRED, AUTHENTICATION, CREDIT_LIMIT, RATE_LIMIT,
    NETWORK, INVALID_RESPONSE, NO_SOURCES, INCOMPLETE, PRIVACY, UNAVAILABLE, FEATURE_DISABLED,
}
class OnlineOperationRejected(val problem: OnlineProblem) : Exception(problem.name)

data class OnlineSpendRecord(
    val id: String, val at: Long, val feature: String, val provider: String, val model: String,
    val reservationUsd: BigDecimal, val reportedUsd: BigDecimal?, val state: String,
)
data class OnlineSpendSnapshot(
    val month: String, val records: List<OnlineSpendRecord>,
    val monthlyLimitUsd: BigDecimal?, val requestLimitUsd: BigDecimal?,
) {
    val reportedUsd: BigDecimal get() = records.fold(BigDecimal.ZERO) { total, row -> total + (row.reportedUsd ?: BigDecimal.ZERO) }
    val estimatedUsd: BigDecimal get() = records.filter { it.reportedUsd == null }.fold(BigDecimal.ZERO) { total, row -> total + row.reservationUsd }
    val accountedUsd: BigDecimal get() = reportedUsd + estimatedUsd
    val unconfirmed: Int get() = records.count { it.reportedUsd == null }
}

/** Local reservations, NOT an invoice or a provider-enforced spending ceiling.
 * Instances share a process lock; requests reserve before dispatch and never auto-refund.
 * The ledger contains no prompts, query text, media, credentials or source URLs.
 */
class OnlineOperationLedger(private val storage: Storage, private val now: () -> Long = System::currentTimeMillis) {
    fun snapshot(): OnlineSpendSnapshot = synchronized(lock) { snapshotLocked(read()) }

    fun configure(monthly: BigDecimal?, perRequest: BigDecimal?) = synchronized(lock) {
        listOfNotNull(monthly, perRequest).forEach { require(validMoney(it, allowZero = true)) }
        storage.putString(LIMITS_KEY, "v1\t${monthly?.toPlainString() ?: "-"}\t${perRequest?.toPlainString() ?: "-"}")
    }

    fun start(feature: String, provider: String, model: String, reservation: BigDecimal): String = synchronized(lock) {
        require(feature in setOf("SEARCH", "IMAGE"))
        require(provider.length in 1..128 && model.length in 1..256)
        if (!validMoney(reservation, allowZero = false)) throw OnlineOperationRejected(OnlineProblem.INVALID_AMOUNT)
        val records = read()
        val snapshot = snapshotLocked(records)
        if (snapshot.requestLimitUsd?.let { reservation > it } == true ||
            snapshot.monthlyLimitUsd?.let { snapshot.accountedUsd + reservation > it } == true)
            throw OnlineOperationRejected(OnlineProblem.BUDGET_REACHED)
        // Never discard current-month records to make another request appear affordable.
        val cutoff = YearMonth.parse(snapshot.month).minusMonths(2).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val retained = records.filter { it.at >= cutoff }
        if (retained.size >= MAX_RECORDS) throw OnlineOperationRejected(OnlineProblem.LEDGER_UNAVAILABLE)
        val row = OnlineSpendRecord(UUID.randomUUID().toString(), now(), feature, provider, model, reservation, null, "SUBMITTED")
        write(retained + row) // A storage failure prevents dispatch.
        row.id
    }

    fun finish(id: String, reported: BigDecimal?, state: String) = synchronized(lock) {
        require(state in setOf("COMPLETED", "UNCERTAIN"))
        val amount = reported?.takeIf { validMoney(it, allowZero = true) }
        val records = read()
        val row = records.firstOrNull { it.id == id } ?: return@synchronized
        if (row.reportedUsd != null) return@synchronized // duplicate callbacks cannot double-charge or erase evidence
        write(records.map { if (it.id == id) it.copy(reportedUsd = amount, state = state) else it })
    }

    private fun snapshotLocked(records: List<OnlineSpendRecord>): OnlineSpendSnapshot {
        val currentMonth = month(now())
        val rawLimits = storage.getString(LIMITS_KEY)
        if (rawLimits != null && rawLimits.length > 128) throw OnlineOperationRejected(OnlineProblem.LEDGER_UNAVAILABLE)
        val limits = rawLimits?.split('\t')
        if (limits != null && (limits.size != 3 || limits[0] != "v1"))
            throw OnlineOperationRejected(OnlineProblem.LEDGER_UNAVAILABLE)
        fun limit(index: Int): BigDecimal? = limits?.get(index)?.let {
            if (it == "-") null else {
                it.toBigDecimalOrNull()?.takeIf { value -> validMoney(value, true) }
                    ?: throw OnlineOperationRejected(OnlineProblem.LEDGER_UNAVAILABLE)
            }
        }
        return OnlineSpendSnapshot(currentMonth, records.filter { month(it.at) == currentMonth }, limit(1), limit(2))
    }
    private fun read(): List<OnlineSpendRecord> {
        try {
            val raw = storage.getString(RECORDS_KEY) ?: return emptyList()
            require(raw.length <= 2_000_000)
            val lines = raw.split('\n')
            require(lines.firstOrNull() == "v1" && lines.size <= MAX_RECORDS + 1)
            return lines.drop(1).map { line ->
                require(line.length <= 4096)
                val c = line.split('\t'); require(c.size == 8)
                require(c[0].length == 36 && c[1].length <= 20 && c[5].length <= 40 && c[6].length <= 40)
                require(c[2] in setOf("SEARCH", "IMAGE") && c[7] in setOf("SUBMITTED", "COMPLETED", "UNCERTAIN"))
                val reservation = c[5].toBigDecimal(); require(validMoney(reservation, false))
                val reported = if (c[6] == "-") null else c[6].toBigDecimal().also { require(validMoney(it, true)) }
                val provider = decode(c[3]); val model = decode(c[4])
                require(provider.length in 1..128 && model.length in 1..256)
                UUID.fromString(c[0])
                OnlineSpendRecord(c[0], c[1].toLong().also { require(it >= 0) }, c[2], provider, model, reservation, reported, c[7])
            }.also { rows -> require(rows.map { it.id }.distinct().size == rows.size) }
        } catch (_: Exception) { throw OnlineOperationRejected(OnlineProblem.LEDGER_UNAVAILABLE) }
    }
    private fun write(records: List<OnlineSpendRecord>) {
        val serialized = "v1" + records.joinToString(separator = "", transform = {
            "\n" + listOf(it.id, it.at.toString(), it.feature, encode(it.provider), encode(it.model),
                it.reservationUsd.toPlainString(), it.reportedUsd?.toPlainString() ?: "-", it.state).joinToString("\t")
        })
        try {
            require(serialized.length <= 2_000_000)
            storage.putString(RECORDS_KEY, serialized)
        }
        catch (_: Exception) { throw OnlineOperationRejected(OnlineProblem.LEDGER_UNAVAILABLE) }
    }
    private fun month(at: Long) = YearMonth.from(Instant.ofEpochMilli(at).atZone(ZoneOffset.UTC)).toString()
    private fun encode(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(UTF_8))
    private fun decode(value: String) = String(Base64.getUrlDecoder().decode(value), UTF_8)
    companion object {
        const val RECORDS_KEY = "online_operation_reservations_v1"
        private const val LIMITS_KEY = "online_operation_limits_v1"
        private const val MAX_RECORDS = 2048
        private val lock = Any()
        fun validMoney(value: BigDecimal, allowZero: Boolean): Boolean =
            value.scale() in 0..8 && value <= BigDecimal("1000000") &&
                (if (allowZero) value >= BigDecimal.ZERO else value > BigDecimal.ZERO)
        fun parseMoney(text: String): BigDecimal? = text.trim().replace(',', '.').takeIf {
            it.length in 1..18 && it.matches(Regex("[0-9]+(?:\\.[0-9]{1,8})?"))
        }?.toBigDecimalOrNull()?.takeIf { validMoney(it, true) }
    }
}
