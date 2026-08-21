package com.callbackdev.thabit.domain

import com.callbackdev.thabit.domain.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Streaks — severe on purpose, and the one place a `no run` day is *not*
 * forgiven (VISION §3.3.3, §3.3.8).
 */
class StreaksTest {

    private val d = Fixture.D0
    private val habit = Fixture.habit()

    @Test
    fun `consecutive passes count, and today counts as soon as it passes`() {
        val today = d.plusDays(4)
        val history = Fixture.greenRun(habit, d, today)
        val streak = Streaks.current(history, habit, today)
        assertEquals(5, streak.length)
        assertEquals(StreakUnit.DAYS, streak.unit)
        assertEquals(d, streak.from)
        assertEquals(today, streak.to)
    }

    @Test
    fun `today still pending does not break the chain - the day is not over`() {
        val today = d.plusDays(4)
        val history = Fixture.history(
            listOf(habit),
            (0L..3L).map { Fixture.pass(habit.id, d.plusDays(it)) },
            Fixture.ranFrom(d, today)
        )
        assertEquals(4, Streaks.current(history, habit, today).length)
    }

    @Test
    fun `a failure breaks the chain immediately`() {
        val today = d.plusDays(4)
        val checks = (0L..3L).map { Fixture.pass(habit.id, d.plusDays(it)) } +
            Fixture.fail(habit.id, today)
        val history = Fixture.history(listOf(habit), checks, Fixture.ranFrom(d, today))
        assertEquals(0, Streaks.current(history, habit, today).length)
    }

    @Test
    fun `skips hold the chain, they do not extend it`() {
        val today = d.plusDays(4)
        val checks = listOf(
            Fixture.pass(habit.id, d),
            Fixture.pass(habit.id, d.plusDays(1)),
            Fixture.skip(habit.id, d.plusDays(2), note = "travel"),
            Fixture.pass(habit.id, d.plusDays(3)),
            Fixture.pass(habit.id, today)
        )
        val history = Fixture.history(listOf(habit), checks, Fixture.ranFrom(d, today))
        assertEquals(4, Streaks.current(history, habit, today).length)
    }

    @Test
    fun `a no-run day breaks the chain - a streak is passes somebody typed`() {
        val today = d.plusDays(4)
        // The app was never opened on day 2, so nobody typed anything there.
        val present = Fixture.ranFrom(d, today) - d.plusDays(2)
        val checks = listOf(0L, 1L, 3L, 4L).map { Fixture.pass(habit.id, d.plusDays(it)) }
        val history = Fixture.history(listOf(habit), checks, present)
        assertEquals(2, Streaks.current(history, habit, today).length)
    }

    @Test
    fun `closing the app on the hard days cannot keep a streak alive`() {
        // The gaming this asymmetry exists to refuse: nine invisible days would
        // make the chain immortal if a `no run` were treated like a skip.
        val today = d.plusDays(10)
        val history = Fixture.history(
            listOf(habit),
            listOf(Fixture.pass(habit.id, d), Fixture.pass(habit.id, today)),
            setOf(d, today)
        )
        assertEquals(1, Streaks.current(history, habit, today).length)
    }

    @Test
    fun `a weekday test only counts the days it was due`() {
        val mwf = Fixture.habit(
            schedule = Schedule.Weekdays(
                setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
            ),
            createdAt = LocalDate.of(2026, 8, 3)
        )
        val monday = LocalDate.of(2026, 8, 3)
        val today = LocalDate.of(2026, 8, 10) // the following Monday
        val dueDays = listOf(0L, 2L, 4L, 7L).map { monday.plusDays(it) }
        val history = Fixture.history(
            listOf(mwf),
            dueDays.map { Fixture.pass(mwf.id, it) },
            Fixture.ranFrom(monday, today)
        )
        assertEquals(4, Streaks.current(history, mwf, today).length)
    }

    @Test
    fun `a quota's streak counts weeks, because a quota is judged on the week`() {
        val quota = Fixture.habit(
            schedule = Schedule.Quota(2),
            createdAt = LocalDate.of(2026, 8, 3)
        )
        val w34 = LocalDate.of(2026, 8, 17)
        val checks = listOf(
            Fixture.pass(quota.id, LocalDate.of(2026, 8, 3)),
            Fixture.pass(quota.id, LocalDate.of(2026, 8, 5)),
            Fixture.pass(quota.id, LocalDate.of(2026, 8, 10)),
            Fixture.pass(quota.id, LocalDate.of(2026, 8, 12)),
            Fixture.pass(quota.id, w34),
            Fixture.pass(quota.id, w34.plusDays(2))
        )
        val today = LocalDate.of(2026, 8, 21)
        val history = Fixture.history(
            listOf(quota), checks,
            Fixture.ranFrom(LocalDate.of(2026, 8, 3), today)
        )
        val streak = Streaks.current(history, quota, today)
        assertEquals(StreakUnit.WEEKS, streak.unit)
        assertEquals(3, streak.length)
    }

