package com.callbackdev.thabit.notifications

import androidx.test.core.app.ApplicationProvider
import com.callbackdev.thabit.domain.CommitHash
import com.callbackdev.thabit.domain.Fixture
import com.callbackdev.thabit.domain.TestOutcome
import com.callbackdev.thabit.domain.TestState
import com.callbackdev.thabit.domain.Verdicts
import com.callbackdev.thabit.domain.model.AssertSpec
import com.callbackdev.thabit.domain.model.HabitType
import com.callbackdev.thabit.ui.log.LogDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

/**
 * The words the shade gets.
 *
 * The rule under test is VISION §3.3.7 in the one place it is hardest to keep: a
 * notification has no file around it, so the **title** has to carry the fact and
 * its arithmetic in the reader's language, and the body is only allowed to
 * decorate what the title already said.
 */
@RunWith(RobolectricTestRunner::class)
class ThabitNotificationsTest {

    private val resources =
        ApplicationProvider.getApplicationContext<android.content.Context>().resources

    private val day = LocalDate.of(2026, 8, 20)
    private val today = day.plusDays(1)

    private val meditate = Fixture.habit(id = 1L, name = "meditate 10 min")
    private val read = Fixture.habit(
        id = 2L,
        name = "read 20 pages",
        type = HabitType.COUNTER,
        assert = AssertSpec(target = 20.0, unit = "pages")
    )
    private val phone = Fixture.habit(id = 3L, name = "no phone after 23:00", type = HabitType.AVOID)

    // ---- the daily commit -------------------------------------------------

    @Test
    fun `an unstable day says four of six in words, and git log underneath`() {
        val history = Fixture.history(
            habits = listOf(meditate, read),
            checks = listOf(Fixture.pass(1L, day), Fixture.fail(2L, day)),
            present = setOf(day)
        )
        val run = Verdicts.dayRun(history, day, today)
        val commit = LogDocument.of(history, today).commitOn(day)!!
        val content = ThabitNotifications.dailyCommit(run, commit, resources)

        // The verdict never travels as a bare token: the glyph and the numbers.
        assertEquals("~ 1 of 2 tests passed", content.title)
        assertFalse(content.summary.contains("\n"))
        assertTrue(content.summary.startsWith("commit ${CommitHash.of(day)}"))
        assertEquals(
            listOf(
                "commit ${CommitHash.of(day)}",
                "Date: Thu 2026-08-20",
                "suite: 1/2 passed",
                "~ build unstable (1/2)",
                "$ thabit log"
            ),
            content.expanded.lines()
        )
    }

    @Test
    fun `a green day and a red day both state the count`() {
        val green = Fixture.history(
            habits = listOf(meditate, read),
            checks = listOf(Fixture.pass(1L, day), Fixture.pass(2L, day)),
            present = setOf(day)
        )
        assertEquals(
            "✓ All 2 tests passed",
            ThabitNotifications.dailyCommit(
                Verdicts.dayRun(green, day, today),
                LogDocument.of(green, today).commitOn(day)!!,
                resources
            ).title
        )

        val red = Fixture.history(
            habits = listOf(meditate, read),
            checks = listOf(Fixture.fail(1L, day), Fixture.fail(2L, day)),
            present = setOf(day)
        )
        assertEquals(
            "✗ None of the 2 tests passed",
            ThabitNotifications.dailyCommit(
                Verdicts.dayRun(red, day, today),
                LogDocument.of(red, today).commitOn(day)!!,
                resources
            ).title
        )
    }

    @Test
    @Config(qualifiers = "it")
    fun `the title is the reader's language, the body stays the file's English`() {
        val history = Fixture.history(
            habits = listOf(meditate, read),
            checks = listOf(Fixture.pass(1L, day), Fixture.fail(2L, day)),
            present = setOf(day)
        )
        val italian = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val content = ThabitNotifications.dailyCommit(
            Verdicts.dayRun(history, day, today),
            LogDocument.of(history, today).commitOn(day)!!,
            italian
        )
        assertEquals("~ 1 test su 2 passati", content.title)
        // A commit is source: the verdict word, the check lines and the command
        // are code, and code reads the same everywhere (Fase 15).
        assertTrue(content.expanded.contains("build unstable"))
        assertTrue(content.expanded.contains("$ thabit log"))
    }

    @Test
    fun `an amended day carries its marker into the shade, like the log does`() {
        val history = Fixture.history(
            habits = listOf(meditate),
            checks = listOf(Fixture.pass(1L, day)),
            present = setOf(day),
            amended = setOf(day)
        )
        val content = ThabitNotifications.dailyCommit(
            Verdicts.dayRun(history, day, today),
            LogDocument.of(history, today).commitOn(day)!!,
            resources
        )
        assertTrue(content.expanded.lines().first().endsWith("# amended"))
    }

    // ---- the evening digest ----------------------------------------------

