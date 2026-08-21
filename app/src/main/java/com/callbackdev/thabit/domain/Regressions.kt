package com.callbackdev.thabit.domain

import com.callbackdev.thabit.domain.model.Habit
import com.callbackdev.thabit.domain.model.Schedule
import java.time.LocalDate

/**
 * A test that *was* solid and is breaking now.
 *
 * `meditate 10 min — 43 days green, 3 of the last 5 red`: the most actionable
 * line the app can produce, and the one it says **once, without advice**
 * (VISION §4.3). It is distinct from a flaky test on purpose — a flaky test was
 * never solid, a regression was, and telling somebody "you skip this often"
 * about a habit they held for six weeks would be both wrong and insulting.
 */
data class Regression(
    val habit: Habit,
    /** The green run that preceded the break, in graded units. */
    val greenRun: Int,
    /** Failures inside the recent window. */
    val recentFails: Int,
    /** Size of the recent window ([Regressions.RECENT_UNITS]). */
    val recentWindow: Int,
    /** The unit the streak was counted in — days, or weeks for a quota test. */
    val unit: StreakUnit
)

/**
 * The regression rule, fixed here beside the health half-life and exported with
 * it (VISION §7: no secret numbers).
 *
 * A test is regressing when **at least [MIN_RECENT_FAILS] of its last
 * [RECENT_UNITS] graded units failed**, and the run of passes immediately before
 * the first of those failures was **at least [MIN_GREEN_RUN] units** long.
 *
 * The three constants are chosen so the line is rare and means something: five
 * recent runs is enough to see a pattern without waiting a month, three of five
 * is a majority rather than a bad Tuesday, and fourteen green units is the same
 * horizon as the health half-life — the point at which the app is willing to
 * call a habit established.
 *
 * Skips and `no run` days are absent from the graded sequence, so neither can
 * fabricate a regression: only runs somebody actually failed count.
 */
object Regressions {

    const val RECENT_UNITS: Int = 5
    const val MIN_RECENT_FAILS: Int = 3
    const val MIN_GREEN_RUN: Int = 14

    /** The rule in one line, for the export header and the docs. */
    const val RULE: String =
        "at least $MIN_RECENT_FAILS of the last $RECENT_UNITS graded units failed, " +
            "after a green run of at least $MIN_GREEN_RUN units"

    /** The regressing tests of the suite, worst green run first. */
    fun detect(history: SuiteHistory, today: LocalDate): List<Regression> =
        history.habits
            .filter { it.archivedAt == null }
            .mapNotNull { habit -> of(history, habit, today) }
            .sortedByDescending { it.greenRun }

    /** The regression of a single test, or null when it is not one. */
    fun of(history: SuiteHistory, habit: Habit, today: LocalDate): Regression? {
        val units = Outcomes.graded(history, habit, habit.createdAt, today, today)
        if (units.size < RECENT_UNITS) return null

        val recentFrom = units.size - RECENT_UNITS
        val recent = units.subList(recentFrom, units.size)
        val fails = recent.count { !it.passed }
        if (fails < MIN_RECENT_FAILS) return null

        val firstFail = recentFrom + recent.indexOfFirst { !it.passed }
        var greenRun = 0
        var i = firstFail - 1
        while (i >= 0 && units[i].passed) { greenRun++; i-- }
        if (greenRun < MIN_GREEN_RUN) return null

        return Regression(
            habit = habit,
            greenRun = greenRun,
            recentFails = fails,
            recentWindow = RECENT_UNITS,
            unit = if (habit.schedule is Schedule.Quota) StreakUnit.WEEKS else StreakUnit.DAYS
        )
    }
}
