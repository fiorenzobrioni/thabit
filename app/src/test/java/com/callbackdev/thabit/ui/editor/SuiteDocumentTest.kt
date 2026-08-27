package com.callbackdev.thabit.ui.editor

import com.callbackdev.thabit.R
import com.callbackdev.thabit.domain.Fixture
import com.callbackdev.thabit.domain.StreakUnit
import com.callbackdev.thabit.domain.SuiteHistory
import com.callbackdev.thabit.domain.TestState
import com.callbackdev.thabit.domain.model.AssertSpec
import com.callbackdev.thabit.domain.model.Check
import com.callbackdev.thabit.domain.model.CheckState
import com.callbackdev.thabit.domain.model.HabitType
import com.callbackdev.thabit.domain.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * `habits.test`, asserted character by character.
 *
 * This is the app's most-read surface and every word on it is a promise, so the
 * document is checked as a value: the header's arithmetic, each row's live
 * detail, the empty state's first sentence.
 */
class SuiteDocumentTest {

    private val d = Fixture.D0 // Saturday 1 August 2026

    private val meditate = Fixture.habit(1L, "meditate 10 min", position = 0)
    private val pushups = Fixture.habit(
        2L, "pushups", HabitType.COUNTER,
        assert = AssertSpec(30.0, "reps", 10.0), position = 1
    )
    private val noSugar = Fixture.habit(3L, "no sugar", HabitType.AVOID, position = 2)

    private fun doc(
        history: SuiteHistory,
        date: LocalDate = d,
        wall: LocalDate = date,
        dayEnds: LocalTime = LocalTime.MIDNIGHT
    ) = SuiteDocument.of(history, date, wall, dayEnds)

    // ---- the header ------------------------------------------------------

    @Test
    fun `the header states the arithmetic of the day`() {
        val history = Fixture.history(
            listOf(meditate, pushups, noSugar),
            listOf(Fixture.pass(1L, d), Fixture.skip(2L, d, note = "rest day")),
            setOf(d)
        )
        assertEquals(
            "suite 2026-08-01 — 1 passed · 1 pending · 1 skipped",
            doc(history).suiteComment
        )
    }

    @Test
    fun `passed is stated even at zero, the rest only when it happened`() {
        val history = Fixture.history(listOf(meditate), emptyList(), setOf(d))
        assertEquals("suite 2026-08-01 — 0 passed · 1 pending", doc(history).suiteComment)
    }

    @Test
    fun `failures make it into the header`() {
        val history = Fixture.history(
            listOf(meditate, noSugar),
            listOf(Fixture.pass(1L, d), Fixture.fail(3L, d)),
            setOf(d)
        )
        assertEquals("suite 2026-08-01 — 1 passed · 1 failed", doc(history).suiteComment)
    }

    @Test
    fun `the logical day is declared only when it has drifted from the wall date`() {
        val history = Fixture.history(listOf(meditate), emptyList(), setOf(d))
        assertNull(doc(history).logicalDayComment)
        assertEquals(
            "logical day 2026-08-01 — ends 03:00",
            doc(history, wall = d.plusDays(1), dayEnds = LocalTime.of(3, 0)).logicalDayComment
        )
    }

    // ---- the rows --------------------------------------------------------

    @Test
    fun `a passed boolean carries the time it was typed`() {
        val history = Fixture.history(
            listOf(meditate),
            listOf(Check(1L, d, CheckState.PASS, at = LocalTime.of(7, 12))),
            setOf(d)
        )
        val row = doc(history).due.single()
        assertEquals(TestState.PASS, row.state)
        assertEquals("07:12", row.comment)
        assertEquals(RowDetail.Passed(LocalTime.of(7, 12)), row.detail)
    }

    @Test
    fun `a pending boolean says nothing rather than something`() {
        val history = Fixture.history(listOf(meditate), emptyList(), setOf(d))
        val row = doc(history).due.single()
        assertEquals(TestState.PENDING, row.state)
        assertNull(row.comment)
    }

    @Test
    fun `a counter mid-way shows its fraction and its unit`() {
        val history = Fixture.history(
            listOf(pushups),
            listOf(Fixture.progress(2L, d, 12.0)),
            setOf(d)
        )
        val row = doc(history).due.single()
        assertEquals(TestState.PENDING, row.state)
        assertEquals("12/30 reps", row.comment)
    }