    @Test
    fun `the digest names what is open and says when the day ends`() {
        val history = Fixture.history(habits = listOf(meditate, read), present = setOf(today))
        val pending = Verdicts.outcomesOn(history, today, today)
            .filter { it.state == TestState.PENDING }
        val content = ThabitNotifications.pendingDigest(pending, LocalTime.MIDNIGHT, resources)

        assertEquals("2 tests still to do", content.title)
        assertEquals("meditate 10 min · read 20 pages", content.summary)
        assertEquals(
            listOf(
                "$ thabit --status",
                "- [ ] meditate 10 min",
                "- [ ] read 20 pages  # 0/20 pages",
                "# 2 pending — the day ends at 00:00"
            ),
            content.expanded.lines()
        )
    }

    @Test
    fun `a counter halfway through carries its fraction`() {
        val history = Fixture.history(
            habits = listOf(read),
            checks = listOf(Fixture.progress(2L, today, 12.0)),
            present = setOf(today)
        )
        val pending = Verdicts.outcomesOn(history, today, today)
        val content = ThabitNotifications.pendingDigest(pending, LocalTime.of(3, 0), resources)
        assertTrue(content.expanded.contains("- [ ] read 20 pages  # 12/20 pages"))
        assertTrue(content.expanded.contains("the day ends at 03:00"))
    }

    // ---- one test's reminder ---------------------------------------------

    @Test
    fun `a reminder's title is the test's own name, and the body says its state`() {
        val outcome = TestOutcome(meditate, TestState.PENDING)
        val content = ThabitNotifications.reminder(outcome, LocalTime.of(7, 0), resources)
        assertEquals("meditate 10 min", content.title)
        assertEquals("still to do", content.summary)
        assertEquals(
            listOf(
                "- [ ] meditate 10 min",
                "  when: daily",
                "  remind: \"07:00\"",
                "# a reminder is a nudge — it can arrive a few minutes late"
            ),
            content.expanded.lines()
        )
    }

    @Test
    fun `a counter's reminder states how far along it is`() {
        val outcome = TestOutcome(read, TestState.PENDING, Fixture.progress(2L, today, 12.0))
        val content = ThabitNotifications.reminder(outcome, LocalTime.of(21, 0), resources)
        assertEquals("read 20 pages", content.title)
        assertEquals("12 of 20 pages", content.summary)
    }

    @Test
    fun `an avoid test is reminded as holding, never as a chore`() {
        val outcome = TestOutcome(phone, TestState.HOLDING)
        val content = ThabitNotifications.reminder(outcome, LocalTime.of(22, 55), resources)
        assertEquals("holding, it fails only if you break it", content.summary)
        // `[·]` is the glyph nobody has met elsewhere, so it never travels alone.
        assertTrue(content.expanded.startsWith("- [·] no phone after 23:00"))
    }

    @Test
    fun `an emoji rides along with the name, because it is the user's own mark`() {
        val outcome = TestOutcome(read.copy(emoji = "📖"), TestState.PENDING)
        val content = ThabitNotifications.reminder(outcome, LocalTime.of(7, 0), resources)
        assertEquals("📖 read 20 pages", content.title)
    }

    // ---- the register rule inside the transcript (Fase 15) ----------------

    /**
     * The `#` footer of a notification is the one line in it that is not a copy
     * of the file: it is the app talking to whoever is looking at the lock
     * screen. So the sentence is the reader's, while the transcript above it —
     * the command, the checkbox rows, the counter's fraction — is the file's own
     * line, verbatim, and does not move. `pending` stays too: it is the name of
     * a state, printed the same way `habits.test` prints it.
     */
    @Test
    @Config(qualifiers = "it")
    fun `the digest footer speaks Italian above an unchanged transcript`() {
        val history = Fixture.history(habits = listOf(meditate, read), present = setOf(today))
        val pending = Verdicts.outcomesOn(history, today, today)
            .filter { it.state == TestState.PENDING }
        val italian = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val content = ThabitNotifications.pendingDigest(pending, LocalTime.MIDNIGHT, italian)

        assertEquals(
            listOf(
                "$ thabit --status",
                "- [ ] meditate 10 min",
                "- [ ] read 20 pages  # 0/20 pages",
                "# 2 pending — la giornata finisce alle 00:00"
            ),
            content.expanded.lines()
        )
    }

    @Test
    @Config(qualifiers = "it")
    fun `the reminder footer says in Italian what a reminder is`() {
        val outcome = TestOutcome(read, TestState.PENDING)
        val italian = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val content = ThabitNotifications.reminder(outcome, LocalTime.of(7, 0), italian)
        assertTrue(
            content.expanded.endsWith(
                "# un promemoria è una spinta — può arrivare con qualche minuto di ritardo"
            )
        )
        // The line above it is the file's: a key and a value.
        assertTrue(content.expanded.contains("  when: daily"))
    }
}
