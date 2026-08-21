package com.callbackdev.thabit.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callbackdev.thabit.R
import com.callbackdev.thabit.domain.StreakUnit
import com.callbackdev.thabit.domain.TestState
import com.callbackdev.thabit.domain.model.HabitType
import com.callbackdev.thabit.ui.components.CanvasLine
import com.callbackdev.thabit.ui.components.CheckboxState
import com.callbackdev.thabit.ui.components.CodeCanvas
import com.callbackdev.thabit.ui.components.GlowFab
import com.callbackdev.thabit.ui.components.LocalEditorOptions
import com.callbackdev.thabit.ui.components.StatusBarDivider
import com.callbackdev.thabit.ui.components.StatusBarStart
import com.callbackdev.thabit.ui.components.StatusBarText
import com.callbackdev.thabit.ui.components.TerminalInput
import com.callbackdev.thabit.ui.components.TerminalStatusBar
import com.callbackdev.thabit.ui.components.WidgetLine
import com.callbackdev.thabit.ui.components.commentLine
import com.callbackdev.thabit.ui.components.yamlNumberLine
import com.callbackdev.thabit.ui.components.yamlStringLine
import androidx.compose.ui.tooling.preview.Preview
import com.callbackdev.thabit.domain.SuiteHistory
import com.callbackdev.thabit.domain.model.AssertSpec
import com.callbackdev.thabit.domain.model.Check
import com.callbackdev.thabit.domain.model.CheckState
import com.callbackdev.thabit.domain.model.Habit
import com.callbackdev.thabit.domain.model.Schedule
import java.time.LocalTime
import com.callbackdev.thabit.ui.format.CodeFormat
import com.callbackdev.thabit.ui.theme.SyntaxColors
import com.callbackdev.thabit.ui.theme.ThabitTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * `habits.test` — the suite, mid-run.
 *
 * The screen is the runner (VISION §3.3.2): one glance says what is left, one tap
 * runs a test. Everything on it is a line of the file — the header states the
 * arithmetic, each row carries its own live detail in the comment channel, and
 * the controls are tokens rather than buttons.
 *
 * The document is computed by [SuiteDocument] with no Compose involved, so what
 * this function does is exactly one thing: turn a value into lines and hand the
 * taps back to [SuiteViewModel].
 */
@Composable
fun HabitsTestScreen(
    modifier: Modifier = Modifier,
    viewModel: SuiteViewModel = viewModel(factory = SuiteViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HabitsTestScreen(state = state, actions = SuiteActions(viewModel), modifier = modifier)
}

/** The stateless half — what the previews and the UI tests drive. */
@Composable
fun HabitsTestScreen(
    state: SuiteUiState,
    actions: SuiteActions,
    modifier: Modifier = Modifier
) {
    val document = state.document
    val listState = rememberLazyListState()

    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            CompositionLocalProvider(LocalEditorOptions provides state.editor) {
                CodeCanvas(
                    lines = if (document == null) loadingLines() else suiteLines(
                        document = document,
                        interaction = state.interaction,
                        actions = actions
                    ),
                    state = listState,
                    // Room at the foot of the file for the FAB: the last test of
                    // the suite must never be the one hidden under it.
                    contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
                    modifier = Modifier.fillMaxSize()
                )
            }
            // The one glowing verb of this app: growing the suite (VISION §4.1).
            GlowFab(
                onClick = actions.onAddTest,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }
        SuiteStatusBar(document)
    }
}

