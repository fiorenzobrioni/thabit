package com.callbackdev.thabit.domain.model

import com.callbackdev.thabit.domain.IsoWeek
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * When a test is due — the four schemes of the MVP (VISION §8).
 *
 * The canonical string form ([format]/[parse]) is the one the file shows, the
 * one Room stores and the one the export writes: one spelling for `when:`
 * everywhere, so a user reading `every 2d` in `habits.test` finds the same three
 * characters in their own CSV.
 *
 * **Quota is the odd one and was prototyped first** (PLANNING, Fase 2): it is the
 * only scheme that touches the daily verdict, the denominators, the streak, the
 * health, the heatmap intensity and the week separator at once. Its rule, fixed
 * here and honoured by every engine downstream: **a quota never fails a day, it
 * passes or fails the ISO week** (VISION §5). Every day of an unmet week is a
 * candidate — [occursOn] says true — but the day's build result only ever counts
 * a quota test the user actually acted on, and the verdict lands on the week
 * separator in the log.
 */
sealed interface Schedule {

    /** Calendar-level occurrence: does this schedule put the test on [date] at all? */
    fun occursOn(date: LocalDate, anchor: LocalDate): Boolean

    /** The canonical string, identical in the file, the database and the export. */
    fun format(): String

    /** Every day. */
    data object Daily : Schedule {
        override fun occursOn(date: LocalDate, anchor: LocalDate): Boolean = true
        override fun format(): String = "daily"
    }

    /** A weekday set: `mon,wed,fri`. */
    data class Weekdays(val days: Set<DayOfWeek>) : Schedule {
        init { require(days.isNotEmpty()) { "a weekday schedule needs at least one day" } }

        override fun occursOn(date: LocalDate, anchor: LocalDate): Boolean =
            date.dayOfWeek in days

        // Stored in calendar order, never in insertion order: `mon,fri` and
        // `fri,mon` are the same schedule and must have one spelling.
        override fun format(): String =
            DayOfWeek.entries.filter { it in days }.joinToString(",") { NAMES[it.value - 1] }
    }

    /**
     * `n/week` — n runs within the ISO week, whichever days.
     *
     * ISO weeks (Monday-based) are used here regardless of the `week_starts`
     * setting: the setting moves the *display* grids (heatmap, README week
     * table), while a quota's verdict and the log's week separators are stated
     * in ISO terms by VISION §4.2/§5. One immovable definition beats a quota
     * whose meaning changes when a display preference flips.
     */
    data class Quota(val times: Int) : Schedule {
        init { require(times in 1..7) { "a weekly quota is between 1 and 7 runs" } }

        // Every day of the week is a candidate; whether the test is still *due*
        // today depends on the week's progress, which is a fact about checks and
        // not about the calendar (see DueTests).
        override fun occursOn(date: LocalDate, anchor: LocalDate): Boolean = true

        override fun format(): String = "$times/week"
    }

    /** `every Nd`, counted from the day the test was created. */
    data class Interval(val everyDays: Int) : Schedule {
        init { require(everyDays >= 1) { "an interval is at least one day" } }

        override fun occursOn(date: LocalDate, anchor: LocalDate): Boolean =
            !date.isBefore(anchor) &&
                ChronoUnit.DAYS.between(anchor, date) % everyDays == 0L

        override fun format(): String = "every ${everyDays}d"
    }

    companion object {
        private val NAMES = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
        private val QUOTA = Regex("""^(\d+)/week$""")
        private val INTERVAL = Regex("""^every (\d+)d$""")

        /** Parses [format]'s output back. Returns null on anything else — callers decide. */
        fun parse(raw: String): Schedule? {
            val text = raw.trim()
            if (text == "daily") return Daily
            QUOTA.matchEntire(text)?.let { m ->
                val times = m.groupValues[1].toIntOrNull() ?: return null
                return if (times in 1..7) Quota(times) else null
            }
            INTERVAL.matchEntire(text)?.let { m ->
                val days = m.groupValues[1].toIntOrNull() ?: return null
                return if (days >= 1) Interval(days) else null
            }
            val days = text.split(',').map { it.trim().lowercase() }
            if (days.isEmpty() || days.any { it !in NAMES }) return null
            return Weekdays(days.map { DayOfWeek.of(NAMES.indexOf(it) + 1) }.toSet())
        }
    }
}

/**
 * How many runs a schedule asks for over a closed date range — the denominator
 * of every rate that is not about a single day.
 *
 * For the day-shaped schemes it is a count of days. For a quota it is a count of
 * *runs*: each ISO week touching the range contributes its target, clamped by
 * the days of that week that are actually inside the range (and inside the
 * test's life), because a week the range only clips two days of cannot honestly
 * demand three runs.
 */
fun Habit.dueCount(from: LocalDate, to: LocalDate): Int {
    if (to.isBefore(from)) return 0
    val start = maxOf(from, createdAt)
    val end = archivedAt?.let { minOf(to, it.minusDays(1)) } ?: to
    if (end.isBefore(start)) return 0

    return when (val s = schedule) {
        is Schedule.Quota -> IsoWeek.weeksBetween(start, end).sumOf { week ->
            val days = ChronoUnit.DAYS.between(
                maxOf(week.start, start),
                minOf(week.endInclusive, end)
            ).toInt() + 1
            minOf(s.times, days)
        }
        else -> generateSequence(start) { d -> d.plusDays(1).takeIf { !it.isAfter(end) } }
            .count { s.occursOn(it, createdAt) }
    }
}
