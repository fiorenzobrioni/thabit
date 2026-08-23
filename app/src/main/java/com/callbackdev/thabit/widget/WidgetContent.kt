package com.callbackdev.thabit.widget

import android.content.res.Resources
import com.callbackdev.thabit.R
import com.callbackdev.thabit.domain.TestOutcome
import com.callbackdev.thabit.domain.TestState
import com.callbackdev.thabit.domain.model.HabitType
import com.callbackdev.thabit.ui.format.CodeFormat
import java.time.LocalDate

/**
 * How much room the launcher has, chosen through the RemoteViews sizes map.
 *
 * The terminal tiers differ only in how many lines fit, so the ladder is a line
 * budget with one rung per line — the series' hard-won scale: a coarse ladder
 * means a widget with room for seven lines silently settles for five, because
 * the map only ever picks a rung that FITS.
 */
sealed interface WidgetTier {
    /** The glanceable strip: 🧪, the fraction, the date. Its own layout. */
    data object Small : WidgetTier

    data class Terminal(val lines: Int) : WidgetTier
}

/** Semantic color role of a token; the renderer maps roles to [WidgetPalette] ints. */
enum class TokenRole { PROMPT, PLAIN, DIM, KEY, STRING, NUMBER, COMMENT, ALERT }

data class WidgetToken(val text: String, val role: TokenRole)

/**
 * What a tap on a line does.
 *
 * This is the widget that **acts** (VISION §4.6), so a line is not just text: it
 * is a control, and which control it is depends on what the test is. Only
 * [Pass] writes; everything else hands the question to the app, which is the
 * surface that can ask it properly.
 */
sealed interface WidgetAction {
    /** Open `habits.test` as it is. */
    data object OpenApp : WidgetAction

    /** Check a boolean test off without opening anything. */
    data class Pass(val habitId: Long) : WidgetAction

    /** Open the app on this test — its prompt for a counter, its spec otherwise. */
    data class Open(val habitId: Long) : WidgetAction
}

/**
 * One line of the widget's terminal.
 *
 * [spoken] is the localized half, and on the widget it matters more than
 * anywhere else in the app: `habits.test` can put a `#` comment beside a glyph
 * to disambiguate it, and here there is no room for one. A screen reader gets
 * the whole sentence — *"read 20 pages, still to do, tap to mark it done"* —
 * never `[ ] read 20 pages` read out as brackets (VISION §3.3.7).
 */
data class WidgetLine(
    val tokens: List<WidgetToken>,
    val action: WidgetAction = WidgetAction.OpenApp,
    val spoken: String? = null
) {
    val text: String get() = tokens.joinToString("") { it.text }
}

/**
 * Everything a widget layout binds. [bodyLines] is what varies per tier; SMALL
 * ignores it and uses [smallValue]/[smallLabel] instead.
 */
data class WidgetContent(
    val headerTitle: String,
    val promptLine: WidgetLine,
    val bodyLines: List<WidgetLine>,
    val emoji: String?,
    val smallValue: WidgetLine,
    val smallLabel: WidgetLine,
    /** Spoken summary of the whole widget, for the small tier and the root. */
    val spokenSummary: String
)

/** What the updater gathers for one render pass. */
data class WidgetData(
    /** The logical day being rendered — stated on screen, never assumed. */
    val date: LocalDate,
    /** Today's run, in file order: what `habits.test` would show. */
    val outcomes: List<TestOutcome> = emptyList(),
    /** Live tests in the suite, so an empty suite can say so. */
    val suiteSize: Int = 0
) {
    val passed: Int get() = outcomes.count { it.state == TestState.PASS }

    /**
     * What is actually still to do — and holding avoid tests are **not** in it.
     *
     * The widget's trailing comment says `tap to pass`, and a `[·]` row cannot be
     * passed by a tap because it passes by itself. Counting it would turn "you
     * are doing fine" into an item on a to-do list, which is the same call the
     * evening digest makes for the same reason (VISION §3.3.4).
     */
    val pending: Int get() = outcomes.count { it.state == TestState.PENDING }

    /** The denominator the app's own status bar uses: due tests minus skips. */
    val graded: Int get() = outcomes.count { it.state != TestState.SKIP }
}

