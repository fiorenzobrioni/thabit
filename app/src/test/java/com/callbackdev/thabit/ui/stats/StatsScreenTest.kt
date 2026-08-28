package com.callbackdev.thabit.ui.stats

import android.content.Context

import androidx.compose.ui.semantics.SemanticsProperties
import com.callbackdev.thabit.domain.Heatmap
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
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.thabit.domain.Regressions
import com.callbackdev.thabit.domain.FlakyTests
import com.callbackdev.thabit.domain.Health
import com.callbackdev.thabit.domain.Fixture
import com.callbackdev.thabit.domain.SuiteHistory
import com.callbackdev.thabit.ui.theme.ThabitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
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

    // ---- the register rule (Fase 15) --------------------------------------

    /**
     * `coverage`, `flaky` and `regression` are the terms §3.3.7 worries about,
     * and these lines are what explains them where the numbers are. A gloss in a
     * language the reader does not have glosses nothing, so the sentences move —
     * while the heading above them and the formula below stay exactly as they
     * are, because one is a key and the other is the arithmetic itself.
     */
    @Test
    @Config(qualifiers = "it")
    fun `the hints explain themselves in Italian, the headings and formulas do not`() {
        show(greenDays(20))
        compose.onNodeWithText("## coverage").assertIsDisplayed()
        compose.onNodeWithText(
            "<!-- un giorno senza run non è una build fallita — è una build che non è mai partita -->"
        ).assertIsDisplayed()
        scrollTo(hasText("flaky:", substring = true))
        compose.onNodeWithText("flaky:", substring = true).assertIsDisplayed()
    }

    /**
     * The guard that keeps the two statements of a rule from drifting apart.
     *
     * The **export** carries the canonical English ([Health.FORMULA] and friends),
     * because an archive whose meaning moves with the phone's language is not an
     * archive. The **screen** carries the same rule as a sentence the reader can
     * read. In English the two must be the same string, character for character:
     * if anybody ever edits one and forgets the other, this fails.
     */
    @Test
    fun `the printed rule and the exported rule are the same sentence`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val printed = StatsDocument.rules().associate { rule ->
            rule.key to context.getString(rule.id, *rule.args.toTypedArray())
        }
        assertEquals(Health.FORMULA, printed.getValue("health"))
        assertEquals(FlakyTests.RULE, printed.getValue("flaky"))
        assertEquals(Regressions.RULE, printed.getValue("regression"))
    }

    /**
     * And in Italian the rule is a sentence the reader can act on, with the same
     * numbers in it — the arithmetic is the half that never translates.
     */
    @Test
    @Config(qualifiers = "it")
    fun `the rules are stated in Italian, with the numbers they are about`() {
        show(greenDays(20))
        scrollTo(hasText("flaky:", substring = true))
        // `pass rate` survives the translation: it is the name of the metric, the
        // same one the table above prints. What moves is the sentence around it.
        compose.onNodeWithText("flaky: pass rate negli ultimi 30 giorni", substring = true)
            .assertIsDisplayed()
    }

    // ---- the grid reads like a grid (Fase 16a) ----------------------------

    /**
     * The labels are data, so they are the reader's: lowercase three-letter day
     * names and month names in the reader's language, the way tsteps' grid next
     * door has always drawn them. They used to be a hardcoded `Locale.ENGLISH`,
     * which was this grid quietly opting out of a rule the rest of the app has
     * followed since Fase 6.
     */
    @Test
    @Config(qualifiers = "it")
    fun `the grid labels its rows and months in the reader's language`() {
        show(greenDays(20))
        compose.onNodeWithText("lun", substring = true).assertIsDisplayed()
        compose.onNodeWithText("mer", substring = true).assertIsDisplayed()
        compose.onNodeWithText("dom", substring = true).assertIsDisplayed()
        // The month row is one line: whichever three months the window spans,
        // they are the Italian abbreviations and not jun/jul/aug.
        compose.onNodeWithText("ago", substring = true).assertIsDisplayed()
    }

    @Test
    fun `and in English they are the English ones, lowercase like the graph`() {
        show(greenDays(20))
        compose.onNodeWithText("mon", substring = true).assertIsDisplayed()
        compose.onNodeWithText("aug", substring = true).assertIsDisplayed()
    }

    /**
     * The paper: a row of the grid is dotted across the window, not a couple of
     * marks floating in a void. Without this the twelve weeks had no shape to
     * place a mark against, which is what the grid was actually for.
     */
    /** The text of the grid row whose label is [label], cells and all. */
    private fun gridRow(label: String): String =
        compose.onAllNodes(hasText(label, substring = true))
            .fetchSemanticsNodes()
            .map { node -> node.config[SemanticsProperties.Text].joinToString("") { it.text } }
            .first { it.startsWith(label) }

    /**
     * The paper: a row is dotted across the whole window, not a couple of marks
     * floating in a void. Twelve weeks of cells, whatever happened in them.
     */
    @Test
    fun `every week of the window is drawn, not just the days that ran`() {
        show(greenDays(20))
        val marks = gridRow("mon").count { it in "·□▪■" }
        assertEquals(Heatmap.WEEKS, marks)
    }

    /** And a suite three days old still draws all twelve, mostly as paper. */
    @Test
    fun `a young suite still draws the whole window`() {
        show(greenDays(3))
        val row = gridRow("mon")
        assertEquals(Heatmap.WEEKS, row.count { it in "·□▪■" })
        assertTrue(row.count { it == '·' } >= Heatmap.WEEKS - 1)
    }

    @Test
    @Config(qualifiers = "it")
    fun `the empty grid says why it is empty in Italian`() {
        show(SuiteHistory.Empty)
        compose.onNodeWithText(
            "<!-- ancora niente da riportare — la griglia si riempie man mano che i giorni si chiudono -->"
        ).assertIsDisplayed()
    }
}