    @Test
    fun `a quota week still short but still open is not a miss yet`() {
        val start = LocalDate.of(2026, 8, 10) // Monday of week 33
        val quota = Fixture.habit(schedule = Schedule.Quota(3), createdAt = start)
        val thisMonday = LocalDate.of(2026, 8, 17) // week 34
        val checks = listOf(
            Fixture.pass(quota.id, start),
            Fixture.pass(quota.id, start.plusDays(1)),
            Fixture.pass(quota.id, start.plusDays(2)),
            Fixture.pass(quota.id, thisMonday) // 1 of 3 so far, week still running
        )
        val today = LocalDate.of(2026, 8, 18)
        val history = Fixture.history(listOf(quota), checks, Fixture.ranFrom(start, today))
        // Last week's 3/3 still stands; this week has not failed anything.
        assertEquals(1, Streaks.current(history, quota, today).length)
    }

    @Test
    fun `an open quota week keeps its full target instead of shrinking to today`() {
        val monday = LocalDate.of(2026, 8, 17)
        val quota = Fixture.habit(schedule = Schedule.Quota(3), createdAt = monday)
        val history = Fixture.history(
            listOf(quota),
            listOf(Fixture.pass(quota.id, monday)),
            setOf(monday)
        )
        // One run in on Monday evening is 1 of 3, not "met" — otherwise the test
        // would vanish from the file for the rest of the week.
        val week = Quotas.weekOf(history, quota, monday, monday)!!
        assertEquals(3, week.target)
        assertEquals(1, week.done)
        assertEquals(0, Streaks.current(history, quota, monday).length)
    }

    @Test
    fun `a week the app never saw breaks a quota streak too`() {
        val start = LocalDate.of(2026, 8, 3) // Monday, week 32
        val quota = Fixture.habit(schedule = Schedule.Quota(2), createdAt = start)
        val checks = listOf(0L, 2L, 14L, 16L).map { Fixture.pass(quota.id, start.plusDays(it)) }
        // Week 33 (10-16 August) was never opened: not a failure, but not a pass.
        val present = Fixture.ranFrom(start, start.plusDays(6)) +
            Fixture.ranFrom(start.plusDays(14), start.plusDays(20))
        val today = start.plusDays(21)
        val history = Fixture.history(listOf(quota), checks, present)
        assertEquals(1, Streaks.current(history, quota, today).length)
    }

    @Test
    fun `a quota week skipped down to nothing holds the chain`() {
        val start = LocalDate.of(2026, 8, 3)
        val quota = Fixture.habit(schedule = Schedule.Quota(2), createdAt = start)
        val checks = listOf(0L, 2L, 14L, 16L).map { Fixture.pass(quota.id, start.plusDays(it)) } +
            (7L..13L).map { Fixture.skip(quota.id, start.plusDays(it), note = "away") }
        val today = start.plusDays(21)
        val history = Fixture.history(listOf(quota), checks, Fixture.ranFrom(start, today))
        assertEquals(2, Streaks.current(history, quota, today).length)
    }

    @Test
    fun `the longest streak remembers where it was`() {
        val today = d.plusDays(9)
        val checks = buildList {
            (0L..4L).forEach { add(Fixture.pass(habit.id, d.plusDays(it))) }
            add(Fixture.fail(habit.id, d.plusDays(5)))
            (6L..9L).forEach { add(Fixture.pass(habit.id, d.plusDays(it))) }
        }
        val history = Fixture.history(listOf(habit), checks, Fixture.ranFrom(d, today))
        val longest = Streaks.longest(history, habit, today)
        assertEquals(5, longest.length)
        assertEquals(d, longest.from)
        assertEquals(d.plusDays(4), longest.to)
        assertEquals(4, Streaks.current(history, habit, today).length)
    }

    @Test
    fun `the longest streak is broken by an invisible day too`() {
        val today = d.plusDays(9)
        val checks = (0L..9L).filter { it != 4L }.map { Fixture.pass(habit.id, d.plusDays(it)) }
        val present = Fixture.ranFrom(d, today) - d.plusDays(4)
        val history = Fixture.history(listOf(habit), checks, present)
        // Four before the gap, five after it: never ten.
        assertEquals(5, Streaks.longest(history, habit, today).length)
    }

    @Test
    fun `an empty history has no streak and says so`() {
        val history = Fixture.history(listOf(habit))
        assertEquals(0, Streaks.current(history, habit, d).length)
        assertEquals(0, Streaks.longest(history, habit, d).length)
    }
}
