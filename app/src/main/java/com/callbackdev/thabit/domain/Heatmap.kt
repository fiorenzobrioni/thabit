package com.callbackdev.thabit.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * One day of the contribution graph.
 *
 * [fraction] is **how much of the day's due suite passed**, or null when there is
 * nothing to colour. A day the app never saw is not a bad day, it is an unknown
 * one (VISION §3.3.8) — but it is not *nothing*, and until Fase 16 the grid drew
 * it as nothing.
 *
 * Three different facts were arriving at the same blank cell: a day outside the
 * suite's life (it could not have run), a day nobody was there for (`no run`),
 * and a day the schedule did not ask for. The first is genuinely outside the
 * graph's knowledge; the other two happened, inside a suite that existed, and
 * saying nothing about them was the grid keeping quiet about a fact `##
 * coverage` counts out loud two sections below. So [silent] separates them, and
 * they draw a dim `·` — the same mark tsteps uses for a day that happened and
 * produced nothing.
 *
 * That is deliberately **not** a fourth intensity: `·` says *there is no level
 * here*, while `□` says *the day ran and passed none of it*, which is a
 * different and much worse fact. The ramp is still three marks.
 */
data class HeatmapCell(
    val date: LocalDate,
    val fraction: Double?,
    /** 0..[Heatmap.LEVELS] once the day is known, null when it is not. */
    val level: Int?,
    /**
     * True when the day is over, the suite already existed, and there is still
     * nothing to colour: nobody was there, or nothing was due.
     */
    val silent: Boolean = false
) {
    val glyph: String
        get() = when {
            level != null -> GLYPHS.getOrNull(level) ?: " "
            silent -> NO_RECORD
            else -> " "
        }

    companion object {
        /**
         * The ramp, from a day that ran and passed nothing to a full one.
         *
         * Four states and not five: the mock in VISION §4.3 uses exactly these
         * three marks plus the blank, they all exist in JetBrains Mono, and
         * three intensity steps are as many as a 13sp cell can actually
         * distinguish. That is why the buckets below are tertiles.
         */
        val GLYPHS: List<String> = listOf("□", "▪", "■")

        /** A day inside the suite's life with nothing to colour. */
        const val NO_RECORD: String = "·"
    }
}

/** One row of the grid: a day of the week, one cell per column. */
data class HeatmapRow(val day: DayOfWeek, val cells: List<HeatmapCell>)

/** Where a month label goes: under the column its first week starts. */
data class MonthLabel(val column: Int, val label: String)

/**
 * The grid, drawn like a contribution graph: **seven rows** (days of the week)
 * by twelve columns (weeks), oldest column first.
 *
 * Seven rows and not the four the mock draws: the mock labels Mon/Wed/Fri/Sun
 * and a literal four-row grid would silently drop three days a week from a graph
 * whose whole job is showing every day. The labels stay on those four rows —
 * that is what the mock was really showing — and the rows between them carry
 * their days unlabelled, exactly like the graph this borrows from.
 */
data class HeatmapGrid(val rows: List<HeatmapRow>, val months: List<MonthLabel>) {
    val columns: Int get() = rows.firstOrNull()?.cells?.size ?: 0

    /** Days the graph actually knows something about — the empty-state test. */
    val knownDays: Int get() = rows.sumOf { row -> row.cells.count { it.level != null } }

    /** Days inside the suite's life the graph has nothing to colour for. */
    val silentDays: Int get() = rows.sumOf { row -> row.cells.count { it.silent } }
}

/**
 * The contribution graph of a habit suite.
 *
 * Intensity is **relative to the user's own days** (tertiles of their non-zero
 * fractions), never to an absolute: the graph answers "how consistent am I, by
 * my own standards", which is the only question it can answer honestly for
 * somebody whose suite is three tests and somebody else's is twelve.
 */
object Heatmap {

    const val LEVELS: Int = 3
    const val WEEKS: Int = 12

    fun build(
        history: SuiteHistory,
        today: LocalDate,
        weekStartsOn: DayOfWeek = DayOfWeek.MONDAY,
        weeks: Int = WEEKS,
        locale: Locale = Locale.ENGLISH
    ): HeatmapGrid {
        val lastStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(weekStartsOn))
        val firstStart = lastStart.minusWeeks(weeks - 1L)

        val fractions = mutableMapOf<LocalDate, Double?>()
        var date = firstStart
        while (!date.isAfter(today)) {
            fractions[date] = fractionOf(history, date, today)
            date = date.plusDays(1)
        }

        val suiteStart = history.suiteStart()
        val known = fractions.values.filterNotNull().filter { it > 0.0 }.sorted()
        val t1 = tertile(known, 1.0 / 3)
        val t2 = tertile(known, 2.0 / 3)

        val rows = (0L..6L).map { offset ->
            val day = weekStartsOn.plus(offset)
            HeatmapRow(
                day = day,
                cells = (0 until weeks).map { column ->
                    val cellDate = firstStart.plusWeeks(column.toLong()).plusDays(offset)
                    val fraction = fractions[cellDate]
                    HeatmapCell(
                        date = cellDate,
                        fraction = fraction,
                        level = fraction?.let { level(it, t1, t2) },
                        // A day before the first test existed could not have run,
                        // so it stays blank: the dot is for days the suite was
                        // alive for and still has nothing to show.
                        silent = fraction == null &&
                            !cellDate.isAfter(today) &&
                            suiteStart != null &&
                            !cellDate.isBefore(suiteStart)
                    )
                }
            )
        }

        var previousMonth = -1
        val months = (0 until weeks).mapNotNull { column ->
            val start = firstStart.plusWeeks(column.toLong())
            if (start.monthValue == previousMonth) {
                null
            } else {
                previousMonth = start.monthValue
                MonthLabel(
                    column = column,
                    label = start.month.getDisplayName(TextStyle.SHORT, locale).lowercase(locale)
                )
            }
        }
        return HeatmapGrid(rows, months)
    }

    /**
     * How much of that day's suite passed, or null when the day says nothing.
     *
     * Skips leave the denominator, so a day spent away and skipped is blank
     * rather than a zero — the same rule every other rate in the app follows.
     */
    private fun fractionOf(history: SuiteHistory, date: LocalDate, today: LocalDate): Double? {
        if (date.isAfter(today)) return null
        if (date < today && !history.ran(date)) return null
        val run = Verdicts.dayRun(history, date, today)
        val denominator = run.graded + run.pending
        if (denominator == 0) return null
        return run.passed.toDouble() / denominator
    }

    private fun level(fraction: Double, t1: Double?, t2: Double?): Int = when {
        fraction <= 0.0 -> 0
        t1 == null || t2 == null -> LEVELS - 1
        fraction < t1 -> 0
        fraction < t2 -> 1
        else -> 2
    }

    /**
     * Upper-bound style tertile (index = floor(size × f)): with `<` comparisons
     * this spreads N distinct values evenly and sends a single lonely value to
     * the top bucket — one good day is that user's best, not a dim dot.
     */
    private fun tertile(sorted: List<Double>, fraction: Double): Double? =
        sorted.getOrNull((sorted.size * fraction).toInt().coerceAtMost(sorted.size - 1))
}
