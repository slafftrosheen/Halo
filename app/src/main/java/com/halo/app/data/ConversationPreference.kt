package com.halo.app.data

internal data class ConversationPreference(val subject: String, val text: String) {
    fun applies(query: String): Boolean = when (subject) {
        "units" -> unitsRequest.containsMatchIn(query) || memoryQuestion.containsMatchIn(query)
        else -> query.isNotBlank()
    }
}

/** Pulls a directly stated preference from the newest eligible transcript. No duplicate profile record is saved. */
internal fun spokenPreference(userText: String): ConversationPreference? {
    val text = userText.trim()
    if (text.length !in 10..180 || !preferencePrefix.containsMatchIn(text) || text.any { it in "?\n\r:;\"“«" }) return null
    val value = preferencePrefix.replaceFirst(text, "").trim().trimEnd('.')
    return when {
        unitsPreference.matches(value) -> ConversationPreference("units", text)
        stylePreference.matches(value) -> ConversationPreference("response length", text)
        else -> null
    }
}

private val preferencePrefix = Regex("^(i (now |generally )?prefer|я (теперь |обычно )?предпочитаю)\\s", RegexOption.IGNORE_CASE)
private val unitsPreference = Regex("celsius|fahrenheit|metric units?|imperial units?|градус[а-яё]* (цельсия|фаренгейта)|метрическ[а-яё]* единиц[а-яё]*", RegexOption.IGNORE_CASE)
private val stylePreference = Regex("(short|brief|concise|long|detailed) (answers|replies|responses)|(кратк|коротк|подробн)[а-яё]+ ответы", RegexOption.IGNORE_CASE)
private val unitsRequest = Regex("\\b(temperature|weather|forecast|unit|units|degrees|celsius|fahrenheit|distance|weight|meters?|miles?|kg|температур[а-яё]*|погод[а-яё]*|прогноз[а-яё]*|градус[а-яё]*|расстояни[а-яё]*|вес[а-яё]*)\\b", RegexOption.IGNORE_CASE)
private val memoryQuestion = Regex("what (do you |can you )?remember|что ты помнишь|что ты запомнил", RegexOption.IGNORE_CASE)
