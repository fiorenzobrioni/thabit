package com.callbackdev.thabit.domain

import com.callbackdev.thabit.domain.model.Habit
import java.time.LocalDate

/**
 * The two records the app is willing to award, rendered as git tags
 * (`tag: longest-streak`, `tag: perfect-week`) in the log and in `stats.md`.
 *
 * There are exactly two, and neither is a score: no points, no levels, no coins,
 * no momentum with a secret formula (VISION §5, §6.4). Both are recomputable
 * from the user's own export, which is the only test a number has to pass to be
 * allowed on screen.
 */
sealed interface Record {

    /** The longest run of passes any test ever had. */
    data class LongestStreak(val habit: Habit, val streak: Streak) : Record

    /**
     * An ISO week in which every graded test passed **and every due day actually
     * ran**.
     *
     * The coverage condition is the whole point (PLANNING, Fase 2): with `no run`
     * days out of the denominators, a week the user saw on two days and passed
     * would otherwise score a flawless 100%. A record is earned, not obtained by
     * absence.
     */
    data class PerfectWeek(val week: IsoWeek, val graded: Int) : Record
}

object Records {

    /** The longest streak in the suite, or null when nothing has a streak yet. */
    fun longestStreak(history: SuiteHistory, today: LocalDate): Record.LongestStreak? =
        history.habits
            .map { it to Streaks.longest(history, it, today) }
            .filter { (_, streak) -> streak.length > 0 }
            .maxByOrNull { (_, streak) -> streak.length }
            ?.let { (habit, streak) -> Record.LongestStreak(habit, streak) }

    /** Was this closed ISO week perfect? */
    fun isPerfectWeek(history: SuiteHistory, week: IsoWeek, today: LocalDate): Boolean {
        if (!week.endInclusive.isBefore(today)) return false // still open: not yet a record
        val suiteStart = history.suiteStart() ?: return false
        if (week.endInclusive.isBefore(suiteStart)) return false

        var graded = 0
        var passed = 0
        for (date in week.dates()) {
            if (date.isBefore(suiteStart) || !date.isBefore(today)) continue
            val due = history.activeOn(date).any { it.occursOn(date) }
            if (!due) continue
            // Coverage first: a day the app never saw cannot be part of a perfect week.
            if (!history.ran(date)) return false
            val run = Verdicts.dayRun(history, date, today)
            graded += run.graded
            passed += run.passed
        }
        if (graded == 0 || passed != graded) return false

        // A quota is judged on the week, so it has its own say here.
        return Quotas.verdictsForWeek(history, week, today).all { it.met }
    }

    /** Every perfect week inside the closed range, newest first. */
    fun perfectWeeks(
        history: SuiteHistory,
        from: LocalDate,
        to: LocalDate,
        today: LocalDate
    ): List<Record.PerfectWeek> = IsoWeek.weeksBetween(from, to)
        .filter { isPerfectWeek(history, it, today) }
        .map { week ->
            val graded = week.dates()
                .filter { it.isBefore(today) }
                .sumOf { Verdicts.dayRun(history, it, today).graded }
            Record.PerfectWeek(week, graded)
        }
        .sortedByDescending { it.week }

    /** The records `stats.md`'s `## tags` section lists. */
    fun all(history: SuiteHistory, today: LocalDate): List<Record> {
        val suiteStart = history.suiteStart() ?: return emptyList()
        val records = mutableListOf<Record>()
        longestStreak(history, today)?.let { records += it }
        records += perfectWeeks(history, suiteStart, today.minusDays(1), today)
        return records
    }
}
