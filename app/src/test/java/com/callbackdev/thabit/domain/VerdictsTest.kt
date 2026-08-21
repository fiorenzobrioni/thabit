package com.callbackdev.thabit.domain

import com.callbackdev.thabit.domain.model.AssertSpec
import com.callbackdev.thabit.domain.model.HabitType
import com.callbackdev.thabit.domain.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The verdicts — including the two the app is proudest of not inventing:
 * `NO_RUN` for a day nobody was there, and a quota that refuses to fail a
 * Tuesday.
 */
class VerdictsTest {

    private val d = Fixture.D0                       // Sat 1 Aug 2026
    private val today = d.plusDays(10)

    private val meditate = Fixture.habit(1L, "meditate 10 min", position = 0)
    private val pushups = Fixture.habit(
        2L, "pushups", HabitType.COUNTER,
        assert = AssertSpec(30.0, "reps"), position = 1
    )
    private val noSugar = Fixture.habit(3L, "no sugar", HabitType.AVOID, position = 2)

    // ---- per-test states -------------------------------------------------

    @Test
    fun `an untouched test is pending while the day is open and a fail once it closes`() {
        assertEquals(TestState.PENDING, Verdicts.resolve(meditate, null, closed = false))
        assertEquals(TestState.FAIL, Verdicts.resolve(meditate, null, closed = true))
    }

    @Test
    fun `an avoid test holds while the day is open and passes at the commit`() {
        // It asserts an absence, so it is never pre-checked and never pending:
        // it holds, and it passes because the day ended without it being broken.
        assertEquals(TestState.HOLDING, Verdicts.resolve(noSugar, null, closed = false))
        assertEquals(TestState.PASS, Verdicts.resolve(noSugar, null, closed = true))
    }

    @Test
    fun `a broken avoid test is a failure the user typed`() {
        val broken = Fixture.fail(noSugar.id, d)
        assertEquals(TestState.FAIL, Verdicts.resolve(noSugar, broken, closed = false))
    }

    @Test
    fun `a counter mid-way is pending today and a fail tomorrow`() {
        val half = Fixture.progress(pushups.id, d, 12.0)
        assertEquals(TestState.PENDING, Verdicts.resolve(pushups, half, closed = false))
        assertEquals(TestState.FAIL, Verdicts.resolve(pushups, half, closed = true))
    }

    @Test
    fun `a skip is a skip on both sides of the boundary`() {
        val skipped = Fixture.skip(meditate.id, d)
        assertEquals(TestState.SKIP, Verdicts.resolve(meditate, skipped, closed = false))
        assertEquals(TestState.SKIP, Verdicts.resolve(meditate, skipped, closed = true))
    }

    // ---- build results ---------------------------------------------------

    @Test
    fun `all passed is a green build`() {
        val history = Fixture.history(
            listOf(meditate, noSugar),
            listOf(Fixture.pass(1L, d)),
            setOf(d)
        )
        val run = Verdicts.dayRun(history, d, today)
        assertEquals(BuildResult.PASSED, run.result)
        assertEquals(2, run.passed) // the avoid test passed at the commit
        assertEquals("2/2", run.fraction)
    }

    @Test
    fun `a mixed day is unstable and carries its arithmetic`() {
        val history = Fixture.history(
            listOf(meditate, pushups, noSugar),
            listOf(Fixture.pass(1L, d), Fixture.progress(2L, d, 12.0)),
            setOf(d)
        )
        val run = Verdicts.dayRun(history, d, today)
        assertEquals(BuildResult.UNSTABLE, run.result)
        assertEquals("2/3", run.fraction)
        assertTrue(run.result.hasBadge)
    }

    @Test
    fun `nothing passed is a red build`() {
        val history = Fixture.history(listOf(meditate, pushups), emptyList(), setOf(d))
        assertEquals(BuildResult.FAILED, Verdicts.dayRun(history, d, today).result)
    }