/** The taps a suite line can produce, gathered so previews can pass no-ops. */
class SuiteActions(
    val onCheckbox: (TestRow) -> Unit = {},
    val onDetails: (TestRow) -> Unit = {},
    val onIncrement: (TestRow) -> Unit = {},
    val onNote: (TestRow) -> Unit = {},
    val onSkip: (TestRow) -> Unit = {},
    val onEdit: (TestRow) -> Unit = {},
    val onArchive: (TestRow) -> Unit = {},
    val onCancelArchive: () -> Unit = {},
    val onToggleNotDue: () -> Unit = {},
    val onPromptChange: (String) -> Unit = {},
    val onCycleSkipWindow: () -> Unit = {},
    val onSubmitPrompt: () -> Unit = {},
    val onCancelPrompt: () -> Unit = {},
    val onAddTest: () -> Unit = {}
) {
    constructor(viewModel: SuiteViewModel) : this(
        onCheckbox = viewModel::onCheckbox,
        onDetails = viewModel::onDetails,
        onIncrement = viewModel::onIncrement,
        onNote = viewModel::onNote,
        onSkip = viewModel::onSkip,
        onEdit = viewModel::onEdit,
        onArchive = viewModel::onArchive,
        onCancelArchive = viewModel::onCancelArchive,
        onToggleNotDue = viewModel::onToggleNotDue,
        onPromptChange = viewModel::onPromptChange,
        onCycleSkipWindow = viewModel::onCycleSkipWindow,
        onSubmitPrompt = viewModel::onSubmitPrompt,
        onCancelPrompt = viewModel::onCancelPrompt,
        onAddTest = viewModel::onAddTest
    )
}

/**
 * `⎇ main | 6 tests` and `3/6 passed`.
 *
 * The git decorations are signature, not information (VISION §3.3.7): every fact
 * here is already stated in plain form in the header comment above, which is why
 * a branch name nobody can switch is allowed to sit there at all.
 */
@Composable
private fun SuiteStatusBar(document: SuiteDocument?) {
    TerminalStatusBar {
        StatusBarStart {
            StatusBarText("⎇ main")
            if (document != null) {
                StatusBarDivider()
                StatusBarText(
                    text = "${document.suiteSize} tests",
                    modifier = Modifier.spokenAs(
                        stringResource(R.string.cd_status_suite, document.suiteSize)
                    )
                )
            }
        }
        if (document != null) {
            StatusBarText(
                text = "${document.passed}/${document.graded} passed",
                color = if (document.graded > 0 && document.passed == document.graded) {
                    ThabitTheme.syntax.diffAdd
                } else {
                    ThabitTheme.syntax.comment
                },
                modifier = Modifier.spokenAs(
                    stringResource(R.string.cd_status_passed, document.passed, document.graded)
                )
            )
        }
    }
}

/**
 * The plain-language half of a git decoration.
 *
 * `⎇ main` and `3/6 passed` are glyph-shaped shorthand; a screen reader gets the
 * sentence instead, so the decoration stays signature rather than becoming the
 * only place a fact lives (VISION §3.3.7).
 */
private fun Modifier.spokenAs(label: String): Modifier =
    semantics { contentDescription = label }

/**
 * The first frame, before the database has answered.
 *
 * Empty rather than a spinner or a skeleton suite: the file must not lie, and
 * drawing rows that are not there yet — even greyed — would be the app asserting
 * a suite it has not read (VISION §1.1). The wait is a few milliseconds from a
 * local database.
 */
private fun loadingLines(): List<CanvasLine> = emptyList()

/**
 * The document, rendered line by line.
 *
 * Ordinary lines are [com.callbackdev.thabit.ui.components.CodeLine]s built by
 * the Fase 1 tokenizer; only the rows that carry more than one gesture (a test
 * line, a control strip, a prompt) become [WidgetLine]s.
 */
@Composable
private fun suiteLines(
    document: SuiteDocument,
    interaction: SuiteInteraction,
    actions: SuiteActions
): List<CanvasLine> {
    val syntax = ThabitTheme.syntax
    val lines = mutableListOf<CanvasLine>()

    lines += commentLine("# ${SuiteDocument.FILE_NAME}", syntax)
    if (document.isEmpty) {
        SuiteDocument.emptyHints().forEach { hint ->
            lines += commentLine(if (hint.isEmpty()) "#" else "# $hint", syntax)
        }
    } else {
        lines += commentLine("# ${document.suiteComment}", syntax)
        document.logicalDayComment?.let { lines += commentLine("# $it", syntax) }
        lines += commentLine("#", syntax)

        document.due.forEach { row ->
            lines += testLines(row, document, interaction, actions, syntax)
        }

        document.notDueComment(interaction.notDueExpanded)?.let { comment ->
            lines += commentLine("#", syntax)
            lines += commentLine(
                text = "# $comment",
                syntax = syntax,
                onClick = actions.onToggleNotDue,
                onClickLabel = stringResource(
                    if (interaction.notDueExpanded) R.string.cd_action_hide_not_due
                    else R.string.cd_action_show_not_due
                )
            )
            if (interaction.notDueExpanded) {
                document.notDue.forEach { row ->
                    lines += commentLine(
                        text = "# ${row.name}  — ${row.reason}",
                        syntax = syntax,
                        indent = 1,
                        contentDescription = "${row.name}, ${stringResource(R.string.cd_not_due)}"
                    )
                }
            }
        }
    }

    interaction.transient?.let { message ->
        lines += commentLine("#", syntax)
        lines += commentLine("# $message", syntax)
    }
    return lines
}

