package com.callbackdev.thabit.ui.init

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.callbackdev.thabit.ui.theme.ThabitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `$ thabit init`: two answers, both of them answers.
 *
 * The copy itself is not frozen here — it is copy, and freezing prose in a test
 * only teaches the next person to update two files. What is asserted is the
 * shape: the transcript, the two ways out, and the fact that the screen never
 * shows the reader a way to be stuck on it.
 */
@RunWith(RobolectricTestRunner::class)
class InitScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var added = 0
    private var skipped = 0

    private fun show() {
        compose.setContent {
            ThabitTheme {
                InitScreen(onAddFirstTest = { added++ }, onSkip = { skipped++ })
            }
        }
    }

    @Test
    fun `the screen is a terminal session, not a slide`() {
        show()

        compose.onNodeWithText("$ thabit init").assertExists()
        // A shell name, because what this opens is a session and not a document.
        compose.onNodeWithText("thabit.sh").assertExists()
    }

    @Test
    fun `the first answer opens the wizard`() {
        show()

        compose.onNodeWithText("> add your first habit").performClick()

        assertEquals(1, added)
        assertEquals(0, skipped)
    }

    /** The `>` is a prompt, not a word: a screen reader gets the answer alone. */
    @Test
    fun `the choices speak without their prompt glyph`() {
        show()

        compose.onNodeWithContentDescription("add your first habit").assertExists()
        compose.onNodeWithContentDescription("skip").assertExists()
    }

    @Test
    fun `skipping is an answer of its own`() {
        show()

        compose.onNodeWithText("> skip").performClick()

        assertEquals(1, skipped)
        assertEquals(0, added)
    }

    /**
     * VISION §3.3.7 on the one screen where it bites hardest: `test` is the
     * app's word, and the *choice* says the plain one. The app's word is
     * introduced beside it, in the note, where it costs nothing to meet.
     */
    @Test
    fun `the choice speaks plainly and the note introduces the app's word`() {
        show()

        compose.onNodeWithText("> add your first habit").assertExists()
        compose.onNodeWithText("# the app calls it a test, and lists them in habits.test")
            .assertExists()
    }

    @Test
    @Config(qualifiers = "it")
    fun `it is localized, unlike the terminal output everywhere else`() {
        show()

        compose.onNodeWithText("> aggiungi la prima abitudine").assertExists()
        compose.onNodeWithText("> salta").assertExists()
        // The command is a command in every language.
        compose.onNodeWithText("$ thabit init").assertExists()
    }
}