    @Test
    fun `skips leave the denominator entirely`() {
        val history = Fixture.history(
            listOf(meditate, pushups),
            listOf(Fixture.pass(1L, d), Fixture.skip(2L, d, note = "rest day")),
            setOf(d)
        )
        val run = Verdicts.dayRun(history, d, today)
        assertEquals(BuildResult.PASSED, run.result)
        assertEquals("1/1", run.fraction)
        assertEquals(1, run.skipped)
    }

    @Test
    fun `a day with nothing due earns no badge and no commit`() {
        val weekend = Fixture.habit(schedule = Schedule.Weekdays(setOf(DayOfWeek.MONDAY)))
        val history = Fixture.history(listOf(weekend), emptyList(), setOf(d)) // d is a Saturday
        val run = Verdicts.dayRun(history, d, today)
        assertEquals(BuildResult.NOT_SCHEDULED, run.result)
        assertFalse(run.result.hasBadge)
        assertFalse(run.hasCommit)
    }

    // ---- the app is allowed to not know ----------------------------------

    @Test
    fun `a day the app never saw is unknown, not failed`() {
        val history = Fixture.history(listOf(meditate), emptyList(), present = emptySet())
        val run = Verdicts.dayRun(history, d, today)
        assertEquals(BuildResult.NO_RUN, run.result)
        assertFalse(run.ran)
        assertFalse(run.result.hasBadge)
        assertFalse(run.hasCommit)
        // No per-test outcomes at all: the app has nothing to say about that day.
        assertTrue(run.outcomes.isEmpty())
    }

    @Test
    fun `coming back from nine days offline produces zero invented failures`() {
        val start = d
        val returnDay = d.plusDays(9)
        val history = Fixture.history(
            listOf(meditate),
            listOf(Fixture.pass(1L, returnDay)),
            present = setOf(returnDay)
        )
        val runs = Verdicts.runs(history, start, returnDay, returnDay.plusDays(1))
        assertEquals(9, runs.count { it.result == BuildResult.NO_RUN })
        assertEquals(0, runs.count { it.result == BuildResult.FAILED })
        assertEquals(1, runs.count { it.result == BuildResult.PASSED })
        assertEquals(0, runs.count { it.hasCommit && it.result == BuildResult.NO_RUN })
    }

    @Test
    fun `nothing due and nobody there are different facts`() {
        val weekend = Fixture.habit(schedule = Schedule.Weekdays(setOf(DayOfWeek.MONDAY)))
        val seen = Fixture.history(listOf(weekend), emptyList(), setOf(d))
        val unseen = Fixture.history(listOf(weekend), emptyList(), emptySet())
        assertEquals(BuildResult.NOT_SCHEDULED, Verdicts.dayRun(seen, d, today).result)
        assertEquals(BuildResult.NO_RUN, Verdicts.dayRun(unseen, d, today).result)
    }

    // ---- today is the working tree ---------------------------------------

    @Test
    fun `today's verdict is provisional and reads on what has been acted on`() {
        val history = Fixture.history(
            listOf(meditate, pushups),
            listOf(Fixture.pass(1L, today)),
            setOf(today)
        )
        val run = Verdicts.dayRun(history, today, today)
        assertFalse(run.closed)
        assertEquals(1, run.pending)   // pushups is not a failure at breakfast
        assertEquals(BuildResult.PASSED, run.result)
        assertEquals("1/1", run.fraction)
    }

    // ---- quota -----------------------------------------------------------

