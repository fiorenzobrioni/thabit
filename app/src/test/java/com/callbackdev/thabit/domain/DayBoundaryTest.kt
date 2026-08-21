package com.callbackdev.thabit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The logical day: `day_ends`, DST, timezone moves, and the rule that the past
 * is never relabelled.
 */
class DayBoundaryTest {

    private val rome: ZoneId = ZoneId.of("Europe/Rome")
    private val threeAm = DayBoundary(LocalTime.of(3, 0))

    private fun at(date: String, time: String) =
        LocalDateTime.parse("${date}T$time").atZone(rome).toInstant()

    @Test
    fun `at midnight the logical day is the wall day and the mechanism is invisible`() {
        val boundary = DayBoundary.Default
        assertEquals(LocalDate.of(2026, 8, 21), boundary.logicalDate(at("2026-08-21", "00:00"), rome))
        assertEquals(LocalDate.of(2026, 8, 21), boundary.logicalDate(at("2026-08-21", "23:59"), rome))
    }

    @Test
    fun `one in the morning still belongs to yesterday when the day ends at three`() {
        // The phone says the 21st; the suite is still the 20th's. Undeclared,
        // this honesty looks like a bug — hence the header line in the file.
        assertEquals(
            LocalDate.of(2026, 8, 20),
            threeAm.logicalDate(at("2026-08-21", "01:00"), rome)
        )
        assertEquals(
            LocalDate.of(2026, 8, 21),
            threeAm.logicalDate(at("2026-08-21", "03:00"), rome)
        )
        assertEquals(
            LocalDate.of(2026, 8, 21),
            threeAm.logicalDate(at("2026-08-21", "03:01"), rome)
        )
    }

    @Test
    fun `a logical day spans exactly from its start to the next one's`() {
        val date = LocalDate.of(2026, 8, 20)
        assertEquals(at("2026-08-20", "03:00"), threeAm.startOf(date, rome))
        assertEquals(at("2026-08-21", "03:00"), threeAm.endOf(date, rome))
        assertEquals(threeAm.startOf(date.plusDays(1), rome), threeAm.endOf(date, rome))
    }

    @Test
    fun `the spring-forward day is 23 hours long`() {
        // Europe/Rome, 29 March 2026: 02:00 jumps to 03:00.
        val boundary = DayBoundary.Default
        val date = LocalDate.of(2026, 3, 29)
        val length = Duration.between(boundary.startOf(date, rome), boundary.endOf(date, rome))
        assertEquals(23, length.toHours())
    }

    @Test
    fun `the fall-back day is 25 hours long`() {
        // Europe/Rome, 25 October 2026: 03:00 falls back to 02:00.
        val boundary = DayBoundary.Default
        val date = LocalDate.of(2026, 10, 25)
        val length = Duration.between(boundary.startOf(date, rome), boundary.endOf(date, rome))
        assertEquals(25, length.toHours())
    }

    @Test
    fun `a day_ends inside the spring-forward gap moves with the clock`() {
        // 02:30 does not exist on 29 March 2026: the boundary lands at 03:30,
        // which is the honest answer — the day is short, not missing.
        val boundary = DayBoundary(LocalTime.of(2, 30))
        val start = boundary.startOf(LocalDate.of(2026, 3, 29), rome)
        assertEquals(at("2026-03-29", "03:30"), start)
        // And the day before it still resolves to itself.
        assertEquals(
            LocalDate.of(2026, 3, 28),
            boundary.logicalDate(at("2026-03-29", "01:00"), rome)
        )
    }

    @Test
    fun `the same instant is a different logical day in a different timezone`() {
        val instant = at("2026-08-21", "01:00")
        val tokyo = ZoneId.of("Asia/Tokyo")
        assertEquals(LocalDate.of(2026, 8, 20), threeAm.logicalDate(instant, rome))
        // 01:00 in Rome is 08:00 in Tokyo: a different, later logical day.
        assertNotEquals(
            threeAm.logicalDate(instant, rome),
            threeAm.logicalDate(instant, tokyo)
        )
        assertEquals(LocalDate.of(2026, 8, 21), threeAm.logicalDate(instant, tokyo))
    }

    @Test
    fun `changing day_ends mid-day moves today, never the checks already written`() {
        val instant = at("2026-08-21", "01:00")
        // Before the edit the user was living the 21st; after it, the 20th.
        assertEquals(LocalDate.of(2026, 8, 21), DayBoundary.Default.logicalDate(instant, rome))
        assertEquals(LocalDate.of(2026, 8, 20), threeAm.logicalDate(instant, rome))
        // The rows themselves carry their own date and are not recomputed: a
        // check written on the 21st stays a check on the 21st (see Check.date).
        val check = Fixture.pass(1L, LocalDate.of(2026, 8, 21))
        assertEquals(LocalDate.of(2026, 8, 21), check.date)
    }

    @Test
    fun `contains answers for the day it is asked about`() {
        val instant = at("2026-08-21", "01:00")
        assertTrue(threeAm.contains(LocalDate.of(2026, 8, 20), instant, rome))
        assertTrue(!threeAm.contains(LocalDate.of(2026, 8, 21), instant, rome))
    }
}
