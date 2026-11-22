package com.halo.app.data.search

import androidx.room.withTransaction
import com.halo.app.data.notebook.WorkspaceObjectType
import com.halo.app.data.workspace.ContentOwnership
import com.halo.app.data.workspace.privateLibraryClaim
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.study.StudyTextExtractor
import com.halo.sdk.runtime.multimodal.MediaAssetRef
import com.halo.sdk.runtime.multimodal.MediaKind
import com.halo.sdk.runtime.multimodal.MediaLifetime
import com.halo.sdk.runtime.multimodal.MediaProvenance
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDate
import java.time.YearMonth
import java.time.Month
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class ReceiptQuery(val merchant: String, val month: YearMonth) {
    init {
        require(merchant.length in 2..80 && merchant.any(Char::isLetterOrDigit) && merchant.none(Char::isISOControl))
        require(month.year in 1970..2199)
    }
}

internal data class SavedReceipt(val objectId: String, val version: String, val title: String, val date: LocalDate,
    val currency: String, val amount: BigDecimal)

internal data class ReceiptTotals(val receipts: List<SavedReceipt>, val unreadable: Int, val limited: Boolean) {
    val byCurrency get() = receipts.groupBy(SavedReceipt::currency)
        .mapValues { (_, entries) -> entries.fold(BigDecimal.ZERO) { total, entry -> total + entry.amount } }

    fun json() = JSONObject().put("totals", JSONArray(byCurrency.map { (currency, amount) ->
        JSONObject().put("currency", currency).put("amount", amount.toPlainString())
    })).put("sources", JSONArray(receipts.map { receipt ->
        JSONObject().put("objectId", receipt.objectId).put("sourceVersion", receipt.version).put("title", receipt.title)
            .put("date", receipt.date.toString()).put("currency", receipt.currency).put("amount", receipt.amount.toPlainString())
    })).put("unreadable", unreadable).put("limited", limited).put("untrustedContent", true)
}

private val receiptSignal = Regex("""(?iu)\b(receipt|чек|квитанция|čeks|kvīts|fiskal\p{L}*|amount paid|card payment|оплачено|karšu maksājums)\b""")
private val localDate = Regex("""(?<!\d)(\d{1,2})[./-](\d{1,2})[./-](20\d{2})(?!\d)""")
private val isoDate = Regex("""(?<!\d)(20\d{2})-(\d{2})-(\d{2})(?!\d)""")
private val totalLine = Regex("""(?iu)^\s*(?:grand total|amount paid|total paid|total(?: incl\.? vat)?|к оплате|всего к оплате|итого|apmaksai|kopā(?: apmaksai)?|summa apmaksai)\s*[:=]?\s*(?:(EUR|USD|GBP|PLN|RUB|€|£|₽)\s*)?([0-9]{1,8}(?:[.,][0-9]{2}))\s*(EUR|USD|GBP|PLN|RUB|€|£|₽)?\s*$""")
private val currencyInText = Regex("""(?iu)(?<!\p{L})(EUR|USD|GBP|PLN|RUB)(?!\p{L})|[€£₽]""")
private val merchantWord = Regex("""[\p{L}\p{N}]+""")
private val receiptQuestion = Regex("""(?iu)^(?:how much (?:did i spend|have i spent) (?:at|in)|receipt totals?(?: at| for)?|сколько я потратил[аи]? в|сумма чеков(?: в)?)\s+(.+?)\s+(?:in\s+|for\s+|за\s+|в\s+)?(last month|прошлый месяц|\d{4}-\d{2}|[\p{L}]+(?:\s+\d{4})?)[?!.]*$""")

internal fun parseReceiptQuery(value: String, today: LocalDate = LocalDate.now()): ReceiptQuery? {
    val result = receiptQuestion.matchEntire(value.trim()) ?: return null
    val (merchant, date) = result.destructured
    val text = date.lowercase(Locale.ROOT)
    val month = when (text) {
        "last month", "прошлый месяц" -> YearMonth.from(today).minusMonths(1)
        else -> runCatching { YearMonth.parse(text) }.getOrNull() ?: run {
            val token = text.substringBefore(' ')
            val number = Month.entries.firstOrNull { month ->
                listOf(Locale.ENGLISH, Locale.forLanguageTag("ru")).any { locale ->
                    listOf(TextStyle.FULL, TextStyle.FULL_STANDALONE).any {
                        month.getDisplayName(it, locale).lowercase(Locale.ROOT) == token
                    }
                }
            } ?: return null
            val year = text.substringAfter(' ', "").toIntOrNull() ?: today.year - if (number > today.month) 1 else 0
            YearMonth.of(year, number)
        }
    }
    return runCatching { ReceiptQuery(merchant.trim(), month) }.getOrNull()
}

