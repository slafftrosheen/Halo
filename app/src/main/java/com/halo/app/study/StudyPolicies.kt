package com.halo.app.study

import com.halo.app.data.study.StudyPracticeItem
import com.halo.app.data.study.StudyReviewEvent
import com.halo.sdk.runtime.study.TutorStage
import kotlin.math.roundToInt

/** How a requested stage change is classified. */
enum class StageMove { REPEAT, FORWARD, BACKWARD, EXPLICIT_SOLUTION, REJECTED }

/**
 * Guided progression policy.
 *
 * Forward motion is one step at a time so Study cannot march the learner to the answer, but a learner
 * may always repeat, step back, or explicitly ask for the full solution.
 */
object TutorStagePolicy {
    fun classify(from: TutorStage, to: TutorStage, solutionExplicitlyRequested: Boolean): StageMove = when {
        to == from -> StageMove.REPEAT
        to.ordinal < from.ordinal -> StageMove.BACKWARD
        to.isSolution -> if (solutionExplicitlyRequested) StageMove.EXPLICIT_SOLUTION else StageMove.REJECTED
        to.ordinal == from.ordinal + 1 -> StageMove.FORWARD
        else -> StageMove.REJECTED
    }

    fun allows(from: TutorStage, to: TutorStage, solutionExplicitlyRequested: Boolean): Boolean =
        classify(from, to, solutionExplicitlyRequested) != StageMove.REJECTED

    /** The single guided next step, or null at the end of the progression. */
    fun next(from: TutorStage): TutorStage? = from.next()

    /**
     * Which stages count as help already revealed for a submission. Used for the attempt record so
     * progress reporting can separate unaided answers from assisted ones.
     */
    fun helpRevealed(path: List<TutorStage>): List<TutorStage> = path
        .filter { it != TutorStage.ASK }
        .distinct()
}

// ---------------------------------------------------------------------------------------------
// Memory model
// ---------------------------------------------------------------------------------------------

/**
 * Deterministic memory model, deliberately small and versioned.
 *
 * Design rules taken from local-first tutor practice:
 * - **Mastery never drifts.** It is a pure function of recorded review events, so the number shown on
 *   screen can always be recomputed from storage and cannot creep upward because time passed or a
 *   chat was long.
 * - **A model opinion is not a grade.** Only reachability of the answer by the learner (a checked
 *   recall outcome) enters the model. AI verdicts are displayed separately and never feed mastery.
 * - **Closed-form, not simulated.** Retainability is evaluated analytically at any point in time, so
 *   the UI can draw the decay curve between reviews without storing daily points.
 *
 * This is an FSRS-inspired approximation, not the official FSRS implementation; [VERSION] must be
 * bumped when the constants change so stored schedules can be recognized as belonging to an older
 * policy.
 */
object StudyMasteryModel {
    const val VERSION = 1

    /** Mastery is reported as the probability of recall this many days after the last review. */
    const val TARGET_HORIZON_DAYS = 30.0

    /** Default target retention used to derive the next review gap. */
    const val DEFAULT_DESIRED_RETENTION = 0.9

    const val MIN_STABILITY_DAYS = 0.6
    const val MAX_STABILITY_DAYS = 3_650.0
    const val INITIAL_KNOWN_STABILITY_DAYS = 3.0
    const val INITIAL_FORGOTTEN_STABILITY_DAYS = 1.0
    const val INITIAL_DIFFICULTY = 5.0
    const val MIN_DIFFICULTY = 1.0
    const val MAX_DIFFICULTY = 10.0

