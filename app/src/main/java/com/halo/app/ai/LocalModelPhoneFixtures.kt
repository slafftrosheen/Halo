package com.halo.app.ai

import com.halo.sdk.provider.AIMessage
import com.halo.sdk.provider.AiModelRole
import com.halo.sdk.provider.Role

/** Synthetic, versioned diagnostic inputs. Never populated from a user's conversations. */
data class PhoneModelCase(val id: String, val role: AiModelRole, val prompt: String,
    val expected: String, val history: List<AIMessage> = emptyList()) {
    fun matches(text: String): Boolean {
        val answer = text.trim().removeSuffix(".").trim()
        val unquoted = if (answer.length >= 2 && answer.first() == '"' && answer.last() == '"')
            answer.substring(1, answer.length - 1).trim() else answer
        return unquoted.equals(expected, ignoreCase = true)
    }
}

object LocalModelPhoneFixtures {
    const val VERSION = 1
    fun cases(language: String): List<PhoneModelCase> {
        require(language in setOf("EN", "LV", "RU")) { "Unsupported fixture language" }
        fun l(en: String, lv: String, ru: String) = when (language) { "LV" -> lv; "RU" -> ru; else -> en }
        val only = l("Reply with only the answer.", "Atbildi tikai ar atbildi.", "Ответь только ответом.")
        fun c(id: String, role: AiModelRole, prompt: String, answer: String, history: List<AIMessage> = emptyList()) =
            PhoneModelCase(id, role, "$prompt $only", answer, history)
        val fast = AiModelRole.FAST
        val chat = AiModelRole.CHAT
        val think = AiModelRole.REASONING
        return listOf(
            c("fast-add", fast, l("What is 5+2?", "Cik ir 5+2?", "Сколько будет 5+2?"), "7"),
            c("fast-copy", fast, l("Copy the code: PINE-82.", "Pārraksti kodu: PINE-82.", "Перепиши код: PINE-82."), "PINE-82"),
            c("fast-first", fast, l("Give the first item: amber, blue, coral.", "Nosauc pirmo vienumu: dzintars, zils, koraļļi.", "Назови первый элемент: янтарь, синий, коралл."), l("amber", "dzintars", "янтарь")),
            c("fast-count", fast, l("How many letters are in HALO? Give a number.", "Cik burtu ir vārdā HALO? Atbildi ar skaitli.", "Сколько букв в слове HALO? Ответь числом."), "4"),
            c("fast-extract", fast, l("Extract the number from ID=934.", "Izraksti skaitli no ID=934.", "Извлеки число из ID=934."), "934"),
            c("chat-grounding", chat, l("Reference: the parcel arrives on Tuesday. Which day does it arrive?", "Avots: sūtījums pienāks otrdien. Kurā dienā tas pienāks?", "Источник: посылка придёт во вторник. В какой день она придёт?"), l("Tuesday", "otrdien", "во вторник")),
            c("chat-extraction", chat, l("Reference: Mara lives in Riga and works in Jelgava. Name only the city where she lives.", "Avots: Māra dzīvo Rīgā un strādā Jelgavā. Nosauc tikai viņas dzīvesvietas pilsētu nominatīvā.", "Источник: Мара живёт в Риге и работает в Елгаве. Назови только город проживания в именительном падеже."), l("Riga", "Rīga", "Рига")),
            c("chat-negation", chat, l("The red box is empty. The blue box contains the key. Which box contains the key? Give only its color.", "Sarkanā kaste ir tukša. Zilajā kastē ir atslēga. Kādā krāsā ir kaste ar atslēgu? Atbildi ar krāsas nosaukumu: zila vai sarkana.", "Красная коробка пуста. В синей коробке ключ. Какого цвета коробка с ключом? Ответь: синяя или красная."), l("blue", "zila", "синяя")),
            c("chat-unknown", chat, l("Reference: Lea owns a bicycle. What is her age? If the reference does not say, reply UNKNOWN.", "Avots: Leai pieder velosipēds. Cik viņai gadu? Ja avotā tas nav norādīts, atbildi UNKNOWN.", "Источник: у Леи есть велосипед. Сколько ей лет? Если в источнике этого нет, ответь UNKNOWN."), "UNKNOWN"),
            c("chat-correction", chat, l("What is my current test code?", "Kāds ir mans pašreizējais testa kods?", "Какой у меня текущий тестовый код?"), "MAPLE-26", listOf(
                AIMessage(Role.USER, l("My test code is OAK-11.", "Mans testa kods ir OAK-11.", "Мой тестовый код OAK-11.")),
                AIMessage(Role.ASSISTANT, "OK"),
                AIMessage(Role.USER, l("Correction: replace it with MAPLE-26.", "Labojums: aizstāj to ar MAPLE-26.", "Исправление: замени его на MAPLE-26.")),
                AIMessage(Role.ASSISTANT, "OK"))),
            c("think-stock", think, l("23 panels need 7 clips each. 19 clips are available. How many more clips are needed?", "23 paneļiem katram vajag 7 skavas. Pieejamas 19 skavas. Cik skavu vēl vajag?", "Для 23 панелей нужно по 7 зажимов. Есть 19 зажимов. Сколько ещё нужно?"), "142"),
            c("think-time", think, l("Four batteries each last 35 minutes. 50 minutes have been used. How many minutes remain?", "Četras baterijas katra darbojas 35 minūtes. Izmantotas 50 minūtes. Cik minūšu atlicis?", "Четырёх батарей хватает на 35 минут каждой. Использовано 50 минут. Сколько минут осталось?"), "90"),
            c("think-order", think, l("A is taller than B; B is taller than C. Who is shortest?", "A ir garāks par B; B ir garāks par C. Kurš ir visīsākais?", "A выше B; B выше C. Кто самый низкий?"), "C"),
            c("think-change", think, l("A ticket costs 17. You buy three and pay 60. What is the change?", "Biļete maksā 17. Tu nopērc trīs un samaksā 60. Cik jāsaņem atlikumā?", "Билет стоит 17. Ты покупаешь три и платишь 60. Сколько сдачи?"), "9"),
            c("think-constraints", think, l("Choose the smallest integer greater than 10 divisible by both 3 and 4.", "Izvēlies mazāko veselo skaitli, kas lielāks par 10 un dalās gan ar 3, gan ar 4.", "Выбери наименьшее целое число больше 10, которое делится и на 3, и на 4."), "12"),
        )
    }
}

/** Strict fixture screening, not an assertion of general language or domain quality. */
fun phoneRoleSuitability(completed: Int, expected: Int, passed: Int, medianAnswerMs: Long?): String = when {
    completed != expected || expected < 15 -> "NOT_TESTED"
    passed != expected -> "NOT_SUITABLE"
    medianAnswerMs != null && medianAnswerMs in 0..4000 -> "GOOD"
    else -> "SUITABLE"
}
