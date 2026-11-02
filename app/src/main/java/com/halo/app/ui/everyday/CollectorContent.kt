package com.halo.app.ui.everyday

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.everyday.collectors.*
import com.halo.app.everyday.organizer.*
import com.halo.app.language.uiText
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal fun LazyListScope.collectorContent(state: CollectorState, itemId: String?, now: Instant, locale: Locale,
    kids: Boolean, onArticle: (String) -> Unit, onExternal: (String) -> Unit, onRemind: (ArticleFact) -> Unit,
    onLocal: (LocalFact) -> Unit) {
    fun stamp(at: Instant, zone: ZoneId = ZoneId.systemDefault()) = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).withZone(zone).format(at)
    when (val payload = state.snapshot?.payload) {
        null -> item { Text(uiText("No saved information yet. Select this source and refresh it when Power is enabled.")) }
        is CollectorPayload.Articles -> {
            val article = itemId?.let { id -> payload.items.find { it.id == id } }
            if (itemId != null && article == null) item { Text(uiText("This article is no longer in the saved feed. Open the source for current saved items.")) }
            else if (article != null) item {
                Text(article.title, style = MaterialTheme.typography.headlineMedium)
                Text(article.publisher, style = MaterialTheme.typography.labelLarge)
                article.publishedAt?.let { Text(uiText("Published %1\$s", stamp(it)), style = MaterialTheme.typography.bodySmall) }
                Text(article.text.ifBlank { uiText("The publisher supplied a title only. Open the publisher to read more.") })
                article.url?.let { url -> Text(url, style = MaterialTheme.typography.bodySmall); if (!kids) TextButton({ onExternal(url) }) { Text(uiText("Open publisher in browser")) } }
                if (!kids) TextButton({ onRemind(article) }) { Text(uiText("Remind me about this article")) }
            } else {
                if (payload.items.isEmpty()) item { Text(uiText("This source returned no articles.")) }
                items(payload.items, key = { it.id }) { entry ->
                    Surface(onClick = { onArticle(entry.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(entry.title, style = MaterialTheme.typography.titleMedium)
                            Text(entry.publisher, style = MaterialTheme.typography.bodySmall)
                            entry.publishedAt?.let { Text(uiText("Published %1\$s", stamp(it)), style = MaterialTheme.typography.bodySmall) }
                            Text(uiText("Read saved article"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        is CollectorPayload.Weather -> {
            val fact = payload.fact
            fun temperature(value: Double?) = value?.let { java.text.NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(it) + " °C" }
            item {
                Text(fact.place.name, style = MaterialTheme.typography.headlineMedium)
                Text(temperature(fact.temperatureC) ?: uiText("Temperature unavailable"), style = MaterialTheme.typography.displaySmall)
                Text(uiText(weatherLabel(fact.weatherCode)), style = MaterialTheme.typography.titleMedium)
                fact.observedAt?.let { Text(uiText("Forecast time %1\$s", stamp(it, ZoneId.of(fact.place.zoneId))), style = MaterialTheme.typography.bodySmall) }
                Text(fact.place.zoneId, style = MaterialTheme.typography.bodySmall)
            }
            items(fact.days, key = { it.date.toString() }) { day ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(day.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)), style = MaterialTheme.typography.titleMedium)
                    Text(uiText(weatherLabel(day.weatherCode)))
                    Text(uiText("Low %1\$s · High %2\$s", temperature(day.minimumC) ?: "—", temperature(day.maximumC) ?: "—"))
                    day.precipitationProbability?.let { Text(uiText("%1\$d%% chance of precipitation", it)) }
                }
            }
            item { Text(fact.attribution, style = MaterialTheme.typography.labelLarge); Text(fact.attributionUrl, style = MaterialTheme.typography.bodySmall) }
        }
        is CollectorPayload.Warnings -> {
            val warnings = payload.items.filter { it.expires == null || it.expires > now }
            if (warnings.isEmpty()) item { Text(uiText("No unexpired notices in the saved snapshot. This does not confirm that no warnings exist.")) }
            items(warnings, key = { it.id }) { warning ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(warning.title, style = MaterialTheme.typography.titleLarge)
                    warning.area?.let { Text(it) }; warning.severity?.let { Text(it) }
                    Text(warning.text); Text(warning.publisher, style = MaterialTheme.typography.labelLarge)
                    warning.expires?.let { Text(uiText("Expires %1\$s", stamp(it))) }
                    warning.url?.takeIf { !kids }?.let { url -> TextButton({ onExternal(url) }) { Text(uiText("Open publisher in browser")) } }
                }
            }
        }
        is CollectorPayload.Calendar -> {
            if (itemId != null) {
                val selected = SavedCalendarSelection.decode(itemId)?.resolve(state)
                item {
                    if (selected == null) Text(uiText("This saved occurrence changed or is no longer available. Open the source to review its current calendar."))
                    else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(uiText("Selected occurrence"), style = MaterialTheme.typography.labelLarge)
                        Text(selected.record.title, style = MaterialTheme.typography.headlineMedium)
                        val zone = ZoneId.of(requireNotNull(SavedCalendarSelection.decode(itemId)).zoneId)
                        Text(stamp(selected.start, zone) + " · " + zone.id)
                        selected.end?.let { Text(uiText("Ends %1\$s", stamp(it, zone))) }
                        if (selected.record.location.isNotBlank()) Text(selected.record.location)
                        if (selected.record.details.isNotBlank()) Text(selected.record.details)
                        selected.record.sourceUrl?.takeIf { !kids }?.let { url -> TextButton({ onExternal(url) }) { Text(uiText("Open publisher in browser")) } }
                    }
                }
            }
            if (payload.preview.records.isEmpty()) item { Text(uiText("No supported events in this saved calendar.")) }
            items(payload.preview.records, key = { it.id }) { event ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(event.title, style = MaterialTheme.typography.titleLarge)
                    if (event.status == OrganizerStatus.CANCELLED) Text(uiText("Cancelled event"), style = MaterialTheme.typography.labelLarge)
                    event.time?.let { CalendarTime(it) }
                    event.repeat?.let { repeat ->
                        Text(uiText("Repeating series · %1\$s · interval %2\$d", uiText(when (repeat.frequency) {
                            RepeatFrequency.DAILY -> "Daily"; RepeatFrequency.WEEKLY -> "Weekly"; RepeatFrequency.MONTHLY -> "Monthly"; RepeatFrequency.YEARLY -> "Yearly"
                        }), repeat.interval), style = MaterialTheme.typography.labelLarge)
                        repeat.count?.let { Text(uiText("Occurrences · %1\$d", it)) }
                        repeat.until?.let { Text(uiText("Repeats until %1\$s", stamp(it, ZoneId.of(requireNotNull(event.time).zoneId)))) }
                        Text(uiText("The date above is the series start. Saved occurrence changes are listed below."), style = MaterialTheme.typography.bodySmall)
                    }
                    if (event.location.isNotBlank()) Text(event.location)
                    if (event.details.isNotBlank()) Text(event.details)
                    event.sourceUrl?.takeIf { !kids }?.let { url -> TextButton({ onExternal(url) }) { Text(uiText("Open publisher in browser")) } }
                }
            }
            val parents = payload.preview.records.associateBy { it.id }
            items(payload.preview.exceptions, key = { "exception:${it.recordId}@${it.originalStart}" }) { changed ->
                val parent = parents[changed.recordId]
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(changed.replacementTitle ?: parent?.title.orEmpty(), style = MaterialTheme.typography.titleMedium)
                    Text(uiText(if (changed.disposition == OccurrenceDisposition.CANCELLED) "Cancelled occurrence" else "Changed occurrence"), style = MaterialTheme.typography.labelLarge)
                    Text(uiText("Original occurrence %1\$s", stamp(changed.originalStart, ZoneId.of(parent?.time?.zoneId ?: "UTC"))))
                    changed.replacementTime?.let { CalendarTime(it) }
                    (changed.replacementLocation ?: parent?.location)?.takeIf { it.isNotBlank() }?.let { Text(it) }
                    (changed.replacementDetails ?: parent?.details)?.takeIf { it.isNotBlank() }?.let { Text(it) }
                    val url = changed.sourceUrlOverride ?: parent?.sourceUrl
                    url?.takeIf { it.isNotBlank() && !kids }?.let { TextButton({ onExternal(it) }) { Text(uiText("Open publisher in browser")) } }
                }
            }
            if (payload.preview.issues.isNotEmpty()) item { Text(uiText("Some calendar entries need review and were omitted. The snapshot does not include unsupported recurrence rules.")) }
        }
        is CollectorPayload.Local -> {
            if (payload.items.isEmpty()) item { Text(uiText("No local information is available from this source yet.")) }
            items(payload.items, key = { it.id }) { fact ->
                Surface(onClick = { onLocal(fact) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp)) { Text(fact.title, style = MaterialTheme.typography.titleMedium); Text(fact.summary) }
                }
            }
        }
    }
}

