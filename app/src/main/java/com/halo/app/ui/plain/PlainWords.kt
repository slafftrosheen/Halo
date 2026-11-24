package com.halo.app.ui.plain

import com.halo.app.ui.adaptive.v13.ExperienceLevel

/**
 * halo 13.0 · plain words.
 * Source of truth: assets/plain/glossary-13.json (EN + RU).
 * - SIMPLE and STANDARD screens must not show a "tech word" from the glossary.
 * - PRO screens may, but always with the plain word on long-press ("What's this?").
 * - String keys starting with pro_ / dev_ / diag_ are Pro-only and exempt.
 */
data class GlossaryEntry(val tech: String, val plain: String, val plainRu: String, val explain: String, val explainRu: String)

data class Finding(val key: String, val word: String, val suggestion: String, val tooLong: Boolean)

class PlainWords(private val entries: List<GlossaryEntry>) {
    private val patterns = entries.associateWith { Regex("\\b${Regex.escape(it.tech)}s?\\b", RegexOption.IGNORE_CASE) }

    fun lint(key: String, text: String, level: ExperienceLevel = ExperienceLevel.STANDARD): List<Finding> {
        if (level == ExperienceLevel.PRO || EXEMPT.any { key.startsWith(it) }) return emptyList()
        val hits = patterns.filter { (_, rx) -> rx.containsMatchIn(text) }
            .map { (e, _) -> Finding(key, e.tech, e.plain, false) }
        val long = if (text.split(' ').size > MAX_WORDS) listOf(Finding(key, "", "split into two sentences or move to “Learn more”", true)) else emptyList()
        return hits + long
    }

    fun explain(word: String, ru: Boolean): String? =
        entries.firstOrNull { it.tech.equals(word, ignoreCase = true) }?.let { if (ru) it.explainRu else it.explain }

    companion object {
        val EXEMPT = listOf("pro_", "dev_", "diag_", "peripheral_")
        const val MAX_WORDS = 22   // one sentence a 12-year-old reads without stopping
    }
}
