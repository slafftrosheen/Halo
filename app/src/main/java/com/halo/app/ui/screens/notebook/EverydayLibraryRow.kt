package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.halo.app.everyday.presentation.EverydayLibraryEntry
import com.halo.app.everyday.briefing.*
import com.halo.app.language.uiText
import java.time.*
import java.time.format.*

@Composable
internal fun EverydayLibraryRow(entry: EverydayLibraryEntry,open: ()->Unit) {
    val locale=LocalConfiguration.current.locales[0]
    val title=if(entry.kind=="BRIEF") BriefPresentation.title(BriefPeriod.valueOf(entry.code),locale) else entry.title
    val kind=when(entry.kind) {"ORGANIZER" -> "Organizer";"BRIEF" -> "Daily brief";"CLOCK" -> "World clock";else -> "Saved source"}
    val detail=when(entry.kind) {
        "BRIEF" -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(entry.updatedAt))
        "ORGANIZER" -> runCatching {DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).format(LocalDateTime.parse(entry.detail.substringBefore(" · ")))+" · "+entry.detail.substringAfter(" · ")}.getOrDefault("")
        else -> entry.detail
    }
    Surface(onClick=open,shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainerLow,
        modifier=Modifier.fillMaxWidth().heightIn(min=88.dp)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(uiText(kind),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title,style=MaterialTheme.typography.titleMedium)
            if(detail.isNotBlank()) Text(detail,style=MaterialTheme.typography.bodyMedium)
            Text(uiText("Manage in its instrument"),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
        }
    }
}
