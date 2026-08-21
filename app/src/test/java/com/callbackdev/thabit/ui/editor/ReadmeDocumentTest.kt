package com.callbackdev.thabit.ui.editor

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.thabit.domain.Fixture
import com.callbackdev.thabit.domain.SuiteHistory
import com.callbackdev.thabit.domain.model.AssertSpec
import com.callbackdev.thabit.domain.model.Check
import com.callbackdev.thabit.domain.model.CheckState
import com.callbackdev.thabit.domain.model.HabitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

/**
 * `README.md`, sentence by sentence — and the verifiable half of §3.3.7.
 *
 * The last test in this file is the one that matters most: for every CI term the
 * other screens can be showing, the prose here says it in plain words while that
 * term is on screen. That is what turns "the metaphor is a gain, never a toll"
 * from a good intention into something a build can fail on.
 */
@RunWith(RobolectricTestRunner::class)
class ReadmeDocumentTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 20) // a Thursday
    private val yesterday: LocalDate = today.minusDays(1)

    private val resources: Resources
        get() = ApplicationProvider.getApplicationContext<Context>().resources

    private val meditate = Fixture.habit(1L, "meditate 10 min", createdAt = today.minusDays(30))
    private val read = Fixture.habit(
        2L, "read 20 pages", HabitType.COUNTER,
        assert = AssertSpec(20.0, "pages"), createdAt = today.minusDays(30), position = 1
    )
    private val noSugar = Fixture.habit(
        3L, "no sugar", HabitType.AVOID, createdAt = today.minusDays(30), position = 2
    )

    private fun readme(
        history: SuiteHistory,
        locale: Locale = Locale.ENGLISH
    ): List<String> = ReadmeDocument.build(history, today, DayOfWeek.MONDAY, locale, resources)

    private fun prose(history: SuiteHistory, locale: Locale = Locale.ENGLISH): String =
        readme(history, locale).joinToString("\n")

    // ---- the day in prose --------------------------------------------------

    @Test
    fun `the title is the day, written the way the language writes it`() {
        val lines = readme(Fixture.history(listOf(meditate), emptyList(), setOf(today)))
        assertEquals("# Thursday, August 20, 2026", lines.first())
    }

    @Test
    fun `today counts what happened, and never a zero`() {
        val history = Fixture.history(
            listOf(meditate, read, noSugar),
            listOf(Fixture.pass(1L, today), Fixture.skip(2L, today, note = "rest")),
            setOf(today)
        )
        val text = prose(history)

        assertTrue(text.contains("1 of 3 done"))
        assertTrue(text.contains("1 skipped"))
        // Nothing failed today, so nothing says so.
        assertFalse(text.contains("not done"))
    }

    @Test
    fun `an empty suite says so instead of counting nothing`() {
        assertTrue(prose(SuiteHistory.Empty).contains("There are no tests yet"))
    }

    // ---- the week ----------------------------------------------------------

    @Test
    fun `the week is a rectangular table, with today alive and bold`() {
        val history = Fixture.history(
            listOf(meditate),
            listOf(Fixture.pass(1L, today), Fixture.pass(1L, yesterday)),
            setOf(today, yesterday)
        )
        val table = readme(history).filter { it.startsWith("|") }

        assertEquals(9, table.size) // header + separator + seven days
        // Every row is padded to the same width: a source view with a ragged
        // table is simply a badly formatted file.
        assertEquals(1, table.map { it.length }.distinct().size)
        assertTrue(table.any { it.contains("**Thu 20**") })
        // Days that have not happened yet claim nothing at all.
        assertTrue(table.last().contains("— "))
    }

    @Test
    fun `a day the app never saw says so in the table, in plain words`() {
        // Yesterday ran; the day before it did not.
        val history = Fixture.history(
            listOf(meditate),
            listOf(Fixture.pass(1L, today), Fixture.pass(1L, yesterday)),
            setOf(today, yesterday)
        )
        assertTrue(prose(history).contains("app not opened"))
    }

    @Test
    fun `the footer says where the numbers came from`() {
        val history = Fixture.history(
            listOf(meditate),
            listOf(Fixture.pass(1L, yesterday)),
            setOf(yesterday)
        )
        assertTrue(prose(history).contains("*Worked out on this device · 1 day recorded*"))
    }

    // ---- the plain-language layer (VISION §3.3.7) ---------------------------

    @Test
    fun `every CI term the other screens can show is said here in words`() {
        // One day with everything on it: an avoid test holding, another one
        // broken (which is the only way an *open* day is ever mixed), an
        // amended yesterday, and a day nobody was there.
        val noSnoozing = Fixture.habit(
            4L, "no snoozing", HabitType.AVOID, createdAt = today.minusDays(30), position = 3
        )
        val history = Fixture.history(
            habits = listOf(meditate, read, noSugar, noSnoozing),
            checks = listOf(
                Fixture.pass(1L, today),
                Check(2L, today, CheckState.PROGRESS, value = 5.0),
                Fixture.fail(4L, today),
                Fixture.pass(1L, yesterday),
                Fixture.fail(2L, yesterday)
            ),
            present = setOf(today, yesterday),
            amended = setOf(yesterday)
        )
        val text = prose(history)

        // `[·]` / "holds" — the glyph nobody has met anywhere else.
        assertTrue(text.contains("asks nothing of you"))
        // `~ build unstable` — the verdict, as an arithmetic sentence.
        assertTrue(text.contains("So far 1 of the 2 you have answered are done."))
        assertTrue(text.contains("Yesterday: 3 of 4 done."))
        // `# amended` — the marker the log puts on a corrected day.
        assertTrue(text.contains("after it had closed"))
        // `no run` — the app is allowed to not know.
        assertTrue(text.contains("that day is not counted as failed"))
        // And no glossary section: every gloss is a sentence inside the prose.
        assertFalse(text.contains("## Glossary"))
    }

    @Test
    fun `a streak is worth a sentence only once it is a streak`() {
        fun historyOfPasses(days: Int): SuiteHistory {
            val dates = (0L until days).map { today.minusDays(it) }
            return Fixture.history(listOf(meditate), dates.map { Fixture.pass(1L, it) }, dates.toSet())
        }
        assertFalse(prose(historyOfPasses(2)).contains("has been going for"))
        assertTrue(prose(historyOfPasses(4)).contains("“meditate 10 min” has been going for 4 days."))
    }

    @Test
    fun `health is mentioned only when it has something to say`() {
        val green = Fixture.greenRun(meditate, today.minusDays(10), today)
        assertFalse(prose(green).contains("has held about"))

        // Failed far more often than not: a rate, stated, with no advice.
        val dates = (0L..9L).map { today.minusDays(it) }
        val checks = dates.mapIndexed { index, date ->
            if (index % 4 == 0) Fixture.pass(1L, date) else Fixture.fail(1L, date)
        }
        val shaky = Fixture.history(listOf(meditate), checks, dates.toSet())
        assertTrue(prose(shaky).contains("has held about"))
    }

    // ---- the other language ------------------------------------------------

    @Test
    @Config(qualifiers = "it")
    fun `and all of it in the reader's language, headings included`() {
        val history = Fixture.history(
            listOf(meditate, noSugar),
            listOf(Fixture.pass(1L, today)),
            setOf(today)
        )
        val text = prose(history, Locale.ITALIAN)

        assertTrue(text.contains("# Giovedì 20 agosto 2026"))
        assertTrue(text.contains("## Oggi"))
        assertTrue(text.contains("## Questa settimana"))
        assertTrue(text.contains("1 fatto su 2"))
        assertTrue(text.contains("non ti chiede niente"))
        assertTrue(text.contains("Calcolato su questo dispositivo"))
    }
}
