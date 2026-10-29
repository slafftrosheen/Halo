package com.halo.app.ui.screens.notebook

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import com.halo.app.language.uiText
import com.halo.runtime.cognition.memory.MemoryScope
import java.time.ZoneId
import java.util.Locale

/** Render-only metadata. The saved fact, scope names and evidence are never rewritten. */
internal data class MemoryDetailCopy(
    val kind: String, val origin: String, val confirmation: String, val confidence: String,
    val scope: String, val usage: String, val lastConfirmed: String?,
)

@Composable
internal fun localizedMemoryDate(at: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ENGLISH
    // This formatter owns Today/Yesterday and calendar dates; no source text enters lookup.
    return uiText(MemoryPresentation.date(at, now, zone, locale))
}

@Composable
internal fun memoryDetailCopy(item: MemoryUiItem, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): MemoryDetailCopy =
    MemoryDetailCopy(
        kind = uiText(item.kindLabel),
        origin = uiText(item.originLabel),
        confirmation = uiText(item.confirmationLabel),
        confidence = uiText(item.confidenceLabel),
        scope = when (item.record.scope) {
            MemoryScope.GLOBAL -> uiText("Everywhere")
            MemoryScope.CONVERSATION -> uiText("Temporary conversation context")
            // Existing strings include arbitrary resolved project/device/object names.
            else -> item.scopeLabel
        },
        usage = if (item.record.usageCount == 0L) uiText("Not used yet") else item.usageSummary,
        lastConfirmed = item.record.lastConfirmedAt?.let { uiText("Last confirmed %1\$s", localizedMemoryDate(it, now, zone)) },
    )
