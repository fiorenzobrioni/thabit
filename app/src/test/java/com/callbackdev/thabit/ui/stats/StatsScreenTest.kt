package com.callbackdev.thabit.ui.stats

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.callbackdev.thabit.domain.Fixture
import com.callbackdev.thabit.domain.SuiteHistory
import com.callbackdev.thabit.ui.theme.ThabitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * The rendered `stats.md`: the sections that appear, the ones that stay away,
 * and the single gesture on the screen.
 */
@RunWith(RobolectricTestRunner::class)
class StatsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val today: LocalDate = LocalDate.of(2026, 8, 20)
    private val meditate = Fixture.habit(1L, "meditate 10 min", createdAt = today.minusDays(60))

    private fun show(history: SuiteHistory, onOpenCommit: (LocalDate) -> Unit = {}) {
        compose.setContent {
            ThabitTheme {
                StatsScreen(
                    state = StatsUiState(document = StatsDocument.of(history, today), loading = false),
                    onOpenCommit = onOpenCommit
                )
            }
        }
    }

    /**
     * `stats.md` is a long file, and the canvas is lazy: anything below the
     * first screen has to be scrolled to before it exists at all.
     */
    private fun scrollTo(matcher: SemanticsMatcher) =
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(matcher)

    private fun greenDays(days: Int): SuiteHistory {
        val dates = (1L..days.toLong()).map { today.minusDays(it) }
        return Fixture.history(listOf(meditate), dates.map { Fixture.pass(1L, it) }, dates.toSet())
    }

    @Test
    fun `the file names itself in the strip and opens with its graph`() {
        show(greenDays(20))
        compose.onNodeWithText("stats.md").assertIsDisplayed()
        compose.onNodeWithText("## contributions (last 12 weeks)").assertIsDisplayed()
        compose.onNodeWithText("## coverage").assertIsDisplayed()
        compose.onNodeWithText("## suite health").assertIsDisplayed()
    }

    @Test
    fun `coverage states its arithmetic, and explains itself once`() {
        show(greenDays(20))
        compose.onNodeWithText("days ran", substring = true).assertIsDisplayed()
        compose.onNodeWithText(
            "<!-- a day with no run is not a failed build — it is a build that never started -->"
        ).assertIsDisplayed()
    }

    @Test
    fun `the hints are markdown comments, because a hash here would be a heading`() {
        show(greenDays(20))
        // VISION §1.1: the comment channel wears the host file's syntax, and the
        // host file is markdown.
        compose.onNodeWithText("# a day with no run", substring = true).assertDoesNotExist()
    }

    @Test
    fun `sections with nothing to report do not appear at all`() {
        show(greenDays(20))
        compose.onNodeWithText("## flaky tests").assertDoesNotExist()
        compose.onNodeWithText("## regressions").assertDoesNotExist()
    }

    @Test
    fun `a tag row opens its commit in the log`() {
        var opened: LocalDate? = null
        show(greenDays(21), onOpenCommit = { opened = it })

        val tag = StatsDocument.of(greenDays(21), today).tags.first()
        scrollTo(hasContentDescription("${tag.tag}: ${tag.value}"))
        compose.onNodeWithContentDescription("${tag.tag}: ${tag.value}")
            .assertIsDisplayed()
            .assert(hasClickAction())
            .performClick()
        assertEquals(tag.date, opened)
    }

    @Test
    fun `the rules it computed with are printed in the file`() {
        show(greenDays(20))
        scrollTo(hasText("flaky:", substring = true))
        compose.onNodeWithText("flaky:", substring = true).assertIsDisplayed()
    }

    @Test
    fun `an empty suite still draws the grid`() {
        show(SuiteHistory.Empty)
        compose.onNodeWithText("## contributions (last 12 weeks)").assertIsDisplayed()
        compose.onNodeWithText(
            "<!-- nothing to report yet — the grid fills in as days close -->"
        ).assertIsDisplayed()
        compose.onNodeWithText("## coverage").assertDoesNotExist()
    }

    // ---- the spoken half of the metrics (Fase 13) -------------------------

    @Test
    fun `a health table row is heard as a sentence, not as pipes`() {
        show(greenDays(20))
        // The file keeps its padded markdown row — that is the file. Read out
        // literally it is pipes and padding, so the row carries the same numbers
        // as words (VISION §3.3.7, the section that reports the app's own
        // metrics was the last place a fact lived only in a form nobody hears).
        scrollTo(hasContentDescription("“meditate 10 min”: held about 100% of the time lately."))
        compose.onNodeWithContentDescription(
            "“meditate 10 min”: held about 100% of the time lately."
        ).assertIsDisplayed()
    }

    @Test
    fun `the table's header and separator are not spoken as data`() {
        show(greenDays(20))
        // Only the data rows get a sentence: a separator row read as a habit
        // would be the screen inventing a test that does not exist.
        compose.onNodeWithContentDescription("| test", substring = true).assertDoesNotExist()
    }
}
