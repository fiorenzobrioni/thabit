package com.callbackdev.thabit.domain

import com.callbackdev.thabit.domain.model.Habit
import com.callbackdev.thabit.domain.model.Schedule
import java.time.LocalDate

/** What a streak counts. Day-shaped schedules count days; a quota counts weeks. */
enum class StreakUnit { DAYS, WEEKS }

/**
 * A run of consecutive passes.
 *
 * [length] is in [unit]s, and [from]..[to] are the ends of the run, so the log
 * can point a `tag: longest-streak` at the right commit.
 */
data class Streak(
    val length: Int,
    val unit: StreakUnit,
    val from: LocalDate? = null,
    val to: LocalDate? = null
) {
    val isEmpty: Boolean get() = length == 0

    companion object {
        fun none(unit: StreakUnit) = Streak(0, unit)
    }
}

/**
 * Streaks — the severe half of the pair.
 *
 * Skips hold a streak (they are neutral everywhere), but a **`no run` day breaks
 * it**, and that asymmetry with [Health] is deliberate (VISION §3.3.3, §3.3.8).
 * Treating an unknown day like a skip would make the chain immortal for anyone
 * who closes the app on the hard days — a worse kind of gaming than a low
 * coverage number, which at least says so out loud. A streak is a chain of
 * passes somebody typed, and a day nobody was there is not one.
 *
 * Health stays forgiving in exactly the same situation, which is the point: the
 * mechanics are humane, the reporting is honest, and the two numbers disagreeing
 * is information rather than a bug.
 */
object Streaks {

    /** The streak running into [today] — today's own pending test does not break it. */
    fun current(history: SuiteHistory, habit: Habit, today: LocalDate): Streak =
        if (habit.schedule is Schedule.Quota) currentWeeks(history, habit, today)
        else currentDays(history, habit, today)

    private fun currentDays(history: SuiteHistory, habit: Habit, today: LocalDate): Streak {
        var length = 0
        var newest: LocalDate? = null
        var oldest: LocalDate? = null
        var date = minOf(today, habit.archivedAt?.minusDays(1) ?: today)

        while (!date.isBefore(habit.createdAt)) {
            if (!habit.occursOn(date)) { date = date.minusDays(1); continue }
            val closed = date < today
            // The app never saw this day: not a pass, so the chain ends here.
            if (closed && !history.ran(date)) break

            when (Verdicts.resolve(habit, history.check(habit.id, date), closed)) {
                TestState.PASS -> {
                    length++
                    if (newest == null) newest = date
                    oldest = date
                }
                TestState.SKIP -> Unit // neutral: the chain steps over it
                TestState.PENDING, TestState.HOLDING -> Unit // today is not over yet
                TestState.FAIL -> break
            }
            date = date.minusDays(1)
        }
        return Streak(length, StreakUnit.DAYS, oldest, newest)
    }

    private fun currentWeeks(history: SuiteHistory, habit: Habit, today: LocalDate): Streak {
        var length = 0
        var newest: LocalDate? = null
        var oldest: LocalDate? = null
        var week = IsoWeek.of(today)
        val firstWeek = IsoWeek.of(habit.createdAt)

        while (week >= firstWeek) {
            val quota = Quotas.weekOf(history, habit, week.start, today) ?: break
            // A week the app never saw is not a week of passes: same rule as a
            // `no run` day, for the same reason. A week skipped down to nothing
            // is neutral and holds the chain, because skips always do.
            if (quota.closed && !quota.ran) break
            if (!quota.neutral) {
                if (quota.met) {
                    length++
                    if (newest == null) newest = quota.week.start
                    oldest = quota.week.start
                } else if (quota.closed) {
                    break // the week is over and short
                }
                // an open week still short: not a miss yet, and not a pass either
            }
            week = IsoWeek.of(week.start.minusWeeks(1))
        }
        return Streak(length, StreakUnit.WEEKS, oldest, newest)
    }

    /** The longest run this test ever had, over its whole life. */
    fun longest(history: SuiteHistory, habit: Habit, today: LocalDate): Streak {
        val unit = if (habit.schedule is Schedule.Quota) StreakUnit.WEEKS else StreakUnit.DAYS
        val units = Outcomes.graded(history, habit, habit.createdAt, today, today)
        if (units.isEmpty()) return Streak.none(unit)

        // Days (or weeks) the app never saw are absent from the graded sequence,
        // so they have to be re-introduced here: not failures, but they do end
        // the chain.
        val breaks = if (unit == StreakUnit.DAYS) {
            noRunDays(history, habit, today)
        } else {
            noRunWeeks(history, habit, today)
        }

        var best = 0
        var bestFrom: LocalDate? = null
        var bestTo: LocalDate? = null
        var run = 0
        var runFrom: LocalDate? = null
        var previous: LocalDate? = null

        for (unitAt in units) {
            val prev = previous
            val brokenByAbsence = prev != null &&
                breaks.any { it.isAfter(prev) && it.isBefore(unitAt.at) }
            if (!unitAt.passed || brokenByAbsence) {
                if (!unitAt.passed) { run = 0; runFrom = null } else { run = 1; runFrom = unitAt.at }
            } else {
                run++
                if (runFrom == null) runFrom = unitAt.at
            }
            if (run > best) { best = run; bestFrom = runFrom; bestTo = unitAt.at }
            previous = unitAt.at
        }
        return Streak(best, unit, bestFrom, bestTo)
    }

    /** Closed weeks of a quota test in which the app was never opened at all. */
    private fun noRunWeeks(history: SuiteHistory, habit: Habit, today: LocalDate): Set<LocalDate> =
        IsoWeek.weeksBetween(habit.createdAt, today)
            .mapNotNull { week -> Quotas.weekOf(history, habit, week.start, today) }
            .filter { it.closed && !it.ran }
            .map { it.week.start }
            .toSet()

    /** Days this test was due on and the app never saw — the chain's silent breaks. */
    private fun noRunDays(history: SuiteHistory, habit: Habit, today: LocalDate): Set<LocalDate> {
        val end = minOf(today.minusDays(1), habit.archivedAt?.minusDays(1) ?: today)
        if (end.isBefore(habit.createdAt)) return emptySet()
        return generateSequence(habit.createdAt) { d -> d.plusDays(1).takeIf { !it.isAfter(end) } }
            .filter { habit.occursOn(it) && !history.ran(it) }
            .toSet()
    }
}
