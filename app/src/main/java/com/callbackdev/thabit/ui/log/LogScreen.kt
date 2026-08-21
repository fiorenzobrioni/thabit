package com.callbackdev.thabit.ui.log

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callbackdev.thabit.R
import com.callbackdev.thabit.domain.BuildResult
import com.callbackdev.thabit.domain.CommitHash
import com.callbackdev.thabit.domain.SuiteHistory
import com.callbackdev.thabit.domain.TestState
import com.callbackdev.thabit.domain.model.AssertSpec
import com.callbackdev.thabit.domain.model.Check
import com.callbackdev.thabit.domain.model.CheckState
import com.callbackdev.thabit.domain.model.Habit
import com.callbackdev.thabit.domain.model.HabitType
import com.callbackdev.thabit.domain.model.Schedule
import com.callbackdev.thabit.ui.components.CanvasLine
import com.callbackdev.thabit.ui.components.CodeCanvas
import com.callbackdev.thabit.ui.components.CodeLine
import com.callbackdev.thabit.ui.components.EditorTabs
import com.callbackdev.thabit.ui.components.StatusBarStart
import com.callbackdev.thabit.ui.components.StatusBarText
import com.callbackdev.thabit.ui.components.StatusBarDivider
import com.callbackdev.thabit.ui.components.TerminalInput
import com.callbackdev.thabit.ui.components.TerminalStatusBar
import com.callbackdev.thabit.ui.components.WidgetLine
import com.callbackdev.thabit.ui.components.commentLine
import com.callbackdev.thabit.ui.editor.TextControl
import com.callbackdev.thabit.ui.editor.checkbox
import com.callbackdev.thabit.ui.editor.decorative
import com.callbackdev.thabit.ui.format.CodeFormat
import com.callbackdev.thabit.ui.theme.SyntaxColors
import com.callbackdev.thabit.ui.theme.ThabitTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * `habits_history.diff` — the CI dashboard as a git log.
 *
 * Today sits on top as uncommitted changes; every closed day that ran is a
 * commit that expands into its own diff. Yesterday's commit is the only one that
 * still answers to a tap, and it says so on its own line rather than waiting to
 * be discovered (VISION §4.2).
 *
 * The document is computed by [LogDocument] with no Compose involved, so this
 * function does one thing: turn a value into lines.
 */
