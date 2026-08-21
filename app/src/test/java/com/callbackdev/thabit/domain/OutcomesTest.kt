package com.callbackdev.thabit.domain

import com.callbackdev.thabit.domain.model.HabitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The graded sequence — and the three things that never enter it.
 */
class OutcomesTest {

    private val d = Fixture.D0
    private val habit = Fixture.habit()

    @Test
    fun `a test added yesterday is one out of one, never one out of thirty`() {
        val today = d.plusDays(1)
        val newHabit = Fixture.habit(createdAt = d)
        val history = Fixture.history(
            listOf(newHabit),
            listOf(Fixture.pass(newHabit.id, d)),
            Fixture.ranFrom(d, today)
        )
        val units = Outcomes.graded(history, newHabit, d.minusDays(30), today, today)
        assertEquals(1, units.size)
        assertEquals(1.0, Outcomes.passRate(history, newHabit, d.minusDays(30), today, today)!!, 0.0)
    }

    @Test
    fun `skips, unknown days and today's pending test all stay out`() {
        val today = d.plusDays(4)
        val present = Fixture.ranFrom(d, today) - d.plusDays(2) // day 2 never ran
        val history = Fixture.history(
            listOf(habit),
            listOf(
                Fixture.pass(habit.id, d),
                Fixture.skip(habit.id, d.plusDays(1)),
                Fixture.fail(habit.id, d.plusDays(3))
                // today: untouched, and the day is not over
            ),
            present
        )
        val units = Outcomes.graded(history, habit, d, today, today)
        assertEquals(listOf(d, d.plusDays(3)), units.map { it.at })
        assertEquals(listOf(true, false), units.map { it.passed })
    }

    @Test
    fun `an avoid test that was never broken counts as passed once the day closes`() {
        val avoid = Fixture.habit(type = HabitType.AVOID)
        val today = d.plusDays(3)
        val history = Fixture.history(listOf(avoid), emptyList(), Fixture.ranFrom(d, today))
        val units = Outcomes.graded(history, avoid, d, today, today)
        assertEquals(3, units.size) // the three closed days; today is still holding
        assertTrue(units.all { it.passed })
    }

    @Test
    fun `nothing graded means no rate at all, not a zero`() {
        val history = Fixture.history(listOf(habit))
        assertNull(Outcomes.passRate(history, habit, d, d.plusDays(9), d.plusDays(9)))
    }

    @Test
    fun `an archived test stops producing units on the day it left`() {
        val gone = Fixture.habit(archivedAt = d.plusDays(3))
        val today = d.plusDays(9)
        val history = Fixture.history(
            listOf(gone),
            (0L..2L).map { Fixture.pass(gone.id, d.plusDays(it)) },
            Fixture.ranFrom(d, today)
        )
        assertEquals(3, Outcomes.graded(history, gone, d, today, today).size)
    }
}