/** One test: its line, and whatever it has unfolded underneath. */
@Composable
private fun testLines(
    row: TestRow,
    document: SuiteDocument,
    interaction: SuiteInteraction,
    actions: SuiteActions,
    syntax: SyntaxColors
): List<CanvasLine> {
    val lines = mutableListOf<CanvasLine>()
    val expanded = interaction.expandedId == row.habitId
    val checkbox = row.state.checkbox()
    val spoken = row.spokenSentence()
    val increment = row.incrementStep?.let { "[+${CodeFormat.number(it)}]" }
    val showNote = row.state == TestState.FAIL && row.type == HabitType.AVOID

    val checkboxLabel = stringResource(
        when {
            row.type == HabitType.COUNTER -> R.string.cd_action_log_value
            row.type == HabitType.AVOID && row.state == TestState.FAIL -> R.string.cd_action_undo
            row.type == HabitType.AVOID -> R.string.cd_action_break
            row.state == TestState.PASS -> R.string.cd_action_undo
            else -> R.string.cd_action_pass
        }
    )
    val detailsLabel = stringResource(
        if (expanded) R.string.cd_action_details_hide else R.string.cd_action_details_show
    )
    val detailsDescription = stringResource(R.string.cd_row_details, row.name)
    val incrementDescription = increment?.let {
        stringResource(R.string.cd_action_increment, CodeFormat.number(row.incrementStep ?: 1.0))
    }
    val noteDescription = stringResource(R.string.cd_action_note)

    lines += WidgetLine(
        measureText = buildString {
            append("- ").append(checkbox.glyph).append(' ').append(row.name)
            row.comment?.let { append("  # ").append(it) }
            increment?.let { append("  ").append(it) }
            append("    ")
        }
    ) {
        TestLine(
            checkbox = checkbox,
            name = row.name,
            comment = row.comment,
            syntax = syntax,
            spokenRow = spoken,
            checkboxActionLabel = checkboxLabel,
            detailsDescription = detailsDescription,
            detailsActionLabel = detailsLabel,
            onCheckbox = { actions.onCheckbox(row) },
            onDetails = { actions.onDetails(row) },
            incrementLabel = increment,
            incrementDescription = incrementDescription,
            onIncrement = increment?.let { { actions.onIncrement(row) } },
            noteLabel = if (showNote) "[note]" else null,
            noteDescription = noteDescription,
            onNote = if (showNote) ({ actions.onNote(row) }) else null
        )
    }

    val prompt = interaction.prompt?.takeIf { it.habitId == row.habitId }
    if (prompt != null) lines += promptLines(prompt, actions, syntax)
    if (expanded) lines += specLines(row, interaction, actions, syntax)
    return lines
}

