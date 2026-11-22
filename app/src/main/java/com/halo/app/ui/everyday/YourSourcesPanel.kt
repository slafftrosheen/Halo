package com.halo.app.ui.everyday

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.collectors.CollectorKind
import com.halo.app.everyday.collectors.CollectorPayload
import com.halo.app.language.uiText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
internal fun YourSourcesPanel(onOpen: (String) -> Unit) {
    val states by Graph.collectorRepository.observe().collectAsStateWithLifecycle(emptyList())
    val pictures by Graph.articlePictureSettings.visible.collectAsStateWithLifecycle()
    val kids by Graph.kidsPreferences.enabled.collectAsStateWithLifecycle()
    val ready by Graph.kidsPreferences.loadedState.collectAsStateWithLifecycle()
    if (!ready || kids) return
    val sources = remember(states) {
        states.asSequence().filter {
            it.source.kind == CollectorKind.FEED && it.source.selected && it.source.enabled && !it.source.muted &&
                it.snapshot?.sourceVersion == it.source.version
        }.flatMap { row ->
            (row.snapshot?.payload as? CollectorPayload.Articles)?.items.orEmpty().asSequence().map { row.source.id to it }
        }.sortedByDescending { (_, article) -> article.publishedAt ?: Instant.MIN }.distinctBy { (_, article) ->
            article.url ?: article.id
        }.take(2).toList()
    }
    if (sources.isEmpty()) return
    val locale = LocalConfiguration.current.locales[0]
    val date = remember(locale) { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).withZone(ZoneId.systemDefault()) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(uiText("Your sources"), style = MaterialTheme.typography.titleMedium)
        sources.forEach { (source, article) ->
            Surface(onClick = { onOpen(EverydayRoutes.source(source, article.id)) }, shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    ArticleSourcePreview(article, pictures, kids = false)
                    Text(article.title, style = MaterialTheme.typography.titleMedium)
                    Text(listOfNotNull(article.publisher, article.publishedAt?.let(date::format)).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        TextButton({ onOpen(EverydayRoutes.SOURCES) }) { Text(uiText("Choose sources")) }
    }
}

/** An explicit local navigation question with no model or external-source dependency. */
internal fun isDayReportQuestion(text: String): Boolean =
    text.trim().lowercase(Locale.ROOT).trimEnd('?', '.', '!', ' ').replace(Regex("\\s+"), " ") in setOf(
        "how was my day", "show me my day", "show your day", "kā pagāja mana diena", "kā pagāja diena",
        "как прошёл мой день", "как прошел мой день", "jak minął mój dzień", "jak mi minął dzień",
    )
