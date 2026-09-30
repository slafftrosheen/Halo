package com.halo.runtime.cognition.ui

import com.halo.sdk.runtime.ScreenContextSnapshot

data class BoundedUiSearchResult(val snapshot: ScreenContextSnapshot, val matches: UiSearchResult,
    val scrolls: Int, val failure: UiFailure? = null)

/** The caller supplies the shared preflight/authorization/guarded scroll path, never a raw gesture. */
class BoundedUiSearchCoordinator {
    suspend fun search(initial: ScreenContextSnapshot, query: String, containerFingerprint: String,
        maxScrolls: Int, scroll: suspend (ScreenContextSnapshot, String, Int) -> Pair<ScreenContextSnapshot?, UiFailure?>
    ): BoundedUiSearchResult {
        var snapshot = initial
        var count = 0
        while (true) {
            val found = UiTargetSearch.find(snapshot, query)
            if (found.ambiguous || found.candidates.isNotEmpty() || count >= maxScrolls.coerceIn(0, 3))
                return BoundedUiSearchResult(snapshot, found, count)
            if (snapshot.packageName != initial.packageName)
                return BoundedUiSearchResult(snapshot, found, count, UiFailure.SNAPSHOT_SUPERSEDED)
            val container = snapshot.nodes.singleOrNull { it.stableFingerprint == containerFingerprint &&
                it.scrollable && it.enabled && !it.sensitive }
                ?: return BoundedUiSearchResult(snapshot, found, count, UiFailure.AMBIGUOUS_TARGET)
            val token = container.token ?: return BoundedUiSearchResult(snapshot, found, count, UiFailure.TOKEN_NOT_FOUND)
            val (fresh, failure) = scroll(snapshot, token, count)
            if (failure != null || fresh == null)
                return BoundedUiSearchResult(snapshot, found, count, failure ?: UiFailure.OBSERVATION_REQUIRED)
            snapshot = fresh
            count++
        }
    }
}