    @Test
    fun `a counter that reached its target shows what was done, not the target`() {
        val history = Fixture.history(
            listOf(pushups),
            listOf(Check(2L, d, CheckState.PASS, value = 32.0)),
            setOf(d)
        )
        assertEquals("32 reps", doc(history).due.single().comment)
    }

    @Test
    fun `an untouched counter is zero of its target, never blank`() {
        val history = Fixture.history(listOf(pushups), emptyList(), setOf(d))
        assertEquals("0/30 reps", doc(history).due.single().comment)
    }

    @Test
    fun `an avoid test holds and says what holding means`() {
        val history = Fixture.history(listOf(noSugar), emptyList(), setOf(d))
        val row = doc(history).due.single()
        assertEquals(TestState.HOLDING, row.state)
        assertEquals("holds — asserts at commit", row.comment)
    }

    @Test
    fun `a broken avoid test carries its time and its note`() {
        val history = Fixture.history(
            listOf(noSugar),
            listOf(Check(3L, d, CheckState.FAIL, note = "had cake", at = LocalTime.of(20, 5))),
            setOf(d)
        )
        assertEquals("failed 20:05: had cake", doc(history).due.single().comment)
    }

    @Test
    fun `a skip states its reason, and its window when it has one`() {
        val history = Fixture.history(
            listOf(meditate),
            listOf(Fixture.skip(1L, d, note = "rest day")),
            setOf(d)
        )
        assertEquals("skip: rest day", doc(history).due.single().comment)

        val away = Fixture.history(
            listOf(meditate),
            listOf(Fixture.skip(1L, d, until = d.plusDays(6), note = "away")),
            setOf(d)
        )
        assertEquals("skip: away until 2026-08-07", doc(away).due.single().comment)
    }

    @Test
    fun `a quota still due shows the week it is being judged on`() {
        val monday = LocalDate.of(2026, 8, 3)
        val quota = Fixture.habit(4L, "run 5k", schedule = Schedule.Quota(3), createdAt = monday)
        val history = Fixture.history(
            listOf(quota),
            listOf(Fixture.pass(4L, monday)),
            Fixture.ranFrom(monday, monday.plusDays(2))
        )
        val row = doc(history, date = monday.plusDays(2)).due.single()
        assertEquals("1/3 this week", row.comment)
        assertEquals(RowDetail.Quota(1, 3), row.detail)
    }

    @Test
    fun `the emoji travels with the name, because it is the user's own`() {
        val read = Fixture.habit(5L, "read 20 pages").copy(emoji = "📖")
        val history = Fixture.history(listOf(read), emptyList(), setOf(d))
        assertEquals("read 20 pages 📖", doc(history).due.single().name)
    }

    // ---- the [+1] control ------------------------------------------------

    @Test
    fun `a counter within a dozen taps offers its increment`() {
        val history = Fixture.history(listOf(pushups), emptyList(), setOf(d))
        assertEquals(10.0, doc(history).due.single().incrementStep!!, 0.0)
    }

    @Test
    fun `a counter that would need thirteen taps does not pretend to be a shortcut`() {
        val pages = Fixture.habit(
            6L, "read", HabitType.COUNTER,
            assert = AssertSpec(20.0, "pages", 1.0)
        )
        val history = Fixture.history(listOf(pages), emptyList(), setOf(d))
        assertNull(doc(history).due.single().incrementStep)
    }

    @Test
    fun `a boolean never offers an increment`() {
        val history = Fixture.history(listOf(meditate), emptyList(), setOf(d))
        assertNull(doc(history).due.single().incrementStep)
    }

    // ---- what today does not ask for -------------------------------------

    @Test
    fun `a test the schedule skips today is commented out, with its reason`() {
        val mondays = Fixture.habit(
            7L, "deep work",
            schedule = Schedule.Weekdays(setOf(DayOfWeek.MONDAY)),
            createdAt = d
        )
        val document = doc(Fixture.history(listOf(mondays), emptyList(), setOf(d)))
        assertTrue(document.due.isEmpty())
        assertEquals("when: mon", document.notDue.single().reason)
        // The sentence beside it is a plural resource, asserted in both
        // languages in HabitsTestScreenTest; the control is a control.
        assertEquals(1, document.notDue.size)
        assertEquals("[show]", document.notDueControl(expanded = false))
        assertEquals("[hide]", document.notDueControl(expanded = true))
    }

