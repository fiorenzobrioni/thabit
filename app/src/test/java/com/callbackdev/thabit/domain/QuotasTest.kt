package com.callbackdev.thabit.domain

import com.callbackdev.thabit.domain.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The quota week — the unit a `n/week` schedule is judged on, and the one that
 * makes the log's week separator possible.
 */
class QuotasTest {

    private val monday = LocalDate.of(2026, 8, 10)
    private val sunday = monday.plusDays(6)
    private val nextMonday = monday.plusDays(7)
    private val quota = Fixture.habit(schedule = Schedule.Quota(3), createdAt = monday)

    @Test
    fun `a met week passes and never fails`() {
        val checks = listOf(0L, 2L, 4L).map { Fixture.pass(quota.id, monday.plusDays(it)) }
        val history = Fixture.history(listOf(quota), checks, Fixture.ranFrom(monday, sunday))
        val week = Quotas.weekOf(history, quota, monday, nextMonday)!!
        assertEquals(3, week.done)
        assertEquals(3, week.target)
        assertTrue(week.met)
        assertFalse(week.failed)
    }

    @Test
    fun `a closed week short of its target fails - on the week, not on a day`() {
        val checks = listOf(0L, 2L).map { Fixture.pass(quota.id, monday.plusDays(it)) }
        val history = Fixture.history(listOf(quota), checks, Fixture.ranFrom(monday, sunday))
        val week = Quotas.weekOf(history, quota, monday, nextMonday)!!
        assertEquals(2, week.done)
        assertTrue(week.failed)
        // and no single day of it was ever a failure
        val runs = Verdicts.runs(history, monday, sunday, nextMonday)
        assertEquals(0, runs.sumOf { it.failed })
    }

    @Test
    fun `skips shrink the target instead of failing the week`() {
        val checks = listOf(Fixture.pass(quota.id, monday)) +
            (1L..6L).map { Fixture.skip(quota.id, monday.plusDays(it), note = "away") }
        val history = Fixture.history(listOf(quota), checks, Fixture.ranFrom(monday, sunday))
        val week = Quotas.weekOf(history, quota, monday, nextMonday)!!
        assertEquals(1, week.available)
        assertEquals(1, week.target)
        assertTrue(week.met)
    }

    @Test
    fun `a week away with everything skipped makes no claim at all`() {
        val checks = (0L..6L).map { Fixture.skip(quota.id, monday.plusDays(it), note = "away") }
        val history = Fixture.history(listOf(quota), checks, Fixture.ranFrom(monday, sunday))
        val week = Quotas.weekOf(history, quota, monday, nextMonday)!!
        assertEquals(0, week.target)
        assertTrue(week.neutral)
        assertFalse(week.failed)
    }

    @Test
    fun `a week with no day run advances no quota claim`() {
        val history = Fixture.history(listOf(quota), emptyList(), present = emptySet())
        val week = Quotas.weekOf(history, quota, monday, nextMonday)!!
        assertFalse(week.ran)
        assertTrue(week.neutral)
        assertFalse(week.failed)
    }

    @Test
    fun `a test created mid-week only owes the days it existed for`() {
        val wednesday = monday.plusDays(2)
        val late = Fixture.habit(schedule = Schedule.Quota(5), createdAt = wednesday)
        val checks = (0L..4L).map { Fixture.pass(late.id, wednesday.plusDays(it)) }
        val history = Fixture.history(listOf(late), checks, Fixture.ranFrom(wednesday, sunday))
        val week = Quotas.weekOf(history, late, monday, nextMonday)!!
        assertEquals(5, week.available) // Wed to Sun
        assertEquals(5, week.target)
        assertTrue(week.met)
    }

    @Test
    fun `the week separator lists only the quotas that made a claim`() {
        val other = Fixture.habit(2L, "journal", createdAt = monday)
        val neutralQuota = Fixture.habit(
            3L, "sauna", schedule = Schedule.Quota(2), createdAt = monday
        )
        val checks = listOf(Fixture.pass(quota.id, monday)) +
            (0L..6L).map { Fixture.skip(neutralQuota.id, monday.plusDays(it)) }
        val history = Fixture.history(
            listOf(quota, other, neutralQuota), checks, Fixture.ranFrom(monday, sunday)
        )
        val verdicts = Quotas.verdictsForWeek(history, IsoWeek.of(monday), nextMonday)
        assertEquals(listOf(quota.id), verdicts.map { it.habitId })
    }

    @Test
    fun `a non-quota schedule has no quota week`() {
        val daily = Fixture.habit()
        val history = Fixture.history(listOf(daily))
        assertEquals(null, Quotas.weekOf(history, daily, monday, nextMonday))
        assertTrue(Quotas.weeksIn(history, daily, monday, sunday, nextMonday).isEmpty())
    }
}
