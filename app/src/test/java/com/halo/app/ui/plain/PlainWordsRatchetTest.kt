package com.halo.app.ui.plain

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A ratchet, not a big-bang rewrite: today 553 of 8,299 English strings (6.7 %) contain a tech word
 * (repo 99aac2f, 23 Nov 2026). The number may only go down. Lower BASELINE in the same PR that fixes strings.
 */
class PlainWordsRatchetTest {
    private val BASELINE = 553
    private val tech = Regex("\\b(provider|model|runtime|diagnostics?|endpoint|receipts?|inference|tokens?|context|authority|grounded|on-device|policy|API|latency|fallback|configure|capabilit(y|ies)|payload)\\b", RegexOption.IGNORE_CASE)
    private val str = Regex("<string name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)

    @Test fun techWordsNeverGrow() {
        val hits = File("src/main/res/values").listFiles { f -> f.extension == "xml" }!!.flatMap { f ->
            str.findAll(f.readText()).filter { m ->
                PlainWords.EXEMPT.none { m.groupValues[1].startsWith(it) } && tech.containsMatchIn(m.groupValues[2])
            }.map { it.groupValues[1] }.toList()
        }
        assertTrue("Tech words grew: ${hits.size} > $BASELINE. Use assets/plain/glossary-13.json", hits.size <= BASELINE)
    }
}
