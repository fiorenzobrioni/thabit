package com.callbackdev.thabit.ui.log

import com.callbackdev.thabit.domain.CommitHash
import com.callbackdev.thabit.domain.Fixture
import com.callbackdev.thabit.domain.IsoWeek
import com.callbackdev.thabit.domain.SuiteHistory
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
import java.time.LocalDate
import java.time.LocalTime

/**
 * `habits_history.diff`, word by word.
 *
 * The log is the file where the metaphor is thickest, so this is where the
 * promise of §3.3.7 is checked hardest: a verdict never appears without the
 * arithmetic that explains it, a quota is judged on its week and never on a day,
 * and a day nobody was there produces no commit at all rather than a red one.
 */
class LogDocumentTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 20)
    private val yesterday: LocalDate = today.minusDays(1)

    private val meditate = Fixture.habit(1L, "meditate 10 min", createdAt = today.minusDays(20))
    private val read = Fixture.habit(
        2L, "read 20 pages", HabitType.COUNTER,
        assert = AssertSpec(20.0, "pages"), createdAt = today.minusDays(20), position = 1
    )
    private val noSugar = Fixture.habit(
        3L, "no sugar", HabitType.AVOID, createdAt = today.minusDays(20), position = 2
    )

    private fun document(history: SuiteHistory) = LogDocument.of(history, today)

    // ---- the working tree -------------------------------------------------

    @Test
    fun `today sits on top as uncommitted changes`() {
        val history = Fixture.history(
            listOf(meditate, read),
            listOf(Fixture.pass(1L, today)),
            setOf(today)
        )
        assertEquals("1/2 passed · 1 pending", document(history).todaySummary)
    }

    @Test
    fun `an empty log says why it is empty, not just that it is`() {
        val document = document(SuiteHistory.Empty)
        assertTrue(document.isEmpty)
        assertNull(document.head)
        assertEquals(
            listOf("no commits yet", "", "a day commits when it ends — the suite is still empty"),
            LogDocument.emptyHints(hasSuite = false)
        )
        assertEquals(
            listOf("no commits yet", "", "today's run commits when the day ends"),
            LogDocument.emptyHints(hasSuite = true)
        )
    }

    // ---- one commit per day that ran --------------------------------------

    @Test
    fun `a day that ran is a commit, newest first, with a stable hash`() {
        val history = Fixture.history(
            listOf(meditate),
            listOf(Fixture.pass(1L, yesterday), Fixture.pass(1L, today.minusDays(2))),
            setOf(yesterday, today.minusDays(2))
        )
        val document = document(history)

        assertEquals(2, document.commitCount)
        assertEquals(listOf(yesterday, today.minusDays(2)), document.commits.map { it.date })
        assertEquals(CommitHash.of(yesterday), document.head)
        // Two days in a row is the longest run the suite has: the tag lands on
        // the commit the run ended on.
        assertEquals(
            "commit ${CommitHash.of(yesterday)}  (tag: longest-streak)",
            document.commits.first().headline
        )
    }

    @Test
    fun `a day nobody was there gets no commit at all`() {
        // Present yesterday, absent the day before: the missing day is not a red
        // build, it is a build that never started (VISION §3.3.8).
        val history = Fixture.history(
            listOf(meditate),
            listOf(Fixture.pass(1L, yesterday)),
            setOf(yesterday)
        )
        val document = document(history)

        assertEquals(1, document.commitCount)
        assertNull(document.commitOn(today.minusDays(2)))
    }

    @Test
    fun `today is never a commit — it is the working tree`() {
        val history = Fixture.history(listOf(meditate), listOf(Fixture.pass(1L, today)), setOf(today))
        assertNull(document(history).commitOn(today))
    }

    // ---- the three verdicts -----------------------------------------------

    @Test
    fun `a green day passes, and says the arithmetic that makes it green`() {
        val history = Fixture.history(
            listOf(meditate, read),
            listOf(Fixture.pass(1L, yesterday), Check(2L, yesterday, CheckState.PASS, value = 31.0)),
            setOf(yesterday)
        )
        val commit = document(history).commitOn(yesterday)!!

        assertEquals("suite: 2/2 passed", commit.message)
        assertEquals("✓ build passed (2/2)", commit.verdict?.text)
    }

    @Test
    fun `a mixed day is unstable, and the skip leaves the denominator`() {
        val history = Fixture.history(
            listOf(meditate, read, noSugar),
            listOf(
                Fixture.pass(1L, yesterday),
                Fixture.skip(2L, yesterday, note = "rest day")
                // no sugar untouched: an avoid test holds, and passes at the commit
            ),
            setOf(yesterday)
        )
        val commit = document(history).commitOn(yesterday)!!

        // The message counts every test the day asked for; the verdict grades
        // only what was gradable. Both numbers are stated, on purpose.
        assertEquals("suite: 2/3 passed · 1 skipped", commit.message)
        assertEquals("✓ build passed (2/2 · 1 skipped)", commit.verdict?.text)
    }

    @Test
    fun `a day where nothing passed fails`() {
        val history = Fixture.history(
            listOf(meditate),
            // A row exists (the app was there), the test was simply not done.
            listOf(Fixture.skip(1L, yesterday), Fixture.fail(1L, yesterday)),
            setOf(yesterday)
        )
        val commit = document(history).commitOn(yesterday)!!
        assertEquals("✗ build failed (0/1)", commit.verdict?.text)
    }

    @Test
    fun `a day that graded nothing earns no badge`() {
        val history = Fixture.history(
            listOf(meditate),
            listOf(Fixture.skip(1L, yesterday, note = "away")),
            setOf(yesterday)
        )
        val commit = document(history).commitOn(yesterday)!!
        assertNull(commit.verdict)
        assertEquals("suite: 0/1 passed · 1 skipped", commit.message)
    }

    // ---- the expansion ----------------------------------------------------

    @Test
    fun `the day expands into its diff, one line per test`() {
        val history = Fixture.history(
            listOf(meditate, read, noSugar),
            listOf(
                Check(1L, yesterday, CheckState.PASS, at = LocalTime.of(7, 3)),
                Check(2L, yesterday, CheckState.PROGRESS, value = 12.0),
                Fixture.skip(3L, yesterday, note = "birthday")
            ),
            setOf(yesterday)
        )
        val rows = document(history).commitOn(yesterday)!!.rows

        assertEquals("+ [x] meditate 10 min  # 07:03", rows[0].text)
        // A counter that never reached its target is a failed line, and it keeps
        // the number the user actually typed.
        assertEquals("- [ ] read 20 pages  # 12/20 pages", rows[1].text)
        assertEquals("~ [~] no sugar  # birthday", rows[2].text)
    }

    @Test
    fun `the suite's own changes are lines of that day's diff`() {
        val born = Fixture.habit(4L, "journal", createdAt = yesterday, position = 3)
        val gone = Fixture.habit(
            5L, "cold shower", createdAt = today.minusDays(10), archivedAt = yesterday, position = 4
        )
        val history = Fixture.history(
            listOf(meditate, born, gone),
            listOf(Fixture.pass(1L, yesterday)),
            setOf(yesterday)
        )
        val rows = document(history).commitOn(yesterday)!!.rows

        assertEquals("+ test added: \"journal\"", rows[0].text)
        assertEquals("- test archived: \"cold shower\"", rows[1].text)
    }

    // ---- the week ---------------------------------------------------------

    @Test
    fun `the week separator carries its rate and the delta against the week before`() {
        // Two full weeks: the older one half passed, the newer one all passed.
        val checks = mutableListOf<Check>()
        val days = mutableSetOf<LocalDate>()
        (1L..14L).forEach { back ->
            val date = today.minusDays(back)
            days += date
            checks += if (back <= 7) Fixture.pass(1L, date) else {
                if (back % 2L == 0L) Fixture.pass(1L, date) else Fixture.fail(1L, date)
            }
        }
        val history = Fixture.history(listOf(meditate), checks, days)
        val weeks = document(history).entries.filterIsInstance<LogEntry.Week>()

        val newest = weeks.first()
        assertEquals("${IsoWeek.of(yesterday)} · 100% passed", newest.label)
        assertEquals("+", newest.delta?.take(1))
        assertTrue(newest.deltaPositive)
        // The first week of the log has nothing to compare itself with, and does
        // not invent a delta against a week that never existed.
        assertNull(weeks.last().delta)
    }

    @Test
    fun `a quota is judged on the week, never on a day`() {
        val runner = Fixture.habit(
            6L, "run 5k", schedule = Schedule.Quota(3), createdAt = today.minusDays(20)
        )
        // Two runs in the closed week before this one: short, and the week is over.
        val lastWeekMonday = yesterday.with(java.time.DayOfWeek.MONDAY).minusWeeks(1)
        val checks = listOf(
            Fixture.pass(6L, lastWeekMonday),
            Fixture.pass(6L, lastWeekMonday.plusDays(1))
        )
        val days = (0L..6L).map { lastWeekMonday.plusDays(it) }.toSet()
        val history = Fixture.history(listOf(runner), checks, days)
        val week = document(history).entries
            .filterIsInstance<LogEntry.Week>()
            .first { it.week == IsoWeek.of(lastWeekMonday) }

        assertEquals(listOf("quota: run 5k 2/3 ✗"), week.quotas.map { it.text })
        // And no day of that week ever carries a quota failure of its own.
        document(history).commits.forEach { commit ->
            assertFalse(commit.verdict?.text?.contains("run 5k") == true)
        }
    }

    @Test
    fun `an open week that is still short makes no claim yet`() {
        val runner = Fixture.habit(
            6L, "run 5k", schedule = Schedule.Quota(3), createdAt = today.minusDays(20)
        )
        val history = Fixture.history(
            listOf(runner),
            listOf(Fixture.pass(6L, yesterday)),
            setOf(yesterday)
        )
        val week = document(history).entries.filterIsInstance<LogEntry.Week>().first()
        assertEquals(listOf("quota: run 5k 1/3"), week.quotas.map { it.text })
    }

    // ---- amend ------------------------------------------------------------

    @Test
    fun `only yesterday declares that it is still editable`() {
        val history = Fixture.history(
            listOf(meditate),
            listOf(Fixture.pass(1L, yesterday), Fixture.pass(1L, today.minusDays(2))),
            setOf(yesterday, today.minusDays(2))
        )
        val document = LogDocument.of(history, today, dayEnds = LocalTime.of(3, 0))

        assertEquals(LocalTime.of(3, 0), document.commitOn(yesterday)?.amendableUntil)
        assertTrue(document.commitOn(yesterday)!!.amendable)
        // Two days back is history, forever.
        assertNull(document.commitOn(today.minusDays(2))?.amendableUntil)
        assertFalse(document.commitOn(today.minusDays(2))!!.amendable)
    }

    @Test
    fun `an amended day carries the marker on its commit line`() {
        val history = Fixture.history(
            listOf(meditate),
            listOf(Fixture.pass(1L, yesterday)),
            setOf(yesterday),
            amended = setOf(yesterday)
        )
        val commit = document(history).commitOn(yesterday)!!

        assertTrue(commit.amended)
        assertEquals("commit ${CommitHash.of(yesterday)}  # amended", commit.headline)
    }

    // ---- tags -------------------------------------------------------------

    @Test
    fun `a run of one day is not a record`() {
        val history = Fixture.history(
            listOf(meditate),
            listOf(Fixture.pass(1L, yesterday)),
            setOf(yesterday)
        )
        // Every first day would otherwise wear `longest-streak`, which makes it
        // a tie between every day the suite has ever seen.
        assertEquals(emptyList<String>(), document(history).commitOn(yesterday)!!.tags)
    }

    @Test
    fun `a record is a tag on the commit that earned it`() {
        // A full closed week, everything passed, every day run.
        val monday = yesterday.with(java.time.DayOfWeek.MONDAY).minusWeeks(1)
        val days = (0L..6L).map { monday.plusDays(it) }
        val history = Fixture.history(
            listOf(meditate),
            days.map { Fixture.pass(1L, it) },
            days.toSet()
        )
        val commit = document(history).commitOn(days.last())!!

        assertTrue("perfect-week" in commit.tags)
        assertTrue(commit.headline.contains("(tag: perfect-week)"))
    }
}