    /** Stability after one more outcome, given the current stability and item difficulty. */
    fun stabilityAfter(previousStabilityDays: Double, known: Boolean, difficulty: Double): Double {
        val ease = (MAX_DIFFICULTY - difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)) /
            (MAX_DIFFICULTY - MIN_DIFFICULTY)
        val next = if (known) {
            previousStabilityDays * (1.6 + 0.4 * ease)
        } else {
            previousStabilityDays * (0.35 + 0.05 * ease)
        }
        return next.coerceIn(MIN_STABILITY_DAYS, MAX_STABILITY_DAYS)
    }

    fun difficultyAfter(difficulty: Double, known: Boolean): Double {
        val delta = if (known) -0.15 else 0.35
        return (difficulty + delta).coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)
    }

    /** Power-curve forgetting: R = (1 + t / (9S))^-1. */
    fun retrievability(stabilityDays: Double, daysSinceReview: Double): Double {
        if (stabilityDays <= 0.0) return 0.0
        val t = daysSinceReview.coerceAtLeast(0.0)
        return 1.0 / (1.0 + t / (9.0 * stabilityDays))
    }

    /** Probability of still remembering at [TARGET_HORIZON_DAYS] after the last review. */
    fun mastery(stabilityDays: Double): Double =
        retrievability(stabilityDays, TARGET_HORIZON_DAYS)

    /** Days until retainability falls to [desiredRetention]; the inverse of [retrievability]. */
    fun desiredGapDays(stabilityDays: Double, desiredRetention: Double): Double {
        val retention = desiredRetention.coerceIn(0.5, 0.99)
        val stability = stabilityDays.coerceIn(MIN_STABILITY_DAYS, MAX_STABILITY_DAYS)
        // R = (1 + t/(9S))^-1  <=>  t = 9S (1/R - 1)
        return (9.0 * stability * (1.0 / retention - 1.0)).coerceAtLeast(0.25)
    }

    /**
     * Stability and difficulty reached immediately after each stored review, in review order.
     *
     * This is the model's only state: mastery, due date and the drawn decay curve are all derived
     * from this list, so a display and a schedule can never disagree.
     */
    fun reviewPoints(events: List<StudyReviewEvent>): List<MasteryReviewPoint> {
        val ordered = events.sortedBy(StudyReviewEvent::reviewedAtEpochMs)
        val points = ArrayList<MasteryReviewPoint>(ordered.size)
        var stability = 0.0
        var difficulty = INITIAL_DIFFICULTY
        ordered.forEachIndexed { index, event ->
            if (index == 0) {
                stability = if (event.known) INITIAL_KNOWN_STABILITY_DAYS else INITIAL_FORGOTTEN_STABILITY_DAYS
                // The first observed recall outcome must influence difficulty too.
                // Otherwise the second review starts from a neutral difficulty regardless
                // of whether the learner remembered or forgot the first time.
                difficulty = difficultyAfter(difficulty, event.known)
            } else {
                stability = stabilityAfter(stability, event.known, difficulty)
                difficulty = difficultyAfter(difficulty, event.known)
            }
            points += MasteryReviewPoint(
                reviewedAtEpochMs = event.reviewedAtEpochMs,
                known = event.known,
                stabilityDays = stability,
                difficulty = difficulty,
            )
        }
        return points
    }

    /**
     * Recompute the whole model from stored review events. Events are sorted before use, and an
     * empty history reports "not measured" with zero stability rather than a default score.
     */
    fun snapshot(
        events: List<StudyReviewEvent>,
        now: Long,
        desiredRetention: Double = DEFAULT_DESIRED_RETENTION,
        ladderDueAtEpochMs: Long? = null,
    ): StudyMasterySnapshot {
        val points = reviewPoints(events)
        val last = points.lastOrNull()
        val stability = last?.stabilityDays ?: 0.0
        val lastReviewAt = last?.reviewedAtEpochMs
        val daysSince = lastReviewAt?.let { (now - it).coerceAtLeast(0L) / DAY_MS.toDouble() } ?: 0.0
        val dueAt = when (lastReviewAt) {
            null -> ladderDueAtEpochMs ?: now
            else -> lastReviewAt + (desiredGapDays(stability, desiredRetention) * DAY_MS).toLong()
        }
        return StudyMasterySnapshot(
            masteredProbability = mastery(stability),
            retrievability = retrievability(stability, daysSince),
            stabilityDays = stability,
            difficulty = last?.difficulty ?: INITIAL_DIFFICULTY,
            reviewCount = points.size,
            lastReviewedAtEpochMs = lastReviewAt,
            dueAtEpochMs = dueAt,
            version = VERSION,
            desiredRetention = desiredRetention,
        )
    }

    const val DAY_MS = 86_400_000L
}

/** One review as the memory model saw it: the state the learner was left in. */
data class MasteryReviewPoint(
    val reviewedAtEpochMs: Long,
    val known: Boolean,
    val stabilityDays: Double,
    val difficulty: Double,
)

/** One sampled point of a drawn decay curve. */
data class MasteryCurvePoint(val atEpochMs: Long, val retrievability: Double)

/**
 * Closed-form decay sampling for the memory chart.
 *
 * The curve is drawn from the same constants the scheduler uses and is evaluated per pixel-sample,
 * so nothing is stored per day and the chart cannot drift away from the schedule it illustrates.
 */
object StudyMasteryCurve {
    const val DEFAULT_SAMPLE_COUNT = 40

    private const val MAX_SAMPLE_COUNT = 240

    /** Samples between [fromEpochMs] and [toEpochMs] inclusive; empty when there is no history. */
    fun points(
        reviewPoints: List<MasteryReviewPoint>,
        fromEpochMs: Long,
        toEpochMs: Long,
        samples: Int = DEFAULT_SAMPLE_COUNT,
    ): List<MasteryCurvePoint> {
        val count = samples.coerceIn(2, MAX_SAMPLE_COUNT)
        if (reviewPoints.isEmpty() || toEpochMs <= fromEpochMs) return emptyList()
        val span = toEpochMs - fromEpochMs
        return (0 until count).map { index ->
            val at = fromEpochMs + span * index / (count - 1)
            MasteryCurvePoint(atEpochMs = at, retrievability = retrievabilityAt(reviewPoints, at))
        }
    }

