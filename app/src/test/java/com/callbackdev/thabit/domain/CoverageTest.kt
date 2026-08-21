package com.callbackdev.thabit.domain

import com.callbackdev.thabit.domain.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Coverage — the number that stops absence from looking like innocence.
 */
class CoverageTest {

    private val d = Fixture.D0
    private val habit = Fixture.habit()

    @Test
    fun `a fully attended stretch is full coverage`() {
        val today = d.plusDays(10)
        val history = Fixture.greenRun(habit, d, today)
        val report = Coverage.report(history, d, today, today)
        assertEquals(10, report.dueDays) // today is excluded: it always ran
        assertEquals(10, report.ranDays)
        assertEquals(0, report.noRunDays)
        assertEquals(1.0, report.fraction!!, 0.0)
        assertEquals("10 of 10", report.ratio)
    }

    @Test
    fun `days the app never saw are counted and stated`() {
        val today = d.plusDays(10)
        val present = Fixture.ranFrom(d, today) - setOf(d.plusDays(3), d.plusDays(4))
        val history = Fixture.history(listOf(habit), emptyList(), present)
        val report = Coverage.report(history, d, today, today)
        assertEquals(10, report.dueDays)
        assertEquals(8, report.ranDays)
        assertEquals(2, report.noRunDays)
        assertEquals("8 of 10", report.ratio)
    }

    @Test
    fun `days with nothing scheduled ask for nothing and are not counted`() {
        val mondays = Fixture.habit(
            schedule = Schedule.Weekdays(setOf(DayOfWeek.MONDAY)),
            createdAt = LocalDate.of(2026, 8, 3)
        )
        val start = LocalDate.of(2026, 8, 3)
        val today = LocalDate.of(2026, 8, 24)
        val history = Fixture.history(listOf(mondays), emptyList(), setOf(start))
        val report = Coverage.report(history, start, today, today)
        assertEquals(3, report.dueDays) // 3, 10 and 17 August
        assertEquals(1, report.ranDays)
    }

    @Test
    fun `coverage never counts days before the suite existed`() {
        val today = d.plusDays(5)
        val history = Fixture.greenRun(habit, d, today)
        val report = Coverage.report(history, d.minusDays(30), today, today)
        assertEquals(5, report.dueDays)
    }

    @Test
    fun `an empty suite has no coverage to report, not a zero percent`() {
        val report = Coverage.report(SuiteHistory.Empty, d, d.plusDays(10), d.plusDays(10))
        assertEquals(0, report.dueDays)
        assertNull(report.fraction)
    }

    @Test
    fun `the last thirty days window is thirty closed days`() {
        val today = d.plusDays(60)
        val history = Fixture.greenRun(habit, d, today)
        assertEquals(30, Coverage.lastDays(history, 30, today).dueDays)
    }
}
