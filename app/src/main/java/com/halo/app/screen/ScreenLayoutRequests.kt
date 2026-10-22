package com.halo.app.screen

/** Bounded local commands. Unknown requests remain unsupported, never guessed or executed. */
internal object ScreenLayoutRequests {
    data class Plan(val changes: List<LayoutChange>, val researchQuery: String? = null)
    private val cardAliases = mapOf(
        "clock" to listOf("clock", "time", "date"),
        "weather" to listOf("weather", "forecast", "laikapstākļi", "laika apstākļi", "погода", "прогноз", "pogoda", "prognoza"),
        "calendar" to listOf("calendar", "календари", "календарь", "kalendārs", "kalendarz"),
        "tasks" to listOf("tasks", "task list", "to do", "to-do", "todos", "darbi", "uzdevumi", "задачи", "zadania"),
        "notebook" to listOf("notebook", "notes", "saved work", "piezīmes", "klade", "блокнот", "заметки", "notatki"),
        "study" to listOf("study", "learning", "mācības", "учёба", "учеба", "nauka"),
        "automation_result" to listOf("routine results", "automation results", "routine", "automātika", "процедуры", "rutyny"),
        "halo_activity" to listOf("halo activity", "what halo did", "activity"),
        "interests" to listOf("reading", "interests", "intereses", "интересы", "zainteresowania"),
    )

    fun propose(text: String, names: Map<String, String>): LayoutChange? {
        val request = text.trim().trimEnd('.', '!', '?').lowercase()
        val commands = listOf(
            listOf("add", "добавь", "добавить", "pievieno", "pievienot", "dodaj") to "add",
            listOf("hide", "remove", "скрой", "убери", "paslēp", "noņemt", "ukryj", "usuń") to "hide",
            listOf("enlarge", "увеличь", "palielini", "powiększ", "zwiększ") to "large",
            listOf("move to top", "в начало", "pārvieto augšā", "przenieś na górę", "przesuń na górę") to "top",
        )
        for ((verbs, command) in commands) for (verb in verbs) {
            if (!request.startsWith("$verb ")) continue
            val noun = request.removePrefix("$verb ").trim()
            val type = names.entries.singleOrNull { (_, label) -> label.lowercase() == noun }?.key ?: return null
            if (type !in ScreenWidgets.types) return null
            return when (command) {
                "add" -> LayoutChange.Add(type, emptyMap())
                "hide" -> LayoutChange.Remove(type)
                "large" -> LayoutChange.Resize(type, CardSize.EXPANDED)
                else -> LayoutChange.Move(type, 0)
            }
        }
        return null
    }

