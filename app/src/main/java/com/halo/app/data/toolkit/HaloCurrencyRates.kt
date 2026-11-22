package com.halo.app.data.toolkit

import android.content.Context
import android.util.Xml
import java.io.StringReader
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser

internal data class CurrencyReferenceRates(val date: LocalDate, private val perEuro: Map<CalcUnit, BigDecimal>) {
    fun crossRate(from: CalcUnit, to: CalcUnit): String? {
        val base = perEuro[from] ?: return null
        val quote = perEuro[to] ?: return null
        return quote.divide(base, MathContext(20)).stripTrailingZeros().toPlainString()
    }
}

/** The request only downloads the public table. It carries no amount, currency pair or chat data. */
internal object HaloCurrencyRates {
    private const val source = "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml"
    private const val limit = 128 * 1024L
    private const val key = "ecb_xml"
    private val refreshLock = Mutex()
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .callTimeout(8, TimeUnit.SECONDS).build()

    private fun storage(context: Context) =
        context.getSharedPreferences("halo_currency_rates_v1", Context.MODE_PRIVATE)

    suspend fun saved(context: Context): CurrencyReferenceRates? = withContext(Dispatchers.IO) {
        storage(context).getString(key, null)?.let { runCatching { parse(it) }.getOrNull() }
    }

    suspend fun refresh(context: Context, allowed: () -> Boolean): CurrencyReferenceRates = withContext(Dispatchers.IO) {
        refreshLock.withLock {
            check(allowed())
            val xml = client.newCall(Request.Builder().url(source).build()).execute().use { response ->
                check(response.isSuccessful)
                val body = requireNotNull(response.body).source()
                check(!body.request(limit + 1)) { "Exchange rates response too large" }
                body.readUtf8()
            }
            val rates = parse(xml)
            val disk = storage(context)
            val old = disk.getString(key, null)?.let { runCatching { parse(it) }.getOrNull() }
            check(allowed())
            if (old != null && old.date > rates.date) return@withLock old
            check(disk.edit().putString(key, xml).commit())
            rates
        }
    }

    internal fun parse(xml: String): CurrencyReferenceRates {
        require(xml.length <= limit && !xml.contains("<!DOCTYPE", ignoreCase = true))
        val parser = Xml.newPullParser().apply { setInput(StringReader(xml)) }
        val rates = mutableMapOf(CalcUnit.EUR to BigDecimal.ONE)
        var date: LocalDate? = null
        var datedDepth = -1
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "Cube") {
                val time = parser.getAttributeValue(null, "time")
                if (time != null) {
                    require(date == null)
                    date = LocalDate.parse(time)
                    datedDepth = parser.depth
                } else if (datedDepth >= 0 && parser.depth == datedDepth + 1) {
                    val code = parser.getAttributeValue(null, "currency")
                    val unit = CalcUnit.entries.firstOrNull { it.kind == CalcUnitKind.CURRENCY && it != CalcUnit.EUR && it.symbol == code }
                    if (unit != null) {
                        val rate = requireNotNull(parser.getAttributeValue(null, "rate")).toBigDecimal()
                        require(rate.signum() == 1 && rate.scale() in 0..12 && rate <= BigDecimal("1000000"))
                        require(rates.put(unit, rate) == null)
                    }
                }
            } else if (parser.eventType == XmlPullParser.END_TAG && parser.depth == datedDepth) {
                datedDepth = -1
            }
            parser.next()
        }
        val publication = requireNotNull(date)
        require(rates.size > 1 && !publication.isAfter(LocalDate.now(ZoneOffset.UTC).plusDays(1)))
        return CurrencyReferenceRates(publication, rates.toMap())
    }
}
