package com.halo.app.everyday.briefing

import com.halo.app.ui.components.HaloReadAloudController
import com.halo.sdk.provider.*
import java.time.Clock
import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Uses the existing sentence player/audio owner; it never creates a second speech provider pipeline. */
class BriefReadAloud(private val briefs: EverydayBriefService, private val reader: HaloReadAloudController,
    private val policy: RemoteDispatchPolicy, private val provider: () -> String, private val power: () -> Boolean,
    private val locale: () -> Locale, private val scope: CoroutineScope, private val clock: Clock = Clock.systemUTC()) {
    private var monitor: Job? = null
    private var sessionToken: String? = null
    suspend fun read(documentId: String) {
        monitor?.cancel()
        val revision = policy.revision
        val document = briefs.visible(documentId) ?: error("Brief no longer exists")
        check(policy.revision == revision) { "Mode changed; choose Read aloud again" }
        val categories = buildSet {
            add(RemoteDataCategory.TEXT)
            (document.facts + document.changes.mapNotNull { it.fact }).forEach { fact -> when (fact.kind) {
                BriefFactKind.EVENT, BriefFactKind.TASK, BriefFactKind.REMINDER, BriefFactKind.ALARM,
                BriefFactKind.TIMER, BriefFactKind.NOTE -> add(RemoteDataCategory.WORKSPACE)
                BriefFactKind.ACTIVITY, BriefFactKind.NOTIFICATION, BriefFactKind.ATTENTION -> add(RemoteDataCategory.RECENT_EVENTS)
                BriefFactKind.DEVICE -> add(RemoteDataCategory.DEVICE_STATE)
                BriefFactKind.WEATHER -> add(RemoteDataCategory.LOCATION)
                else -> Unit
            } }
        }
        val language = locale()
        val readingId = "brief:${document.id}"
        val token = reader.start(readingId, BriefPresentation.spoken(document, clock.instant(), power(), language), provider(),
            cloudApproved = power(), language = language.toLanguageTag(), remoteProvenance = RemoteDispatchProvenance(categories, revision),
            beforeSentence = { policy.revision == revision && briefs.visible(document.id) == document })
        sessionToken = token
        monitor = scope.launch {
            // Wait for this sentence player's state, then stop monitoring when another reader replaces it.
            if (withTimeoutOrNull(10_000) { reader.state.first { it?.sessionToken == token } } == null) return@launch
            try {
                combine(briefs.observeVisible(document.id), policy.revisions, reader.state) { current, actualRevision, playing ->
                    (current == document && actualRevision == revision) to (playing?.sessionToken == token)
                }.takeWhile { it.second }.collect { (permitted, _) -> if (!permitted) reader.stopSession(token) }
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) { reader.stopSession(token) }
        }
    }
    fun stop() { monitor?.cancel(); monitor = null; sessionToken?.let(reader::stopSession); sessionToken = null }
}
