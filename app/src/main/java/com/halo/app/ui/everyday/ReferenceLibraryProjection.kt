package com.halo.app.ui.everyday

import com.halo.app.data.knowledge.ReferenceEntryEntity

/** A late completed read cannot revive content after its visible source/family scope changed. */
private fun referenceRowVisible(state: ReferenceLibrariesState, entry: ReferenceEntryEntity, version: Long?): Boolean =
    state.libraries.any { row -> row.source.id == entry.sourceId && row.source.version == version && row.library.generation == entry.generation &&
        row.source.selected && row.source.enabled && !row.source.muted && (!state.kids || row.source.childApproved) }

internal fun referenceVisibleEntry(state: ReferenceLibrariesState, familyReady: Boolean, familyEnabled: Boolean): ReferenceEntryEntity? =
    state.entry?.takeIf { familyReady && familyEnabled == state.kids && referenceRowVisible(state, it, state.entrySourceVersion) }

internal fun referenceVisibleHits(state: ReferenceLibrariesState, familyReady: Boolean, familyEnabled: Boolean): List<ReferenceEntryEntity> =
    if (!familyReady || familyEnabled != state.kids) emptyList() else state.results?.let { page ->
        page.entries.filter { referenceRowVisible(state, it, page.sources[it.sourceId]?.version) }
    }.orEmpty()
