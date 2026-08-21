package com.callbackdev.thabit.ui.wizard

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.callbackdev.thabit.domain.model.HabitType
import com.callbackdev.thabit.ui.theme.ThabitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek

/**
 * The transcript as it reads.
 *
 * These are the app's first sixty seconds, so the assertions are about exactly
 * that: that one answer is visibly enough, that no token is left without its
 * plain meaning, and that a screen reader is never handed the word "boolean" on
 * its own.
 */
@RunWith(RobolectricTestRunner::class)
class WizardScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(
        state: WizardUiState = WizardUiState(),
        actions: WizardActions = WizardActions()
    ) {
        compose.setContent { ThabitTheme { WizardScreen(state = state, actions = actions) } }
    }

    // ---- the session ------------------------------------------------------

    @Test
    fun `the transcript opens as a command and asks for a name`() {
        show()
        compose.onNodeWithText("$ thabit add").assertIsDisplayed()
        compose.onNodeWithText("> name:").assertIsDisplayed()
        compose.onNodeWithText("# what do you want to call it?").assertIsDisplayed()
    }

    @Test
    fun `without a name there is nothing to finish yet`() {
        show()
        compose.onNodeWithText("[done]").assertDoesNotExist()
        compose.onNodeWithText("[more]").assertIsDisplayed()
    }

    @Test
    fun `done appears the moment the test has a name`() {
        show(WizardUiState(draft = WizardDraft(name = "meditate"), focus = null))
        compose.onNodeWithText("[done]").assertIsDisplayed().assert(hasClickAction())
    }

    @Test
    fun `more unfolds the remaining questions, each with its plain meaning`() {
        show(WizardUiState(draft = WizardDraft(name = "meditate", expanded = true), focus = null))
        // Every option gets its own line and its own plain meaning: on one row
        // the later ones ran off the right edge of a phone.
        compose.onNodeWithText("> type:  # what kind of test is this?").assertIsDisplayed()
        compose.onNodeWithText("  # did you do it, yes or no").assertIsDisplayed()
        compose.onNodeWithText("  # count up to a number").assertIsDisplayed()
        compose.onNodeWithText("  # something to stay away from").assertIsDisplayed()
        compose.onNodeWithText("> when:  # how often?").assertIsDisplayed()
        compose.onNodeWithText("  # a number of times each week").assertIsDisplayed()
        compose.onNodeWithText("[more]").assertDoesNotExist()
    }

    @Test
    fun `the chosen option is the one in brackets`() {
        show(WizardUiState(draft = WizardDraft(name = "x", expanded = true), focus = null))
        compose.onNodeWithText("[boolean]").assertIsDisplayed()
        compose.onNodeWithText(" counter ").assertIsDisplayed()
        compose.onNodeWithText("[daily]").assertIsDisplayed()
        compose.onNodeWithText(" 3/week ").assertIsDisplayed()
    }

    @Test
    fun `choosing a type is one tap`() {
        var chosen: HabitType? = null
        show(
            WizardUiState(draft = WizardDraft(name = "x", expanded = true), focus = null),
            WizardActions(onType = { chosen = it })
        )
        compose.onNodeWithText(" counter ").performClick()
        assertEquals(HabitType.COUNTER, chosen)
    }

    // ---- the counter ------------------------------------------------------

    @Test
    fun `an assertion reads without knowing the word assert`() {
        show(
            WizardUiState(
                draft = WizardDraft(
                    name = "read",
                    type = HabitType.COUNTER,
                    unit = "pages",
                    target = 20.0,
                    expanded = true
                ),
                focus = null
            )
        )
        compose.onNodeWithText("[pages]").assertIsDisplayed().assert(hasClickAction())
        compose.onNodeWithText(">=").assertIsDisplayed()
        compose.onNodeWithText("[20]").assertIsDisplayed().assert(hasClickAction())
        compose.onNodeWithText("  # how much counts as done").assertIsDisplayed()
    }

    @Test
    fun `only a counter is asked what it counts`() {
        show(WizardUiState(draft = WizardDraft(name = "x", expanded = true), focus = null))
        compose.onNodeWithText("> assert:").assertDoesNotExist()
    }

    // ---- the schedule details --------------------------------------------

    @Test
    fun `a weekday schedule shows the days, with the chosen ones in brackets`() {
        var toggled: DayOfWeek? = null
        show(
            WizardUiState(
                draft = WizardDraft(name = "x", scheme = ScheduleScheme.Weekdays, expanded = true),
                focus = null
            ),
            WizardActions(onToggleWeekday = { toggled = it })
        )
        compose.onNodeWithText("[mon]").assertIsDisplayed()
        compose.onNodeWithText("[fri]").assertIsDisplayed()
        // The weekend is on its own row: seven tokens on one line do not fit.
        compose.onNodeWithText(" sat ").assertIsDisplayed().performClick()
        assertEquals(DayOfWeek.SATURDAY, toggled)
    }

    @Test
    fun `a quota shows how many times a week`() {
        show(
            WizardUiState(
                draft = WizardDraft(name = "x", scheme = ScheduleScheme.Quota, expanded = true),
                focus = null
            )
        )
        compose.onNodeWithText("[3]").assertIsDisplayed().assert(hasClickAction())
        compose.onNodeWithText("times a week").assertIsDisplayed()
    }

    @Test
    fun `an interval shows how many days apart`() {
        show(
            WizardUiState(
                draft = WizardDraft(name = "x", scheme = ScheduleScheme.Interval, expanded = true),
                focus = null
            )
        )
        compose.onNodeWithText("every").assertIsDisplayed()
        compose.onNodeWithText("[2]").assertIsDisplayed()
        compose.onNodeWithText("days").assertIsDisplayed()
    }

    // ---- honesty ----------------------------------------------------------

    @Test
    fun `the reminder row says where reminders are instead of offering a dead switch`() {
        show(WizardUiState(draft = WizardDraft(name = "x", expanded = true), focus = null))
        compose.onNodeWithText("# remind: off — reminders arrive with their own phase")
            .assertIsDisplayed()
    }

    @Test
    fun `a refusal reads like compiler output`() {
        show(WizardUiState(error = "ERROR: a test needs a name"))
        compose.onNodeWithText("# ERROR: a test needs a name").assertIsDisplayed()
    }

    @Test
    fun `what was already added stays on screen as a receipt`() {
        show(WizardUiState(added = listOf("meditate 10 min"), focus = null))
        compose.onNodeWithText("✓ \"meditate 10 min\" added to the suite").assertIsDisplayed()
        compose.onNodeWithText("[+ another]").assertIsDisplayed().assert(hasClickAction())
    }

    // ---- editing ----------------------------------------------------------

    @Test
    fun `an edit names the test it is editing and offers save`() {
        show(
            WizardUiState(
                draft = WizardDraft(editing = 7L, name = "read 20 pages", expanded = true),
                focus = null
            )
        )
        compose.onNodeWithText("$ thabit edit \"read 20 pages\"").assertIsDisplayed()
        compose.onNodeWithText("[save]").assertIsDisplayed()
        compose.onNodeWithText("[done]").assertDoesNotExist()
    }

    // ---- the spoken half --------------------------------------------------

    @Test
    fun `nobody is ever handed the word boolean on its own`() {
        show(WizardUiState(draft = WizardDraft(name = "x", expanded = true), focus = null))
        compose.onNodeWithContentDescription("yes or no, chosen").assertIsDisplayed()
        compose.onNodeWithContentDescription("Choose: a number to reach").assertIsDisplayed()
        compose.onNodeWithContentDescription("every day, chosen").assertIsDisplayed()
        compose.onNodeWithContentDescription("Choose: 3 times a week").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "it")
    fun `the spoken half is complete in Italian too`() {
        show(
            WizardUiState(
                draft = WizardDraft(name = "x", scheme = ScheduleScheme.Interval, expanded = true),
                focus = null
            )
        )
        compose.onNodeWithContentDescription("sì o no, scelto").assertIsDisplayed()
        compose.onNodeWithContentDescription("Scegli: qualcosa da cui stare alla larga")
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("ogni 2 giorni, scelto").assertIsDisplayed()
    }
}