/** The unfolded spec: what the file would say if you opened the line. */
@Composable
private fun specLines(
    row: TestRow,
    interaction: SuiteInteraction,
    actions: SuiteActions,
    syntax: SyntaxColors
): List<CanvasLine> {
    val spec = row.spec
    val lines = mutableListOf<CanvasLine>()
    lines += yamlStringLine("when", spec.schedule, syntax, indent = 1)
    spec.assertText?.let { lines += yamlStringLine("assert", it, syntax, indent = 1) }
    spec.remind?.let { lines += yamlStringLine("remind", it, syntax, indent = 1, quoted = true) }
    lines += yamlNumberLine(
        key = "streak",
        value = spec.streak.toString(),
        syntax = syntax,
        indent = 1,
        comment = if (spec.streakUnit == StreakUnit.WEEKS) "weeks" else "days"
    )
    lines += yamlStringLine(
        key = "health",
        value = "${CodeFormat.bar(spec.health)} ${CodeFormat.percent(spec.health)}",
        syntax = syntax,
        indent = 1,
        comment = if (spec.health == null) "not enough runs yet" else null
    )

    if (interaction.archiveConfirmId == row.habitId) {
        // The series' shape for anything destructive: a `$` command, spelled out,
        // that only runs on the second tap (VISION §1.1).
        //
        // Two lines rather than one, and that is not cosmetic: with the command
        // naming the test, a single row pushed `[esc]` past the right edge on a
        // phone — the way out of a destructive confirm would have needed a
        // horizontal scroll to find. The way out always stays on screen.
        lines += WidgetLine(indent = 1, measureText = "$ thabit archive \"${row.name}\"    ") {
            TextControl(
                label = "$ thabit archive \"${row.name}\"",
                color = syntax.diffDel,
                description = stringResource(R.string.cd_action_archive_confirm),
                onClick = { actions.onArchive(row) }
            )
        }
        lines += WidgetLine(indent = 1, measureText = "# tap the command to confirm  [esc]  ") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "# tap the command to confirm",
                    style = MaterialTheme.typography.bodySmall,
                    color = syntax.comment
                )
                TextControl(
                    label = "[esc]",
                    color = syntax.comment,
                    description = stringResource(R.string.cd_action_cancel),
                    onClick = actions.onCancelArchive
                )
            }
        }
    } else {
        lines += WidgetLine(indent = 1, measureText = "[~ skip]  [edit]  [rm]      ") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextControl(
                    label = "[~ skip]",
                    color = syntax.comment,
                    description = stringResource(R.string.cd_action_skip),
                    onClick = { actions.onSkip(row) }
                )
                TextControl(
                    label = "[edit]",
                    color = syntax.key,
                    description = stringResource(R.string.cd_action_edit),
                    onClick = { actions.onEdit(row) }
                )
                TextControl(
                    label = "[rm]",
                    color = syntax.diffDel,
                    description = stringResource(R.string.cd_action_archive),
                    onClick = { actions.onArchive(row) }
                )
            }
        }
    }
    return lines
}

/** An in-place terminal prompt, opened inside the file rather than over it. */
@Composable
private fun promptLines(
    prompt: SuitePrompt,
    actions: SuiteActions,
    syntax: SyntaxColors
): List<CanvasLine> {
    val label = when (prompt) {
        is SuitePrompt.Value -> prompt.unit.ifBlank { "value" }
        is SuitePrompt.Skip -> "skip"
        is SuitePrompt.Note -> "note"
    }
    val text = when (prompt) {
        is SuitePrompt.Value -> prompt.text
        is SuitePrompt.Skip -> prompt.note
        is SuitePrompt.Note -> prompt.text
    }
    val numeric = prompt is SuitePrompt.Value

    val lines = mutableListOf<CanvasLine>(
        WidgetLine(indent = 1, measureText = "> $label: ${"_".repeat(16)}    ") {
            TerminalInput(
                value = text,
                onValueChange = actions.onPromptChange,
                prompt = "> $label:",
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { actions.onSubmitPrompt() })
            )
        }
    )

    lines += WidgetLine(indent = 1, measureText = "# window: [today]   [ok]  [esc]     ") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (prompt is SuitePrompt.Skip) {
                TextControl(
                    label = "# window: ${prompt.window.token}",
                    color = syntax.comment,
                    description = stringResource(R.string.cd_action_skip_window),
                    onClick = actions.onCycleSkipWindow
                )
            }
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
    return lines
}

/** The domain's state, in the glyph the file draws for it. */
private fun TestState.checkbox(): CheckboxState = when (this) {
    TestState.PASS -> CheckboxState.Passed
    TestState.FAIL -> CheckboxState.Failed
    TestState.SKIP -> CheckboxState.Skipped
    TestState.HOLDING -> CheckboxState.Holding
    TestState.PENDING -> CheckboxState.Pending
}