@Composable
private fun CalendarTime(time: OrganizerTime) {
    Text(time.start.toString().replace('T', ' ') + " · " + time.zoneId)
    if (time.allDay) Text(uiText("All day · end date is exclusive"), style = MaterialTheme.typography.bodySmall)
    if (time.followDeviceZone) Text(uiText("Follows the phone time zone"), style = MaterialTheme.typography.bodySmall)
    time.end?.let { Text(uiText("Ends %1\$s", it.toString().replace('T', ' '))) }
}

/** Open-Meteo WMO mapping, verified against https://open-meteo.com/en/docs on 2026-11-01. */
internal fun weatherLabel(code: Int?) = when (code) {
    0 -> "Clear sky"; 1 -> "Mainly clear"; 2 -> "Partly cloudy"; 3 -> "Overcast"; 45, 48 -> "Fog"
    51, 53, 55 -> "Drizzle"; 56, 57 -> "Freezing drizzle"; 61, 63 -> "Rain"; 65 -> "Heavy rain"
    66, 67 -> "Freezing rain"; 71, 73, 75, 77 -> "Snow"; 80, 81, 82 -> "Rain showers"
    85, 86 -> "Snow showers"; 95, 97 -> "Thunderstorm"; 96, 99 -> "Thunderstorm with hail"
    else -> "Weather condition unavailable"
}
