package com.callbackdev.thabit.ui.settings

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.callbackdev.thabit.data.NotificationSettings
import com.callbackdev.thabit.ui.theme.ThabitTheme
import com.callbackdev.thabit.ui.theme.ThemeProfile
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
 * The rendered `settings.config`: the file is the settings screen, so the test
 * reads it as a file — the keys, the values, the comments and the commands.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(
        actions: SettingsActions = SettingsActions(),
        dayEnds: LocalTime = LocalTime.of(3, 0),
        weekStartsOn: DayOfWeek = DayOfWeek.MONDAY,
        theme: ThemeProfile = ThemeProfile.Obsidian,
        showLineNumbers: Boolean = true,
        wordWrap: Boolean = false,
        lastModified: Long? = null,
        notifications: NotificationSettings = NotificationSettings(),
        reminderCount: Int = 0,
        widgetOpacityPct: Int = 100,
        notifState: NotifLineState = NotifLineState.Armed,
        interaction: SettingsInteraction = SettingsInteraction()
    ) {
        compose.setContent {
            ThabitTheme {
                SettingsScreen(
                    state = SettingsUiState(
                        document = SettingsDocument(
                            dayEnds = dayEnds,
                            weekStartsOn = weekStartsOn,
                            theme = theme,
                            showLineNumbers = showLineNumbers,
                            wordWrap = wordWrap,
                            lastModified = lastModified,
                            versionName = "0.1.0",
                            notifications = notifications,
                            reminderCount = reminderCount,
                            widgetOpacityPct = widgetOpacityPct
                        ),
                        interaction = interaction
                    ),
                    actions = actions,
                    notifState = notifState
                )
            }
        }
    }

    /**
     * The config is a long file: its foot is below the fold, and a LazyColumn
     * simply has not composed those rows yet. Scrolling the file is what a reader
     * does too.
     */
    private fun scrollTo(text: String) =
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))

    // ---- the file --------------------------------------------------------

    @Test
    fun `the strip names the file, and the file opens a real JSON object`() {
        show()
        // The name lives in the tab strip now; the document does not repeat it,
        // and neither does the status bar (which says `rw` instead).
        compose.onNodeWithText("settings.config").assertIsDisplayed()
        compose.onNodeWithText("// settings.config").assertDoesNotExist()
        compose.onNodeWithText("{").assertIsDisplayed()
        compose.onNodeWithText("rw").assertIsDisplayed()
    }

    @Test
    fun `the suite section states the boundary with the hint that explains it`() {
        show()
        compose.onNodeWithText("\"day_ends\": \"03:00\",  // the nightly build; \"03:00\" if your day ends late")
            .assertIsDisplayed()
            .assert(hasClickAction())
    }

    @Test
    fun `booleans are written unquoted, the way JSON writes them`() {
        show(showLineNumbers = true, wordWrap = false)
        compose.onNodeWithText("\"line_numbers\": true,").assertIsDisplayed()
        compose.onNodeWithText("\"word_wrap\": false").assertIsDisplayed()
    }

    @Test
    fun `the active theme is marked, and every profile is tappable`() {
        var chosen: ThemeProfile? = null
        show(theme = ThemeProfile.Dracula, actions = SettingsActions(onSelectTheme = { chosen = it }))

        compose.onNodeWithText("\"dracula\",  // active").assertIsDisplayed()
        compose.onNodeWithText("\"monokai\"").performClick()
        assertEquals(ThemeProfile.Monokai, chosen)
    }

    @Test
    fun `an untouched file carries no last-modified line`() {
        show(lastModified = null)
        compose.onNodeWithText("// Last modified:", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the first change puts a stamp at the top of the file`() {
        show(lastModified = 1_787_302_800_000L)
        compose.onNodeWithText("// Last modified:", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the notifications block shows its two switches and the hour they use`() {
        show(notifications = NotificationSettings(dailyCommit = true, pendingDigest = false))
        // The tab strip costs the file 48dp at the top (Fase 7), so this section
        // needs a scroll to reach.
        scrollTo("\"daily_commit\": true,  ${SettingsDocument.DAILY_COMMIT_HINT}")
        compose.onNodeWithText("\"notifications\": {").assertIsDisplayed()
        compose.onNodeWithText("\"daily_commit\": true,", substring = true)
            .assertIsDisplayed()
            .assert(hasClickAction())
        compose.onNodeWithText("\"pending_digest\": false,", substring = true)
            .assertIsDisplayed()
            .assert(hasClickAction())
        compose.onNodeWithText("\"digest_hour\": \"20:00\"", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the block says how many reminders it does not own, and where they live`() {
        show(reminderCount = 2)
        scrollTo("// 2 tests carry a reminder — set on the test, in habits.test")
        compose.onNodeWithText("// 2 tests carry a reminder — set on the test, in habits.test")
            .assertIsDisplayed()
    }

    @Test
    fun `a missing permission is stated in the file, and the line grants it`() {
        var tapped = false
        show(
            notifState = NotifLineState.MissingPermission,
            actions = SettingsActions(onNotifLine = { tapped = true })
        )
        scrollTo("// ERROR: notifications permission missing — tap to grant")
        compose.onNodeWithText("// ERROR: notifications permission missing — tap to grant")
            .assertIsDisplayed()
            .performClick()
        assertTrue(tapped)
    }

    @Test
    fun `an armed block says so, and says nothing to tap`() {
        show(notifState = NotifLineState.Armed)
        scrollTo("// armed — posts at the boundary and at the times you set")
        compose.onNodeWithText("// armed — posts at the boundary and at the times you set")
            .assertIsDisplayed()
            .assert(hasClickAction().not())
    }

    @Test
    fun `the widget block offers its opacity as a number that cycles`() {
        var cycled = false
        show(
            widgetOpacityPct = 85,
            actions = SettingsActions(onCycleWidgetOpacity = { cycled = true })
        )
        scrollTo("\"bg_opacity_pct\": 85  ${SettingsDocument.WIDGET_OPACITY_HINT}")
        compose.onNodeWithText("\"widget\": {").assertIsDisplayed()
        compose.onNodeWithText("\"bg_opacity_pct\": 85", substring = true)
            .assertIsDisplayed()
            .performClick()
        assertTrue(cycled)
    }

    @Test
    fun `about states the version and the series' proudest line`() {
        show()
        scrollTo("\"version\": \"0.1.0\",")
        compose.onNodeWithText("\"version\": \"0.1.0\",").assertIsDisplayed()
        scrollTo("\"network\": \"none — no INTERNET permission\",")
        compose.onNodeWithText("\"network\": \"none — no INTERNET permission\",").assertIsDisplayed()
    }

    // ---- the commands ----------------------------------------------------

    @Test
    fun `the reset says what it will not touch`() {
        show()
        scrollTo("$ git restore settings.config")
        compose.onNodeWithText("$ git restore settings.config").assertIsDisplayed()
        scrollTo(SettingsDocument.RESTORE_HINT)
        compose.onNodeWithText(SettingsDocument.RESTORE_HINT).assertIsDisplayed()
        assertTrue(SettingsDocument.RESTORE_HINT.contains("untouched"))
    }

    @Test
    fun `the reset asks for a second tap, with the way out on its own line`() {
        show(interaction = SettingsInteraction(restoreConfirm = true))
        scrollTo(SettingsDocument.RESTORE_CONFIRM)
        compose.onNodeWithText(SettingsDocument.RESTORE_CONFIRM).assertIsDisplayed()
        // Regression guard, same lesson as the suite's archive confirm: the way
        // out of a destructive action never hides past the right edge.
        compose.onNodeWithText("[esc]").assertIsDisplayed().assert(hasClickAction())
    }

    @Test
    fun `a transient answer appears at the foot of the file`() {
        show(interaction = SettingsInteraction(transient = "$ thabit export --json  // nothing yet"))
        scrollTo("$ thabit export --json  // nothing yet")
        compose.onNodeWithText("$ thabit export --json  // nothing yet").assertIsDisplayed()
    }

    // ---- the spoken half -------------------------------------------------

    @Test
    fun `a settings row speaks a sentence, not JSON punctuation`() {
        show(dayEnds = LocalTime.of(3, 0), showLineNumbers = true)
        // Substring, not the whole sentence: the localized clock is the platform's
        // and its spacing has changed between CLDR releases before now.
        compose.onNodeWithContentDescription("The day ends at", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("Line numbers, on").assertIsDisplayed()
        compose.onNodeWithContentDescription("The week starts on Monday").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "it")
    fun `the spoken half is complete in Italian too`() {
        show(dayEnds = LocalTime.of(3, 0), showLineNumbers = false, theme = ThemeProfile.Dracula)
        compose.onNodeWithContentDescription("Il giorno finisce alle", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("Numeri di riga, disattivati").assertIsDisplayed()
        compose.onNodeWithContentDescription("La settimana comincia di lunedì").assertIsDisplayed()
        compose.onNodeWithContentDescription("Tema dracula, in uso").assertIsDisplayed()
    }
}
