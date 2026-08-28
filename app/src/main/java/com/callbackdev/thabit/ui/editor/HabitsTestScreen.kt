package com.callbackdev.thabit.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callbackdev.thabit.R
import com.callbackdev.thabit.domain.StreakUnit
import com.callbackdev.thabit.domain.TestState
import com.callbackdev.thabit.domain.model.HabitType
import com.callbackdev.thabit.ui.components.CanvasLine
import com.callbackdev.thabit.ui.components.CheckboxState
import com.callbackdev.thabit.ui.components.CodeCanvas
import com.callbackdev.thabit.ui.components.CodeLine
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
    onAddTest: () -> Unit,
    onEditTest: (Long) -> Unit,
    onOpenHelp: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    viewModel: SuiteViewModel = viewModel(factory = SuiteViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val showHelpHint by viewModel.showHelpHint.collectAsStateWithLifecycle()
    // A file left on screen at half past eleven and looked at again after
    // midnight has to be the new day's before anything is tapped: coming back to
    // the front is the moment to read the clock again (the view model does not
    // poll one).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }
    HabitsTestScreen(
        state = state,
        listState = listState,
        // Adding and editing are the same conversation, so they are navigation
        // rather than state: the file hands the reader to `$ thabit add` and
        // gets them back with a test in the suite.
        actions = SuiteActions(viewModel).copy(
            onAddTest = onAddTest,
            onEdit = onEditTest,
            onOpenHelp = {
                viewModel.onHelpHintUsed()
                onOpenHelp()
            }
        ),
        showHelpHint = showHelpHint,
        modifier = modifier
    )
}

/** The stateless half — what the previews and the UI tests drive. */
@Composable
fun HabitsTestScreen(
    state: SuiteUiState,
    actions: SuiteActions,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    showHelpHint: Boolean = false
) {
    val document = state.document
    val hint = if (showHelpHint) stringResource(R.string.help_hint) else null

    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            // Line numbers and word wrap arrive through LocalEditorOptions, which
            // the shell provides from `settings.config` for every file at once:
            // they are properties of the editor, not of this screen.
            CodeCanvas(
                lines = if (document == null) loadingLines() else helpHintLines(
                    hint = hint,
                    onOpenHelp = actions.onOpenHelp
                ) + suiteLines(
                    document = document,
                    interaction = state.interaction,
                    actions = actions
                ),
                state = listState,
                // Room at the foot of the file for the FAB: the last test of
                // the suite must never be the one hidden under it. Shared with
                // the README, which shares the FAB.
                contentPadding = EditorFileClearance,
                modifier = Modifier.fillMaxSize()
            )
            AddTestFab(onClick = actions.onAddTest)
        }
        SuiteStatusBar(document)
    }
}

/**
 * The taps a suite line can produce, gathered so previews can pass no-ops.
 *
 * The gestures that need to know *what state the row is in* take a row; the
 * ones that only need to know *which test* take an id, because those are the
 * ones the commented-out rows share with the due ones.
 */
