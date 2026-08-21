package com.callbackdev.thabit.domain

import com.callbackdev.thabit.domain.model.CheckState
import com.callbackdev.thabit.domain.model.Habit
import com.callbackdev.thabit.domain.model.Schedule
import java.time.LocalDate

/**
 * A quota test's week — the unit a `n/week` schedule is actually judged on.
 *
 * [neutral] weeks make no claim at all and leave every denominator: a week
 * nobody ran (VISION §4.2 — "a week with no day run advances no quota claim"),
 * or a week whose days were skipped down to nothing.
 */
data class QuotaWeek(
    val habitId: Long,
    val week: IsoWeek,
    /** Runs the week actually asks for, after skips and the test's own lifetime. */
    val target: Int,
    val done: Int,
    /** Days of the week that were available: in the test's life, in range, not skipped. */
    val available: Int,
    /** Did any day of this week run at all? */
    val ran: Boolean,
    /** Is the week over? An open week can still be met. */
    val closed: Boolean
) {
    val neutral: Boolean get() = target == 0 || !ran
    val met: Boolean get() = done >= target
    /** A week only fails once it is over and still short. */
    val failed: Boolean get() = closed && !neutral && !met
}

/**
 * The quota engine, prototyped before every other verdict (PLANNING, Fase 2).
 *
 * The rule it defends everywhere downstream: **a quota never fails a day, it
 * passes or fails the ISO week.** Everything else follows from that — the daily
 * build result ignores untouched quota tests, the streak counts weeks instead of
 * days, the health EMA steps once per week, and the log states the verdict on
 * the week separator (`quota: run 5k 2/3 ✗`).
 *
 * Skips shrink the target instead of failing the week, because skips are neutral
 * everywhere (VISION §3.3.3): a week spent away with five days skipped asks for
 * the two days that were left, not for three that were never possible.
 */
object Quotas {

    /** The quota week for [habit] containing [date], or null for any other schedule. */
    fun weekOf(
        history: SuiteHistory,
        habit: Habit,
        date: LocalDate,
        today: LocalDate
    ): QuotaWeek? {
        val quota = habit.schedule as? Schedule.Quota ?: return null
        val week = IsoWeek.of(date)
        val days = week.dates()

        // Days the week could honestly be run on: inside the test's life and not
        // skipped. Deliberately NOT clamped to today — an open week keeps its full
        // target, or a 3/week would report itself "met" on Monday evening after
        // one run and vanish from the file for the rest of the week.
        val available = days.count { day ->
            habit.isActiveOn(day) && history.check(habit.id, day)?.state != CheckState.SKIP
        }
        val done = days.count { day -> history.check(habit.id, day)?.state == CheckState.PASS }
        val ran = days.any { day -> !day.isAfter(today) && history.ran(day) }

        return QuotaWeek(
            habitId = habit.id,
            week = week,
            target = minOf(quota.times, available),
            done = done,
            available = available,
            ran = ran,
            closed = week.endInclusive < today
        )
    }

    /** Every quota week of [habit] touched by the closed range, oldest first. */
    fun weeksIn(
        history: SuiteHistory,
        habit: Habit,
        from: LocalDate,
        to: LocalDate,
        today: LocalDate
    ): List<QuotaWeek> {
        if (habit.schedule !is Schedule.Quota || to.isBefore(from)) return emptyList()
        return IsoWeek.weeksBetween(from, to).mapNotNull { week ->
            weekOf(history, habit, week.start, today)
        }
    }

    /** Every quota verdict of a single week, for the log's week separator. */
    fun verdictsForWeek(
        history: SuiteHistory,
        week: IsoWeek,
        today: LocalDate
    ): List<QuotaWeek> = history.habits
        .filter { it.schedule is Schedule.Quota }
        .filter { habit -> week.dates().any { habit.isActiveOn(it) } }
        .sortedBy { it.position }
        .mapNotNull { weekOf(history, it, week.start, today) }
        .filterNot { it.neutral }
}
