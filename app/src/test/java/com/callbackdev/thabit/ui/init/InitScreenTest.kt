package com.callbackdev.thabit.ui.init

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.thabit.R
import com.callbackdev.thabit.ui.theme.ObsidianSyntax
import com.callbackdev.thabit.ui.theme.ThabitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
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
 *
 * Since Fase 19 the transcript prints itself, so every test about its *content*
 * asks for the still version first — which is not a testing trick but the screen
 * a phone with "Remove animations" on actually gets, and therefore worth
 * asserting. The two tests at the bottom are about the animation itself.
 */
@RunWith(RobolectricTestRunner::class)
// A phone, not Robolectric's default 320x470 handset: the transcript keeps its
// last line in sight, so on a screen too short for it the top of the session has
// honestly scrolled away and these assertions would be measuring a device nobody
// ships.
@Config(qualifiers = "w360dp-h740dp")
class InitScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var added = 0
    private var skipped = 0

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun animations(on: Boolean) {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            if (on) 1f else 0f
        )
    }

    @Before
    fun stillByDefault() = animations(on = false)

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

    /**
     * The same trade one line higher. The session says what the app IS before it
     * says what it needs, and `build` arrives with a "like a" in front of it —
     * a simile is an introduction, a bare noun would be a toll.
     */
    @Test
    fun `the session introduces the app in plain words`() {
        show()

        compose.onNodeWithText("like a build", substring = true).assertExists()
        compose.onNodeWithText("No network", substring = true).assertExists()
    }

    @Test
    @Config(qualifiers = "+it")
    fun `it is localized, unlike the terminal output everywhere else`() {
        show()

        compose.onNodeWithText("> aggiungi la prima abitudine").assertExists()
        compose.onNodeWithText("> salta").assertExists()
        // The command is a command in every language.
        compose.onNodeWithText("$ thabit init").assertExists()
    }

    // ---- the animation -----------------------------------------------------

    /**
     * "Remove animations" is not a slower animation: it is no animation. The whole
     * transcript, choices included, has to be there on the frame the screen opens
     * — not a fade later, which is what a half-hearted implementation would leave.
     */
    @Test
    fun `with animations off the transcript is whole on the first frame`() {
        animations(on = false)
        compose.mainClock.autoAdvance = false
        show()
        compose.mainClock.advanceTimeByFrame()

        compose.onNodeWithText("> skip").assertExists()
        compose.onNodeWithText("like a build", substring = true).assertExists()
    }

    /**
     * Tap-to-skip: the touch that ends the printing lands on the transcript, never
     * on a choice. Somebody impatient enough to tap is not somebody who wanted to
     * be dropped into the wizard.
     */
    @Test
    fun `a tap ends the printing without answering the question`() {
        animations(on = true)
        compose.mainClock.autoAdvance = false
        show()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("> skip").assertDoesNotExist()

        compose.onRoot().performTouchInput { down(center); up() }
        compose.mainClock.advanceTimeByFrame()

        compose.onNodeWithText("> skip").assertExists()
        assertEquals(0, added)
        assertEquals(0, skipped)
    }

    /**
     * The budget, in both languages. The copy is free to grow — but not past the
     * point where a first-run screen starts costing the reader time, and Italian is
     * the longer of the two. This is the test that has to be argued with before the
     * intro becomes a carousel by accretion.
     */
    @Test
    fun `the whole session prints in under two seconds`() {
        assertTrue("English: ${sessionMs(context)}ms", sessionMs(context) < 2_000)
    }

    @Test
    @Config(qualifiers = "+it")
    fun `the italian session prints in under two seconds too`() {
        assertTrue("Italian: ${sessionMs(context)}ms", sessionMs(context) < 2_000)
    }

    private fun sessionMs(context: Context): Long = Typist(
        buildInitScript(
            syntax = ObsidianSyntax,
            intro = context.getString(R.string.init_intro),
            files = context.getString(R.string.init_files),
            privacy = context.getString(R.string.init_privacy),
            ask = context.getString(R.string.init_ask),
            add = context.getString(R.string.init_option_add),
            addNote = context.getString(R.string.init_option_add_note),
            skip = context.getString(R.string.init_option_skip),
            skipNote = context.getString(R.string.init_option_skip_note),
            onAddFirstTest = {},
            onSkip = {}
        )
    ).totalMs
}
