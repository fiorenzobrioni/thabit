package com.callbackdev.thabit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The grace window: today plus the previous logical day, shrinking to nothing as
 * today runs out. The edges are where a habit tracker either keeps its promise
 * or quietly lets somebody rewrite last month.
 */
class AmendWindowTest {

    private val rome: ZoneId = ZoneId.of("Europe/Rome")
    private val midnight = AmendWindow(DayBoundary.Default)
    private val threeAm = AmendWindow(DayBoundary(LocalTime.of(3, 0)))

    private fun at(date: String, time: String) =
        LocalDateTime.parse("${date}T$time").atZone(rome).toInstant()

    @Test
    fun `today and yesterday are writable, the day before is history`() {
        val now = at("2026-08-21", "10:00")
        assertTrue(midnight.isWritable(LocalDate.of(2026, 8, 21), now, rome))
        assertTrue(midnight.isWritable(LocalDate.of(2026, 8, 20), now, rome))
        assertFalse(midnight.isWritable(LocalDate.of(2026, 8, 19), now, rome))
    }

    @Test
    fun `tomorrow is not writable either - the file does not run ahead`() {
        val now = at("2026-08-21", "10:00")
        assertFalse(midnight.isWritable(LocalDate.of(2026, 8, 22), now, rome))
    }

    @Test
    fun `only the previous day is the amendable one`() {
        val now = at("2026-08-21", "10:00")
        assertTrue(midnight.isAmendable(LocalDate.of(2026, 8, 20), now, rome))
        assertFalse(midnight.isAmendable(LocalDate.of(2026, 8, 21), now, rome))
    }

    @Test
    fun `coming back after a week away, only the last closed day can be amended`() {
        val now = at("2026-08-21", "10:00")
        (14..19).forEach { day ->
            assertFalse(midnight.isWritable(LocalDate.of(2026, 8, day), now, rome))
        }
        assertTrue(midnight.isWritable(LocalDate.of(2026, 8, 20), now, rome))
    }

    @Test
    fun `the grace shrinks from a full day to nothing as today passes`() {
        val morning = at("2026-08-21", "00:30")
        val night = at("2026-08-21", "23:30")
        assertEquals(Duration.ofMinutes(23 * 60 + 30), midnight.remaining(morning, rome))
        assertEquals(Duration.ofMinutes(30), midnight.remaining(night, rome))
    }

    @Test
    fun `with a day_ends at three the window closes at three, not at midnight`() {
        val now = at("2026-08-21", "01:00")
        // The logical day is still the 20th, so the 19th is the amendable one.
        assertTrue(threeAm.isWritable(LocalDate.of(2026, 8, 20), now, rome))
        assertTrue(threeAm.isWritable(LocalDate.of(2026, 8, 19), now, rome))
        assertFalse(threeAm.isWritable(LocalDate.of(2026, 8, 18), now, rome))
        assertEquals(at("2026-08-21", "03:00"), threeAm.closesAt(now, rome))
    }

    @Test
    fun `at the exact second the window closes, yesterday has already moved on`() {
        val closing = at("2026-08-21", "03:00")
        // 03:00 sharp starts the 21st: the 19th is now two days back, forever.
        assertTrue(threeAm.isWritable(LocalDate.of(2026, 8, 21), closing, rome))
        assertTrue(threeAm.isWritable(LocalDate.of(2026, 8, 20), closing, rome))
        assertFalse(threeAm.isWritable(LocalDate.of(2026, 8, 19), closing, rome))
    }

    @Test
    fun `a DST night inside the window keeps the window honest`() {
        // 25 October 2026 is 25 hours long in Rome; the grace follows the clock
        // rather than a hardcoded 24 hours.
        val now = at("2026-10-25", "01:30")
        val remaining = midnight.remaining(now, rome)
        assertTrue("expected more than 23h of grace, got $remaining", remaining.toHours() >= 23)
        assertEquals(at("2026-10-26", "00:00"), midnight.closesAt(now, rome))
    }

    @Test
    fun `the remaining grace is never negative`() {
        val now = at("2026-08-21", "23:59")
        assertFalse(midnight.remaining(now, rome).isNegative)
    }
}
