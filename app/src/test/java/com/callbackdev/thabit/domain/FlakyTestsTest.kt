package com.callbackdev.thabit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Flaky tests: a number and a fixed hint, never a finger.
 */
class FlakyTestsTest {

    private val d = Fixture.D0
    private val habit = Fixture.habit()

    private fun history(results: List<Boolean>, id: Long = 1L): SuiteHistory {
        val checks = results.mapIndexed { i, ok ->
            val date = d.plusDays(i.toLong())
            if (ok) Fixture.pass(id, date) else Fixture.fail(id, date)
        }
        return Fixture.history(
            listOf(habit),
            checks,
            Fixture.ranFrom(d, d.plusDays(results.size.toLong()))
        )
    }

    @Test
    fun `a test that keeps going red is reported with its fraction`() {
        val results = List(20) { it % 4 == 0 } // 25% pass rate
        val today = d.plusDays(results.size.toLong())
        val flaky = FlakyTests.detect(history(results), today).single()
        assertEquals(habit.id, flaky.habit.id)
        assertTrue(flaky.passRate < FlakyTests.THRESHOLD)
        assertEquals(20, flaky.samples)
        assertEquals("5/20", flaky.fraction)
    }

    @Test
    fun `a test added two days ago is never shamed at fifty percent`() {
        // The sample floor is the whole point: two runs are not evidence.
        val results = listOf(true, false)
        val today = d.plusDays(results.size.toLong())
        assertTrue(FlakyTests.detect(history(results), today).isEmpty())
    }

    @Test
    fun `a healthy test is not flaky`() {
        val results = List(20) { it % 5 != 0 } // 80%
        val today = d.plusDays(results.size.toLong())
        assertTrue(FlakyTests.detect(history(results), today).isEmpty())
    }

    @Test
    fun `a regression is not reported as flaky - it was solid`() {
        val results = List(43) { true } + List(3) { false }
        val today = d.plusDays(results.size.toLong())
        val h = history(results)
        val regressions = Regressions.detect(h, today)
        assertEquals(1, regressions.size)
        assertTrue(FlakyTests.detect(h, today, regressions).isEmpty())
    }

    @Test
    fun `the window is thirty days, so an old bad patch stops counting`() {
        val results = List(20) { false } + List(20) { true }
        val today = d.plusDays(results.size.toLong())
        assertTrue(FlakyTests.detect(history(results), today).isEmpty())
    }

    @Test
    fun `the rule is stated in one line for the export`() {
        assertTrue(FlakyTests.RULE.contains("${FlakyTests.MIN_SAMPLES}"))
        assertTrue(FlakyTests.RULE.contains("${FlakyTests.WINDOW_DAYS}"))
    }
}