    /**
     * Recall probability at [atEpochMs]. The newest review at or before that instant owns the
     * segment; before the first review nothing is claimed and the value is zero.
     */
    fun retrievabilityAt(reviewPoints: List<MasteryReviewPoint>, atEpochMs: Long): Double {
        val point = reviewPoints.lastOrNull { it.reviewedAtEpochMs <= atEpochMs } ?: return 0.0
        val daysSince = (atEpochMs - point.reviewedAtEpochMs).coerceAtLeast(0L).toDouble() /
            StudyMasteryModel.DAY_MS
        return StudyMasteryModel.retrievability(point.stabilityDays, daysSince)
    }

    /**
     * The window a card's chart should cover: from its first review to the later of its due date and
     * now, so a card reviewed long ago still shows the fall it has already taken.
     */
    fun window(
        reviewPoints: List<MasteryReviewPoint>,
        dueAtEpochMs: Long,
        now: Long,
    ): Pair<Long, Long>? {
        val first = reviewPoints.firstOrNull()?.reviewedAtEpochMs ?: return null
        val end = maxOf(dueAtEpochMs, now, first + StudyMasteryModel.DAY_MS)
        return first to end
    }

    /** Everything the memory chart of one card needs, or null when the card was never reviewed. */
    fun cardCurve(
        history: List<StudyReviewEvent>,
        dueAtEpochMs: Long,
        now: Long,
        samples: Int = DEFAULT_SAMPLE_COUNT,
    ): MasteryCardCurve? {
        val reviewPoints = StudyMasteryModel.reviewPoints(history)
        val bounds = window(reviewPoints, dueAtEpochMs, now) ?: return null
        val (from, to) = bounds
        val sampled = points(reviewPoints, from, to, samples)
        if (sampled.isEmpty()) return null
        return MasteryCardCurve(
            fromEpochMs = from,
            toEpochMs = to,
            dueAtEpochMs = dueAtEpochMs.coerceIn(from, to),
            reviewCount = reviewPoints.size,
            points = sampled,
        )
    }
}

/** A drawn decay curve and the window it covers, so the chart can label its own axis. */
data class MasteryCardCurve(
    val fromEpochMs: Long,
    val toEpochMs: Long,
    val dueAtEpochMs: Long,
    val reviewCount: Int,
    val points: List<MasteryCurvePoint>,
) {
    /** Where along the window the due date sits, in 0..1, or null when the window is degenerate. */
    val dueFraction: Float?
        get() = (toEpochMs - fromEpochMs).takeIf { it > 0 }?.let { span ->
            ((dueAtEpochMs - fromEpochMs).toFloat() / span).coerceIn(0f, 1f)
        }

    /** Span label, e.g. "3 days" or "6 weeks". */
    fun spanLabel(): String {
        val days = ((toEpochMs - fromEpochMs) / StudyMasteryModel.DAY_MS).coerceAtLeast(1)
        return if (days < 14) "$days days" else "${(days / 7)} weeks"
    }
}

data class StudyMasterySnapshot(
    val masteredProbability: Double,
    val retrievability: Double,
    val stabilityDays: Double,
    val difficulty: Double,
    val reviewCount: Int,
    val lastReviewedAtEpochMs: Long?,
    val dueAtEpochMs: Long,
    val version: Int,
    /** Retention target the due date was derived from; shown so the learner sees the trade-off. */
    val desiredRetention: Double = StudyMasteryModel.DEFAULT_DESIRED_RETENTION,
) {
    /** Never reviewed: no claim is made about mastery. */
    val isMeasured: Boolean get() = reviewCount > 0

    val masteryPercent: Int get() = (masteredProbability * 100).roundToInt()

    val retentionPercent: Int get() = (retrievability * 100).roundToInt()

    /** Plain-language memory estimate, e.g. "about 6 more days". */
    fun stabilityLabel(): String = when {
        !isMeasured -> "Not measured yet"
        stabilityDays < 1.0 -> "less than a day"
        stabilityDays < 2.0 -> "about 1 day"
        stabilityDays < 31.0 -> "about ${stabilityDays.roundToInt()} days"
        else -> "about ${(stabilityDays / 30.4).roundToInt()} months"
    }

    fun statusLabel(now: Long): String = when {
        !isMeasured -> "New"
        now >= dueAtEpochMs -> "Due now"
        else -> "Due in ${((dueAtEpochMs - now) / StudyMasteryModel.DAY_MS).coerceAtLeast(0)}d"
    }
}

// ---------------------------------------------------------------------------------------------
// Mistake ladder
// ---------------------------------------------------------------------------------------------

