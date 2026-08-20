package com.callbackdev.thabit.ui.components

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.thabit.R
import com.callbackdev.thabit.ui.theme.ThabitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * VISION §3.3.7: the metaphor is a gain for whoever gets the joke, never a toll for
 * whoever does not — so the glyph is the look and the words are the meaning. These
 * tests guard the spoken half of the app in both languages: a screen reader must
 * never be handed `[·]`, and accessibility text is chrome, so it never ships as an
 * English literal (§1.3).
 */
@RunWith(RobolectricTestRunner::class)
class AccessibilityTextTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun everyCheckboxStateSpeaksWordsNotGlyphs() {
        val spoken = CheckboxState.entries.map { context.getString(it.spokenRes) }

        spoken.forEach { words ->
            assertTrue("a state has no spoken text", words.isNotBlank())
            assertFalse("the spoken text renders the glyph: '$words'", words.contains("["))
        }
        // Distinct words, or two states would sound identical — which is exactly the
        // `[ ]` / `[·]` ambiguity the glyph was split to remove.
        assertEquals(CheckboxState.entries.size, spoken.distinct().size)
    }

    @Test
    @Config(qualifiers = "it")
    fun spokenStatesAreTranslated() {
        assertEquals("passato", context.getString(R.string.cd_state_passed))
        assertEquals("ancora da fare", context.getString(R.string.cd_state_pending))
        assertEquals(
            "sta reggendo, fallisce solo se lo interrompi",
            context.getString(R.string.cd_state_holding)
        )
        assertEquals("saltato", context.getString(R.string.cd_state_skipped))
        assertEquals("fallito", context.getString(R.string.cd_state_failed))
    }

    @Test
    @Config(qualifiers = "it")
    fun fabNamesItsVerbInTheReadersLanguage() {
        // Regression: the default used to be the English literal "Add", which said
        // nothing about the suite and never localized.
        compose.setContent { ThabitTheme { GlowFab(onClick = {}) } }

        compose.onNodeWithContentDescription("Aggiungi un test alla suite").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add").assertDoesNotExist()
    }
}
