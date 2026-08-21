package com.callbackdev.thabit.ui.log

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.callbackdev.thabit.domain.CommitHash
import com.callbackdev.thabit.domain.Fixture
import com.callbackdev.thabit.domain.SuiteHistory
import com.callbackdev.thabit.domain.model.Check
import com.callbackdev.thabit.domain.model.CheckState
import com.callbackdev.thabit.ui.theme.ThabitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

/**
 * The rendered log: the lines a reader sees, and the sentences a screen reader
 * hears instead of the hashes and the glyphs.
 *
 * The document layer already asserts *what* the file says; this asserts that the
 * screen draws it, that only yesterday answers a tap, and that the CI vocabulary
 * never arrives without its plain-language half (VISION §3.3.7).
 */
@RunWith(RobolectricTestRunner::class)
class LogScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val today: LocalDate = LocalDate.of(2026, 8, 20)
    private val yesterday: LocalDate = today.minusDays(1)
    private val before: LocalDate = today.minusDays(2)

    private val meditate = Fixture.habit(1L, "meditate 10 min", createdAt = today.minusDays(10))

    private fun history(amended: Set<LocalDate> = emptySet()) = Fixture.history(
        habits = listOf(meditate),
        checks = listOf(
            Check(1L, yesterday, CheckState.PASS, at = LocalTime.of(7, 3)),
            Check(1L, before, CheckState.PASS, at = LocalTime.of(8, 15)),
            Check(1L, today, CheckState.PASS, at = LocalTime.of(6, 55))
        ),
        present = setOf(today, yesterday, before),
        amended = amended
    )

    private fun show(
        history: SuiteHistory = history(),
        actions: LogActions = LogActions(),
        interaction: LogInteraction = LogInteraction(),
        dayEnds: LocalTime = LocalTime.MIDNIGHT
    ) {
        val document = LogDocument.of(history, today, dayEnds)
        compose.setContent {
            ThabitTheme {
                LogScreen(
                    state = LogUiState(document = document, interaction = interaction, loading = false),
                    actions = actions
                )
            }
        }
    }

    // ---- what is drawn ----------------------------------------------------

    @Test
    fun `the file names itself and puts today on top as uncommitted changes`() {
        show()
        compose.onNodeWithText("# habits_history.diff").assertIsDisplayed()
        compose.onNodeWithText("# ${LogDocument.BRANCH_LINE}").assertIsDisplayed()
        compose.onNodeWithText("#   1/1 passed").assertIsDisplayed()
    }

    @Test
    fun `a commit draws its hash, its message and its verdict`() {
        show()
        compose.onNodeWithText("commit ${CommitHash.of(yesterday)}").assertIsDisplayed()
        // Both days ran the same one test, so both commits say the same words:
        // the assertion is that a commit says them, not that only one does.
        compose.onAllNodesWithText("    suite: 1/1 passed").onFirst().assertIsDisplayed()
        compose.onAllNodesWithText("    ✓ build passed (1/1)").onFirst().assertIsDisplayed()
    }

    @Test
    fun `the empty log says why it is empty`() {
        show(history = SuiteHistory.Empty)
        compose.onNodeWithText("# no commits yet").assertIsDisplayed()
        compose.onNodeWithText("# a day commits when it ends — the suite is still empty")
            .assertIsDisplayed()
    }

    @Test
    fun `the status bar counts the commits and names the head`() {
        show()
        compose.onNodeWithText("⎇ main").assertIsDisplayed()
        compose.onNodeWithText("2 commits").assertIsDisplayed()
        compose.onNodeWithText("HEAD → ${CommitHash.of(yesterday)}").assertIsDisplayed()
    }

    // ---- the expansion ----------------------------------------------------

    @Test
    fun `a commit unfolds into its diff on a tap`() {
        var toggled: LocalDate? = null
        show(actions = LogActions(onToggleCommit = { toggled = it }))

        compose.onNodeWithText("commit ${CommitHash.of(yesterday)}")
            .assert(hasClickAction())
            .performClick()
        assertEquals(yesterday, toggled)
    }

    @Test
    fun `the unfolded day draws one line per test`() {
        show(interaction = LogInteraction(expanded = setOf(yesterday)))
        compose.onNodeWithText("meditate 10 min").assertIsDisplayed()
        compose.onNodeWithText("  # 07:03").assertIsDisplayed()
    }

    // ---- amend ------------------------------------------------------------

    @Test
    fun `yesterday declares that it is still editable, and answers a tap`() {
        var amended: Pair<Long, LocalDate>? = null
        show(
            actions = LogActions(onCheckbox = { row, date -> amended = row.habitId to date }),
            interaction = LogInteraction(expanded = setOf(yesterday)),
            dayEnds = LocalTime.of(3, 0)
        )
        // The window is declared, not discovered (VISION §4.2). The clock is
        // localized, so the assertion is on the sentence and not on the format
        // a given ICU release picks for it.
        compose.onNodeWithText("# still editable until", substring = true).assertIsDisplayed()

        compose.onNodeWithText("+ [x]").assert(hasClickAction()).performClick()
        assertEquals(1L to yesterday, amended)
    }

    @Test
    fun `two days back is read-only, and does not pretend otherwise`() {
        var amended: Pair<Long, LocalDate>? = null
        show(
            actions = LogActions(onCheckbox = { row, date -> amended = row.habitId to date }),
            interaction = LogInteraction(expanded = setOf(before))
        )
        // The same line, drawn identically — it simply does not answer.
        compose.onNodeWithText("+ [x] meditate 10 min  # 08:15")
            .assertIsDisplayed()
            .assert(hasClickAction().not())
        assertNull(amended)
    }

    @Test
    fun `an amended commit carries its marker`() {
        show(history = history(amended = setOf(yesterday)))
        compose.onNodeWithText("commit ${CommitHash.of(yesterday)}  # amended").assertIsDisplayed()
    }

    // ---- the spoken half --------------------------------------------------

    @Test
    fun `a commit speaks its day and its verdict, never its hash`() {
        show()
        compose.onNodeWithContentDescription("Commit of Aug 19, 2026, everything passed (1/1)")
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("The newest commit").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "it")
    fun `and it speaks them in the reader's language`() {
        show(dayEnds = LocalTime.of(3, 0))
        compose.onNodeWithContentDescription("Commit del 19 ago 2026, è passato tutto (1/1)")
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("Puoi ancora cambiare questo giorno fino alle 03:00")
            .assertIsDisplayed()
    }
}
