package com.callbackdev.thabit.ui.stats

import com.callbackdev.thabit.domain.Fixture
import com.callbackdev.thabit.domain.FlakyTests
import com.callbackdev.thabit.domain.Regressions
import com.callbackdev.thabit.domain.SuiteHistory
import com.callbackdev.thabit.domain.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * `stats.md`, line by line.
 *
 * The file's two promises are what this asserts: **every rate travels with its
 * fraction**, and **every rule is printed in the file** — a number nobody can
 * recompute from their own export is not allowed on this screen (VISION §5).
 */
class StatsDocumentTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 20)
    private val start: LocalDate = today.minusDays(60)

    private val meditate = Fixture.habit(1L, "meditate 10 min", createdAt = start)
    private val journal = Fixture.habit(2L, "journal", createdAt = start, position = 1)

    private fun document(history: SuiteHistory) = StatsDocument.of(history, today)

    /** Every closed day of the range ran; [passing] decides which ones passed. */
    private fun run(
        habits: List<com.callbackdev.thabit.domain.model.Habit>,
        days: Int,
        passing: (Long, Long) -> Boolean = { _, _ -> true }
    ): SuiteHistory {
        val dates = (1L..days.toLong()).map { today.minusDays(it) }
        val checks = habits.flatMap { habit ->
            dates.mapIndexed { index, date ->
                if (passing(habit.id, index.toLong())) Fixture.pass(habit.id, date)
                else Fixture.fail(habit.id, date)
            }
        }
        return Fixture.history(habits, checks, dates.toSet())
    }

    // ---- coverage ----------------------------------------------------------

    @Test
    fun `coverage counts the days that ran against the days that asked`() {
        // Ten closed days, of which three nobody was there for.
        val dates = (1L..10L).map { today.minusDays(it) }
        val seen = dates.filterIndexed { index, _ -> index % 4 != 0 }
        val history = Fixture.history(
            listOf(meditate),
            seen.map { Fixture.pass(1L, it) },
            seen.toSet()
        )
        val document = document(history)

        assertEquals(30, document.coverage.dueDays)
        assertEquals("${document.coverage.ranDays} of 30 days ran · " +
            "${document.coverage.noRunDays} days no run", document.coverageLine)
        // The percentage never appears without the count that made it.
        assertTrue(document.coverageBar.endsWith("%"))
        assertTrue(document.coverageLine.contains(" of "))
    }

    // ---- suite health ------------------------------------------------------

    @Test
    fun `the health table leads with health and states the window's fraction`() {
        val history = run(listOf(meditate, journal), days = 20) { id, index ->
            id == 1L || index % 2 == 0L
        }
        val table = document(history).healthTable

        // Numeric columns are right-aligned, header included: the padding and
        // the markdown alignment marker agree on what the column is.
        assertEquals("| test            | health | streak |   30d |", table[0])
        assertTrue(table[1].startsWith("| ---"))
        // Sorted by health: the test that never missed is first.
        assertTrue(table[2].contains("meditate 10 min"))
        assertTrue(table[3].contains("journal"))
        // `20/20` and not `20/30`: the window is thirty days, the denominator is
        // the due days that ran inside it (VISION §4.3).
        assertTrue(table[2].contains("20/20"))
    }

    @Test
    fun `a quota's streak says it counts weeks`() {
        val runner = Fixture.habit(3L, "run 5k", schedule = Schedule.Quota(1), createdAt = start)
        val dates = (1L..14L).map { today.minusDays(it) }
        val history = Fixture.history(listOf(runner), dates.map { Fixture.pass(3L, it) }, dates.toSet())

        assertTrue(document(history).healthTable.any { it.contains("w ") })
    }

    // ---- flaky and regressions ---------------------------------------------

    @Test
    fun `a flaky test is a rate with its fraction, never a finger`() {
        // Passes one day in four, for long enough to have something to say.
        val history = run(listOf(meditate), days = 24) { _, index -> index % 4 == 0L }
        val document = document(history)

        val line = document.flaky.single()
        assertTrue(line.startsWith("meditate 10 min — "))
        assertTrue(line.contains("pass rate over 30 days"))
        assertTrue(line.contains("/"))
        // The hint is fixed, factual, and the only advice on the screen.
        assertEquals(
            "a flaky test wants a smaller assert or a different schedule",
            StatsDocument.FLAKY_HINT
        )
    }

    @Test
    fun `a test added yesterday is not flaky with a fabricated rate`() {
        val fresh = Fixture.habit(9L, "cold shower", createdAt = today.minusDays(1))
        val history = Fixture.history(
            listOf(fresh),
            listOf(Fixture.fail(9L, today.minusDays(1))),
            setOf(today.minusDays(1))
        )
        assertTrue(document(history).flaky.isEmpty())
    }

    @Test
    fun `a regression says what it was and what it is doing now`() {
        // Twenty green days, then four red ones.
        val dates = (1L..24L).map { today.minusDays(it) }.reversed()
        val checks = dates.mapIndexed { index, date ->
            if (index < 20) Fixture.pass(1L, date) else Fixture.fail(1L, date)
        }
        val history = Fixture.history(listOf(meditate), checks, dates.toSet())
        val document = document(history)

        assertEquals(
            listOf("meditate 10 min — 20 days green, 4 of the last 5 red"),
            document.regressions
        )
        // A regression is never also reported as flaky: it was solid once.
        assertTrue(document.flaky.none { it.startsWith("meditate 10 min") })
    }

    // ---- tags --------------------------------------------------------------

    @Test
    fun `tags are rows that point at the commit that earned them`() {
        val history = run(listOf(meditate), days = 21)
        val document = document(history)

        val streak = document.tags.first { it.tag == "longest-streak" }
        assertTrue(streak.value.startsWith("meditate 10 min · "))
        assertTrue(streak.date <= today.minusDays(1))
        // The table's data rows line up with the tags, in order.
        assertEquals(document.tags.size + 2, document.tagTable.size)
        assertTrue(document.tagTable[2].contains(streak.tag))
        assertTrue(document.tags.any { it.tag == "perfect-week" })
    }

    // ---- the rules, and the empty file --------------------------------------

    @Test
    fun `the file prints the rules it computed itself with`() {
        val rules = StatsDocument.rules()
        assertTrue(rules.any { it.contains(FlakyTests.RULE) })
        assertTrue(rules.any { it.contains(Regressions.RULE) })
        // No secret formulas: the health EMA states its half-life too.
        assertTrue(rules.any { it.contains("half-life") })
    }

    @Test
    fun `an empty suite still has a grid, and says why it is empty`() {
        val document = document(SuiteHistory.Empty)

        assertTrue(document.isEmpty)
        assertEquals(7, document.heatmap.rows.size)
        assertEquals(0, document.heatmap.knownDays)
        assertTrue(document.healthTable.isEmpty())
        assertTrue(document.flaky.isEmpty())
        assertTrue(document.tagTable.isEmpty())
        assertFalse(StatsDocument.EMPTY_HINT.isBlank())
    }
}
