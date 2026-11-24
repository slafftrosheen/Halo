package com.halo.app.ui.onboarding.v13

import com.halo.app.ui.adaptive.v13.ExperienceLevel
import com.halo.app.ui.adaptive.v13.StartAnswer

/**
 * halo 13.0 · onboarding in four human steps (ONBOARDING_13.md).
 * Replaces the 10 SetupStage screens of ui/onboarding/HaloSetupScreen.kt:
 *   WELCOME, USE                       → HELLO + WHO
 *   (new)                              → DEPTH
 *   AI                                 → THINKING
 *   PERMISSIONS, SPEECH, DEVICES       → asked when first needed (AskWhenNeeded) or as setup cards
 *   PRIVACY                            → one line on THINKING + "What halo can use" in Settings
 *   EVERYDAY, READINESS, FIRST         → the first real try on Home + setup cards
 * Every step has "Just open halo", which fills in the defaults below and lands on Home.
 */
enum class Step { HELLO, WHO, DEPTH, THINKING }

enum class WhoFor { ME, MY_CHILD, FAMILY, STUDYING, CREATING }
enum class Thinking { ON_THIS_PHONE, ONLINE_SERVICE, DECIDE_LATER }
enum class Comfort { LARGER_TEXT, READ_ALOUD, HIGH_CONTRAST, LESS_MOTION }

data class OnboardingState(
    val step: Step = Step.HELLO,
    val language: String,                       // from the phone; can be changed on HELLO
    val comfort: Set<Comfort> = emptySet(),     // applied live, before anything else
    val whoFor: Set<WhoFor> = emptySet(),       // several allowed
    val start: StartAnswer? = null,
    val thinking: Thinking? = null,
    val downloadOnWifiOnly: Boolean = true,
    val finished: Boolean = false,
)

sealed interface OnboardingEvent {
    data class SetComfort(val c: Comfort, val on: Boolean) : OnboardingEvent
    data class Toggle(val who: WhoFor) : OnboardingEvent
    data class Depth(val a: StartAnswer) : OnboardingEvent
    data class Think(val t: Thinking) : OnboardingEvent
    data object Next : OnboardingEvent
    data object Back : OnboardingEvent
    data object JustOpenHalo : OnboardingEvent
}

object Onboarding {
    const val MAX_STEPS = 4

    fun reduce(s: OnboardingState, e: OnboardingEvent): OnboardingState = when (e) {
        is OnboardingEvent.SetComfort -> s.copy(comfort = if (e.on) s.comfort + e.c else s.comfort - e.c)
        is OnboardingEvent.Toggle -> s.copy(whoFor = if (e.who in s.whoFor) s.whoFor - e.who else s.whoFor + e.who)
        is OnboardingEvent.Depth -> s.copy(start = e.a)
        is OnboardingEvent.Think -> s.copy(thinking = e.t)
        OnboardingEvent.Back -> s.copy(step = Step.values()[(s.step.ordinal - 1).coerceAtLeast(0)])
        OnboardingEvent.Next -> if (s.step == Step.THINKING) withDefaults(s).copy(finished = true)
            else s.copy(step = Step.values()[s.step.ordinal + 1])
        OnboardingEvent.JustOpenHalo -> withDefaults(s).copy(finished = true)
    }

    /** Skipping is never punished: halo opens fully usable with these. */
    fun withDefaults(s: OnboardingState) = s.copy(
        whoFor = s.whoFor.ifEmpty { setOf(WhoFor.ME) },
        start = s.start ?: if (Comfort.LARGER_TEXT in s.comfort || Comfort.READ_ALOUD in s.comfort) StartAnswer.KEEP_IT_SIMPLE else StartAnswer.A_BIT_OF_EVERYTHING,
        thinking = s.thinking ?: Thinking.DECIDE_LATER,
    )

    /** What Home shows first after onboarding – a real thing to try, never a checklist. */
    fun firstTry(s: OnboardingState): String = when {
        WhoFor.MY_CHILD in s.whoFor && s.whoFor.size == 1 -> "kids_handover"
        WhoFor.STUDYING in s.whoFor -> "try_explain_photo"
        WhoFor.CREATING in s.whoFor -> "try_first_drawing"
        else -> "try_plan_today"
    }

    fun level(s: OnboardingState): ExperienceLevel = when (s.start) {
        StartAnswer.KEEP_IT_SIMPLE -> ExperienceLevel.SIMPLE
        StartAnswer.SHOW_ME_EVERYTHING -> ExperienceLevel.PRO
        else -> ExperienceLevel.STANDARD
    }
}