    /**
     * Parses a bounded multi-card composition request. It only adds registered native cards;
     * a research topic is handed to the existing grounded-search flow, never answered here.
     */
    fun compose(text: String, names: Map<String, String>): Plan? {
        propose(text, names)?.let { return Plan(listOf(it)) }
        val normalized = normalize(text)
        val prefixes = listOf(
            "build my screen with", "build screen with", "create my screen with", "create screen with",
            "make my screen with", "make screen with", "добавь на экран", "собери экран с",
            "создай экран с", "izveido ekrānu ar", "pievieno ekrānam", "izveido manu ekrānu ar",
            "zbuduj ekran z", "dodaj na ekran",
        )
        val prefix = prefixes.firstOrNull { normalized.startsWith("$it ") } ?: return null
        val body = normalized.removePrefix(prefix).trim()
        if (body.isBlank()) return null

        val researchTerms = listOf(
            "research articles", "research", "articles", "pētījumi", "pētniecība", "raksti",
            "исследования", "исследование", "статьи", "badania", "artykuły", "artykuly",
        )
        val hasResearch = researchTerms.any { containsPhrase(body, it) }
        val recognized = names.entries.flatMap { (type, label) ->
            listOf(normalize(label)) + cardAliases[type].orEmpty()
        }.filter { phrase -> phrase.isNotBlank() && containsPhrase(body, phrase) }
            .flatMap { phrase -> names.entries.filter { (type, label) ->
                normalize(label) == phrase || phrase in cardAliases[type].orEmpty()
            }.map { it.key } }.toMutableSet()
        if (hasResearch) recognized += "research"
        if (recognized.isEmpty()) return null

        val additions = recognized.sortedBy { type ->
            (listOfNotNull(names[type]?.let(::normalize)) + cardAliases[type].orEmpty())
                .map { body.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: Int.MAX_VALUE
        }.map { type ->
            if (type !in ScreenWidgets.types) return null
            LayoutChange.Add(type, emptyMap())
        }
        val query = if (hasResearch) extractResearchQuery(body, names, researchTerms) else null
        return Plan(additions, query)
    }

    fun apply(layout: ScreenLayout, plan: Plan): ScreenLayout? = plan.changes.fold(layout) { current, change ->
        apply(current, change) ?: if (change is LayoutChange.Add && current.cards.any { it.widgetType == change.widgetType }) current else return null
    }.takeIf { it.cards != layout.cards || !plan.researchQuery.isNullOrBlank() }

    private fun extractResearchQuery(body: String, names: Map<String, String>, researchTerms: List<String>): String? {
        var query = body
        (names.values.map(::normalize) + researchTerms + cardAliases.values.flatten())
            .distinct().sortedByDescending { it.length }.forEach { phrase ->
            if (phrase.isNotBlank()) query = replacePhrase(query, phrase, " ")
        }
        val ignored = setOf(
            "and", "with", "about", "for", "my", "the", "a", "an", "screen", "build", "create", "make", "please",
            "с", "и", "на", "про", "экран", "экрану", "пожалуйста", "добавь", "собери", "создай",
            "un", "ar", "par", "lūdzu", "ekrānu", "ekrānam", "izveido", "pievieno",
            "i", "z", "na", "proszę", "ekran", "zbuduj", "dodaj",
        )
        val tokens = query.split(Regex("\\s+")).map { it.trim(',', '.', ';', ':', '!', '?', '"', '\'', '(', ')') }
            .filter { it.length > 1 && it !in ignored }
        return tokens.joinToString(" ").take(200).takeIf { it.isNotBlank() }
    }

    private fun normalize(value: String): String = value.lowercase()
        .replace('&', ' ')
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private fun containsPhrase(text: String, phrase: String): Boolean =
        Regex("(?<![\\p{L}\\p{N}])${Regex.escape(phrase)}(?![\\p{L}\\p{N}])").containsMatchIn(text)

    private fun replacePhrase(text: String, phrase: String, replacement: String): String =
        Regex("(?<![\\p{L}\\p{N}])${Regex.escape(phrase)}(?![\\p{L}\\p{N}])").replace(text, replacement)

    fun apply(layout: ScreenLayout, change: LayoutChange): ScreenLayout? {
        val cards = layout.cards.toMutableList()
        when (change) {
            is LayoutChange.Add -> {
                if (change.widgetType !in ScreenWidgets.types || cards.size >= 24 || cards.any { it.widgetType == change.widgetType }) return null
                cards.add(ScreenCard(change.widgetType, null, CardSize.STANDARD, false))
            }
            is LayoutChange.Remove -> { if (!cards.removeAll { it.widgetType == change.widgetType }) return null }
            is LayoutChange.Resize -> {
                val index = cards.indexOfFirst { it.widgetType == change.widgetType }
                if (index < 0 || cards[index].size == change.size) return null
                cards[index] = cards[index].copy(size = change.size)
            }
            is LayoutChange.Move -> {
                val index = cards.indexOfFirst { it.widgetType == change.widgetType }
                if (index < 0 || change.toIndex !in cards.indices || index == change.toIndex) return null
                cards.add(change.toIndex, cards.removeAt(index))
            }
        }
        return layout.copy(cards = cards)
    }
}
