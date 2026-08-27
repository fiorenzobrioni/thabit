package com.callbackdev.thabit.ui.stats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callbackdev.thabit.R
import com.callbackdev.thabit.domain.HeatmapGrid
import com.callbackdev.thabit.domain.SuiteHistory
import com.callbackdev.thabit.domain.model.Check
import com.callbackdev.thabit.domain.model.CheckState
import com.callbackdev.thabit.domain.model.Habit
import com.callbackdev.thabit.ui.components.CanvasLine
import com.callbackdev.thabit.ui.components.CodeCanvas
import com.callbackdev.thabit.ui.components.CodeLine
import com.callbackdev.thabit.ui.components.EditorTabs
import com.callbackdev.thabit.ui.components.StatusBarStart
import com.callbackdev.thabit.ui.components.StatusBarText
import com.callbackdev.thabit.ui.components.TerminalStatusBar
import com.callbackdev.thabit.ui.components.buildMarkdownLines
import com.callbackdev.thabit.ui.format.CodeFormat
import com.callbackdev.thabit.ui.theme.SyntaxColors
import com.callbackdev.thabit.ui.theme.ThabitTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * `stats.md` — the coverage report, rendered as markdown **source**.
 *
 * Everything on it is a markdown line except the contribution graph, which is
 * drawn cell by cell so each mark can carry its own intensity colour: it is the
 * one picture in an app made of text, and a picture in a source view is still
 * made of characters.
 *
 * The file is read-only by nature — `ro` in the status bar — with exactly one
 * gesture: a tag row jumps to its commit in the log (VISION §4.3).
 */
@Composable
fun StatsScreen(
    onOpenCommit: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = viewModel(factory = StatsViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }
    StatsScreen(state = state, onOpenCommit = onOpenCommit, modifier = modifier)
}

/** The stateless half — what the previews and the UI tests drive. */
@Composable
fun StatsScreen(
    state: StatsUiState,
    onOpenCommit: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val document = state.document
    Column(modifier.fillMaxSize()) {
        EditorTabs(fileNames = listOf(StatsDocument.FILE_NAME), activeIndex = 0, onSelect = {})
        Box(Modifier.weight(1f)) {
            CodeCanvas(
                lines = if (document == null) emptyList() else statsLines(document, onOpenCommit),
                state = rememberLazyListState(),
                modifier = Modifier.fillMaxSize()
            )
        }
        TerminalStatusBar {
            StatusBarStart { StatusBarText("⎇ main") }
            // A stats file is computed, not edited — and unlike the log's `⎇`
            // decorations this one is a fact about the screen.
            StatusBarText("ro")
        }
    }
}

@Composable
private fun statsLines(
    document: StatsDocument,
    onOpenCommit: (LocalDate) -> Unit
): List<CanvasLine> {
    val syntax = ThabitTheme.syntax
    val lines = mutableListOf<CanvasLine>()

    fun markdown(vararg text: String) {
        lines += buildMarkdownLines(text.toList(), syntax)
    }

    // ---- contributions -----------------------------------------------------
    markdown(StatsDocument.H_CONTRIBUTIONS, "")
    lines += heatmapLines(document.heatmap, syntax)

    if (document.isEmpty) {
        markdown("", comment(stringResource(StatsDocument.EMPTY_HINT)))
        return lines
    }

    // ---- coverage ----------------------------------------------------------
    markdown("", StatsDocument.H_COVERAGE, "")
    lines += CodeLine(
        text = AnnotatedString("${document.coverageLine}   ${document.coverageBar}"),
        contentDescription = stringResource(
            R.string.cd_stats_coverage,
            document.coverage.ranDays,
            document.coverage.dueDays,
            document.coverage.noRunDays
        )
    )
    markdown(comment(stringResource(StatsDocument.COVERAGE_HINT)))

    // ---- suite health ------------------------------------------------------
    if (document.healthTable.isNotEmpty()) {
        markdown("", StatsDocument.H_HEALTH, "")
        lines += spokenRows(
            rendered = document.healthTable,
            // A table's first two lines are its header and its separator; the
            // data rows follow, in the same order as the facts behind them.
            skip = 2,
            spoken = document.healthRows.map { row ->
                stringResource(
                    R.string.cd_stats_health_row,
                    row.name,
                    CodeFormat.percent(row.health)
                )
            },
            syntax = syntax
        )
    }

    // ---- flaky tests -------------------------------------------------------
    if (document.flaky.isNotEmpty()) {
        markdown("", StatsDocument.H_FLAKY, "")
        lines += spokenRows(
            rendered = document.flaky,
            skip = 0,
            spoken = document.flakyRows.map { test ->
                pluralStringResource(
                    R.plurals.cd_stats_flaky_row,
                    StatsDocument.WINDOW_DAYS.toInt(),
                    test.habit.name,
                    CodeFormat.percent(test.passRate),
                    StatsDocument.WINDOW_DAYS.toInt()
                )
            },
            syntax = syntax
        )
        markdown(comment(stringResource(StatsDocument.FLAKY_HINT)))
    }

    // ---- regressions -------------------------------------------------------
    if (document.regressions.isNotEmpty()) {
        markdown("", StatsDocument.H_REGRESSIONS, "")
        lines += spokenRows(
            rendered = document.regressions,
            skip = 0,
            spoken = document.regressionRows.map { regression ->
                stringResource(R.string.cd_stats_regression_row, regression.habit.name)
            },
            syntax = syntax
        )
        markdown(comment(stringResource(StatsDocument.REGRESSION_HINT)))
    }

    // ---- tags --------------------------------------------------------------
    if (document.tagTable.isNotEmpty()) {
        markdown("", StatsDocument.H_TAGS, "")
        lines += tagLines(document, syntax, onOpenCommit)
    }

    // ---- the rules, printed ------------------------------------------------
    markdown("")
    markdown(comment(stringResource(StatsDocument.WINDOW_RULE, StatsDocument.WINDOW_DAYS.toInt())))
    StatsDocument.rules().forEach { markdown(comment(it)) }
    return lines
}