@Composable
fun LogScreen(
    modifier: Modifier = Modifier,
    viewModel: LogViewModel = viewModel(factory = LogViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The amend window closes at `day_ends`, so a log left on screen through it
    // must not still be offering yesterday's rows when the reader comes back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }

    // A tag row in `stats.md` asked for a commit. The view model has already
    // opened it; this is the half that can only happen once the file exists as
    // lines — scrolling it into view.
    val focus by LogFocus.request.collectAsStateWithLifecycle()
    LogScreen(
        state = state,
        actions = LogActions(viewModel),
        modifier = modifier,
        focusDate = focus,
        onFocusHandled = LogFocus::consume
    )
}

/** The stateless half — what the previews and the UI tests drive. */
@Composable
fun LogScreen(
    state: LogUiState,
    actions: LogActions,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    /** A commit `stats.md` asked for, or null. */
    focusDate: LocalDate? = null,
    onFocusHandled: () -> Unit = {}
) {
    val document = state.document
    val lines = if (document == null) {
        emptyList()
    } else {
        logLines(document = document, interaction = state.interaction, actions = actions)
    }

    // The line and not the entry: the canvas is a list of lines, and the commit
    // the reader asked for has to land at the top of the screen rather than
    // somewhere inside the day above it.
    LaunchedEffect(focusDate, lines.size) {
        val date = focusDate ?: return@LaunchedEffect
        val hash = CommitHash.of(date)
        val index = lines.indexOfFirst {
            it is CodeLine && it.text.text.startsWith("commit $hash")
        }
        if (index >= 0) {
            listState.animateScrollToItem(index)
            onFocusHandled()
        }
    }

    Column(modifier.fillMaxSize()) {
        EditorTabs(fileNames = listOf(LogDocument.FILE_NAME), activeIndex = 0, onSelect = {})
        Box(Modifier.weight(1f)) {
            CodeCanvas(lines = lines, state = listState, modifier = Modifier.fillMaxSize())
        }
        LogStatusBar(document)
    }
}

/** The taps a log line can produce, gathered so previews can pass no-ops. */
class LogActions(
    val onToggleCommit: (LocalDate) -> Unit = {},
    val onCheckbox: (RowTest, LocalDate) -> Unit = { _, _ -> },
    val onPromptChange: (String) -> Unit = {},
    val onSubmitPrompt: () -> Unit = {},
    val onCancelPrompt: () -> Unit = {}
) {
    constructor(viewModel: LogViewModel) : this(
        onToggleCommit = viewModel::onToggleCommit,
        onCheckbox = viewModel::onCheckbox,
        onPromptChange = viewModel::onPromptChange,
        onSubmitPrompt = viewModel::onSubmitPrompt,
        onCancelPrompt = viewModel::onCancelPrompt
    )
}

/** `⎇ main | 12 commits` and `HEAD → 9e31c7a`. */
@Composable
private fun LogStatusBar(document: LogDocument?) {
    TerminalStatusBar {
        StatusBarStart {
            StatusBarText("⎇ main")
            if (document != null && !document.isEmpty) {
                StatusBarDivider()
                StatusBarText(
                    text = "${document.commitCount} commits",
                    modifier = Modifier.spokenAs(
                        pluralStringResource(
                            R.plurals.cd_status_commits,
                            document.commitCount,
                            document.commitCount
                        )
                    )
                )
            }
        }
        document?.head?.let { head ->
            StatusBarText(
                text = "HEAD → $head",
                // A hash is signature, not information: the sentence a screen
                // reader gets is the fact it stands for (VISION §3.3.7).
                modifier = Modifier.spokenAs(stringResource(R.string.cd_status_head))
            )
        }
    }
}

@Composable
private fun logLines(
    document: LogDocument,
    interaction: LogInteraction,
    actions: LogActions
): List<CanvasLine> {
    val syntax = ThabitTheme.syntax
    val lines = mutableListOf<CanvasLine>()

    lines += commentLine("# ${LogDocument.BRANCH_LINE}", syntax)
    document.todaySummary?.let { lines += commentLine("#   $it", syntax) }

    if (document.isEmpty) {
        LogDocument.emptyHints(hasSuite = document.todaySummary != null).forEach { hint ->
            lines += commentLine(if (hint.isEmpty()) "#" else "# $hint", syntax)
        }
        return lines
    }

    document.entries.forEach { entry ->
        lines += commentLine("", syntax)
        when (entry) {
            is LogEntry.Commit -> lines += commitLines(entry, interaction, actions, syntax)
            is LogEntry.Week -> lines += weekLines(entry, syntax)
        }
    }
    return lines
}

/** One commit: its headline, its date, its message, and whatever it has unfolded. */
@Composable
private fun commitLines(
    commit: LogEntry.Commit,
    interaction: LogInteraction,
    actions: LogActions,
    syntax: SyntaxColors
): List<CanvasLine> {
    val expanded = commit.date in interaction.expanded
    val lines = mutableListOf<CanvasLine>()

    lines += CodeLine(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = syntax.key)) { append("commit ${commit.hash}") }
            commit.tags.forEach { tag ->
                // Tags in number-orange: git's tag yellow, in this palette.
                withStyle(SpanStyle(color = syntax.number)) { append("  (tag: $tag)") }
            }
            if (commit.amended) {
                withStyle(SpanStyle(color = syntax.comment)) { append("  # amended") }
            }
        },
        onClick = { actions.onToggleCommit(commit.date) },
        onClickLabel = stringResource(
            if (expanded) R.string.cd_log_commit_hide else R.string.cd_log_commit_show
        ),
        contentDescription = commit.spokenHeadline()
    )
    lines += commentLine("Date:   ${commit.date.spokenDate()}", syntax)
    commit.amendableUntil?.let { until ->
        // The window is declared, not discovered: nobody taps a line hoping it
        // might be editable (VISION §4.2).
        lines += commentLine(
            text = "# still editable until ${until.spokenTime()}",
            syntax = syntax,
            contentDescription = stringResource(R.string.cd_log_amendable, until.spokenTime())
        )
    }
    lines += commentLine("", syntax)
    lines += CodeLine(AnnotatedString("    ${commit.message}"))
    commit.verdict?.let { verdict ->
        lines += CodeLine(
            text = AnnotatedString(
                "    ${verdict.text}",
                SpanStyle(color = verdict.result.color(syntax))
            ),
            contentDescription = verdict.spoken()
        )
    }

    if (expanded) {
        lines += commentLine("", syntax)
        commit.rows.forEach { row ->
            lines += diffLine(row, commit, actions, syntax)
            val prompt = interaction.prompt
            if (prompt != null && prompt.date == commit.date && prompt.habitId == row.test?.habitId) {
                lines += promptLines(prompt, actions, syntax)
            }
        }
    }
    interaction.transient?.takeIf { it.date == commit.date }?.let {
        lines += commentLine("# ${it.text}", syntax)
    }
    return lines
}