internal fun parseSavedReceipt(text: String, title: String, query: ReceiptQuery): Triple<LocalDate, String, BigDecimal>? {
    val folded = WorkspaceLocalContentSearch.folded(text)
    val words = merchantWord.findAll(WorkspaceLocalContentSearch.folded(query.merchant)).map { it.value }.toList()
    if (words.isEmpty()) return null
    val merchant = Regex(words.joinToString("[^\\p{L}\\p{N}]+", "(?<![\\p{L}\\p{N}])", "(?![\\p{L}\\p{N}])") { Regex.escape(it) })
    if (!merchant.containsMatchIn(folded.lineSequence().take(12).joinToString("\n"))) return null
    if (!receiptSignal.containsMatchIn("$title\n$text")) return null
    val dates = (isoDate.findAll(text).map { it.groupValues.drop(1).map(String::toInt) } +
        localDate.findAll(text).map { match -> match.groupValues.drop(1).map(String::toInt).reversed() })
        .mapNotNull { (year, month, day) -> runCatching { LocalDate.of(year, month, day) }.getOrNull() }.distinct().toList()
    val date = dates.singleOrNull()?.takeIf { YearMonth.from(it) == query.month } ?: return null
    fun currency(value: String) = when (value.uppercase(Locale.ROOT)) {
        "€" -> "EUR"
        "£" -> "GBP"
        "₽" -> "RUB"
        else -> value.uppercase(Locale.ROOT)
    }
    val stated = currencyInText.findAll(text).map { currency(it.value) }.distinct().toList()
    val totals = text.lineSequence().mapNotNull(totalLine::matchEntire).mapNotNull { match ->
        val (_, before, value, after) = match.groupValues
        val units = listOf(before, after).filter(String::isNotBlank).map(::currency).distinct()
        val unit = if (units.isEmpty()) stated.singleOrNull() else units.singleOrNull()
        unit?.let { it to BigDecimal(value.replace(',', '.')) }
    }.distinct().toList()
    val (unit, amount) = totals.singleOrNull() ?: return null
    return Triple(date, unit, amount)
}

/** Only local bytes and active Workspace versions are read. Every reported amount has a source. */
internal suspend fun readLibraryReceiptTotals(query: ReceiptQuery, cloudResult: Boolean = false): ReceiptTotals = withContext(Dispatchers.IO) {
    val family = Graph.kidsPreferences.adultActionRevision()
    val db = Graph.appDatabase
    val zone = ZoneId.systemDefault()
    val from = query.month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val until = query.month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val candidates = db.notebookDao().receiptCandidates(from, until)
    val extractor = StudyTextExtractor(Graph.applicationContext, Graph.mediaAssetResolver, Graph.imageInputPreparer)
    val seen = hashSetOf<String>()
    val receipts = mutableListOf<SavedReceipt>()
    var unreadable = 0
    var scanned = 0
    var limited = candidates.size > 160
    for (item in candidates.take(160)) {
        Graph.kidsPreferences.requireAdultAction(family)
        val version = Graph.workspaceUseCases.sourceVersion(item.id) ?: continue
        if (!Graph.workspaceContext.permits(item.id, !cloudResult)) continue
        val note = item.type == WorkspaceObjectType.NOTE.name
        val text = try {
            if (note) Graph.notebookRepository.getNote(item.id)?.body else {
                val file = Graph.workspaceUseCases.getFile(item.id) ?: continue
                if (file.ownership != ContentOwnership.HALO_OWNED || file.sha256 == null || file.sizeBytes == null ||
                    file.sizeBytes !in 1L..12_000_000L) continue
                if (scanned >= 12) { limited = true; continue }
                scanned++
                if (file.mimeType.startsWith("image/")) extractor.image(MediaAssetRef("workspace:${item.id}", MediaKind.IMAGE,
                    file.mimeType, file.sizeBytes, file.sha256, item.createdAt, MediaProvenance.WORKSPACE,
                    MediaLifetime.WORKSPACE_PERSISTED, workspaceObjectId = item.id))
                else {
                    val parts = mutableListOf<String>()
                    val pages = if (file.mimeType == "application/pdf") Graph.studySourceResolver.pdf.pageCount(file) else 1
                    if (pages > 3) limited = true
                    extractor.document(file.uri, file.mimeType, item.id, version, item.title, lastPage = pages.coerceAtMost(3)) {
                        if (it.text.isBlank()) limited = true
                        parts += it.text
                    }
                    parts.joinToString("\n")
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { unreadable++; continue }
        if (text.isNullOrBlank()) { unreadable++; continue }
        val (date, unit, amount) = parseSavedReceipt(text, item.title, query) ?: continue
        val normalized = WorkspaceLocalContentSearch.folded(text).replace(Regex("\\s+"), " ").trim()
        val key = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray()).joinToString("") { "%02x".format(it) }
        if (WorkspaceLocalContentSearch.safe(item.title) && seen.add(key)) {
            if (receipts.size >= 48) { limited = true; break }
            receipts += SavedReceipt(item.id, version, item.title.take(200), date, unit, amount)
        }
    }
    db.withTransaction {
        Graph.kidsPreferences.requireAdultAction(family)
        val claims = receipts.map { privateLibraryClaim(it.objectId) }
        if (claims.isNotEmpty()) check(db.privateLibraryDao().claims(claims).isEmpty()) { "A Library source became private" }
        for (receipt in receipts) check(Graph.workspaceUseCases.sourceVersion(receipt.objectId) == receipt.version &&
            Graph.workspaceContext.permits(receipt.objectId, !cloudResult)) { "A Library source changed" }
        ReceiptTotals(receipts.sortedByDescending(SavedReceipt::date), unreadable, limited)
    }
}