/**
 * The graph: one row per day of the week, one mark per week.
 *
 * Each mark is coloured by its own level, which is why this is not markdown —
 * and the blanks are left blank on purpose: a day the app never saw looks
 * exactly like a day that has not happened yet, because that is what it is
 * (VISION §3.3.8).
 */
@Composable
private fun heatmapLines(grid: HeatmapGrid, syntax: SyntaxColors): List<CanvasLine> {
    val locale = Locale.ENGLISH
    val lines = grid.rows.mapIndexed { index, row ->
        // Labels on every other row, like the graph this borrows from: seven
        // labels in a 13sp column would be noise, and none at all would leave
        // the reader counting rows.
        val label = if (index % 2 == 0) row.day.short(locale) else "   "
        CodeLine(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = syntax.comment)) { append(label.padEnd(5)) }
                row.cells.forEach { cell ->
                    withStyle(SpanStyle(color = cell.level.color(syntax))) {
                        append(cell.glyph)
                    }
                    append(" ")
                }
            },
            contentDescription = pluralStringResource(
                R.plurals.cd_stats_heatmap_row,
                row.cells.count { it.level != null && it.level > 0 },
                row.day.getDisplayName(TextStyle.FULL, locale),
                row.cells.count { it.level != null && it.level > 0 }
            )
        )
    }
    return lines + monthLine(grid, syntax)
}

/** `         jun         jul         aug` — one label per month, under its week. */
private fun monthLine(grid: HeatmapGrid, syntax: SyntaxColors): CodeLine {
    val text = StringBuilder(" ".repeat(5))
    grid.months.forEach { month ->
        val target = 5 + month.column * 2
        while (text.length < target) text.append(' ')
        text.append(month.label)
    }
    return CodeLine(AnnotatedString(text.toString(), SpanStyle(color = syntax.comment)))
}

/**
 * Markdown rows, each carrying the sentence a screen reader gets instead.
 *
 * The rendered half stays exactly what the file shows — a padded table row, a
 * line with an em dash — because that is the file and the file is the point. The
 * spoken half is the same numbers as a sentence, in the reader's language: read
 * literally, `| meditate | 82% | 18 | 24/28 |` is pipes and padding, and the
 * three sections that report the app's own metrics were the last place where a
 * fact lived only in a form nobody could hear (Fase 13's §3.3.7 audit).
 *
 * [skip] is how many leading lines are chrome rather than data — two for a
 * markdown table (header and separator), none for a plain list of lines.
 */
@Composable
private fun spokenRows(
    rendered: List<String>,
    skip: Int,
    spoken: List<String>,
    syntax: SyntaxColors
): List<CanvasLine> = rendered.mapIndexed { index, line ->
    val built = buildMarkdownLines(listOf(line), syntax).first()
    spoken.getOrNull(index - skip)
        ?.let { built.copy(contentDescription = it) }
        ?: built
}

/** The tag table, with its data rows pointing at their commit. */
@Composable
private fun tagLines(
    document: StatsDocument,
    syntax: SyntaxColors,
    onOpenCommit: (LocalDate) -> Unit
): List<CanvasLine> {
    val open = stringResource(R.string.cd_stats_open_commit)
    return document.tagTable.mapIndexed { index, line ->
        // The first two lines are the header and the separator; every line after
        // them is a record, in the same order as `tags`.
        val tag = document.tags.getOrNull(index - 2)
        val rendered = buildMarkdownLines(listOf(line), syntax).first()
        if (tag == null) {
            rendered
        } else {
            rendered.copy(
                onClick = { onOpenCommit(tag.date) },
                onClickLabel = open,
                contentDescription = "${tag.tag}: ${tag.value}"
            )
        }
    }
}

/** Markdown's own comment channel — `#` here would be a heading (VISION §1.1). */
private fun comment(text: String): String = "<!-- $text -->"

private fun Int?.color(syntax: SyntaxColors) = when (this) {
    null -> syntax.border
    0 -> syntax.comment
    1 -> syntax.diffAdd.copy(alpha = 0.55f)
    else -> syntax.diffAdd
}

private fun DayOfWeek.short(locale: Locale): String =
    getDisplayName(TextStyle.SHORT, locale)

@Preview(showBackground = true, backgroundColor = 0xFF10141A, heightDp = 720)
@Composable
private fun StatsScreenPreview() {
    ThabitTheme {
        StatsScreen(state = StatsUiState(document = previewDocument(), loading = false), onOpenCommit = {})
    }
}

private val PREVIEW_DAY: LocalDate = LocalDate.of(2026, 8, 20)

private fun previewDocument(): StatsDocument {
    val start = PREVIEW_DAY.minusDays(40)
    val habits = listOf(
        Habit(1, "meditate 10 min", createdAt = start, position = 0),
        Habit(2, "journal", createdAt = start, position = 1)
    )
    val checks = mutableListOf<Check>()
    val days = mutableSetOf<LocalDate>()
    (0L..40L).forEach { back ->
        val date = PREVIEW_DAY.minusDays(back)
        if (back % 7 == 3L) return@forEach // a few days nobody was there
        days += date
        checks += Check(1, date, CheckState.PASS)
        if (back % 3 != 0L) checks += Check(2, date, CheckState.PASS)
    }
    return StatsDocument.of(SuiteHistory(habits, checks, days), PREVIEW_DAY)
}
