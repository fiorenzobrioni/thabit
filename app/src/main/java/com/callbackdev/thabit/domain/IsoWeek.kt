package com.callbackdev.thabit.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields

/**
 * One ISO-8601 week (Monday to Sunday), week-based year included.
 *
 * The log's separators (`--- week 34 · 89% passed ---`), the quota verdicts and
 * `perfect-week` all speak ISO weeks (VISION §4.2, §5). The `week_starts`
 * setting deliberately does **not** reach here: it moves display grids (the
 * heatmap columns, the README's seven-day table), not the definition of a week
 * a record is awarded on. A record whose meaning changes when a display
 * preference flips would not be recomputable from an export.
 *
 * Week-based year is carried explicitly because the last days of December can
 * belong to week 1 of the next year: `2026-12-31` is week 1 of 2027, and a map
 * keyed on the week number alone would silently merge two different weeks.
 */
data class IsoWeek(val weekBasedYear: Int, val week: Int) : Comparable<IsoWeek> {

    /** The Monday of this week. */
    val start: LocalDate
        get() = LocalDate.of(weekBasedYear, 1, 4)
            .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week.toLong())
            .with(DayOfWeek.MONDAY)

    /** The Sunday of this week, inclusive. */
    val endInclusive: LocalDate get() = start.plusDays(6)

    /** The seven dates of this week, Monday first. */
    fun dates(): List<LocalDate> = (0L..6L).map { start.plusDays(it) }

    override fun compareTo(other: IsoWeek): Int =
        compareValuesBy(this, other, IsoWeek::weekBasedYear, IsoWeek::week)

    /** `week 34` — the log's separator label; the year shows only when it differs. */
    override fun toString(): String = "week $week"

    companion object {
        fun of(date: LocalDate): IsoWeek = IsoWeek(
            weekBasedYear = date.get(IsoFields.WEEK_BASED_YEAR),
            week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        )

        /** Every ISO week touched by the closed range [from]..[to], in order. */
        fun weeksBetween(from: LocalDate, to: LocalDate): List<IsoWeek> {
            if (to.isBefore(from)) return emptyList()
            val weeks = mutableListOf<IsoWeek>()
            var cursor = from.with(DayOfWeek.MONDAY)
            val last = to.with(DayOfWeek.MONDAY)
            while (!cursor.isAfter(last)) {
                weeks += of(cursor)
                cursor = cursor.plusWeeks(1)
            }
            return weeks
        }
    }
}
