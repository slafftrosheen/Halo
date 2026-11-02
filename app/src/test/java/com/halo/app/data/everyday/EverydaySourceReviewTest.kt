package com.halo.app.data.everyday

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.halo.app.everyday.collectors.*
import com.halo.app.everyday.organizer.*
import com.halo.app.ui.everyday.EverydayBriefEntry
import com.halo.app.ui.shell.HaloScreenRequests
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class EverydaySourceReviewTest {
    private val now = Instant.parse("2026-11-01T12:00:00Z")
    private val source = CollectorSource("source", "Source", "Publisher", CollectorKind.FEED, SourceCategory.WORLD,
        "en", "https://example.org/feed", selected = true)
    private fun state(payload: CollectorPayload) = CollectorState(source,
        CollectorSnapshot(source.id, source.version, payload, "revision1", now, now, now.plusSeconds(3600)),
        CollectorAttempt(source.id, now, CollectorAttemptState.CURRENT))

    @Test fun reviewedArticleCannotRetargetToTheSameIdWithNewContentOrRevokedSource() {
        val article = ArticleFact("article", "Reviewed title", "Text", "https://example.org/old", "Publisher", now, "en")
        val current = state(CollectorPayload.Articles(listOf(article)))
        val review = ArticleReminderReview(source.id, source.version, "revision1", article)
        assertEquals(article, review.requireCurrent(current))
        assertEquals(article, review.requireCurrent(current.copy(source = source.copy(muted = true))))
        val alternatives = listOf(null, current.copy(source = source.copy(selected = false)), current.copy(source = source.copy(enabled = false)),
            current.copy(source = source.copy(version = 2)), current.copy(snapshot = current.snapshot!!.copy(contentRevision = "revision2")),
            state(CollectorPayload.Articles(listOf(article.copy(title = "Replaced", url = "https://example.org/new")))), state(CollectorPayload.Articles(emptyList())))
        alternatives.forEach { changed -> assertTrue(runCatching { review.requireCurrent(changed) }.exceptionOrNull() is ArticleReviewChanged) }
    }

    @Test fun calendarSelectionRetainsMovedOccurrenceAndRefusesChangedOrCancelledSnapshot() {
        val event = OrganizerRecord("calendar-event", OrganizerKind.EVENT, "Original", time = OrganizerTime(LocalDateTime.parse("2026-11-01T12:00"),
            LocalDateTime.parse("2026-11-01T13:00"), "UTC"), repeat = RepeatRule(RepeatFrequency.DAILY, count = 4), createdAt = now)
        val moved = OccurrenceOverride(event.id, now, OccurrenceDisposition.REPLACED,
            OrganizerTime(LocalDateTime.parse("2026-11-03T18:00"), LocalDateTime.parse("2026-11-03T19:00"), "Asia/Tokyo"),
            replacementTitle = "Moved title", replacementDetails = "Moved details", sourceUrlOverride = "https://example.org/moved")
        val current = state(CollectorPayload.Calendar(CalendarImportPreview(listOf(event), listOf(moved), emptyList())))
        val selection = SavedCalendarSelection(event.id, now, "revision1", "Europe/Riga")
        assertEquals(selection, SavedCalendarSelection.decode(selection.encode()))
        val actual = selection.resolve(current)!!
        assertEquals("Moved title", actual.record.title); assertEquals("Moved details", actual.record.details)
        assertEquals("https://example.org/moved", actual.record.sourceUrl)
        assertEquals(Instant.parse("2026-11-03T09:00:00Z"), actual.start)
        assertNull(selection.resolve(current.copy(snapshot = current.snapshot!!.copy(contentRevision = "revision2"))))
        assertNull(selection.resolve(state(CollectorPayload.Calendar(CalendarImportPreview(listOf(event.copy(status = OrganizerStatus.CANCELLED)), listOf(moved), emptyList())))))
        assertNull(selection.resolve(state(CollectorPayload.Calendar(CalendarImportPreview(listOf(event), listOf(moved.copy(disposition = OccurrenceDisposition.CANCELLED)), emptyList())))))
        assertNull(SavedCalendarSelection.decode("calendar:not-json"))
    }

    @Test fun twoBriefNoticesRetainDifferentDocumentsAndOnlyTypedRoutesCanBeClaimed() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val morning = "a".repeat(64); val evening = "b".repeat(64)
        val first = EverydayBriefEntry.pending(context, morning)
        val second = EverydayBriefEntry.pending(context, evening)
        assertNotEquals(first, second)
        assertEquals(morning, shadowOf(first).savedIntent.getStringExtra(EverydayBriefEntry.EXTRA_BRIEF_ID))
        assertEquals(evening, shadowOf(second).savedIntent.getStringExtra(EverydayBriefEntry.EXTRA_BRIEF_ID))
        listOf(morning, evening).forEach { id ->
            val route = EverydayBriefEntry.route(id)!!
            assertEquals(route, HaloScreenRequests.claim(MutableStateFlow(route), true))
        }
        listOf("screen/brief?briefId=missing", "screen/brief?briefId=$morning&sourceId=private", "screen/brief?briefId=%61" + "a".repeat(63)).forEach {
            assertNull(HaloScreenRequests.claim(MutableStateFlow(it), true))
        }
        assertNull(EverydayBriefEntry.route("../private"))
    }
}
