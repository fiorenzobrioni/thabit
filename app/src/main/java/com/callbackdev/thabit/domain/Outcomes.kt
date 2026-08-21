package com.callbackdev.thabit.domain

import com.callbackdev.thabit.domain.model.Habit
import com.callbackdev.thabit.domain.model.Schedule
import java.time.LocalDate

/**
 * One graded unit of a test's history: a due day that was passed or failed —
 * or, for a quota test, an ISO week that met or missed its target.
 *
 * [at] is the day itself, or the week's Monday for a quota test, so a single
 * chronological sequence feeds health, pass rates, regressions and flakiness
 * without any of them having to know which schedule they are looking at.
 */
data class GradedUnit(val at: LocalDate, val passed: Boolean)

/**
 * The one place that turns a test's stored rows into the graded sequence every
 * statistic reads.
 *
 * Three things never enter that sequence, and they are the honesty rules of the
 * whole phase: **skips** (neutral everywhere — VISION §3.3.3), **`no run` days**
 * (the app was not there, so it does not know — §3.3.8), and whatever is still
 * open (today's pending test is not a failure yet, this week's quota can still
 * be met). What is left is only what somebody actually did.
 */
object Outcomes {

    /**
     * The graded units of [habit] inside the closed range, oldest first.
     *
     * The range is always clamped to [Habit.createdAt] (VISION §4.3): a test
     * added yesterday is `1/1`, never `1/30`, and never appears among the flaky
     * with a fabricated 3%.
     */
    fun graded(
        history: SuiteHistory,
        habit: Habit,
        from: LocalDate,
        to: LocalDate,
        today: LocalDate
    ): List<GradedUnit> {
        val start = maxOf(from, habit.createdAt)
        val end = minOf(to, today, habit.archivedAt?.minusDays(1) ?: to)
        if (end.isBefore(start)) return emptyList()

        if (habit.schedule is Schedule.Quota) {
            return Quotas.weeksIn(history, habit, start, end, today)
                .filterNot { it.neutral }
                // An open week is only graded once it is met: still short with
                // days to go is not a miss, it is a week in progress.
                .filter { it.closed || it.met }
                .map { GradedUnit(it.week.start, it.met) }
        }

        return generateSequence(start) { d -> d.plusDays(1).takeIf { !it.isAfter(end) } }
            .mapNotNull { date ->
                val closed = date < today
                if (closed && !history.ran(date)) return@mapNotNull null
                val check = history.check(habit.id, date)
                // Same rule as the day's run: a day the schedule asks for, or a
                // day the user wrote a row on. A schedule edit must not delete
                // results that were already earned under the old one.
                if (!habit.occursOn(date) && check == null) return@mapNotNull null
                val state = Verdicts.resolve(habit, check, closed)
                when (state) {
                    TestState.PASS -> GradedUnit(date, true)
                    TestState.FAIL -> GradedUnit(date, false)
                    else -> null
                }
            }
            .toList()
    }

    /** Pass rate over the range, or null when the test has nothing graded in it. */
    fun passRate(
        history: SuiteHistory,
        habit: Habit,
        from: LocalDate,
        to: LocalDate,
        today: LocalDate
    ): Double? {
        val units = graded(history, habit, from, to, today)
        if (units.isEmpty()) return null
        return units.count { it.passed }.toDouble() / units.size
    }
}
