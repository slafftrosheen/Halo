package com.halo.app.data.routine

import com.halo.app.data.workspace.privateLibraryClaim

private val automationReference = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}|\\bmem_\\d+\\b", RegexOption.IGNORE_CASE)

/** Definitions may carry canonical IDs inside JSON arguments or an interpolated message. */
internal fun automationClaimKeys(identity: List<String>, vararg content: String?): List<String> = buildSet {
    addAll(identity)
    for (text in content) {
        if (text != null) addAll(automationReference.findAll(text).map { it.value })
    }
}.map(::privateLibraryClaim)