/**
 * What a screen reader hears instead of the glyphs: *meditate 10 min, passed, at
 * 07:12* — the name, the state in words, and the same live detail the comment
 * carries in English (VISION §3.3.7, §4.1).
 */
@Composable
private fun TestRow.spokenSentence(): String {
    val parts = mutableListOf(name, stringResource(state.checkbox().spokenRes))
    detailSentence()?.let { parts += it }
    return parts.joinToString(", ")
}

@Composable
private fun TestRow.detailSentence(): String? = when (val detail = detail) {
    is RowDetail.Passed -> detail.at?.let { stringResource(R.string.cd_detail_at, CodeFormat.time(it)) }
    is RowDetail.Counter ->
        if (detail.passed) {
            stringResource(
                R.string.cd_detail_counter_done,
                CodeFormat.number(detail.value),
                detail.unit
            )
        } else {
            stringResource(
                R.string.cd_detail_counter,
                CodeFormat.number(detail.value),
                CodeFormat.number(detail.target),
                detail.unit
            )
        }
    is RowDetail.Skipped -> listOfNotNull(
        detail.note?.takeIf { it.isNotBlank() },
        detail.until?.let { stringResource(R.string.cd_detail_until, it.spoken()) }
    ).joinToString(", ").ifBlank { null }
    is RowDetail.Failed -> listOfNotNull(
        detail.at?.let { stringResource(R.string.cd_detail_at, CodeFormat.time(it)) },
        detail.note?.takeIf { it.isNotBlank() }
    ).joinToString(", ").ifBlank { null }
    is RowDetail.Quota -> stringResource(R.string.cd_detail_quota, detail.done, detail.target)
    RowDetail.Holding, RowDetail.Pending -> null
}

/** A date said the way the reader's language says dates — chrome, so localized. */
@Composable
private fun LocalDate.spoken(): String {
    val locale = LocalConfiguration.current.locales[0]
    return format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A, heightDp = 520)
@Composable
private fun HabitsTestScreenPreview() {
    ThabitTheme {
        HabitsTestScreen(
            state = SuiteUiState(document = previewDocument(), loading = false),
            actions = SuiteActions()
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A, heightDp = 320)
@Composable
private fun HabitsTestEmptyPreview() {
    ThabitTheme {
        HabitsTestScreen(
            state = SuiteUiState(
                document = SuiteDocument.of(SuiteHistory.Empty, PREVIEW_DAY, PREVIEW_DAY),
                loading = false
            ),
            actions = SuiteActions()
        )
    }
}

private val PREVIEW_DAY: LocalDate = LocalDate.of(2026, 8, 20)

/** A suite mid-run, for the previews — the shape VISION §4.1 draws. */
private fun previewDocument(): SuiteDocument {
    val day = PREVIEW_DAY
    val habits = listOf(
        Habit(1, "meditate 10 min", createdAt = day.minusDays(30), position = 0),
        Habit(
            2, "read 20 pages", HabitType.COUNTER, AssertSpec(20.0, "pages", 5.0),
            emoji = "📖", createdAt = day.minusDays(30), position = 1
        ),
        Habit(
            3, "pushups", HabitType.COUNTER, AssertSpec(30.0, "reps", 10.0),
            createdAt = day.minusDays(30), position = 2
        ),
        Habit(4, "run 5k", schedule = Schedule.Quota(3), createdAt = day.minusDays(30), position = 3),
        Habit(5, "no sugar", HabitType.AVOID, createdAt = day.minusDays(30), position = 4),
        Habit(6, "journal", createdAt = day.minusDays(30), position = 5)
    )
    val checks = listOf(
        Check(1, day, CheckState.PASS, at = LocalTime.of(7, 12)),
        Check(2, day, CheckState.PASS, value = 23.0, at = LocalTime.of(8, 40)),
        Check(3, day, CheckState.PROGRESS, value = 12.0),
        Check(4, day, CheckState.SKIP, note = "rest day"),
        Check(6, day, CheckState.PASS, at = LocalTime.of(21, 40))
    )
    return SuiteDocument.of(
        history = SuiteHistory(habits, checks, setOf(day)),
        logicalDate = day,
        wallDate = day
    )
}