/**
 * One line of the day's diff.
 *
 * On yesterday's commit the sign and the box are a single tap target — the same
 * gesture as today's file, which is the whole of `--amend`: the control is the
 * row, never a verb the reader has to recognize (VISION §4.2). Every other day
 * draws the identical line and simply does not answer.
 */
@Composable
private fun diffLine(
    row: CommitRow,
    commit: LogEntry.Commit,
    actions: LogActions,
    syntax: SyntaxColors
): CanvasLine {
    val color = row.sign.color(syntax)
    val test = row.test
    if (!commit.amendable || test == null) {
        return CodeLine(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = color)) { append("${row.sign.glyph} ${row.label}") }
                row.detail?.let {
                    withStyle(SpanStyle(color = syntax.comment.copy(alpha = 0.6f))) {
                        append("  # $it")
                    }
                }
            },
            contentDescription = row.spoken()
        )
    }

    val spoken = row.spoken()
    val amendLabel = stringResource(R.string.cd_log_amend)
    return WidgetLine(measureText = "${row.text}    ") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextControl(
                label = "${row.sign.glyph} ${test.state.checkbox().glyph}",
                color = color,
                description = spoken,
                actionLabel = amendLabel,
                onClick = { actions.onCheckbox(test, commit.date) }
            )
            Text(
                text = test.name,
                style = MaterialTheme.typography.bodySmall,
                color = color
            )
            row.detail?.let {
                Text(
                    text = "  # $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = syntax.comment.copy(alpha = 0.6f),
                    modifier = Modifier.decorative()
                )
            }
        }
    }
}

/** The counter's prompt, opened inside the commit it is amending. */
@Composable
private fun promptLines(
    prompt: LogPrompt,
    actions: LogActions,
    syntax: SyntaxColors
): List<CanvasLine> {
    val label = prompt.unit.ifBlank { "value" }
    return listOf(
        WidgetLine(indent = 1, measureText = "> $label: ${"_".repeat(16)}    ") {
            TerminalInput(
                value = prompt.text,
                onValueChange = actions.onPromptChange,
                prompt = "> $label:",
                autoFocus = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { actions.onSubmitPrompt() })
            )
        },
        WidgetLine(indent = 1, measureText = "[ok]  [esc]     ") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextControl(
                    label = "[ok]",
                    color = syntax.diffAdd,
                    description = stringResource(R.string.cd_action_confirm),
                    onClick = actions.onSubmitPrompt
                )
                TextControl(
                    label = "[esc]",
                    color = syntax.comment,
                    description = stringResource(R.string.cd_action_cancel),
                    onClick = actions.onCancelPrompt
                )
            }
        }
    )
}

/** `--- week 34 · 89% passed (+6% vs week 33) ---`, and the quotas it judges. */
@Composable
private fun weekLines(week: LogEntry.Week, syntax: SyntaxColors): List<CanvasLine> {
    val lines = mutableListOf<CanvasLine>()
    lines += CodeLine(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = syntax.comment)) { append("--- ${week.label}") }
            week.delta?.let { delta ->
                withStyle(SpanStyle(color = syntax.comment)) { append(" (") }
                withStyle(
                    SpanStyle(color = if (week.deltaPositive) syntax.diffAdd else syntax.diffDel)
                ) { append(delta) }
                withStyle(SpanStyle(color = syntax.comment)) {
                    append(" vs ${week.previousLabel})")
                }
            }
            withStyle(SpanStyle(color = syntax.comment)) { append(" ---") }
        },
        contentDescription = week.spoken()
    )
    week.quotas.forEach { quota ->
        lines += CodeLine(
            text = AnnotatedString(
                quota.text,
                SpanStyle(
                    color = when {
                        quota.met -> syntax.diffAdd
                        quota.closed -> syntax.diffDel
                        else -> syntax.comment
                    }
                )
            ),
            contentDescription = stringResource(
                R.string.cd_log_quota,
                quota.name,
                quota.done,
                quota.target
            )
        )
    }
    return lines
}

// ---- the localized half ---------------------------------------------------