data class SuiteActions(
    val onCheckbox: (TestRow) -> Unit = {},
    val onIncrement: (TestRow) -> Unit = {},
    val onDetails: (Long) -> Unit = {},
    val onNote: (Long) -> Unit = {},
    val onSkip: (Long) -> Unit = {},
    val onUnskip: (Long) -> Unit = {},
    val onEdit: (Long) -> Unit = {},
    val onArchive: (Long) -> Unit = {},
    val onCancelArchive: () -> Unit = {},
    val onToggleNotDue: () -> Unit = {},
    val onPromptChange: (String) -> Unit = {},
    val onCycleSkipWindow: () -> Unit = {},
    val onSubmitPrompt: () -> Unit = {},
    val onCancelPrompt: () -> Unit = {},
    val onClearPrompt: () -> Unit = {},
    val onAddTest: () -> Unit = {},
    val onOpenHelp: () -> Unit = {}
) {
    constructor(viewModel: SuiteViewModel) : this(
        onCheckbox = viewModel::onCheckbox,
        onIncrement = viewModel::onIncrement,
        onDetails = viewModel::onDetails,
        onNote = viewModel::onNote,
        onSkip = viewModel::onSkip,
        onUnskip = viewModel::onUnskip,
        onArchive = viewModel::onArchive,
        onCancelArchive = viewModel::onCancelArchive,
        onToggleNotDue = viewModel::onToggleNotDue,
        onPromptChange = viewModel::onPromptChange,
        onCycleSkipWindow = viewModel::onCycleSkipWindow,
        onSubmitPrompt = viewModel::onSubmitPrompt,
        onCancelPrompt = viewModel::onCancelPrompt,
        onClearPrompt = viewModel::onClearPrompt
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
                        pluralStringResource(R.plurals.cd_status_suite, document.suiteSize, document.suiteSize)
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
 * `# new here? open HELP.md` — the head of the file, once (Fase 14).
 *
 * A `#` and not a `//`: the comment channel wears the host file's syntax, and
 * `habits.test` is the YAML-flavored one (VISION §1.1). The siblings' hint is a
 * `//` for exactly the same reason, because the file it heads is JSON.
 *
 * It heads `habits.test` and not the `README.md` tab beside it: a "new here?"
 * pointer on the plain-language surface would be pointing away from the very
 * thing it is standing on. The suite is where the metaphor is thickest, and it
 * is the first file the app ever opens.
 *
 * Not part of [SuiteDocument]: the document is the suite, and this line is not
 * a fact about it. It also does not belong in the empty state — a hint that
 * disappeared the moment the first test was written would be gone by the time
 * the words it explains had anything to attach to.
 */
@Composable
private fun helpHintLines(hint: String?, onOpenHelp: () -> Unit): List<CanvasLine> {
    if (hint == null) return emptyList()
    val syntax = ThabitTheme.syntax
    return listOf(
        CodeLine(
            AnnotatedString("# $hint", SpanStyle(color = syntax.key)),
            onClick = onOpenHelp,
            onClickLabel = hint,
            contentDescription = hint
        )
    )
}

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

    // Terminal output goes under the row it answers, and only lands at the foot
    // of the file when there is no such row left to print it against.
    val message = interaction.transient
    val inlineId = message?.habitId?.takeIf { id -> document.due.any { it.habitId == id } }

    if (document.isEmpty) {
        // The marker is the file's syntax and stays; the sentence after it is
        // the one the reader has to understand, so it arrives as a resource
        // (VISION §1.3, Fase 15).
        SuiteDocument.emptyHints().forEach { id ->
            lines += commentLine(
                if (id == SuiteDocument.BLANK_LINE) "#" else "# " + stringResource(id),
                syntax
            )
        }
    } else {
        lines += commentLine("# ${document.suiteComment}", syntax)
        document.logicalDayComment?.let { lines += commentLine("# $it", syntax) }
        lines += commentLine("#", syntax)

        document.due.forEach { row ->
            lines += testLines(row, document, interaction, actions, syntax)
            if (row.habitId == inlineId && message != null) {
                lines += commentLine(
                    text = "# " + message.note.printed(),
                    syntax = syntax,
                    indent = 1,
                    contentDescription = message.note.spoken()
                )
            }
        }

        document.notDueControl(interaction.notDueExpanded)?.let { control ->
            val summary = pluralStringResource(
                R.plurals.suite_not_due,
                document.notDue.size,
                document.notDue.size
            )
            lines += commentLine("#", syntax)
            lines += commentLine(
                text = "# $summary — $control",
                syntax = syntax,
                onClick = actions.onToggleNotDue,
                onClickLabel = stringResource(
                    if (interaction.notDueExpanded) R.string.cd_action_hide_not_due
                    else R.string.cd_action_show_not_due
                )
            )
            if (interaction.notDueExpanded) {
                document.notDue.forEach { row ->
                    lines += notDueLines(row, interaction, actions, syntax)
                }
            }
        }
    }

    if (message != null && inlineId == null) {
        lines += commentLine("#", syntax)
        lines += commentLine(
            text = "# " + message.note.printed(),
            syntax = syntax,
            contentDescription = message.note.spoken()
        )
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
    // Exactly one of the two is set: the readout as the document spelled it, or
    // the one comment that is a sentence, spoken here (Fase 15).
    val comment = row.comment ?: row.commentNote?.let { stringResource(it) }

    lines += WidgetLine(
        measureText = buildString {
            append("- ").append(checkbox.glyph).append(' ').append(row.name)
            comment?.let { append("  # ").append(it) }
            increment?.let { append("  ").append(it) }
            append("    ")
        }
    ) {
        TestLine(
            checkbox = checkbox,
            name = row.name,
            comment = comment,
            syntax = syntax,
            spokenRow = spoken,
            checkboxActionLabel = checkboxLabel,
            detailsDescription = detailsDescription,
            detailsActionLabel = detailsLabel,
            onCheckbox = { actions.onCheckbox(row) },
            onDetails = { actions.onDetails(row.habitId) },
            incrementLabel = increment,
            incrementDescription = incrementDescription,
            onIncrement = increment?.let { { actions.onIncrement(row) } },
            noteLabel = if (showNote) "[note]" else null,
            noteDescription = noteDescription,
            onNote = if (showNote) ({ actions.onNote(row.habitId) }) else null
        )
    }

    val prompt = interaction.prompt?.takeIf { it.habitId == row.habitId }
    if (prompt != null) lines += promptLines(prompt, actions, syntax)
    if (expanded) {
        lines += specLines(row.habitId, row.name, row.spec, row.state, interaction, actions, syntax)
    }
    return lines
}

/**
 * A test today does not ask for: the commented-out line, and the same expansion
 * underneath it.
 *
 * The line stays a comment — today it asserts nothing, and that is the whole
 * point of drawing it this way — but it answers to a tap like every other test
 * in the file, because `[edit]` and `[rm]` cannot be things you have to wait
 * until Monday for.
 */
@Composable
private fun notDueLines(
    row: NotDueRow,
    interaction: SuiteInteraction,
    actions: SuiteActions,
    syntax: SyntaxColors
): List<CanvasLine> {
    val expanded = interaction.expandedId == row.habitId
    val lines = mutableListOf<CanvasLine>(
        commentLine(
            text = "# ${row.name}  — ${row.reason}",
            syntax = syntax,
            indent = 1,
            onClick = { actions.onDetails(row.habitId) },
            onClickLabel = stringResource(
                if (expanded) R.string.cd_action_details_hide else R.string.cd_action_details_show
            ),
            contentDescription = "${row.name}, ${stringResource(R.string.cd_not_due)}"
        )
    )
    if (expanded) {
        // No state: a test nobody is asking about today has nothing to skip and
        // nothing to take back, and a `[~ skip]` here would write a row on a day
        // the schedule never claimed.
        lines += specLines(row.habitId, row.name, row.spec, null, interaction, actions, syntax)
    }
    return lines
}

/**
 * The unfolded spec: what the file would say if you opened the line.
 *
 * [state] is the row's state today, or null when today does not ask for this
 * test at all — which is exactly the difference between the two kinds of row,
 * and the only thing the expansion needs to know about it.
 */
@Composable
private fun specLines(
    habitId: Long,
    name: String,
    spec: TestSpec,
    state: TestState?,
    interaction: SuiteInteraction,
    actions: SuiteActions,
    syntax: SyntaxColors
): List<CanvasLine> {
    val lines = mutableListOf<CanvasLine>()
    lines += yamlStringLine(
        key = "when",
        value = spec.schedule,
        syntax = syntax,
        indent = 1,
        contentDescription = stringResource(R.string.cd_spec_when, spec.schedule)
    )
    spec.assertText?.let {
        lines += yamlStringLine(
            key = "assert",
            value = it,
            syntax = syntax,
            indent = 1,
            contentDescription = stringResource(R.string.cd_spec_assert, it)
        )
    }
    spec.remind?.let {
        lines += yamlStringLine(
            key = "remind",
            value = it,
            syntax = syntax,
            indent = 1,
            quoted = true,
            // The approximation travels with the reminder here too: it is stated
            // in the wizard where the reminder is set, and a reader who only ever
            // hears this row would otherwise never be told (VISION §6.7).
            contentDescription = stringResource(R.string.cd_spec_remind, it)
        )
    }
    lines += yamlNumberLine(
        key = "streak",
        value = spec.streak.toString(),
        syntax = syntax,
        indent = 1,
        comment = if (spec.streakUnit == StreakUnit.WEEKS) "weeks" else "days",
        contentDescription = if (spec.streak == 0) {
            stringResource(R.string.cd_spec_streak_none)
        } else {
            pluralStringResource(
                if (spec.streakUnit == StreakUnit.WEEKS) {
                    R.plurals.cd_spec_streak_weeks
                } else {
                    R.plurals.cd_spec_streak_days
                },
                spec.streak,
                spec.streak
            )
        }
    )
    lines += yamlStringLine(
        key = "health",
        value = "${CodeFormat.bar(spec.health)} ${CodeFormat.percent(spec.health)}",
        syntax = syntax,
        indent = 1,
        comment = if (spec.health == null) stringResource(R.string.suite_health_unknown) else null,
        // The bar IS the fact on this row, and a bar cannot be heard: without
        // this the only health a screen reader ever gets is `--%` read as
        // punctuation. The percentage is the same number the bar draws.
        contentDescription = spec.health
            ?.let { stringResource(R.string.cd_spec_health, CodeFormat.percent(it)) }
            ?: stringResource(R.string.cd_spec_health_unknown)
    )

    if (interaction.archiveConfirmId == habitId) {
        // The series' shape for anything destructive: a `$` command, spelled out,
        // that only runs on the second tap (VISION §1.1).
        //
        // Two lines rather than one, and that is not cosmetic: with the command
        // naming the test, a single row pushed `[esc]` past the right edge on a
        // phone — the way out of a destructive confirm would have needed a
        // horizontal scroll to find. The way out always stays on screen.
        lines += WidgetLine(indent = 1, measureText = "$ thabit archive \"$name\"    ") {
            TextControl(
                label = "$ thabit archive \"$name\"",
                color = syntax.diffDel,
                description = stringResource(R.string.cd_action_archive_confirm),
                onClick = { actions.onArchive(habitId) }
            )
        }
        val confirm = stringResource(R.string.confirm_command)
        lines += WidgetLine(indent = 1, measureText = "# $confirm  [esc]  ") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "# $confirm",
                    style = MaterialTheme.typography.bodySmall,
                    color = syntax.comment,
                    // The command and the `[esc]` beside it already say this in
                    // the reader's language; read aloud it would be a third
                    // sentence about the same two controls.
                    modifier = Modifier.decorative()
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
        val skipped = state == TestState.SKIP
        lines += WidgetLine(indent = 1, measureText = "[~ unskip]  [edit]  [rm]      ") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state != null) {
                    // The same slot, both ways round: a skipped test offers the
                    // way back, and it is the only way back once the skip has a
                    // window — the days it covers are not reachable one tap at a
                    // time (VISION §4.1).
                    TextControl(
                        label = if (skipped) "[~ unskip]" else "[~ skip]",
                        color = syntax.comment,
                        description = stringResource(
                            if (skipped) R.string.cd_action_unskip else R.string.cd_action_skip
                        ),
                        onClick = {
                            if (skipped) actions.onUnskip(habitId) else actions.onSkip(habitId)
                        }
                    )
                }
                TextControl(
                    label = "[edit]",
                    color = syntax.key,
                    description = stringResource(R.string.cd_action_edit),
                    onClick = { actions.onEdit(habitId) }
                )
                TextControl(
                    label = "[rm]",
                    color = syntax.diffDel,
                    description = stringResource(R.string.cd_action_archive),
                    onClick = { actions.onArchive(habitId) }
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
                // The prompt exists because a tap asked for it, so it opens
                // ready to be answered: the keyboard is part of the question.
                autoFocus = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { actions.onSubmitPrompt() })
            )
        }
    )

    // The row declares what it will actually render: `# window:` belongs to a
    // skip and `[clear]` to a counter, never both, and a measure text claiming
    // both would hand the whole canvas a horizontal scroll nothing needs.
    val controlsWidth = buildString {
        if (prompt is SuitePrompt.Skip) append("# window: ${prompt.window.token}   ")
        append("[ok]  [esc]")
        if (prompt is SuitePrompt.Value && prompt.written) append("  [clear]")
        append("     ")
    }

    lines += WidgetLine(indent = 1, measureText = controlsWidth) {
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
            // Last on the row, and only when there is a number to take back.
            // `[ok]` keeps the place the thumb already knows, and the one
            // control that erases sits as far from it as the row allows — the
            // same reasoning that put `[esc]` at the end of the wizard's row.
            // No red: this is the counter's version of tapping an `[x]` back to
            // `[ ]`, and that gesture wears no warning either.
            if (prompt is SuitePrompt.Value && prompt.written) {
                TextControl(
                    label = "[clear]",
                    color = syntax.comment,
                    description = stringResource(R.string.cd_action_clear_value),
                    onClick = actions.onClearPrompt
                )
            }
        }
    }
    return lines
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

