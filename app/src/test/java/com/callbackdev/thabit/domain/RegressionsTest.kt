package com.callbackdev.thabit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A regression is a habit that used to hold — reported once, without advice, and
 * never confused with a test that was flaky all along.
 */
class RegressionsTest {

    private val d = Fixture.D0
    private val habit = Fixture.habit()

    /** A run of results, one per day, with the presence to match. */
    private fun history(results: List<Boolean>): SuiteHistory {
        val checks = results.mapIndexed { i, ok ->
            val date = d.plusDays(i.toLong())
            if (ok) Fixture.pass(habit.id, date) else Fixture.fail(habit.id, date)
        }
        return Fixture.history(listOf(habit), checks, Fixture.ranFrom(d, d.plusDays(results.size.toLong())))
    }

    private fun today(results: List<Boolean>) = d.plusDays(results.size.toLong())

    @Test
    fun `a long green run now breaking is a regression, with its arithmetic`() {
        val results = List(43) { true } + listOf(false, false, true, false, true)
        val regression = Regressions.of(history(results), habit, today(results))
        assertNotNull(regression)
        assertEquals(43, regression!!.greenRun)
        assertEquals(3, regression.recentFails)
        assertEquals(5, regression.recentWindow)
        assertEquals(StreakUnit.DAYS, regression.unit)
    }

    @Test
    fun `two red days out of five is a bad week, not a regression`() {
        val results = List(43) { true } + listOf(false, true, false, true, true)
        assertNull(Regressions.of(history(results), habit, today(results)))
    }

    @Test
    fun `a test that was never solid is not regressing - it is flaky`() {
        val results = List(20) { it % 2 == 0 } + listOf(false, false, false, true, false)
        assertNull(Regressions.of(history(results), habit, today(results)))
    }

    @Test
    fun `too little history to judge produces no line`() {
        val results = listOf(true, false, false)
        assertNull(Regressions.of(history(results), habit, today(results)))
    }

    @Test
    fun `skipping a habit cannot fabricate a regression`() {
        val today = d.plusDays(50)
        val checks = (0L until 43L).map { Fixture.pass(habit.id, d.plusDays(it)) } +
            (43L until 50L).map { Fixture.skip(habit.id, d.plusDays(it), note = "away") }
        val history = Fixture.history(listOf(habit), checks, Fixture.ranFrom(d, today))
        assertNull(Regressions.of(history, habit, today))
    }

    @Test
    fun `disappearing for a week cannot fabricate a regression either`() {
        val today = d.plusDays(50)
        val checks = (0L until 43L).map { Fixture.pass(habit.id, d.plusDays(it)) }
        val present = Fixture.ranFrom(d, d.plusDays(42))
        val history = Fixture.history(listOf(habit), checks, present)
        assertNull(Regressions.of(history, habit, today))
    }

    @Test
    fun `the rule is stated in one line for the export`() {
        assertTrue(Regressions.RULE.contains("${Regressions.MIN_RECENT_FAILS}"))
        assertTrue(Regressions.RULE.contains("${Regressions.MIN_GREEN_RUN}"))
    }

    @Test
    fun `the suite's regressions come back worst green run first`() {
        val a = Fixture.habit(1L, "meditate", position = 0)
        val b = Fixture.habit(2L, "journal", position = 1)
        // Both series end on the same day, so both are judged on the same window.
        val long = List(43) { true } + List(3) { false }
        val short = List(22) { it % 2 == 0 } + List(21) { true } + List(3) { false }
        val today = d.plusDays(long.size.toLong())

        fun rows(id: Long, results: List<Boolean>) = results.mapIndexed { i, ok ->
            val date = d.plusDays(i.toLong())
            if (ok) Fixture.pass(id, date) else Fixture.fail(id, date)
        }

        val history = Fixture.history(
            listOf(a, b),
            rows(a.id, long) + rows(b.id, short),
            Fixture.ranFrom(d, today)
        )
        val found = Regressions.detect(history, today)
        assertEquals(listOf("meditate", "journal"), found.map { it.habit.name })
        assertEquals(listOf(43, 21), found.map { it.greenRun })
    }
}
