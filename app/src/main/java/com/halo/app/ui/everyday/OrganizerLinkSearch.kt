package com.halo.app.ui.everyday

/** Search the full eligible projection before bounding visible results; selected items stay reachable. */
internal fun organizerLinkSearch(choices: List<OrganizerLinkChoice>,query: String,selected: List<String>,limit: Int=90): List<OrganizerLinkChoice> {
    require(query.length<=200 && limit in 1..100)
    return choices.asSequence().filter {it.id in selected || it.searchText.contains(query,ignoreCase=true)}
        .distinctBy {it.id}.sortedBy {it.id !in selected}.take(limit).toList()
}
