package com.callbackdev.thabit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * ISO weeks, including the ones that make a naive implementation lie: the last
 * days of December that already belong to next year's week 1.
 */
class IsoWeekTest {

    @Test
    fun `a week runs Monday to Sunday`() {
        val week = IsoWeek.of(LocalDate.of(2026, 8, 20)) // a Thursday
        assertEquals(DayOfWeek.MONDAY, week.start.dayOfWeek)
        assertEquals(LocalDate.of(2026, 8, 17), week.start)
        assertEquals(LocalDate.of(2026, 8, 23), week.endInclusive)
        assertEquals(7, week.dates().size)
    }

    @Test
    fun `the last days of December can belong to next year's week one`() {
        // 30 December 2024 is the Monday that opens ISO week 1 of 2025.
        val silvester = IsoWeek.of(LocalDate.of(2024, 12, 30))
        assertEquals(1, silvester.week)
        assertEquals(2025, silvester.weekBasedYear)
        assertEquals(LocalDate.of(2024, 12, 30), silvester.start)
        // Keying on the number alone would merge this with January 2024's week 1.
        assertNotEquals(IsoWeek(2024, 1), silvester)
    }

    @Test
    fun `the first days of January can still belong to last year's last week`() {
        // 1 January 2027 is a Friday, still inside ISO week 53 of 2026.
        val newYear = IsoWeek.of(LocalDate.of(2027, 1, 1))
        assertEquals(53, newYear.week)
        assertEquals(2026, newYear.weekBasedYear)
    }

    @Test
    fun `week 53 exists in the years that have one`() {
        // 2026 is a 53-week ISO year: 2026-12-28 is Monday of week 53.
        val week = IsoWeek.of(LocalDate.of(2026, 12, 28))
        assertEquals(53, week.week)
        assertEquals(2026, week.weekBasedYear)
        assertEquals(LocalDate.of(2026, 12, 28), week.start)
    }

    @Test
    fun `weeksBetween covers every week the range touches, once each`() {
        val weeks = IsoWeek.weeksBetween(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 20))
        // Sat 1 Aug is in week 31; 20 Aug is in week 34.
        assertEquals(listOf(31, 32, 33, 34), weeks.map { it.week })
        assertEquals(weeks.size, weeks.distinct().size)
    }

    @Test
    fun `weeksBetween crosses a year boundary without collapsing weeks`() {
        val weeks = IsoWeek.weeksBetween(LocalDate.of(2026, 12, 20), LocalDate.of(2027, 1, 10))
        assertEquals(weeks.size, weeks.distinct().size)
        assertEquals(
            listOf(IsoWeek(2026, 51), IsoWeek(2026, 52), IsoWeek(2026, 53), IsoWeek(2027, 1)),
            weeks
        )
    }

    @Test
    fun `an inverted range has no weeks`() {
        assertEquals(emptyList<IsoWeek>(), IsoWeek.weeksBetween(Fixture.D0.plusDays(1), Fixture.D0))
    }

    @Test
    fun `weeks order by their week-based year first`() {
        val sorted = listOf(IsoWeek(2027, 1), IsoWeek(2026, 53)).sorted()
        assertEquals(listOf(IsoWeek(2026, 53), IsoWeek(2027, 1)), sorted)
    }
}
