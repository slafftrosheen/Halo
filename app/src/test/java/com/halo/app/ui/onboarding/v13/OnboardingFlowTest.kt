package com.halo.app.ui.onboarding.v13

import com.halo.app.ui.adaptive.v13.ExperienceLevel
import org.junit.Assert.*
import org.junit.Test

class OnboardingFlowTest {
    private val start = OnboardingState(language = "en")

    @Test fun neverMoreThanFourSteps() = assertEquals(Onboarding.MAX_STEPS, Step.values().size)

    @Test fun justOpenHaloGivesUsableDefaults() {
        val s = Onboarding.reduce(start, OnboardingEvent.JustOpenHalo)
        assertTrue(s.finished); assertEquals(setOf(WhoFor.ME), s.whoFor); assertEquals(Thinking.DECIDE_LATER, s.thinking)
        assertEquals(ExperienceLevel.STANDARD, Onboarding.level(s))
    }

    @Test fun largerTextStartsSimple() {
        val s = Onboarding.reduce(Onboarding.reduce(start, OnboardingEvent.SetComfort(Comfort.LARGER_TEXT, true)), OnboardingEvent.JustOpenHalo)
        assertEquals(ExperienceLevel.SIMPLE, Onboarding.level(s))
    }

    @Test fun noAskWithoutPersonStartingTheFeature() =
        assertEquals(AskOutcome.DONT_ASK, AskWhenNeeded.next(false, AskHistory(0, 0, false), startedByPerson = false))

    @Test fun twoAndroidDenialsShowSettingsPath() =
        assertEquals(AskOutcome.SHOW_SETTINGS_PATH, AskWhenNeeded.next(false, AskHistory(0, 2, false), true))
}