/** Outcome of one review pass, with everything needed to store and audit the schedule. */
data class ReviewDecision(
    val ladderStep: Int,
    val knownStreak: Int,
    val wrongStreak: Int,
    val mastered: Boolean,
    val dueAtEpochMs: Long,
    val intervalDays: Int,
)

/**
 * Two-rule mistake ladder: remember moves up, forget moves down, two in a row move two steps or
 * reset. It is intentionally independent of the memory model so a learner's drills and their
 * long-term retention are not conflated.
 */
class StudyReviewPolicy(
    desiredRetention: Double = StudyMasteryModel.DEFAULT_DESIRED_RETENTION,
) {
    val steps: IntArray = DEFAULT_STEPS_DAYS.copyOf()

    private val desiredRetention: Double = desiredRetention

    val version: Int get() = VERSION

    fun schedule(item: StudyPracticeItem, known: Boolean, atEpochMs: Long): ReviewDecision {
        val previous = item.intervalIndex.coerceIn(0, steps.lastIndex)
        val knownStreak = if (known) item.knownStreak + 1 else 0
        val wrongStreak = if (known) 0 else item.wrongStreak + 1

        val nextStep = when {
            known && wrongStreak == 0 && knownStreak >= 2 -> (previous + 2).coerceAtMost(steps.lastIndex)
            known -> (previous + 1).coerceAtMost(steps.lastIndex)
            wrongStreak >= 2 -> 0
            else -> (previous - 1).coerceAtLeast(0)
        }
        val mastered = known && nextStep >= steps.lastIndex
        val intervalDays = if (mastered) {
            // Maintenance interval: three times the final rung, then the memory model takes over.
            (steps.last() * 3).coerceAtLeast(30)
        } else {
            steps[nextStep]
        }
        return ReviewDecision(
            ladderStep = nextStep,
            knownStreak = if (known) knownStreak else 0,
            wrongStreak = wrongStreak,
            mastered = mastered,
            dueAtEpochMs = atEpochMs + intervalDays * StudyMasteryModel.DAY_MS,
            intervalDays = intervalDays,
        )
    }

    /** Retention the learner selected; recorded with the schedule so later policy changes are visible. */
    fun desiredRetention(): Double = desiredRetention

    companion object {
        const val VERSION = 1

        /** 1, 2, 4, 7, 15, 30 days. */
        val DEFAULT_STEPS_DAYS: IntArray = intArrayOf(1, 2, 4, 7, 15, 30)
    }
}

// ---------------------------------------------------------------------------------------------
// Answer checking and comparison
// ---------------------------------------------------------------------------------------------

/**
 * Bounded deterministic checking for exact answers. Only supported forms are validated; everything
 * else returns null so the caller can say "unverified" instead of inventing a grade.
 */
object StudyAnswerChecker {
    private const val MAX_SUPPORTED_LENGTH = 200

    fun check(expected: String, attempt: String): Boolean? {
        val normalisedExpected = normalise(expected)
        if (normalisedExpected.isEmpty() || normalisedExpected.length > MAX_SUPPORTED_LENGTH) return null
        val normalisedAttempt = normalise(attempt)
        if (normalisedAttempt.isEmpty()) return null
        return normalisedExpected == normalisedAttempt
    }

    fun normalise(value: String): String = value
        .lowercase()
        .replace(Regex("[\\s\\u00A0]+"), " ")
        .trim()
        .trimEnd('.', '!', '?', ',', ';', ':')
}

/**
 * Comparison of a learner's answer with the stored answer, split into shared prefix, the differing
 * middles, and a shared suffix. Both sides keep their original casing for display.
 */
data class AnswerDiff(
    val prefix: String,
    val learnerMiddle: String,
    val expectedMiddle: String,
    val suffix: String,
) {
    val isIdentical: Boolean get() = learnerMiddle.isEmpty() && expectedMiddle.isEmpty()
}

object StudyAnswerDiff {
    fun of(expected: String, learner: String): AnswerDiff {
        val maxPrefix = minOf(expected.length, learner.length)
        var prefix = 0
        while (prefix < maxPrefix && equalsIgnoreCase(expected[prefix], learner[prefix])) prefix++

        var suffix = 0
        val maxSuffix = minOf(expected.length, learner.length) - prefix
        while (suffix < maxSuffix &&
            equalsIgnoreCase(expected[expected.length - 1 - suffix], learner[learner.length - 1 - suffix])
        ) {
            suffix++
        }
        return AnswerDiff(
            prefix = expected.substring(0, prefix),
            learnerMiddle = learner.substring(prefix, learner.length - suffix),
            expectedMiddle = expected.substring(prefix, expected.length - suffix),
            suffix = expected.substring(expected.length - suffix),
        )
    }

    private fun equalsIgnoreCase(a: Char, b: Char): Boolean = a.equals(b, ignoreCase = true)
}