/**
 * The note in the reader's own language, beside the English the file prints.
 *
 * Exhaustive on purpose: a note added to [SuiteNote] tomorrow will not compile
 * until somebody writes the words that will be spoken for it. That is the whole
 * reason the notes stopped being String constants in Fase 12.
 */
/**
 * What the file prints after its `# `, in the reader's language (Fase 15).
 *
 * The twin of [spoken], and deliberately built the same way: the note says what
 * happened, the screen says it in words. The `ERROR:` level is a token of the
 * comment channel, so it is added here and never translated — the same shape the
 * wizard's refusals and `settings.config`'s permission line have.
 */
@Composable
private fun SuiteNote.printed(): String {
    val sentence = when (this) {
        SuiteNote.ReadOnly -> stringResource(R.string.note_read_only_suite)
        SuiteNote.UnknownTest -> stringResource(R.string.note_unknown_test_suite)
        is SuiteNote.RolledOver -> stringResource(R.string.note_rolled_over, printedDate)
    }
    return if (isError) "ERROR: $sentence" else sentence
}

@Composable
private fun SuiteNote.spoken(): String = when (this) {
    SuiteNote.ReadOnly -> stringResource(R.string.cd_note_suite_read_only)
    SuiteNote.UnknownTest -> stringResource(R.string.cd_note_suite_unknown_test)
    is SuiteNote.RolledOver -> stringResource(R.string.cd_note_rolled_over, date.spoken())
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
