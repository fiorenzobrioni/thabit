package com.callbackdev.thabit.ui.wizard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.callbackdev.thabit.domain.model.HabitType
import com.callbackdev.thabit.ui.theme.ThabitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalTime

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

    /**
     * A viewport shorter than an expanded transcript. Not a phone measurement:
     * it only has to put the last row past the edge, which is the condition the
     * keyboard creates on a real narrow screen.
     */
    private val TranscriptFold = 150.dp

    private fun show(
        state: WizardUiState = WizardUiState(),
        actions: WizardActions = WizardActions(),
        remindArmed: Boolean = true,
        onGrantNotifications: () -> Unit = {}
    ) {
        compose.setContent {
            ThabitTheme {
                WizardScreen(
                    state = state,
                    actions = actions,
                    remindArmed = remindArmed,
                    onGrantNotifications = onGrantNotifications
                )
            }
        }
    }

    /**
     * The same transcript in a viewport too short to hold it — the narrow screen
     * with the keyboard up, which is where Fase 9 found `[save]` under the fold.
     * The returned state is the session's own: writing to it is what a tap on a
     * control does, and it is how these tests exercise a *transition* rather
     * than a first frame.
     */
    private fun showShort(initial: WizardUiState): MutableState<WizardUiState> {
        val session = mutableStateOf(initial)
        compose.setContent {
            ThabitTheme {
                Box(Modifier.height(TranscriptFold)) {
                    WizardScreen(state = session.value, actions = WizardActions())
                }
            }
        }
        return session
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
    fun `the name prompt opens with the caret already in it`() {
        show()
        // The question has been asked: the answer must not need a second tap
        // before the keyboard shows up (the siblings' prompts do the same).
        compose.onNode(hasSetTextAction()).assertIsFocused()
    }

    @Test
    fun `the line left behind by a done does not steal the keyboard back`() {
        show(WizardUiState(added = listOf("meditate"), focus = null))
        compose.onNodeWithText("[+ another]").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertIsNotFocused()
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
    fun `esc asks once before throwing a draft away`() {
        show(
            WizardUiState(
                draft = WizardDraft(name = "meditate"),
                focus = null,
                discardConfirm = true
            )
        )
        compose.onNodeWithText("# " + DISCARD_CONFIRM).assertIsDisplayed()
        compose.onNodeWithContentDescription("Confirm: leave and discard this test")
            .assertIsDisplayed()
    }

    @Test
    fun `with nothing to lose esc says only what it is`() {
        show()
        compose.onNodeWithText("# " + DISCARD_CONFIRM).assertDoesNotExist()
        compose.onNodeWithContentDescription("Close").assertIsDisplayed()
    }

    @Test
    fun `a quota of one is spoken in the singular`() {
        show(
            WizardUiState(
                draft = WizardDraft(name = "x", scheme = ScheduleScheme.Quota, quota = 1, expanded = true),
                focus = null
            )
        )
        // The file writes `1/week` because that is the canonical form; the
        // sentence a screen reader gets has to be a sentence.
        compose.onNodeWithText("[1/week]").assertIsDisplayed()
        compose.onNodeWithContentDescription("once a week").assertIsDisplayed()
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
    fun `the reminder row offers off by default, and declares the approximation`() {
        show(WizardUiState(draft = WizardDraft(name = "x", expanded = true), focus = null))
        compose.onNodeWithText("> remind:").assertIsDisplayed()
        compose.onNodeWithText("[off]").assertIsDisplayed().assert(hasClickAction())
        compose.onNodeWithText("  # optional — a nudge at a time you pick").assertIsDisplayed()
    }

    @Test
    fun `a reminder that is set says it can be late, and can be taken back`() {
        var cleared = false
        show(
            WizardUiState(
                draft = WizardDraft(name = "x", expanded = true, remindAt = LocalTime.of(7, 0)),
                focus = null
            ),
            actions = WizardActions(onClearRemind = { cleared = true })
        )
        compose.onNodeWithText("[07:00]").assertIsDisplayed().assert(hasClickAction())
        // The approximation is declared where the reminder is set, not only in
        // `settings.config` (VISION §6.7).
        compose.onNodeWithText("  # approximate — a nudge, not an alarm").assertIsDisplayed()
        compose.onNodeWithText("[off]").performClick()
        assertTrue(cleared)
    }

    @Test
    fun `a reminder nothing could post says so, and offers the grant`() {
        var granted = false
        show(
            WizardUiState(
                draft = WizardDraft(name = "x", expanded = true, remindAt = LocalTime.of(7, 0)),
                focus = null
            ),
            remindArmed = false,
            onGrantNotifications = { granted = true }
        )
        compose.onNodeWithText("# not armed: notifications are off").assertIsDisplayed()
        // On its own line, so a narrow screen cannot push it past the right edge.
        compose.onNodeWithText("[grant]").assertIsDisplayed().performClick()
        assertTrue(granted)
    }

    @Test
    fun `the reminder prompt says how to turn it off`() {
        show(
            WizardUiState(
                draft = WizardDraft(name = "x", expanded = true),
                focus = WizardField.Remind,
                pending = ""
            )
        )
        compose.onNodeWithText("> remind:").assertIsDisplayed()
        compose.onNodeWithText("# empty to turn it off").assertIsDisplayed()
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

    // ---- the transcript follows the reader (Fase 12) -----------------------

    @Test
    fun `closing a prompt brings the controls back into view`() {
        // An `[edit]` with a reminder: the transcript Fase 9 measured, on a
        // screen that cannot show all of it at once.
        val session = showShort(
            WizardUiState(
                draft = WizardDraft(
                    editing = 7L,
                    name = "read 20 pages",
                    expanded = true,
                    remindAt = LocalTime.of(7, 0)
                ),
                focus = WizardField.Remind,
                pending = "07:00"
            )
        )
        compose.onNodeWithText("[save]").assertDoesNotExist()

        session.value = session.value.copy(focus = null, pending = "")
        compose.waitForIdle()

        compose.onNodeWithText("[save]").assertIsDisplayed()
    }

    @Test
    fun `an add session opens showing the command that started it`() {
        // The first frame runs the effect before the canvas has been laid out:
        // if a reveal fired there it would scroll `$ thabit add` off the top of
        // the very first screen the app ever shows.
        showShort(WizardUiState(focus = WizardField.Name))
        compose.onNodeWithText("$ thabit add").assertIsDisplayed()
    }

    @Test
    fun `a session opens at its own head, not at its footer`() {
        // Arriving is not a transition: an `[edit]` scrolled to the controls
        // would hide the one row that says which test is being edited.
        showShort(
            WizardUiState(
                draft = WizardDraft(
                    editing = 7L,
                    name = "read 20 pages",
                    expanded = true,
                    remindAt = LocalTime.of(7, 0)
                ),
                focus = null
            )
        )
        compose.onNodeWithText("$ thabit edit \"read 20 pages\"").assertIsDisplayed()
        compose.onNodeWithText("[save]").assertDoesNotExist()
    }

    @Test
    fun `more leaves the reader at the rows it just revealed`() {
        // `[more]` closes the name prompt, but it closes it by asking for *more
        // file*: jumping to the footer would answer a question nobody asked.
        val session = showShort(
            WizardUiState(draft = WizardDraft(name = "read 20 pages"), focus = WizardField.Name)
        )

        session.value = WizardUiState(
            draft = WizardDraft(name = "read 20 pages", expanded = true),
            focus = null
        )
        compose.waitForIdle()

        compose.onNodeWithText("> type:  # what kind of test is this?").assertIsDisplayed()
    }

    @Test
    fun `a prompt reopened below the fold is scrolled to, so it can take the keyboard`() {
        // A lazy list does not compose the rows it cannot show, so a prompt
        // opened off-screen is a prompt whose caret finds nothing to focus.
        val session = showShort(
            WizardUiState(
                draft = WizardDraft(name = "read 20 pages", expanded = true),
                focus = null
            )
        )
        compose.onNodeWithText("> emoji:").assertDoesNotExist()

        session.value = session.value.copy(focus = WizardField.Emoji, pending = "")
        compose.waitForIdle()

        compose.onNodeWithText("> emoji:").assertIsDisplayed()
    }
}