    @Test
    fun `a quota is listed while the week wants runs and disappears once met`() {
        val run5k = Fixture.habit(9L, "run 5k", schedule = Schedule.Quota(3), createdAt = d)
        val monday = LocalDate.of(2026, 8, 3)
        val history = Fixture.history(
            listOf(run5k),
            listOf(Fixture.pass(9L, monday), Fixture.pass(9L, monday.plusDays(1))),
            Fixture.ranFrom(monday, monday.plusDays(3))
        )
        val thursday = monday.plusDays(3)
        val stillDue = Verdicts.outcomesOn(history, thursday, thursday)
        assertEquals(1, stillDue.size)
        assertEquals(TestState.PENDING, stillDue.single().state)

        val met = Fixture.history(
            listOf(run5k),
            listOf(
                Fixture.pass(9L, monday),
                Fixture.pass(9L, monday.plusDays(1)),
                Fixture.pass(9L, monday.plusDays(2))
            ),
            Fixture.ranFrom(monday, thursday)
        )
        assertTrue(Verdicts.outcomesOn(met, thursday, thursday).isEmpty())
    }

    @Test
    fun `a quota never fails a day`() {
        val run5k = Fixture.habit(9L, "run 5k", schedule = Schedule.Quota(3), createdAt = d)
        val monday = LocalDate.of(2026, 8, 3)
        val history = Fixture.history(listOf(run5k), emptyList(), setOf(monday))
        // Monday closed with nothing done: a daily test would be red here.
        val run = Verdicts.dayRun(history, monday, monday.plusDays(1))
        assertEquals(BuildResult.NOT_SCHEDULED, run.result)
        assertEquals(0, run.failed)
    }

    @Test
    fun `a quota the user did act on counts on the day they acted`() {
        val run5k = Fixture.habit(9L, "run 5k", schedule = Schedule.Quota(3), createdAt = d)
        val monday = LocalDate.of(2026, 8, 3)
        val history = Fixture.history(listOf(run5k), listOf(Fixture.pass(9L, monday)), setOf(monday))
        val run = Verdicts.dayRun(history, monday, monday.plusDays(1))
        assertEquals(BuildResult.PASSED, run.result)
        assertEquals("1/1", run.fraction)
    }

    @Test
    fun `quota state states the week's progress`() {
        val run5k = Fixture.habit(9L, "run 5k", schedule = Schedule.Quota(3), createdAt = d)
        val monday = LocalDate.of(2026, 8, 3)
        val history = Fixture.history(listOf(run5k), listOf(Fixture.pass(9L, monday)), setOf(monday))
        val week = Quotas.weekOf(history, run5k, monday.plusDays(2), monday.plusDays(2))!!
        assertEquals(1, week.done)
        assertEquals(3, week.target)
        assertFalse(week.met)
        assertFalse(week.failed) // the week is not over: nothing has failed yet
    }

    @Test
    fun `a pass survives a schedule edit that would no longer ask for that day`() {
        // The user passed a daily test on Tuesday, then narrowed the schedule to
        // mon,wed,fri. Tuesday's `[x]` was earned under the old rule and stays.
        val tuesday = LocalDate.of(2026, 8, 4)
        val narrowed = Fixture.habit(
            schedule = Schedule.Weekdays(
                setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
            ),
            createdAt = d
        )
        val history = Fixture.history(
            listOf(narrowed),
            listOf(Fixture.pass(narrowed.id, tuesday)),
            setOf(tuesday)
        )
        val run = Verdicts.dayRun(history, tuesday, today)
        assertEquals(BuildResult.PASSED, run.result)
        assertEquals(1, run.passed)
    }

    @Test
    fun `a day the schedule skips and the user ignored stays out of the run`() {
        val narrowed = Fixture.habit(
            schedule = Schedule.Weekdays(setOf(DayOfWeek.MONDAY)),
            createdAt = d
        )
        val tuesday = LocalDate.of(2026, 8, 4)
        val history = Fixture.history(listOf(narrowed), emptyList(), setOf(tuesday))
        assertTrue(Verdicts.outcomesOn(history, tuesday, today).isEmpty())
    }

    @Test
    fun `a test archived today is already out of today's run`() {
        val gone = Fixture.habit(4L, "old habit", archivedAt = d)
        val history = Fixture.history(listOf(gone), emptyList(), setOf(d))
        assertTrue(Verdicts.outcomesOn(history, d, today).isEmpty())
    }
}
