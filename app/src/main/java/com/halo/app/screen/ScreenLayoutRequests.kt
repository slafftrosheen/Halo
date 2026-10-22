package com.halo.app.screen

/** Bounded local commands. Unknown requests remain unsupported, never guessed or executed. */
internal object ScreenLayoutRequests {
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
