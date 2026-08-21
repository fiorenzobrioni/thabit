package com.callbackdev.thabit.domain

import java.time.LocalDate

/**
 * How many of the days that asked for something actually ran.
 *
 * This is the honest counterweight to `no run` (VISION §4.3): never opening the
 * app stops the red, and coverage is the number that says so out loud. Without
 * it, `stats.md` would let absence look like innocence — with it, the file
 * states the size of what it does not know.
 *
 * It is also what makes the file's title literal: §4.3 was called *coverage
 * report* before it contained a metric called coverage.
 */
data class CoverageReport(
    val from: LocalDate,
    val to: LocalDate,
    /** Days in the window on which the suite had at least one test scheduled. */
    val dueDays: Int,
    /** Of those, the days the app actually saw. */
    val ranDays: Int
) {
    val noRunDays: Int get() = dueDays - ranDays

    /** 0..1, or null when the window asked for nothing — no fabricated 100%. */
    val fraction: Double? get() = if (dueDays == 0) null else ranDays.toDouble() / dueDays

    /** `24 of 30 days ran` — the arithmetic that travels with the word. */
    val ratio: String get() = "$ranDays of $dueDays"
}

/**
 * Coverage over closed days only.
 *
 * Today is deliberately excluded: the user is looking at the screen, so today
 * always ran, and counting it would inflate every window by one day of
 * tautology.
 */
object Coverage {

    fun report(history: SuiteHistory, from: LocalDate, to: LocalDate, today: LocalDate): CoverageReport {
        val suiteStart = history.suiteStart()
        val start = if (suiteStart == null) from else maxOf(from, suiteStart)
        val end = minOf(to, today.minusDays(1))
        if (suiteStart == null || end.isBefore(start)) {
            return CoverageReport(from, to, dueDays = 0, ranDays = 0)
        }

        var due = 0
        var ran = 0
        var date = start
        while (!date.isAfter(end)) {
            if (history.activeOn(date).any { it.occursOn(date) }) {
                due++
                if (history.ran(date)) ran++
            }
            date = date.plusDays(1)
        }
        return CoverageReport(from, to, due, ran)
    }

    /** The last [days] closed days — the window `stats.md` shows. */
    fun lastDays(history: SuiteHistory, days: Int, today: LocalDate): CoverageReport =
        report(history, today.minusDays(days.toLong()), today.minusDays(1), today)
}
