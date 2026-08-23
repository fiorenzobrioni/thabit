package com.callbackdev.thabit.notifications

import com.callbackdev.thabit.domain.model.Habit
import com.callbackdev.thabit.domain.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * When an alarm should ring — the arithmetic, with no Android anywhere near it.
 */
class ReminderPlanTest {

    private val rome = ZoneId.of("Europe/Rome")
    private val created = LocalDate.of(2026, 8, 1)

    private fun habit(
        schedule: Schedule = Schedule.Daily,
        remindAt: LocalTime? = LocalTime.of(7, 0),
        archivedAt: LocalDate? = null
    ) = Habit(
        id = 1L,
        name = "meditate 10 min",
        schedule = schedule,
        remindAt = remindAt,
        createdAt = created,
        archivedAt = archivedAt
    )

    private fun at(date: String, time: String) =
        ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), rome)

    @Test
    fun `a daily reminder fires today if the hour is still ahead`() {
        assertEquals(
            at("2026-08-20", "07:00"),
            ReminderPlan.nextFire(habit(), at("2026-08-20", "06:30"))
        )
    }

    @Test
    fun `an hour already gone moves to tomorrow`() {
        assertEquals(
            at("2026-08-21", "07:00"),
            ReminderPlan.nextFire(habit(), at("2026-08-20", "07:00"))
        )
    }

    @Test
    fun `a weekday schedule skips the days it does not name`() {
        // Saturday 22nd → Monday 24th.
        val weekdays = Schedule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        assertEquals(
            at("2026-08-24", "07:00"),
            ReminderPlan.nextFire(habit(schedule = weekdays), at("2026-08-22", "09:00"))
        )
    }

    @Test
    fun `an interval counts from the day the test was created`() {
        // Created on the 1st, every 3 days: the 1st, 4th, 7th, ... 22nd.
        assertEquals(
            at("2026-08-22", "07:00"),
            ReminderPlan.nextFire(
                habit(schedule = Schedule.Interval(3)),
                at("2026-08-20", "09:00")
            )
        )
    }

    @Test
    fun `a quota is a candidate every day — whether it still needs a run is decided at fire time`() {
        assertEquals(
            at("2026-08-20", "07:00"),
            ReminderPlan.nextFire(habit(schedule = Schedule.Quota(3)), at("2026-08-20", "06:00"))
        )
    }

    @Test
    fun `no reminder and an archived test both mean no alarm`() {
        assertNull(ReminderPlan.nextFire(habit(remindAt = null), at("2026-08-20", "06:00")))
        assertNull(
            ReminderPlan.nextFire(
                habit(archivedAt = LocalDate.of(2026, 8, 10)),
                at("2026-08-20", "06:00")
            )
        )
    }

    @Test
    fun `a test created in the future waits for its first day`() {
        val future = habit().copy(createdAt = LocalDate.of(2026, 9, 1))
        assertEquals(
            at("2026-09-01", "07:00"),
            ReminderPlan.nextFire(future, at("2026-08-20", "06:00"))
        )
    }

    @Test
    fun `the spring-forward gap moves the reminder on, it never loses it`() {
        // Italy jumps 02:00 → 03:00 on 29 March 2026: 02:30 does not exist.
        val nightOwl = habit(remindAt = LocalTime.of(2, 30))
            .copy(createdAt = LocalDate.of(2026, 1, 1))
        val fire = ReminderPlan.nextFire(nightOwl, at("2026-03-29", "01:00"))
        assertEquals(LocalTime.of(3, 30), fire?.toLocalTime())
        assertEquals(LocalDate.of(2026, 3, 29), fire?.toLocalDate())
    }

    @Test
    fun `the digest is tonight, or tomorrow night once the hour has passed`() {
        assertEquals(
            at("2026-08-20", "20:00"),
            ReminderPlan.nextDigest(LocalTime.of(20, 0), at("2026-08-20", "18:00"))
        )
        assertEquals(
            at("2026-08-21", "20:00"),
            ReminderPlan.nextDigest(LocalTime.of(20, 0), at("2026-08-20", "22:00"))
        )
    }
}
