package com.callbackdev.thabit.domain

import com.callbackdev.thabit.domain.model.Habit
import java.time.LocalDate

/**
 * A test that keeps going red — reported as a number, never as a finger.
 *
 * `stats.md` prints these with one fixed, factual hint (*a flaky test wants a
 * smaller assert or a different schedule*) and nothing else: no streak-shaming,
 * no encouragement, no "you're slipping!" (VISION §3.3.4).
 */
data class FlakyTest(
    val habit: Habit,
    val passRate: Double,
    /** Graded units the rate was computed on — always shown beside the percentage. */
    val samples: Int
) {
    /** `4/17` — the arithmetic the percentage never travels without (VISION §3.3.7). */
    val fraction: String get() = "${Math.round(passRate * samples).toInt()}/$samples"
}

/**
 * The flaky rule, fixed here with the other constants of the phase.
 *
 * A test is flaky when its **30-day pass rate is below [THRESHOLD]** over at
 * least [MIN_SAMPLES] graded units. Both halves matter: without the sample
 * floor, a test added two days ago and missed once would be shamed at 50%, and
 * without the window the number would never recover.
 *
 * Every window clamps to `createdAt` (VISION §4.3) — [Outcomes.graded] does that
 * for free — so a test added yesterday cannot appear here with a fabricated 3%.
 *
 * A test already reported as a [Regression] is excluded: it was solid, so it is
 * not flaky, and saying both about the same habit would be saying one of them
 * falsely.
 */
object FlakyTests {

    const val THRESHOLD: Double = 0.6
    const val MIN_SAMPLES: Int = 8
    const val WINDOW_DAYS: Long = 30

    /** The rule in one line, for the export header and the docs. */
    const val RULE: String =
        "pass rate over the last $WINDOW_DAYS days below ${(THRESHOLD * 100).toInt()}%, " +
            "with at least $MIN_SAMPLES graded units, excluding regressions"

    /** The flaky tests of the suite, worst rate first. */
    fun detect(
        history: SuiteHistory,
        today: LocalDate,
        regressions: List<Regression> = Regressions.detect(history, today)
    ): List<FlakyTest> {
        val regressing = regressions.map { it.habit.id }.toSet()
        return history.habits
            .filter { it.archivedAt == null && it.id !in regressing }
            .mapNotNull { habit ->
                val units = Outcomes.graded(
                    history,
                    habit,
                    today.minusDays(WINDOW_DAYS),
                    today,
                    today
                )
                if (units.size < MIN_SAMPLES) return@mapNotNull null
                val rate = units.count { it.passed }.toDouble() / units.size
                if (rate >= THRESHOLD) null else FlakyTest(habit, rate, units.size)
            }
            .sortedBy { it.passRate }
    }
}
