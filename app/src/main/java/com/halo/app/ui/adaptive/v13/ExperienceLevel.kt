package com.halo.app.ui.adaptive.v13

/**
 * halo 13.0 · one app, three depths.
 *
 * SIMPLE   – one main action per screen, everyday words, big targets, no menus.
 * STANDARD – today's halo, with plain words.
 * PRO      – every instrument, menus, shortcuts, technical names allowed.
 *
 * Rules (FOUNDATIONS_13.md §3):
 *  1. A level can be set for the whole app AND per tool (Studio on Pro, Settings on Simple).
 *  2. halo never changes the level silently. It can only OFFER a change, at most once per 14 days per tool.
 *  3. Kids profiles are always SIMPLE and never get offers.
 *  4. Nothing is ever removed at a lower level – it moves behind "Show more" (search and voice still find it).
 */
enum class ExperienceLevel { SIMPLE, STANDARD, PRO }

data class LevelSignals(
    val daysUsed: Int,
    val advancedActions14d: Int,      // instruments used that live behind "Show more"
    val helpOpens14d: Int,            // "What's this?", Explain, "I'm stuck"
    val undoAfterAdapt7d: Int,        // the person undid a change halo made
    val largeTextOrTalkBack: Boolean,
    val kidsProfile: Boolean,
    val chosenByPerson: ExperienceLevel?,
)

sealed interface LevelDecision {
    data class Keep(val level: ExperienceLevel) : LevelDecision
    data class Offer(val from: ExperienceLevel, val to: ExperienceLevel, val reason: OfferReason) : LevelDecision
}

enum class OfferReason { USES_ADVANCED_TOOLS, OFTEN_ASKS_FOR_HELP, UNDOES_CHANGES }

object LevelAdvisor {
    const val OFFER_COOLDOWN_DAYS = 14

    fun firstLevel(answer: StartAnswer, signals: LevelSignals): ExperienceLevel = when {
        signals.kidsProfile -> ExperienceLevel.SIMPLE
        answer == StartAnswer.KEEP_IT_SIMPLE || signals.largeTextOrTalkBack -> ExperienceLevel.SIMPLE
        answer == StartAnswer.SHOW_ME_EVERYTHING -> ExperienceLevel.PRO
        else -> ExperienceLevel.STANDARD
    }

    fun decide(current: ExperienceLevel, s: LevelSignals, daysSinceLastOffer: Int): LevelDecision {
        if (s.kidsProfile) return LevelDecision.Keep(ExperienceLevel.SIMPLE)
        if (s.chosenByPerson != null || daysSinceLastOffer < OFFER_COOLDOWN_DAYS) return LevelDecision.Keep(current)
        val up = when (current) { ExperienceLevel.SIMPLE -> ExperienceLevel.STANDARD; else -> ExperienceLevel.PRO }
        val down = when (current) { ExperienceLevel.PRO -> ExperienceLevel.STANDARD; else -> ExperienceLevel.SIMPLE }
        return when {
            current != ExperienceLevel.PRO && s.daysUsed >= 7 && s.advancedActions14d >= 12 && s.helpOpens14d <= 2 ->
                LevelDecision.Offer(current, up, OfferReason.USES_ADVANCED_TOOLS)
            current != ExperienceLevel.SIMPLE && s.helpOpens14d >= 6 ->
                LevelDecision.Offer(current, down, OfferReason.OFTEN_ASKS_FOR_HELP)
            s.undoAfterAdapt7d >= 3 -> LevelDecision.Keep(current) // stop adapting this tool; see DynamicSurface.freeze
            else -> LevelDecision.Keep(current)
        }
    }
}

/** First question of onboarding (batch 2) – "How much do you want to see?" */
enum class StartAnswer { KEEP_IT_SIMPLE, A_BIT_OF_EVERYTHING, SHOW_ME_EVERYTHING }
