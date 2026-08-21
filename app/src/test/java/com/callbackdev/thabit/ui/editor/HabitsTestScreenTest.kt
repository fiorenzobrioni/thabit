package com.callbackdev.thabit.ui.editor

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.callbackdev.thabit.domain.Fixture
import com.callbackdev.thabit.domain.SuiteHistory
import com.callbackdev.thabit.domain.model.AssertSpec
import com.callbackdev.thabit.domain.model.Check
import com.callbackdev.thabit.domain.model.CheckState
import com.callbackdev.thabit.domain.model.HabitType
import com.callbackdev.thabit.ui.theme.ThabitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalTime

/**
 * The rendered file: the lines a reader sees, and the sentences a screen reader
 * hears instead of the glyphs.
 *
 * The document layer already asserts *what* the file says; this asserts that the
 * screen actually draws it, that both gestures on a row survive being on the
 * same line, and that the spoken half is complete in both languages.
 */
@RunWith(RobolectricTestRunner::class)
class HabitsTestScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val d = Fixture.D0

    private val meditate = Fixture.habit(1L, "meditate 10 min", position = 0)
    private val pushups = Fixture.habit(
        2L, "pushups", HabitType.COUNTER,
        assert = AssertSpec(30.0, "reps", 10.0), position = 1
    )
    private val noSugar = Fixture.habit(3L, "no sugar", HabitType.AVOID, position = 2)

    private fun show(
        history: SuiteHistory,
        actions: SuiteActions = SuiteActions(),
        interaction: SuiteInteraction = SuiteInteraction()
    ) {
        val document = SuiteDocument.of(history, d, d)
        compose.setContent {
            ThabitTheme {
                HabitsTestScreen(
                    state = SuiteUiState(
                        document = document,
                        interaction = interaction,
                        loading = false
                    ),
                    actions = actions
                )
            }
        }
    }

    private fun suite() = Fixture.history(
        listOf(meditate, pushups, noSugar),
        listOf(
            Check(1L, d, CheckState.PASS, at = LocalTime.of(7, 12)),
            Fixture.progress(2L, d, 12.0)
        ),
        setOf(d)
    )

    // ---- what is drawn ---------------------------------------------------

    @Test
    fun `the file states the day's arithmetic`() {
        show(suite())
        // The file's own name lives in the tab strip above it now (Fase 7), so
        // the document does not repeat it.
        compose.onNodeWithText("# habits.test").assertDoesNotExist()
        compose.onNodeWithText("# suite 2026-08-01 — 1 passed · 2 pending").assertIsDisplayed()
    }

    @Test
    fun `every due test gets a line, with its glyph and its live detail`() {
        show(suite())
        compose.onNodeWithText("meditate 10 min").assertIsDisplayed()
        compose.onNodeWithText("  # 07:12").assertIsDisplayed()
        compose.onNodeWithText("  # 12/30 reps").assertIsDisplayed()
        compose.onNodeWithText("  # holds — asserts at commit").assertIsDisplayed()
    }

    @Test
    fun `a counter within a dozen taps gets its increment control`() {
        show(suite())
        compose.onNodeWithText("[+10]").assertIsDisplayed().assert(hasClickAction())
    }

    @Test
    fun `the empty suite says so and points at the FAB`() {
        show(SuiteHistory.Empty)
        compose.onNodeWithText("# no tests in the suite yet").assertIsDisplayed()
        compose.onNodeWithText("# tap + to add your first test").assertIsDisplayed()
        // The README tab shipped in Fase 7, so the first sentence the app ever
        // says is allowed to point at it (SuiteDocument.README_TAB_SHIPPED).
        compose.onNodeWithText("# the README tab says what a test is here").assertIsDisplayed()
    }

    @Test
    fun `the status bar shows the branch and the live score`() {
        show(suite())
        compose.onNodeWithText("⎇ main").assertIsDisplayed()
        compose.onNodeWithText("3 tests").assertIsDisplayed()
        compose.onNodeWithText("1/3 passed").assertIsDisplayed()
    }

    // ---- two gestures on one line ----------------------------------------

    @Test
    fun `the checkbox runs the test and the name unfolds it`() {
        var checked: TestRow? = null
        var detailed: Long? = null
        show(
            suite(),
            SuiteActions(
                onCheckbox = { checked = it },
                onDetails = { detailed = it }
            )
        )

        compose.onNodeWithContentDescription("meditate 10 min, passed, at 07:12").performClick()
        assertEquals(1L, checked?.habitId)

        compose.onNodeWithContentDescription("Details of meditate 10 min").performClick()
        assertEquals(1L, detailed)
    }

    @Test
    fun `the expansion shows the spec and its three text controls`() {
        show(suite(), interaction = SuiteInteraction(expandedId = 1L))
        compose.onNodeWithText("when: daily").assertIsDisplayed()
        compose.onNodeWithText("[~ skip]").assertIsDisplayed().assert(hasClickAction())
        compose.onNodeWithText("[edit]").assertIsDisplayed()
        compose.onNodeWithText("[rm]").assertIsDisplayed()
    }

    @Test
    fun `archiving asks for the second tap as a spelled-out command`() {
        show(suite(), interaction = SuiteInteraction(expandedId = 1L, archiveConfirmId = 1L))
        compose.onNodeWithText("$ thabit archive \"meditate 10 min\"").assertIsDisplayed()
        compose.onNodeWithText("# tap the command to confirm").assertIsDisplayed()
        // Regression: on one line the command pushed the way out off the screen.
        compose.onNodeWithText("[esc]").assertIsDisplayed()
        compose.onNodeWithText("[rm]").assertDoesNotExist()
    }

    @Test
    fun `a prompt opens inside the file, with its window token and its exits`() {
        show(
            suite(),
            interaction = SuiteInteraction(
                prompt = SuitePrompt.Skip(1L, "", SkipWindow.OneWeek)
            )
        )
        compose.onNodeWithText("> skip:").assertIsDisplayed()
        compose.onNodeWithText("# window: [1w]").assertIsDisplayed().assert(hasClickAction())
        compose.onNodeWithText("[ok]").assertIsDisplayed()
        compose.onNodeWithText("[esc]").assertIsDisplayed()
    }

    @Test
    fun `tests not due today are commented out and can be unfolded`() {
        val mondays = Fixture.habit(
            4L, "deep work",
            schedule = com.callbackdev.thabit.domain.model.Schedule.Weekdays(
                setOf(java.time.DayOfWeek.MONDAY)
            ),
            createdAt = d
        )
        val history = Fixture.history(listOf(meditate, mondays), emptyList(), setOf(d))
        show(history, interaction = SuiteInteraction(notDueExpanded = true))
        compose.onNodeWithText("# 1 test not due today — [hide]").assertIsDisplayed()
        compose.onNodeWithText("# deep work  — when: mon")
            .assertIsDisplayed()
            .assert(hasClickAction())
    }

    @Test
    fun `a test not due today opens the same spec, and the actions that make sense`() {
        val mondays = Fixture.habit(
            4L, "deep work",
            schedule = com.callbackdev.thabit.domain.model.Schedule.Weekdays(
                setOf(java.time.DayOfWeek.MONDAY)
            ),
            createdAt = d
        )
        val history = Fixture.history(listOf(meditate, mondays), emptyList(), setOf(d))
        show(
            history,
            interaction = SuiteInteraction(notDueExpanded = true, expandedId = 4L)
        )

        // `[edit]` and `[rm]` cannot be things you have to wait until Monday for.
        compose.onNodeWithText("when: mon").assertIsDisplayed()
        compose.onNodeWithText("[edit]").assertIsDisplayed().assert(hasClickAction())
        compose.onNodeWithText("[rm]").assertIsDisplayed().assert(hasClickAction())
        // Nothing is asked of this test today, so there is nothing to skip.
        compose.onNodeWithText("[~ skip]").assertDoesNotExist()
    }

    @Test
    fun `a skipped test offers the way back, and it is the only way back`() {
        val history = Fixture.history(
            listOf(meditate),
            listOf(Fixture.skip(1L, d, until = d.plusDays(6), note = "away")),
            setOf(d)
        )
        show(history, interaction = SuiteInteraction(expandedId = 1L))

        compose.onNodeWithText("[~ unskip]").assertIsDisplayed().assert(hasClickAction())
        compose.onNodeWithText("[~ skip]").assertDoesNotExist()
    }

    // ---- terminal output --------------------------------------------------

    @Test
    fun `terminal output is printed under the row it answers`() {
        show(
            suite(),
            interaction = SuiteInteraction(
                transient = SuiteMessage(SuiteViewModel.UNKNOWN_TEST, habitId = 1L)
            )
        )
        // A message is always the answer to a tap, and the thumb that tapped is
        // still on that line — so it is printed there, not at the foot of a file
        // that may be longer than the screen.
        val message = compose.onNodeWithText("# " + SuiteViewModel.UNKNOWN_TEST)
            .getUnclippedBoundsInRoot()
        val lastRow = compose.onNodeWithText("no sugar").getUnclippedBoundsInRoot()
        assertTrue(message.top < lastRow.top)
    }

    @Test
    fun `terminal output with no row of its own falls to the foot of the file`() {
        show(
            suite(),
            interaction = SuiteInteraction(transient = SuiteMessage(SuiteViewModel.UNKNOWN_TEST))
        )
        val message = compose.onNodeWithText("# " + SuiteViewModel.UNKNOWN_TEST)
            .getUnclippedBoundsInRoot()
        val lastRow = compose.onNodeWithText("no sugar").getUnclippedBoundsInRoot()
        assertTrue(message.top > lastRow.top)
    }

    // ---- the spoken half -------------------------------------------------

    @Test
    fun `the comment channel is drawn, not spoken`() {
        show(suite())
        // `# 12/30 reps` is source: English, for the eye. The checkbox has just
        // said the same thing in the listener's language, and hearing it twice —
        // the second time in the wrong one — is the noise §3.3.7 forbids.
        compose.onNodeWithText("  # 12/30 reps")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
        // The row's own sentence is untouched by that.
        compose.onNodeWithContentDescription("pushups, still to do, 12 of 30 reps")
            .assertIsDisplayed()
    }

    @Test
    fun `a row speaks words, never brackets`() {
        show(suite())
        compose.onNodeWithContentDescription("pushups, still to do, 12 of 30 reps").assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "no sugar, holding, it fails only if you break it"
        ).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "it")
    fun `the spoken half is complete in Italian too`() {
        show(suite())
        compose.onNodeWithContentDescription("meditate 10 min, passato, alle 07:12").assertIsDisplayed()
        compose.onNodeWithContentDescription("pushups, ancora da fare, 12 di 30 reps").assertIsDisplayed()
        compose.onNodeWithContentDescription("Dettagli di pushups").assertIsDisplayed()
    }

    @Test
    fun `the git decorations carry their fact in plain words as well`() {
        show(suite())
        compose.onNodeWithContentDescription("3 tests in the suite").assertIsDisplayed()
        compose.onNodeWithContentDescription("1 of 3 passed").assertIsDisplayed()
    }
}
