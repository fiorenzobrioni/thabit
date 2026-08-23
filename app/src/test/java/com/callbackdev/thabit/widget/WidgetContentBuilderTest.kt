package com.callbackdev.thabit.widget

import androidx.test.core.app.ApplicationProvider
import com.callbackdev.thabit.domain.Fixture
import com.callbackdev.thabit.domain.TestOutcome
import com.callbackdev.thabit.domain.TestState
import com.callbackdev.thabit.domain.model.AssertSpec
import com.callbackdev.thabit.domain.model.HabitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The widget's transcript, word by word.
 *
 * Two rules carry most of these assertions. **The suite line is never cut** — it
 * is the minimum tier by itself, and it is the line that states both the
 * arithmetic and the date the render belongs to. And **the glyph is the only
 * channel**: there is no `#` comment here to tell `[·]` from `[ ]`, so the
 * spoken half has to say it in words.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetContentBuilderTest {

    private val resources =
        ApplicationProvider.getApplicationContext<android.content.Context>().resources

    private val today = LocalDate.of(2026, 8, 21)

    private val meditate = Fixture.habit(id = 1L, name = "meditate 10 min")
    private val read = Fixture.habit(
        id = 2L,
        name = "read 20 pages",
        type = HabitType.COUNTER,
        assert = AssertSpec(target = 20.0, unit = "pages")
    )
    private val phone = Fixture.habit(id = 3L, name = "no phone after 23:00", type = HabitType.AVOID)
    private val run5k = Fixture.habit(id = 4L, name = "run 5k")

    private fun data(
        outcomes: List<TestOutcome> = emptyList(),
        suiteSize: Int = outcomes.size,
        date: LocalDate = today
    ) = WidgetData(date = date, outcomes = outcomes, suiteSize = suiteSize)

    private fun build(data: WidgetData, lines: Int = 6) =
        WidgetContentBuilder.build(data, WidgetTier.Terminal(lines), resources)

    private fun fullDay() = data(
        listOf(
            TestOutcome(meditate, TestState.PASS),
            TestOutcome(read, TestState.PENDING),
            TestOutcome(phone, TestState.HOLDING),
            TestOutcome(run5k, TestState.SKIP)
        )
    )

    // ---- the suite line ---------------------------------------------------

    @Test
    fun `the first line leads with its field name, like every sibling widget`() {
        val content = build(fullDay())
        assertEquals("Suite: 1/3 ▓▓▓░░░░░░░", content.bodyLines.first().text)
    }

    @Test
    fun `the day being rendered is stated, because a widget cannot notice it is stale`() {
        // It leads the trailing comment, so it is the half that survives an
        // ellipsis on a narrow widget.
        assertTrue(build(fullDay()).bodyLines.last().text.startsWith("# 2026-08-21"))
    }

    @Test
    fun `a skip leaves the denominator, exactly as it does everywhere else`() {
        // Four tests on screen, three graded: `run 5k` was skipped.
        val content = build(fullDay())
        assertTrue(content.bodyLines.first().text.startsWith("Suite: 1/3 "))
        assertTrue(content.bodyLines.any { it.text == "[~] run 5k" })
    }

    @Test
    fun `nothing graded yet draws an empty bar, never a full one`() {
        val content = build(data(listOf(TestOutcome(run5k, TestState.SKIP))))
        assertTrue(content.bodyLines.first().text.startsWith("Suite: 0/0 ░░░░░░░░░░"))
    }

    @Test
    fun `the smallest tier is the suite line alone`() {
        val content = WidgetContentBuilder.build(fullDay(), WidgetTier.Terminal(1), resources)
        assertEquals(1, content.bodyLines.size)
        assertTrue(content.bodyLines.single().text.startsWith("Suite: 1/3 "))
    }

    // ---- the rows ---------------------------------------------------------

    @Test
    fun `every state wears its own glyph, because the glyph is the only channel here`() {
        val content = build(fullDay())
        assertEquals(
            listOf(
                "[x] meditate 10 min",
                "[ ] read 20 pages",
                "[·] no phone after 23:00",
                "[~] run 5k"
            ),
            content.bodyLines.drop(1).take(4).map { it.text }
        )
    }

    @Test
    fun `only an untouched boolean can be answered from the home screen`() {
        val content = build(fullDay())
        val actions = content.bodyLines.drop(1).take(4).map { it.action }
        assertEquals(
            listOf(
                // passed: nothing left to ask, and an undo belongs where the row
                // can say what it is undoing
                WidgetAction.Open(1L),
                // a counter needs a number
                WidgetAction.Open(2L),
                // an avoid test's only widget verb would be "I broke it"
                WidgetAction.Open(3L),
                WidgetAction.Open(4L)
            ),
            actions
        )

        val pending = build(data(listOf(TestOutcome(meditate, TestState.PENDING))))
        assertEquals(WidgetAction.Pass(1L), pending.bodyLines[1].action)
    }

    @Test
    fun `an emoji rides along with the name, like it does in the file`() {
        val content = build(
            data(listOf(TestOutcome(read.copy(emoji = "📖"), TestState.PENDING)))
        )
        assertEquals("[ ] 📖 read 20 pages", content.bodyLines[1].text)
    }

    // ---- the trailing comment --------------------------------------------

    @Test
    fun `the comment says what is left, and how to answer it`() {
        // Four rows, but only `read 20 pages` is genuinely pending: the holding
        // avoid test cannot be "tapped to pass", it passes by itself.
        val content = build(fullDay())
        assertEquals("# 2026-08-21 · 1 pending — tap to pass", content.bodyLines.last().text)
    }

    @Test
    fun `a holding avoid test is not something to be reminded about`() {
        val content = build(
            data(
                listOf(
                    TestOutcome(meditate, TestState.PASS),
                    TestOutcome(phone, TestState.HOLDING)
                )
            )
        )
        // Same call the evening digest makes, for the same reason.
        assertEquals("# 2026-08-21 · nothing pending", content.bodyLines.last().text)
    }

    @Test
    fun `a finished day states the fact, and does not congratulate anybody`() {
        val content = build(data(listOf(TestOutcome(meditate, TestState.PASS))))
        assertEquals("# 2026-08-21 · nothing pending", content.bodyLines.last().text)
    }

    @Test
    fun `rows win the last line over the comment, because the total is already stated`() {
        // Four rows, room for four body lines: the suite line, three rows, and
        // the comment loses. Nothing is hidden — `1/3` is on the first line.
        val content = build(fullDay(), lines = 4)
        assertEquals(4, content.bodyLines.size)
        assertFalse(content.bodyLines.any { it.text.startsWith("#") })
        assertTrue(content.bodyLines.first().text.startsWith("Suite: 1/3 "))
    }

    // ---- the empty states -------------------------------------------------

    @Test
    fun `an empty suite says so, and says what to do about it`() {
        val content = build(data(emptyList(), suiteSize = 0))
        assertEquals("# no tests yet — tap to add one", content.bodyLines[1].text)
        assertEquals(WidgetAction.OpenApp, content.bodyLines[1].action)
    }

    @Test
    fun `a suite with nothing due today is not an empty suite`() {
        val content = build(data(emptyList(), suiteSize = 3))
        assertEquals("# nothing due today", content.bodyLines[1].text)
    }

    // ---- the glanceable strip --------------------------------------------

    @Test
    fun `the small tier keeps the fraction and the date, and drops the bar`() {
        val content = WidgetContentBuilder.build(fullDay(), WidgetTier.Small, resources)
        assertEquals("1/3", content.smallValue.text)
        // The bar IS the fraction drawn; the date repeats nothing.
        assertEquals("2026-08-21", content.smallLabel.text)
        assertTrue(content.bodyLines.isEmpty())
    }

    // ---- the spoken half --------------------------------------------------

    @Test
    fun `a screen reader hears words, never brackets`() {
        val content = build(fullDay())
        val spoken = content.bodyLines.mapNotNull { it.spoken }
        assertTrue(spoken.none { it.contains("[") })
        assertEquals("1 of 3 passed on 2026-08-21", content.bodyLines.first().spoken)
        // `[·]` gets the only explanation it ever gets.
        assertTrue(
            spoken.any { it.contains("holding, it fails only if you break it") }
        )
    }

    @Test
    fun `the spoken half says which tap the row offers`() {
        val pending = build(data(listOf(TestOutcome(meditate, TestState.PENDING))))
        assertEquals(
            "meditate 10 min, still to do. Tap to mark it done",
            pending.bodyLines[1].spoken
        )
        val counter = build(data(listOf(TestOutcome(read, TestState.PENDING))))
        assertEquals(
            "read 20 pages, still to do. Tap to open it in the app",
            counter.bodyLines[1].spoken
        )
    }

    @Test
    @Config(qualifiers = "it")
    fun `the spoken half is the reader's language, the transcript stays English`() {
        val italian = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val content = WidgetContentBuilder.build(fullDay(), WidgetTier.Terminal(6), italian)
        assertEquals("1 su 3 passati il 2026-08-21", content.bodyLines.first().spoken)
        // The transcript is source, and source is English everywhere.
        assertEquals("# 2026-08-21 · 1 pending — tap to pass", content.bodyLines.last().text)
        assertEquals("thabit --status", content.headerTitle)
    }

    @Test
    fun `the prompt is decoration and carries no fact of its own`() {
        val content = build(fullDay())
        assertEquals("you@thabit:~$ cat habits.test", content.promptLine.text)
        assertNull(content.promptLine.spoken)
    }
}
