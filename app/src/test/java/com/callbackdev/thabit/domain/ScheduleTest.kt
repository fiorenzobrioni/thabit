package com.callbackdev.thabit.domain

import com.callbackdev.thabit.domain.model.Schedule
import com.callbackdev.thabit.domain.model.dueCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The four schemes of the MVP, and the one spelling each of them has.
 *
 * The canonical string is what the file shows, what Room stores and what the
 * export writes, so a round trip that loses information would put three
 * different truths in front of the same user.
 */
class ScheduleTest {

    private val anchor = LocalDate.of(2026, 8, 1) // a Saturday

    @Test
    fun `every schedule survives a round trip through its canonical string`() {
        val schedules = listOf(
            Schedule.Daily,
            Schedule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
            Schedule.Quota(3),
            Schedule.Interval(2)
        )
        schedules.forEach { schedule ->
            assertEquals(schedule, Schedule.parse(schedule.format()))
        }
    }

    @Test
    fun `weekday sets have one spelling regardless of insertion order`() {
        val a = Schedule.Weekdays(setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY))
        val b = Schedule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
        assertEquals("mon,fri", a.format())
        assertEquals(a.format(), b.format())
    }

    @Test
    fun `garbage parses to null instead of to a guess`() {
        listOf("", "weekly", "8/week", "0/week", "every 0d", "mon,funday", "3 / week")
            .forEach { assertNull(it, Schedule.parse(it)) }
    }

    @Test
    fun `weekday schedule is due only on its days`() {
        val schedule = Schedule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        assertTrue(schedule.occursOn(LocalDate.of(2026, 8, 3), anchor))  // Monday
        assertTrue(schedule.occursOn(LocalDate.of(2026, 8, 5), anchor))  // Wednesday
        assertFalse(schedule.occursOn(LocalDate.of(2026, 8, 4), anchor)) // Tuesday
    }

    @Test
    fun `interval counts from the day the test was created`() {
        val schedule = Schedule.Interval(3)
        assertTrue(schedule.occursOn(anchor, anchor))
        assertFalse(schedule.occursOn(anchor.plusDays(1), anchor))
        assertTrue(schedule.occursOn(anchor.plusDays(3), anchor))
        assertTrue(schedule.occursOn(anchor.plusDays(30), anchor))
        // Before the anchor there is no test at all, let alone an occurrence.
        assertFalse(schedule.occursOn(anchor.minusDays(3), anchor))
    }

    @Test
    fun `an interval crossing a leap day counts real days, not calendar arithmetic`() {
        val leapAnchor = LocalDate.of(2028, 2, 27)
        val schedule = Schedule.Interval(2)
        assertTrue(schedule.occursOn(LocalDate.of(2028, 2, 29), leapAnchor)) // 27 + 2
        assertFalse(schedule.occursOn(LocalDate.of(2028, 3, 1), leapAnchor)) // 27 + 3
        assertTrue(schedule.occursOn(LocalDate.of(2028, 3, 2), leapAnchor))  // 27 + 4
    }

    @Test
    fun `a quota is a candidate every day - the week decides, never the day`() {
        val schedule = Schedule.Quota(3)
        (0L..6L).forEach { offset ->
            assertTrue(schedule.occursOn(anchor.plusDays(offset), anchor))
        }
    }

    @Test
    fun `due count over a window counts days for day-shaped schedules`() {
        val daily = Fixture.habit(schedule = Schedule.Daily, createdAt = anchor)
        assertEquals(10, daily.dueCount(anchor, anchor.plusDays(9)))

        val mwf = Fixture.habit(
            schedule = Schedule.Weekdays(
                setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
            ),
            createdAt = anchor
        )
        // Sat 1 Aug through Fri 14 Aug: two full weeks of mon/wed/fri.
        assertEquals(6, mwf.dueCount(anchor, LocalDate.of(2026, 8, 14)))
    }

    @Test
    fun `due count clamps to the days the test actually existed`() {
        val habit = Fixture.habit(createdAt = anchor.plusDays(5))
        // The window opens five days before the test did: those days ask nothing.
        assertEquals(5, habit.dueCount(anchor, anchor.plusDays(9)))
    }

    @Test
    fun `an archived test stops asking on the day it left the suite`() {
        val habit = Fixture.habit(createdAt = anchor, archivedAt = anchor.plusDays(3))
        assertEquals(3, habit.dueCount(anchor, anchor.plusDays(9)))
        assertTrue(habit.occursOn(anchor.plusDays(2)))
        assertFalse(habit.occursOn(anchor.plusDays(3)))
    }

    @Test
    fun `a quota asks for its target per ISO week, clamped by the days in range`() {
        val habit = Fixture.habit(schedule = Schedule.Quota(3), createdAt = LocalDate.of(2026, 8, 1))
        // Sat 1 - Sun 2 Aug is the tail of ISO week 31: two days left, so the
        // week can honestly ask for two runs, not three.
        assertEquals(2, habit.dueCount(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2)))
        // A full week asks for the target.
        assertEquals(3, habit.dueCount(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 9)))
        // Two full weeks, twice.
        assertEquals(6, habit.dueCount(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 16)))
    }

    @Test
    fun `an inverted range asks for nothing`() {
        val habit = Fixture.habit()
        assertEquals(0, habit.dueCount(anchor.plusDays(3), anchor))
    }
}
