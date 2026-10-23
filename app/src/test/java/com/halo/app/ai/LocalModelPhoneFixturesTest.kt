package com.halo.app.ai

import com.halo.sdk.provider.AiModelRole
import org.junit.Assert.*
import org.junit.Test

class LocalModelPhoneFixturesTest {
    @Test fun everyLanguageHasScoredFastChatAndReasoningCases() {
        for (language in listOf("EN", "LV", "RU", "PL")) {
            val cases = LocalModelPhoneFixtures.cases(language)
            assertEquals(cases.size, cases.map { it.id }.distinct().size)
            for (role in listOf(AiModelRole.FAST, AiModelRole.CHAT, AiModelRole.REASONING)) {
                assertTrue(cases.count { it.role == role } >= 5)
            }
            assertTrue(cases.any { it.history.isNotEmpty() })
        }
    }

    @Test fun polishCasesUsePolishQuestionsAnswersAndCorrectedHistory() {
        assertEquals(2, LocalModelPhoneFixtures.VERSION)
        val polish = LocalModelPhoneFixtures.cases("PL").associateBy { it.id }
        val english = LocalModelPhoneFixtures.cases("EN").associateBy { it.id }
        assertEquals(english.keys, polish.keys)
        polish.forEach { (id, case) ->
            assertNotEquals(english.getValue(id).prompt, case.prompt)
            assertTrue(case.prompt.endsWith("Podaj tylko odpowiedź."))
            assertTrue(case.matches(case.expected))
            assertFalse(case.matches("Odpowiedź to ${case.expected}"))
        }
        assertEquals("bursztynowy", polish.getValue("fast-first").expected)
        assertEquals("wtorek", polish.getValue("chat-grounding").expected)
        assertEquals("Ryga", polish.getValue("chat-extraction").expected)
        assertEquals("niebieski", polish.getValue("chat-negation").expected)
        val correction = polish.getValue("chat-correction")
        assertEquals("MAPLE-26", correction.expected)
        assertEquals("Mój kod testowy to OAK-11.", correction.history[0].content)
        assertEquals("Poprawka: zastąp go kodem MAPLE-26.", correction.history[2].content)
        assertFalse(correction.matches("OAK-11"))
        assertEquals("UNKNOWN", polish.getValue("chat-unknown").expected)
        assertFalse(polish.getValue("chat-unknown").matches("18"))
    }

    @Test fun arbitraryTextCannotPassAndProseIsNotAnExactAnswer() {
        val case = LocalModelPhoneFixtures.cases("EN").first()
        assertFalse(case.matches("ok"))
        assertFalse(case.matches("The answer is ${case.expected}"))
        assertTrue(case.matches(" \"${case.expected}\". "))
    }

    @Test fun unknownLanguageIsRejectedRatherThanTestedInEnglish() {
        assertThrows(IllegalArgumentException::class.java) { LocalModelPhoneFixtures.cases("DE") }
    }

    @Test fun recommendationsRequireCompleteRepeatedCorrectEvidenceAndLatency() {
        assertEquals("NOT_TESTED", phoneRoleSuitability(0, 15, 0, null))
        assertEquals("NOT_TESTED", phoneRoleSuitability(10, 15, 10, 100))
        assertEquals("NOT_SUITABLE", phoneRoleSuitability(15, 15, 14, 100))
        assertEquals("SUITABLE", phoneRoleSuitability(15, 15, 15, null))
        assertEquals("SUITABLE", phoneRoleSuitability(15, 15, 15, 9000))
        assertEquals("GOOD", phoneRoleSuitability(15, 15, 15, 3000))
        assertEquals("NOT_TESTED", phoneRoleSuitability(5, 5, 5, 100))
    }
}
