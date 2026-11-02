package com.halo.app.screen

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.presentation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal data class EverydayScreenState(val snapshot: EverydaySnapshot? = null, val failed: Boolean = false)

/** Observed owner changes plus one visible-minute tick; no collectors or models are dispatched. */
@Composable
internal fun rememberEverydayScreenState(refresh: Int): State<EverydayScreenState> {
    val context=LocalContext.current.applicationContext
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    return produceState(EverydayScreenState(),context,lifecycle,refresh) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            merge(Graph.organizerRepository.observe().map { Unit },Graph.organizerRepository.observeReceipts().map { Unit },Graph.collectorRepository.observe().map { Unit },
                Graph.briefRepository.observe().map { Unit },Graph.briefRepository.observeSettings().map { Unit },
                Graph.worldClocks.observe().map { Unit },Graph.kidsPreferences.enabled.map { Unit },Graph.privacyModes.mode.map { Unit },
                flow { while (currentCoroutineContext().isActive) { emit(Unit); delay(60_000) } }).catch { value=EverydayScreenState(failed=true) }.collectLatest {
                try { value=EverydayScreenState(readEverydaySnapshot(context)) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { value=EverydayScreenState(failed=true) }
            }
        }
    }
}

internal fun everydayScreenDisplay(card: ScreenCard, state: EverydayScreenState, now: java.time.Instant,
    locale: java.util.Locale, resources: android.content.res.Resources): ScreenDisplay {
    val kind=requireNotNull(EverydayCards.screenKinds[card.widgetType])
    val snapshot=state.snapshot?.takeIf { Graph.kidsPreferences.loadedState.value && it.kids==Graph.kidsPreferences.enabled.value }
    val title=com.halo.app.language.resolveUiText(resources,EverydayCards.title(kind))
    if (snapshot == null) return ScreenDisplay(title,com.halo.app.language.resolveUiText(resources,
        if (state.failed) "Saved information could not be opened. Open Halo and try again." else "Loading"),
        com.halo.app.language.resolveUiText(resources,if (state.failed) "Needs attention" else "Loading"),null,sourceTitle=true,sourceSummary=true)
    val config=runCatching { EverydayCardConfig.from(card.config,kind).also { require(it.kind==kind) } }.getOrElse {
        return ScreenDisplay(title,com.halo.app.language.resolveUiText(resources,"Reconfigure this card to use its current settings."),
            com.halo.app.language.resolveUiText(resources,"Needs setup"),null,sourceTitle=true,sourceSummary=true)
    }
    val face=EverydayCards.project(config,snapshot.copy(now=now),locale,text={com.halo.app.language.resolveUiText(resources,it)})
    val details=(listOf(face.value)+face.rows.map { listOf(it.title,it.detail).filter(String::isNotBlank).joinToString(" · ") }+
        listOfNotNull(face.attribution)).joinToString("\n")
    return ScreenDisplay(face.title,details,face.status,"everyday",face.personal,face.observedAt,
        sourceTitle=true,sourceSummary=true,detailSummary=listOfNotNull(face.value,face.attribution).joinToString("\n"),showFullTimestamp=face.observedAt != null,
        continuation=ScreenContinuation.Everyday(face.target),everydayRows=face.rows)
}
