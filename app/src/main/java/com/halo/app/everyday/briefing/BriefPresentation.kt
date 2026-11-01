package com.halo.app.everyday.briefing

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** One localized factual projection for the page, widgets and canonical read-aloud controller. */
object BriefPresentation {
    fun title(period: BriefPeriod, locale: Locale): String = when (period) {
        BriefPeriod.MORNING -> text(locale, "Your morning", "Ваше утро", "Tavs rīts", "Twój poranek")
        BriefPeriod.ON_DEMAND -> text(locale, "Your day in Halo", "Ваш день в Halo", "Tava diena ar Halo", "Twój dzień w Halo")
        BriefPeriod.EVENING -> text(locale, "Your evening", "Ваш вечер", "Tavs vakars", "Twój wieczór")
        BriefPeriod.CHANGES -> text(locale, "Since morning", "С утра", "Kopš rīta", "Od rana")
    }
    fun sourceStatus(source: BriefSourceStamp, now: Instant, power: Boolean, locale: Locale, zone: ZoneId): String {
        if (source.retrievedAt == null) return text(locale, "No stored snapshot", "Сохранённых данных нет", "Nav saglabātu datu", "Brak zapisanych danych")
        val status = if (source.stale(now, power)) text(locale, "Cached", "Из кеша", "Saglabāts", "Z pamięci")
            else text(locale, "Checked", "Проверено", "Pārbaudīts", "Sprawdzono")
        return "$status · ${time(source.verifiedAt ?: source.retrievedAt, locale, zone)} · ${source.publisher}"
    }
    fun factText(fact: BriefFact, locale: Locale, zone: ZoneId): String {
        if (fact.kind == BriefFactKind.WEATHER) {
            val format = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
            val temperature = fact.values["temperatureC"]?.toDoubleOrNull()?.let { "${format.format(it)} °C" }
            val rain = fact.values["rainProbability"]?.toIntOrNull()?.let {
                text(locale, "$it% chance of rain", "Вероятность дождя $it%", "Lietus iespējamība $it%", "$it% szans na deszcz")
            }
            return listOfNotNull(fact.title, temperature, rain).joinToString(" · ")
        }
        val at = fact.at?.takeIf { fact.kind in setOf(BriefFactKind.EVENT, BriefFactKind.TASK, BriefFactKind.REMINDER, BriefFactKind.ALARM) }
            ?.let { time(it, locale, zone) }
        val completed = if (fact.values["completed"] == "true") text(locale, "Done", "Готово", "Paveikts", "Gotowe") else null
        return listOfNotNull(fact.title, at, completed).joinToString(" · ")
    }
    fun comparisonNotice(brief: EverydayBrief, locale: Locale): String? = when {
        brief.baselineMissing -> text(locale, "This saved view has no matching morning brief for comparison. Save a morning brief in this experience, then check changes.",
            "У этого сохранённого представления нет соответствующего утреннего обзора для сравнения. Сохраните утренний обзор в этом режиме, затем проверьте изменения.",
            "Šim saglabātajam skatam nav atbilstoša rīta pārskata salīdzināšanai. Saglabā rīta pārskatu šajā vidē un tad pārbaudi izmaiņas.",
            "Ten zapisany widok nie ma pasującego porannego podsumowania do porównania. Zapisz poranne podsumowanie w tym trybie, a potem sprawdź zmiany.")
        brief.period == BriefPeriod.CHANGES && brief.changes.isEmpty() && brief.comparisonUnavailableSources.isNotEmpty() -> text(locale,
            "Some sources have no morning snapshot. No changes were found in the sources that could be compared.",
            "Для части источников нет утренних данных. В доступных для сравнения источниках изменений не найдено.",
            "Dažiem avotiem nav rīta datu. Salīdzināmajos avotos izmaiņas nav atrastas.",
            "Część źródeł nie ma porannych danych. W źródłach dostępnych do porównania nie znaleziono zmian.")
        brief.period == BriefPeriod.CHANGES && brief.changes.isEmpty() -> text(locale,
            "No changes in the available information since your morning brief.", "В доступных данных нет изменений с утреннего обзора.",
            "Pieejamajos datos kopš rīta pārskata izmaiņu nav.", "Brak zmian w dostępnych danych od porannego podsumowania.")
        else -> null
    }
    /** Only a deliberate read action calls the audio owner with this text; creating/displaying a brief is silent. */
    fun spoken(brief: EverydayBrief, now: Instant, power: Boolean, locale: Locale): String {
        val zone = ZoneId.of(brief.zoneId)
        val sources = brief.sources.associateBy { it.id }
        return buildList {
            add(title(brief.period, locale))
            comparisonNotice(brief, locale)?.let(::add)
            if (brief.facts.isEmpty()) add(text(locale, "No selected information is available for this brief.",
                "Для этого обзора нет доступных выбранных данных.", "Šim pārskatam nav pieejamu atlasīto datu.", "Brak dostępnych wybranych danych do tego podsumowania."))
            val facts = if (brief.period == BriefPeriod.CHANGES && !brief.baselineMissing) brief.changes.mapNotNull { it.fact }.distinctBy { it.id } else brief.facts
            facts.take(if (brief.audience == BriefAudience.KIDS) 8 else 20).forEach { fact ->
                val stamp = sources[fact.sourceId]
                add(factText(fact, locale, zone) + if (stamp?.external == true) ". ${sourceStatus(stamp, now, power, locale, zone)}" else "")
            }
        }.joinToString(". ").take(12000)
    }
    fun text(locale: Locale, en: String, ru: String, lv: String, pl: String) = when (locale.language) { "ru" -> ru; "lv" -> lv; "pl" -> pl; else -> en }
    private fun time(at: Instant, locale: Locale, zone: ZoneId) = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).withZone(zone).format(at)
}