/**
 * The widget's transcript, worked out as a value.
 *
 * Same split as every file in the app: the words are a pure function of the
 * state, so they are asserted character by character, and the renderer only
 * paints them and wires the taps.
 *
 * The transcript is built **whole and then cut from the bottom** to the tier's
 * budget, so a new rung needs no new code here — and the one line that always
 * survives is the suite line, which is why it carries both the date and the
 * arithmetic.
 */
object WidgetContentBuilder {

    const val HEADER = "thabit --status"
    const val EMOJI = "🧪"

    /** VISION §4.6 draws ten cells; the widget keeps them. */
    const val BAR_WIDTH = 10

    /**
     * The tallest transcript this builder can produce: the suite line, a
     * generous suite, and the trailing comment. A rung past what anything can
     * fill just promises height it never uses.
     */
    const val MAX_LINES = 10

    /** Rows a tier can spend on tests, once the suite line and comment are paid for. */
    fun bodyLineBudget(tier: WidgetTier): Int = when (tier) {
        is WidgetTier.Small -> 0
        is WidgetTier.Terminal -> tier.lines
    }

    fun build(data: WidgetData, tier: WidgetTier, resources: Resources): WidgetContent {
        val prompt = WidgetLine(
            listOf(
                WidgetToken("you@thabit", TokenRole.PROMPT),
                WidgetToken(":~", TokenRole.PLAIN),
                WidgetToken("$ ", TokenRole.PLAIN),
                WidgetToken("cat habits.test", TokenRole.DIM)
            )
        )
        val budget = bodyLineBudget(tier)

        // The suite line, and it is the line that never gets cut. It carries the
        // **date it is rendering** because a widget cannot know it has gone
        // stale: nothing repaints between the rollover and the next look, so the
        // only honest defence against yesterday's suite posing as today's is to
        // say which day this is (VISION §4.6).
        //
        // The order is the priority order, because this line is the one that
        // ellipsizes on a narrow widget: **the arithmetic first**, since a
        // verdict that loses its numbers is the one thing §3.3.7 forbids; the
        // bar next, which is only the fraction drawn; the date last, because it
        // is the extra check and not the fact. A narrow-but-tall widget
        // therefore keeps `3/6` and may drop the date — and the glanceable strip,
        // which is what a narrow widget usually gets, states the date outright.
        val suiteLine = WidgetLine(
            tokens = listOf(
                WidgetToken("${data.passed}/${data.graded}", TokenRole.NUMBER),
                WidgetToken(" ", TokenRole.PLAIN),
                WidgetToken(CodeFormat.bar(fraction(data), BAR_WIDTH), TokenRole.PROMPT),
                WidgetToken("  ${CodeFormat.date(data.date)}", TokenRole.DIM)
            ),
            spoken = resources.getString(
                R.string.cd_widget_suite,
                data.passed,
                data.graded,
                CodeFormat.date(data.date)
            )
        )

        // A skipped test keeps its row: `[~]` is a state the day had, and hiding
        // it would make the widget's list disagree with the file's.
        val rows = when {
            data.suiteSize == 0 -> listOf(
                comment("# no tests yet — tap to add one", resources.getString(R.string.cd_widget_empty))
            )
            data.outcomes.isEmpty() -> listOf(
                comment("# nothing due today", resources.getString(R.string.cd_widget_nothing_due))
            )
            else -> data.outcomes.map { outcome -> testLine(outcome, resources) }
        }

        // The suite line is paid for first and never cut — it is the minimum
        // tier all by itself. The rows take whatever is left, and the trailing
        // comment gets a line only if one is still spare: it is the least
        // important thing here, and there is deliberately no `# N more` when
        // rows are cut, because the suite line above already states `3/6`. A
        // line spent repeating the total would cost a row to say something the
        // reader can already see.
        val shownRows = rows.take(maxOf(budget - 1, 0))
        val trailing = trailingComment(data, resources)
        val lines = buildList {
            add(suiteLine)
            addAll(shownRows)
            if (trailing != null && size < budget) add(trailing)
        }

        return WidgetContent(
            headerTitle = HEADER,
            promptLine = prompt,
            bodyLines = lines.take(budget),
            emoji = EMOJI,
            // The glanceable strip keeps the fraction and the date, and drops the
            // bar: the bar *is* the fraction drawn, so it is the one thing here
            // that repeats something already on screen, while the date repeats
            // nothing and is what tells a stale widget from a fresh one.
            smallValue = WidgetLine(
                listOf(WidgetToken("${data.passed}/${data.graded}", TokenRole.NUMBER))
            ),
            smallLabel = WidgetLine(
                listOf(WidgetToken(CodeFormat.date(data.date), TokenRole.DIM))
            ),
            spokenSummary = suiteLine.spoken.orEmpty()
        )
    }

