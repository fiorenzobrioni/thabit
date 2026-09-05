package com.callbackdev.thabit.ui.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.width
import com.callbackdev.thabit.ui.components.EditorOptions
import com.callbackdev.thabit.ui.components.LocalEditorOptions
import com.callbackdev.thabit.ui.theme.ThabitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `HELP.md` (Fase 14): the second file behind the Settings tab bar.
 *
 * It is a document, so the test checks that it renders as markdown *source* and
 * stays half of the tab strip — the vocabulary itself is copy, and copy is not
 * something a test should freeze. The one exception is the line that hands the
 * reader to the `README.md` tab: that is not copy, it is the seam that keeps
 * the two plain-language surfaces from becoming two truths to align.
 */
@RunWith(RobolectricTestRunner::class)
class HelpScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var selected = -1

    private fun show(wordWrap: Boolean = false) {
        compose.setContent {
            ThabitTheme {
                CompositionLocalProvider(
                    LocalEditorOptions provides EditorOptions(wordWrap = wordWrap)
                ) {
                    HelpScreen(onSelectFile = { selected = it })
                }
            }
        }
    }

    /** The file is longer than a phone: reach a line the way a reader would. */
    private fun scrollTo(text: String) =
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))

    @Test
    fun `the document renders with its headings`() {
        show()

        compose.onNodeWithText("# thabit").assertExists()
        scrollTo("## The four tabs")
        compose.onNodeWithText("## The four tabs").assertExists()
        scrollTo("## The borrowed words")
        compose.onNodeWithText("## The borrowed words").assertExists()
        scrollTo("## Where the numbers come from")
        compose.onNodeWithText("## Where the numbers come from").assertExists()
    }

    /**
     * Fase 18: the one file that wraps whatever `settings.config` says. Its
     * paragraphs run past 400 characters, and panning sideways through a sentence
     * is not reading — this is the surface addressed to somebody who cannot read
     * the app yet, so it is the one that cannot ask them to work for the words.
     */
    @Test
    fun `the document wraps even with word_wrap off`() {
        show(wordWrap = false)

        val paragraph = compose.onNodeWithText("A habit tracker that", substring = true)
            .getUnclippedBoundsInRoot()
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        assertTrue(
            "the paragraph is ${paragraph.width}, the screen ${screen.width}",
            paragraph.width <= screen.width
        )
    }

    @Test
    fun `the status bar declares the modes the file has of its own`() {
        show(wordWrap = false)

        compose.onNodeWithText("ro").assertExists()
        compose.onNodeWithText("wrap").assertExists()
    }

    @Test
    fun `help is the open file of the settings tab strip`() {
        show()

        compose.onNodeWithText("HELP.md").assertIsSelected()
    }

    @Test
    fun `the config file is one tap away`() {
        show()

        compose.onNodeWithText("settings.config").performClick()

        assertEquals(0, selected)
    }

    /**
     * The division of labour with `README.md`, asserted rather than assumed: the
     * words that stand next to a number are glossed there, and this file points
     * at them instead of repeating them.
     */
    @Test
    fun `the verdict words are handed to the README tab, not restated here`() {
        show()

        val seam = "The other words the app prints - `build`, `health`, `coverage`, " +
            "`flaky`, `regression` - are explained in the `README.md` tab, next to the " +
            "numbers they are about."

        scrollTo(seam)

        compose.onNodeWithText(seam).assertExists()
    }

    @Test
    @Config(qualifiers = "it")
    fun `it is prose, so it is localized headings included`() {
        show()

        scrollTo("## Le quattro schede")
        compose.onNodeWithText("## Le quattro schede").assertExists()
        scrollTo("## Le parole prese in prestito")
        compose.onNodeWithText("## Le parole prese in prestito").assertExists()
        // File names are file names in every language.
        compose.onNodeWithText("HELP.md").assertIsSelected()
    }
}
