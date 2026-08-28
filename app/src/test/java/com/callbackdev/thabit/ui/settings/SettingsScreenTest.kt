package com.callbackdev.thabit.ui.settings

import android.content.Context
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
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.thabit.R
import com.callbackdev.thabit.data.NotificationSettings
import com.callbackdev.thabit.export.ExportFormat
import com.callbackdev.thabit.export.ExportResult
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

    /** The words behind a note id, in whatever locale the test is configured for. */
    private fun string(id: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

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
        export: ExportState = ExportState.Idle,
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
                        interaction = interaction,
                        export = export
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
        scrollTo("\"daily_commit\": true,  // " + string(R.string.cfg_daily_commit))
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
        val note = "// " + string(R.string.cfg_restore_hint)
        scrollTo(note)
        compose.onNodeWithText(note).assertIsDisplayed()
        assertTrue(note.contains("untouched"))
    }

    @Test
    fun `the reset asks for a second tap, with the way out on its own line`() {
        show(interaction = SettingsInteraction(restoreConfirm = true))
        val confirm = "// " + string(R.string.confirm_command)
        scrollTo(confirm)
        compose.onNodeWithText(confirm).assertIsDisplayed()
        // Regression guard, same lesson as the suite's archive confirm: the way
        // out of a destructive action never hides past the right edge.
        compose.onNodeWithText("[esc]").assertIsDisplayed().assert(hasClickAction())
    }

    @Test
    fun `the export reports the names the store wrote, and what went into them`() {
        show(
            export = ExportState.Done(
                ExportResult.Written(
                    files = listOf("thabit-export-2026-08-21.json"),
                    tests = 6,
                    checks = 142,
                    days = 30
                )
            )
        )
        scrollTo("// wrote Downloads/thabit-export-2026-08-21.json")
        compose.onNodeWithText("// wrote Downloads/thabit-export-2026-08-21.json")
            .assertIsDisplayed()
        compose.onNodeWithText("// 6 tests · 142 checks · 30 days").assertIsDisplayed()
    }

    @Test
    fun `an empty database is stated as a fact, not as a problem`() {
        show(export = ExportState.Done(ExportResult.Empty))
        scrollTo("// " + string(R.string.cfg_export_pending))
        compose.onNodeWithText("// nothing to export yet").assertIsDisplayed()
    }

    @Test
    fun `a failed write goes out on the ERROR channel`() {
        show(export = ExportState.Done(ExportResult.Failed("Downloads is not writable")))
        scrollTo("// ERROR: Downloads is not writable")
        compose.onNodeWithText("// ERROR: Downloads is not writable").assertIsDisplayed()
    }

    @Test
    fun `the export commands are the two the exporter knows`() {
        val asked = mutableListOf<ExportFormat>()
        show(actions = SettingsActions(onExport = { asked += it }))
        scrollTo("$ ${ExportFormat.JSON.command}")
        compose.onNodeWithText("$ ${ExportFormat.JSON.command}").performClick()
        scrollTo("$ ${ExportFormat.CSV.command}")
        compose.onNodeWithText("$ ${ExportFormat.CSV.command}").performClick()
        assertEquals(listOf(ExportFormat.JSON, ExportFormat.CSV), asked)
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

    // ---- the register rule (Fase 15) --------------------------------------

    /**
     * One file, both registers, line by line. The keys, the values, `// active`
     * and the `$` command are the file and never move; the notes beside them
     * exist only to be understood, so they are the reader\'s.
     */
    @Test
    @Config(qualifiers = "it")
    fun `the notes speak Italian while the file stays the file`() {
        show(notifications = NotificationSettings(dailyCommit = true, pendingDigest = false))
        val note = "// " + string(R.string.cfg_daily_commit)
        assertEquals("// il risultato della build del giorno, in silenzio, al commit", note)
        scrollTo("\"daily_commit\": true,  $note")
        compose.onNodeWithText("\"daily_commit\": true,", substring = true).assertIsDisplayed()
        compose.onNodeWithText("\"notifications\": {").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "it")
    fun `the reset speaks, the command it guards does not`() {
        show()
        scrollTo("$ git restore settings.config")
        compose.onNodeWithText("$ git restore settings.config").assertIsDisplayed()
        val note = "// azzera solo questo file — la suite e la sua storia restano intatte"
        scrollTo(note)
        compose.onNodeWithText(note).assertIsDisplayed()
    }

    /**
     * `ERROR:` is a level, so it stays outside the sentence exactly as a
     * `net::ERR_*` code would — the line is red, and it says why in words the
     * reader can act on.
     */
    @Test
    @Config(qualifiers = "it")
    fun `an error keeps its level and explains itself in Italian`() {
        show(notifState = NotifLineState.MissingPermission)
        val line = "// ERROR: manca il permesso per le notifiche — tocca per concederlo"
        scrollTo(line)
        compose.onNodeWithText(line).assertIsDisplayed().assert(hasClickAction())
    }

    @Test
    @Config(qualifiers = "it")
    fun `the export says what it wrote in Italian and where in the store's own name`() {
        show(
            export = ExportState.Done(
                ExportResult.Written(
                    files = listOf("thabit-export-2026-08-21.json"),
                    tests = 6,
                    checks = 142,
                    days = 30
                )
            )
        )
        val line = "// scritto in Downloads/thabit-export-2026-08-21.json"
        scrollTo(line)
        compose.onNodeWithText(line).assertIsDisplayed()
        // The tally is a readout: three counts and three code nouns.
        compose.onNodeWithText("// 6 tests · 142 checks · 30 days").assertIsDisplayed()
    }
}
