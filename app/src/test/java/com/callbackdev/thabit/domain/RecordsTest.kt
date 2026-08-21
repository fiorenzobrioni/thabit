package com.callbackdev.thabit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import com.callbackdev.thabit.domain.model.Schedule
import org.junit.Test
import java.time.LocalDate

/**
 * The two records, and the condition that keeps `perfect-week` honest: a record
 * is earned, never obtained by absence.
 */
class RecordsTest {

    private val monday = LocalDate.of(2026, 8, 10) // week 33
    private val sunday = monday.plusDays(6)
    private val nextMonday = monday.plusDays(7)
    private val habit = Fixture.habit(createdAt = monday)
    private val week = IsoWeek.of(monday)

    @Test
    fun `a week fully attended and fully passed is perfect`() {
        val history = Fixture.greenRun(habit, monday, sunday)
        assertTrue(Records.isPerfectWeek(history, week, nextMonday))
    }

    @Test
    fun `a week with two days seen and passed is not perfect`() {
        // Without the coverage condition this would score a flawless 100%.
        val history = Fixture.history(
            listOf(habit),
            listOf(Fixture.pass(habit.id, monday), Fixture.pass(habit.id, monday.plusDays(1))),
            setOf(monday, monday.plusDays(1))
        )
        assertFalse(Records.isPerfectWeek(history, week, nextMonday))
    }

    @Test
    fun `a single failure costs the week`() {
        val checks = (0L..6L).map {
            if (it == 3L) Fixture.fail(habit.id, monday.plusDays(it))
            else Fixture.pass(habit.id, monday.plusDays(it))
        }
        val history = Fixture.history(listOf(habit), checks, Fixture.ranFrom(monday, sunday))
        assertFalse(Records.isPerfectWeek(history, week, nextMonday))
    }

    @Test
    fun `a skipped day does not cost the week - skips are neutral`() {
        val checks = (0L..6L).map {
            if (it == 3L) Fixture.skip(habit.id, monday.plusDays(it), note = "rest")
            else Fixture.pass(habit.id, monday.plusDays(it))
        }
        val history = Fixture.history(listOf(habit), checks, Fixture.ranFrom(monday, sunday))
        assertTrue(Records.isPerfectWeek(history, week, nextMonday))
    }

    @Test
    fun `a quota short of its target costs the week it is judged on`() {
        val quota = Fixture.habit(2L, "run 5k", schedule = Schedule.Quota(3), createdAt = monday)
        val checks = (0L..6L).map { Fixture.pass(habit.id, monday.plusDays(it)) } +
            listOf(Fixture.pass(quota.id, monday))
        val history = Fixture.history(listOf(habit, quota), checks, Fixture.ranFrom(monday, sunday))
        assertFalse(Records.isPerfectWeek(history, week, nextMonday))
    }

    @Test
    fun `the current week is never a record yet`() {
        val history = Fixture.greenRun(habit, monday, monday.plusDays(2))
        assertFalse(Records.isPerfectWeek(history, week, monday.plusDays(3)))
    }

    @Test
    fun `the longest streak of the suite wins the tag`() {
        val a = Fixture.habit(1L, "meditate", createdAt = monday, position = 0)
        val b = Fixture.habit(2L, "journal", createdAt = monday, position = 1)
        val today = monday.plusDays(10)
        val checks = (0L..9L).map { Fixture.pass(a.id, monday.plusDays(it)) } +
            (0L..4L).map { Fixture.pass(b.id, monday.plusDays(it)) }
        val history = Fixture.history(listOf(a, b), checks, Fixture.ranFrom(monday, today))
        val record = Records.longestStreak(history, today)!!
        assertEquals("meditate", record.habit.name)
        assertEquals(10, record.streak.length)
    }

    @Test
    fun `an empty suite earns no records and invents none`() {
        assertNull(Records.longestStreak(SuiteHistory.Empty, monday))
        assertTrue(Records.all(SuiteHistory.Empty, monday).isEmpty())
    }

    @Test
    fun `the tags section lists both kinds of record`() {
        val history = Fixture.greenRun(habit, monday, sunday)
        val records = Records.all(history, nextMonday)
        assertTrue(records.any { it is Record.LongestStreak })
        assertTrue(records.any { it is Record.PerfectWeek })
    }
}