private fun BuildResult.color(syntax: SyntaxColors): Color = when (this) {
    BuildResult.PASSED -> syntax.diffAdd
    BuildResult.UNSTABLE -> syntax.number
    else -> syntax.diffDel
}

private fun DiffSign.color(syntax: SyntaxColors): Color = when (this) {
    DiffSign.ADD -> syntax.diffAdd
    DiffSign.DEL -> syntax.diffDel
    DiffSign.SKIP -> syntax.comment
}

/** *Commit del 19 agosto, 6 di 6 passati* — the headline, in words. */
@Composable
private fun LogEntry.Commit.spokenHeadline(): String {
    val parts = mutableListOf(stringResource(R.string.cd_log_commit, date.spokenDate()))
    verdict?.let { parts += it.spoken() }
    if (amended) parts += stringResource(R.string.cd_log_amended)
    tags.forEach { parts += stringResource(R.string.cd_log_tag, it) }
    return parts.joinToString(", ")
}

@Composable
private fun LogEntry.Verdict.spoken(): String = stringResource(
    when (result) {
        BuildResult.PASSED -> R.string.cd_build_passed
        BuildResult.UNSTABLE -> R.string.cd_build_unstable
        else -> R.string.cd_build_failed
    },
    // The arithmetic travels with the word in both channels (VISION §3.3.7).
    text.substringAfter('(').substringBefore(')')
)

/**
 * *meditate 10 min, passato, 07:03*.
 *
 * The detail is repeated here rather than hidden, unlike the suite's comment
 * channel: every detail a log row carries is a clock, a number, or the user's
 * own words (a unit they typed, a note they wrote). None of it is English the
 * app chose, so reading it aloud is reading the reader their own data back.
 */
@Composable
private fun CommitRow.spoken(): String {
    val test = test ?: return label
    val parts = mutableListOf(test.name, stringResource(test.state.checkbox().spokenRes))
    detail?.let { parts += it }
    return parts.joinToString(", ")
}

@Composable
private fun LogEntry.Week.spoken(): String = stringResource(
    R.string.cd_log_week,
    week.week,
    rate?.let { CodeFormat.percent(it) } ?: "--%"
)

/** A date said the way the reader's language says dates — chrome, so localized. */
@Composable
private fun LocalDate.spokenDate(): String {
    val locale = LocalConfiguration.current.locales[0]
    return format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
}

@Composable
private fun LocalTime.spokenTime(): String {
    val locale = LocalConfiguration.current.locales[0]
    return format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
}

/** The plain-language half of a git decoration (VISION §3.3.7). */
private fun Modifier.spokenAs(label: String): Modifier =
    semantics { contentDescription = label }

@Preview(showBackground = true, backgroundColor = 0xFF10141A, heightDp = 640)
@Composable
private fun LogScreenPreview() {
    ThabitTheme {
        LogScreen(
            state = LogUiState(document = previewDocument(), loading = false),
            actions = LogActions(),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A, heightDp = 400)
@Composable
private fun LogExpandedPreview() {
    ThabitTheme {
        LogScreen(
            state = LogUiState(
                document = previewDocument(),
                interaction = LogInteraction(expanded = setOf(PREVIEW_DAY.minusDays(1))),
                loading = false
            ),
            actions = LogActions()
        )
    }
}

private val PREVIEW_DAY: LocalDate = LocalDate.of(2026, 8, 20)

/** Three days of history — the shape VISION §4.2 draws. */
private fun previewDocument(): LogDocument {
    val day = PREVIEW_DAY
    val habits = listOf(
        Habit(1, "meditate 10 min", createdAt = day.minusDays(10), position = 0),
        Habit(
            2, "read 20 pages", HabitType.COUNTER, AssertSpec(20.0, "pages"),
            createdAt = day.minusDays(10), position = 1
        ),
        Habit(3, "run 5k", schedule = Schedule.Quota(3), createdAt = day.minusDays(10), position = 2)
    )
    val checks = (1L..3L).flatMap { back ->
        val date = day.minusDays(back)
        listOf(
            Check(1, date, CheckState.PASS, at = LocalTime.of(7, 3)),
            Check(2, date, CheckState.PASS, value = 31.0, at = LocalTime.of(22, 10))
        )
    } + Check(3, day.minusDays(2), CheckState.SKIP, note = "rest day")
    return LogDocument.of(
        history = SuiteHistory(
            habits = habits,
            checks = checks,
            presentDays = (0L..3L).map { day.minusDays(it) }.toSet(),
            amendedDays = setOf(day.minusDays(2))
        ),
        logicalDate = day
    )
}
