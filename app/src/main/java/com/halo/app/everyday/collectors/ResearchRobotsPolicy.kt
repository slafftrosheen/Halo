package com.halo.app.everyday.collectors

/** Bounded robots parser for the Halo product token. Unknown directives do not grant access. */
class ResearchRobotsPolicy private constructor(private val rules: List<Pair<String, Boolean>>, val crawlDelaySeconds: Long) {
    fun allows(encodedPathAndQuery: String): Boolean {
        val path = normalize(encodedPathAndQuery)
        val matches = rules.mapNotNull { (pattern, allow) ->
            val normalized = normalize(pattern)
            val anchored = normalized.endsWith('$')
            val body = if (anchored) normalized.dropLast(1) else normalized
            if (matches(body, path, anchored)) normalized.count { it != '*' && it != '$' } to allow else null
        }
        val length = matches.maxOfOrNull { it.first } ?: return true
        return matches.any { it.first == length && it.second }
    }
    companion object {
        // Glob matching avoids executing a site-supplied, potentially exponential regex.
        private fun matches(pattern: String, path: String, anchored: Boolean): Boolean {
            var p = 0; var at = 0; var star = -1; var retry = 0
            while (at < path.length) {
                if (p == pattern.length && !anchored) return true
                if (p < pattern.length && pattern[p] == '*') { star = p++; retry = at }
                else if (p < pattern.length && pattern[p] == path[at]) { p++; at++ }
                else if (star >= 0) { p = star + 1; at = ++retry }
                else return false
            }
            while (p < pattern.length && pattern[p] == '*') p++
            return p == pattern.length
        }
        private data class Group(val agents: MutableList<String> = mutableListOf(), val rules: MutableList<Pair<String, Boolean>> = mutableListOf(), var delay: Long = 0)
        fun parse(body: String): ResearchRobotsPolicy {
            require(body.toByteArray(Charsets.UTF_8).size <= 65536)
            val groups = mutableListOf<Group>(); var group = Group(); var hasRules = false
            body.lineSequence().take(5000).forEach { raw ->
                val line = raw.substringBefore('#').trim(); if (line.isBlank() || ':' !in line) return@forEach
                val field = line.substringBefore(':').trim().lowercase(java.util.Locale.ROOT)
                val value = line.substringAfter(':').trim(); require(value.length <= 2048)
                if (field == "user-agent") {
                    if (hasRules) { groups += group; group = Group(); hasRules = false }
                    group.agents += value.lowercase(java.util.Locale.ROOT)
                } else if (field in setOf("allow", "disallow") && group.agents.isNotEmpty()) {
                    hasRules = true
                    if (value.isNotEmpty()) { require(value.startsWith('/')); group.rules += value to (field == "allow") }
                } else if (field == "crawl-delay" && group.agents.isNotEmpty()) {
                    hasRules = true
                    group.delay = kotlin.math.ceil(value.toDouble().also { require(it.isFinite() && it >= 0) }).toLong()
                }
            }
            require(body.lineSequence().count() <= 5000)
            groups += group
            val exact = groups.filter { "halo" in it.agents }
            val selected = exact.ifEmpty { groups.filter { "*" in it.agents } }
            return ResearchRobotsPolicy(selected.flatMap { it.rules }, selected.maxOfOrNull { it.delay } ?: 0)
        }
        fun allowMissing() = ResearchRobotsPolicy(emptyList(), 0)
        private fun normalize(value: String): String = buildString {
            var i = 0
            while (i < value.length) {
                val char = value[i]
                if (char == '%') {
                    require(i + 2 < value.length)
                    val code = value.substring(i + 1, i + 3).toIntOrNull(16) ?: throw IllegalArgumentException("Invalid escape")
                    val decoded = code.toChar()
                    if (decoded.isLetterOrDigit() && code < 128 || decoded in "-._~") append(decoded)
                    else append('%').append(value.substring(i + 1, i + 3).uppercase(java.util.Locale.ROOT))
                    i += 3
                } else if (char.code > 127) {
                    val count = if (Character.isHighSurrogate(char) && i + 1 < value.length) 2 else 1
                    value.substring(i, i + count).toByteArray(Charsets.UTF_8).forEach { append("%%%02X".format(it.toInt() and 255)) }
                    i += count
                } else { append(char); i++ }
            }
        }
    }
}
