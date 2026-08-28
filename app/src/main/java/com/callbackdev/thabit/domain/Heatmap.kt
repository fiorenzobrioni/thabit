package com.callbackdev.thabit.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month

/**
 * One day of the contribution graph.
 *
 * [fraction] is **how much of the day's due suite passed**, or null when there is
 * nothing to colour. A day the app never saw is not a bad day, it is an unknown
 * one (VISION §3.3.8) — but it is not *nothing*, and until Fase 16 the grid drew
 * it as nothing.
 *
 * Every day already behind the reader draws a dim `·` when there is no level to
 * put there — `no run`, nothing due, or a day before the suite existed. That dot
 * is the **graph paper**, not a verdict about that day, which is the correction
 * Fase 16a made to 16: a mark every past cell carries cannot be read as an
 * accusation, and without it the grid had no shape at all — a handful of squares
 * floating in a void with nothing to place them against. It is the same paper
 * tsteps draws, and the same one a contribution graph has always had.
 *
 * The future stays blank: it has not happened, so there is no paper for it yet.
 *
 * The dot is deliberately **not** a fourth intensity. `·` says *there is no level
 * here*; `□` says *the day ran and passed none of it*, which is a different and
 * much worse fact, and it stays a different glyph. §3.3.8 forbids colouring an
 * unknown day like a failure — it never asked the graph to hide it.
 */
data class HeatmapCell(
    val date: LocalDate,
    val fraction: Double?,
    /** 0..[Heatmap.LEVELS] once the day is known, null when it is not. */
    val level: Int?,
    /** True when the day is behind the reader and there is no level to draw. */
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

        /** The paper: a day already behind the reader with no level to draw. */
        const val NO_RECORD: String = "·"
    }
}

/** One row of the grid: a day of the week, one cell per column. */
data class HeatmapRow(val day: DayOfWeek, val cells: List<HeatmapCell>)

/**
 * Where a month label goes: under the column its first week starts.
 *
 * The [month] and not its name: a month name is **data**, so it localizes, and
 * localizing is the renderer's job everywhere else in this app (VISION §1.3).
 * Keeping the value here is also what stops the grid from having to be rebuilt
 * when the reader changes language.
 */
data class MonthLabel(val column: Int, val month: Month)

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
        weeks: Int = WEEKS
    ): HeatmapGrid {
        val lastStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(weekStartsOn))
        val firstStart = lastStart.minusWeeks(weeks - 1L)

        val fractions = mutableMapOf<LocalDate, Double?>()
        var date = firstStart
        while (!date.isAfter(today)) {
            fractions[date] = fractionOf(history, date, today)
            date = date.plusDays(1)
        }

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
                        silent = fraction == null && !cellDate.isAfter(today)
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
                MonthLabel(column = column, month = start.month)
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