    @Test
    fun `a quota already met leaves the file and says it is done`() {
        val monday = LocalDate.of(2026, 8, 3)
        val quota = Fixture.habit(4L, "run 5k", schedule = Schedule.Quota(2), createdAt = monday)
        val history = Fixture.history(
            listOf(quota),
            listOf(Fixture.pass(4L, monday), Fixture.pass(4L, monday.plusDays(1))),
            Fixture.ranFrom(monday, monday.plusDays(2))
        )
        val document = doc(history, date = monday.plusDays(2))
        assertTrue(document.due.isEmpty())
        assertEquals("2/2 this week — done", document.notDue.single().reason)
    }

    @Test
    fun `the collapsed line counts what it is collapsing`() {
        val a = Fixture.habit(7L, "a", schedule = Schedule.Weekdays(setOf(DayOfWeek.MONDAY)), createdAt = d)
        val b = Fixture.habit(8L, "b", schedule = Schedule.Weekdays(setOf(DayOfWeek.MONDAY)), createdAt = d)
        val document = doc(Fixture.history(listOf(a, b), emptyList(), setOf(d)))
        assertEquals(2, document.notDue.size)
        assertEquals("[show]", document.notDueControl(expanded = false))
    }

    @Test
    fun `nothing to collapse means no line at all`() {
        val document = doc(Fixture.history(listOf(meditate), emptyList(), setOf(d)))
        assertNull(document.notDueControl(expanded = false))
    }

    // ---- the expansion ---------------------------------------------------

    @Test
    fun `the spec says when, what it asserts, and how it is going`() {
        val history = Fixture.greenRun(pushups, d.minusDays(4), d.minusDays(1)).let {
            Fixture.history(
                listOf(pushups.copy(createdAt = d.minusDays(4), remindAt = LocalTime.of(7, 0))),
                (0L..3L).map { offset -> Fixture.pass(2L, d.minusDays(4).plusDays(offset)) },
                Fixture.ranFrom(d.minusDays(4), d)
            )
        }
        val spec = doc(history).due.single().spec
        assertEquals("daily", spec.schedule)
        assertEquals("reps >= 30", spec.assertText)
        assertEquals("07:00", spec.remind)
        assertEquals(4, spec.streak)
        assertEquals(StreakUnit.DAYS, spec.streakUnit)
        assertEquals(1.0, spec.health!!, 0.001)
    }

    @Test
    fun `a test with nothing behind it has no health to show, not a zero`() {
        val history = Fixture.history(listOf(meditate), emptyList(), setOf(d))
        assertNull(doc(history).due.single().spec.health)
    }

    @Test
    fun `a quota's streak is counted in weeks`() {
        val monday = LocalDate.of(2026, 8, 3)
        val quota = Fixture.habit(4L, "run 5k", schedule = Schedule.Quota(1), createdAt = monday)
        val history = Fixture.history(
            listOf(quota), listOf(Fixture.pass(4L, monday)), setOf(monday)
        )
        assertEquals(StreakUnit.WEEKS, doc(history, date = monday).due.single().spec.streakUnit)
    }

    // ---- the empty suite -------------------------------------------------

    @Test
    fun `an empty suite says so, and points somewhere`() {
        val document = doc(SuiteHistory.Empty)
        assertTrue(document.isEmpty)
        assertTrue(document.due.isEmpty())
        // The words are resources now (Fase 15) and are asserted in both
        // languages in HabitsTestScreenTest. What belongs here is the shape the
        // document decides: which lines, in which order, and the blank between.
        assertEquals(
            listOf(
                R.string.suite_empty_none,
                SuiteDocument.BLANK_LINE,
                R.string.suite_empty_add,
                R.string.suite_empty_readme
            ),
            SuiteDocument.emptyHints()
        )
    }

    @Test
    fun `the empty state points at the README tab only once that tab exists`() {
        assertFalse(SuiteDocument.emptyHints(readmeTab = false).contains(R.string.suite_empty_readme))
        assertTrue(SuiteDocument.emptyHints(readmeTab = true).contains(R.string.suite_empty_readme))
    }

    @Test
    fun `the status bar denominator excludes skips`() {
        val history = Fixture.history(
            listOf(meditate, pushups, noSugar),
            listOf(Fixture.pass(1L, d), Fixture.skip(2L, d)),
            setOf(d)
        )
        val document = doc(history)
        assertEquals(1, document.passed)
        assertEquals(2, document.graded) // meditate passed + no sugar holding
        assertEquals(3, document.suiteSize)
    }
}