    /**
     * `[x] meditate 10 min` — one test, one line, one tap target.
     *
     * **One test per line, and not the two columns VISION §4.6 sketches.** That
     * sketch was drawn before the rows became controls: here every row is a
     * write, and two 120dp-wide targets side by side on a 30dp row is a mis-tap
     * that files a `[x]` nobody meant — against an app whose whole premise is
     * that every `[x]` was typed by a person. One column also leaves the name
     * its full width, and a habit's name is the user's own words: truncating it
     * to fit a column is the one kind of clipping this app should not do. The
     * cost is real (half as many tests per screenful) and is paid by the suite
     * line, which states `3/6` whatever the tier had room for.
     */
    private fun testLine(outcome: TestOutcome, resources: Resources): WidgetLine {
        val habit = outcome.habit
        val glyph = glyphFor(outcome.state)
        val name = listOfNotNull(habit.emoji, habit.name).joinToString(" ")
        // Only an untouched boolean is answerable from a home screen. A counter
        // needs a number, an avoid test's only widget verb would be "I broke it"
        // (a one-tap failure button is a trap, not a feature), and a test already
        // settled has nothing left to ask — undoing it is the app's job, where
        // the row says what it is undoing.
        val answerable = habit.type == HabitType.BOOLEAN && outcome.state == TestState.PENDING
        return WidgetLine(
            tokens = listOf(
                WidgetToken(glyph, roleFor(outcome.state)),
                WidgetToken(" $name", TokenRole.PLAIN)
            ),
            action = if (answerable) WidgetAction.Pass(habit.id) else WidgetAction.Open(habit.id),
            spoken = resources.getString(
                if (answerable) R.string.cd_widget_row_pass else R.string.cd_widget_row_open,
                name,
                resources.getString(spokenStateOf(outcome.state))
            )
        )
    }

    private fun trailingComment(data: WidgetData, resources: Resources): WidgetLine? = when {
        data.suiteSize == 0 || data.outcomes.isEmpty() -> null
        data.pending > 0 -> comment(
            "# ${data.pending} pending — tap to pass",
            resources.getQuantityString(R.plurals.cd_widget_pending, data.pending, data.pending)
        )
        // Factual, never a congratulation: the day is still open, so there is no
        // verdict to announce (VISION §3.3.4 — no theatrics either way).
        else -> comment("# nothing pending", resources.getString(R.string.cd_widget_none_pending))
    }

    /**
     * The glyph is the **only** channel here: `habits.test` puts a `#` comment
     * beside it to tell a holding avoid test from a pending one, and the widget
     * has no room for that comment — so `[·]` has to carry the difference on its
     * own (VISION §4.6). The spoken half says it in words regardless.
     */
    private fun glyphFor(state: TestState): String = when (state) {
        TestState.PASS -> "[x]"
        TestState.FAIL -> "[!]"
        TestState.SKIP -> "[~]"
        TestState.HOLDING -> "[·]"
        TestState.PENDING -> "[ ]"
    }

    private fun roleFor(state: TestState): TokenRole = when (state) {
        TestState.PASS -> TokenRole.PROMPT
        TestState.FAIL -> TokenRole.ALERT
        TestState.SKIP, TestState.HOLDING -> TokenRole.COMMENT
        TestState.PENDING -> TokenRole.PLAIN
    }

    private fun spokenStateOf(state: TestState): Int = when (state) {
        TestState.PASS -> R.string.cd_state_passed
        TestState.FAIL -> R.string.cd_state_failed
        TestState.SKIP -> R.string.cd_state_skipped
        TestState.HOLDING -> R.string.cd_state_holding
        TestState.PENDING -> R.string.cd_state_pending
    }

    /** Null when nothing is graded yet: an empty bar, never a full one. */
    private fun fraction(data: WidgetData): Double? =
        if (data.graded == 0) null else data.passed.toDouble() / data.graded

    private fun comment(text: String, spoken: String) =
        WidgetLine(listOf(WidgetToken(text, TokenRole.COMMENT)), spoken = spoken)
}
