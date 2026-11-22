package com.halo.app.everyday.briefing

import com.halo.app.everyday.collectors.CollectorAttempt
import com.halo.app.everyday.collectors.CollectorAttemptState
import com.halo.app.everyday.collectors.CollectorKind
import com.halo.app.everyday.collectors.CollectorPayload
import com.halo.app.everyday.collectors.CollectorSnapshot
import com.halo.app.everyday.collectors.CollectorSource
import com.halo.app.everyday.collectors.CollectorState
import com.halo.app.everyday.collectors.SourceCategory
import com.halo.app.everyday.presentation.EverydayCardConfig
import com.halo.app.everyday.presentation.EverydayCardKind
import com.halo.app.everyday.presentation.EverydayCardSettings
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class TodayNewsHistoryTest {
    @Test fun savedReportSurvivesFeedRolloverWhileSourceRemainsAuthorized() {
        val at = Instant.parse("2026-11-23T08:00:00Z")
        val source = CollectorSource("starter:public", "Publisher", "Publisher", CollectorKind.FEED,
            SourceCategory.WORLD, "en", "https://example.org/feed", selected = true)
        val newSnapshot = CollectorSnapshot(source.id, source.version, CollectorPayload.Articles(emptyList()),
            "replacement", at.plusSeconds(3600), at.plusSeconds(3600), at.plusSeconds(7200))
        val row = CollectorState(source, newSnapshot, CollectorAttempt(source.id, at.plusSeconds(3600), CollectorAttemptState.CURRENT))
        val report = TodayNewsReport(at, "en", "Saved report with attribution [1].",
            listOf(TodayNewsSource(1, source.id, source.version, "older-article", "Publisher", "original")))

        assertTrue(report.isVisible(listOf(row), "en", at.plusSeconds(3800)))
        assertFalse(report.isVisible(listOf(row.copy(source = source.copy(selected = false))), "en", at.plusSeconds(3800)))
        assertFalse(report.isVisible(listOf(row.copy(snapshot = null)), "en", at.plusSeconds(3800)))
        assertFalse(report.isVisible(listOf(row), "ru", at.plusSeconds(3800)))
        assertFalse(report.isVisible(listOf(row), "en", at.plusSeconds(48 * 3600)))
    }

    @Test fun launcherPagingIsOptInAndSurvivesTheExistingSettingsCodec() {
        val legacy = EverydayCardConfig.from(mapOf("kind" to "TODAY"), EverydayCardKind.TODAY)
        assertFalse(legacy.widgetAutoPage)
        val updated = EverydayCardSettings.patch(legacy, mapOf("widgetAutoPage" to true))
        assertTrue(EverydayCardConfig.from(updated.fields(), EverydayCardKind.TODAY).widgetAutoPage)
    }
}
