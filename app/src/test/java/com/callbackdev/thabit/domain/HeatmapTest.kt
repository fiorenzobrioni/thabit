package com.callbackdev.thabit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The contribution graph's geometry and its honesty.
 *
 * The geometry is easy to get subtly wrong (a grid that drops a day, a column
 * that starts on the wrong weekday), and the honesty is the whole point: a blank
 * cell must mean "the app does not know", never "you failed".
 */
class HeatmapTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 20) // Thursday
    private val habit = Fixture.habit(1L, createdAt = today.minusDays(90))

    @Test
    fun `the grid is seven rows by twelve weeks, ending on today's week`() {
        val grid = Heatmap.build(Fixture.history(listOf(habit)), today)

        assertEquals(7, grid.rows.size)
        assertEquals(Heatmap.WEEKS, grid.columns)
        assertEquals(DayOfWeek.MONDAY, grid.rows.first().day)
        // Every day of the window is somewhere in the grid: 7 × 12 cells, and
        // the last column is the week today lives in.
        assertEquals(84, grid.rows.sumOf { it.cells.size })
        assertTrue(grid.rows.any { row -> row.cells.last().date == today })
    }

    @Test
    fun `the week starts where the reader's week starts`() {
        val grid = Heatmap.build(Fixture.history(listOf(habit)), today, DayOfWeek.SUNDAY)
        assertEquals(DayOfWeek.SUNDAY, grid.rows.first().day)
    }

    /**
     * The graph paper (Fase 16, widened in 16a): every day behind the reader
     * carries at least a dot, so the twelve weeks have a shape; only the future
     * is blank.
     *
     * A day nobody was there for still has no level — §3.3.8 stands, the app does
     * not know and does not pretend. What the dot says is *this is a day of the
     * window*, not *you missed it*: `□`, the day that ran and passed nothing, is
     * a different glyph on purpose.
     */
    @Test
    fun `the future is blank, every day behind the reader is at least a dot`() {
        val ran = today.minusDays(1)
        val history = Fixture.history(listOf(habit), listOf(Fixture.pass(1L, ran)), setOf(ran))
        val cells = Heatmap.build(history, today).rows.flatMap { it.cells }.associateBy { it.date }

        val future = cells.getValue(today.plusDays(1))
        assertNull(future.level)
        assertFalse(future.silent)
        assertEquals(" ", future.glyph)

        val nobodyThere = cells.getValue(today.minusDays(2))
        assertNull(nobodyThere.level)
        assertTrue(nobodyThere.silent)
        assertEquals("·", nobodyThere.glyph)

        assertEquals(1.0, cells.getValue(ran).fraction!!, 0.0)
    }

    /**
     * A day before the first test existed gets paper too, and that is the
     * correction 16a made to 16.
     *
     * The first draft left it blank, reasoning that a dot there would invent a
     * day somebody was supposed to show up for. That treated the dot as a
     * statement about the day, which it is not once **every** past cell carries
     * one: it is the grid, and a grid with holes in it is just a grid that is
     * hard to read. The graph the metaphor borrows from has always drawn the
     * whole window.
     */
    @Test
    fun `a day before the suite existed is paper, not a hole in it`() {
        val born = today.minusDays(3)
        val young = Fixture.habit(1L, createdAt = born)
        val cells = Heatmap.build(Fixture.history(listOf(young)), today)
            .rows.flatMap { it.cells }.associateBy { it.date }

        assertTrue(cells.getValue(born.minusDays(1)).silent)
        assertEquals("·", cells.getValue(born.minusDays(1)).glyph)
        assertNull(cells.getValue(born.minusDays(1)).level)
    }

    /** And the window really is covered: no cell behind today is left empty. */
    @Test
    fun `no day behind today is left without a mark`() {
        val grid = Heatmap.build(Fixture.history(listOf(habit)), today)
        val past = grid.rows.flatMap { it.cells }.filter { !it.date.isAfter(today) }
        assertTrue(past.isNotEmpty())
        assertTrue(past.none { it.glyph == " " })
        assertTrue(grid.rows.flatMap { it.cells }.filter { it.date.isAfter(today) }
            .all { it.glyph == " " })
    }

    @Test
    fun `a day that ran and passed nothing is drawn, not hidden`() {
        val date = today.minusDays(1)
        val history = Fixture.history(listOf(habit), listOf(Fixture.fail(1L, date)), setOf(date))
        val cell = Heatmap.build(history, today).rows.flatMap { it.cells }.first { it.date == date }

        assertEquals(0.0, cell.fraction!!, 0.0)
        assertEquals(0, cell.level)
        assertEquals("□", cell.glyph)
    }

    @Test
    fun `a day skipped down to nothing leaves the graph rather than scoring zero`() {
        val date = today.minusDays(1)
        val history = Fixture.history(
            listOf(habit),
            listOf(Fixture.skip(1L, date, note = "away")),
            setOf(date)
        )
        val cell = Heatmap.build(history, today).rows.flatMap { it.cells }.first { it.date == date }
        assertNull(cell.level)
    }

    @Test
    fun `intensity is relative to the reader's own days`() {
        // Three tests, and three days at three different fractions.
        val habits = (1L..3L).map { Fixture.habit(it, "test $it", createdAt = today.minusDays(90)) }
        val low = today.minusDays(3)
        val mid = today.minusDays(2)
        val high = today.minusDays(1)
        val history = Fixture.history(
            habits,
            listOf(
                Fixture.pass(1L, low),
                Fixture.pass(1L, mid), Fixture.pass(2L, mid),
                Fixture.pass(1L, high), Fixture.pass(2L, high), Fixture.pass(3L, high)
            ),
            setOf(low, mid, high)
        )
        val cells = Heatmap.build(history, today).rows.flatMap { it.cells }.associateBy { it.date }

        assertEquals(0, cells.getValue(low).level)
        assertEquals(1, cells.getValue(mid).level)
        assertEquals(2, cells.getValue(high).level)
    }

    @Test
    fun `a single good day is that reader's best, not a dim dot`() {
        val date = today.minusDays(1)
        val history = Fixture.history(listOf(habit), listOf(Fixture.pass(1L, date)), setOf(date))
        val cell = Heatmap.build(history, today).rows.flatMap { it.cells }.first { it.date == date }
        assertEquals(Heatmap.LEVELS - 1, cell.level)
    }

    @Test
    fun `month labels land on the column their month starts in`() {
        val grid = Heatmap.build(Fixture.history(listOf(habit)), today)
        // Twelve weeks span three or four months, each labelled once.
        assertTrue(grid.months.size in 3..4)
        assertEquals(grid.months.map { it.column }.distinct(), grid.months.map { it.column })
        // The month itself, not its name: naming it is the renderer's job, in the
        // reader's language (Fase 16a). Each label carries the month its column
        // actually starts in, which is the only thing this level can be wrong
        // about.
        val firstColumnDate = grid.rows.first().cells.first().date
        grid.months.forEach { label ->
            assertEquals(firstColumnDate.plusWeeks(label.column.toLong()).month, label.month)
        }
    }
}
