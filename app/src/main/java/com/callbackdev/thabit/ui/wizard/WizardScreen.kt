package com.callbackdev.thabit.ui.wizard

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callbackdev.thabit.R
import com.callbackdev.thabit.domain.model.HabitType
import com.callbackdev.thabit.notifications.ThabitNotifier
import com.callbackdev.thabit.ui.components.CanvasLine
import com.callbackdev.thabit.ui.components.CodeCanvas
import com.callbackdev.thabit.ui.components.CodeLine
import com.callbackdev.thabit.ui.components.StatusBarStart
import com.callbackdev.thabit.ui.components.StatusBarText
import com.callbackdev.thabit.ui.components.TerminalInput
import com.callbackdev.thabit.ui.components.TerminalStatusBar
import com.callbackdev.thabit.ui.components.WidgetLine
import com.callbackdev.thabit.ui.components.commentLine
import com.callbackdev.thabit.ui.editor.TextControl
import com.callbackdev.thabit.ui.editor.decorative
import com.callbackdev.thabit.ui.format.CodeFormat
import com.callbackdev.thabit.ui.theme.SyntaxColors
import com.callbackdev.thabit.ui.theme.ThabitTheme
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * `$ thabit add` — a terminal session, not a form (VISION §4.5).
 *
 * The transcript asks one thing at a time, and everything except the name has a
 * default: after the name, `[done]` adds the test as it stands and `[more]`
 * walks the rest. Six mandatory prompts would be eighteen answers inside the
 * minute §9 promises.
 *
 * These labels are the app's **first sixty seconds**, so §3.3.7 is held harder
 * here than anywhere else: every token the transcript offers carries its plain
 * meaning beside it, in the file's own comment channel for the reader who is
 * looking, and in the reader's own language for the one who is listening. The
 * metaphor's vocabulary waits in the test's expansion and in the file, where
 * nobody is a newcomer any more.
 */
@Composable
fun WizardScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    editingId: Long? = null,
    viewModel: WizardViewModel = viewModel(
        key = "wizard-${editingId ?: "new"}",
        factory = WizardViewModel.appFactory(editingId)
    )
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.closeRequested) { if (state.closeRequested) onClose() }

    // A reminder that cannot ring is a field that lies (VISION §1.1), so setting
    // one here asks for the permission the same way flipping a switch does in
    // `settings.config` — the series' gated-toggle rule, applied to the place
    // where reminders are actually created.
    val context = LocalContext.current
    var permissionEpoch by remember { mutableIntStateOf(0) }
    val hasPermission = remember(permissionEpoch) { ThabitNotifier.canPost(context) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionEpoch++ }
    val justSet = state.draft.remindAt != null && state.draft.remindAt != state.baseline.remindAt
    LaunchedEffect(state.draft.remindAt, hasPermission) {
        // Only a reminder the reader has just set, and only once: reopening
        // `[edit]` on a test that already had one must not throw a system dialog
        // at somebody who came to change its name.
        if (justSet && !hasPermission) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    WizardScreen(
        state = state,
        actions = WizardActions(viewModel),
        modifier = modifier,
        remindArmed = hasPermission,
        onGrantNotifications = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    )
}

/** The stateless half — what the previews and the UI tests drive. */
@Composable
fun WizardScreen(
    state: WizardUiState,
    actions: WizardActions,
    modifier: Modifier = Modifier,
    /** False when a reminder would be set but nothing could post it. */
    remindArmed: Boolean = true,
    onGrantNotifications: () -> Unit = {}
) {
    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            CodeCanvas(
                lines = if (state.loading) {
                    emptyList()
                } else {
                    transcriptLines(state, actions, remindArmed, onGrantNotifications)
                },
                state = rememberLazyListState(),
                modifier = Modifier.fillMaxSize()
            )
        }
        TerminalStatusBar {
            StatusBarStart { StatusBarText("⎇ main") }
            StatusBarText(if (state.draft.isEditing) "thabit edit" else "thabit add")
        }
    }
}

/** The taps the transcript can produce. */
class WizardActions(
    val onName: (String) -> Unit = {},
    val onType: (HabitType) -> Unit = {},
    val onScheme: (ScheduleScheme) -> Unit = {},
    val onToggleWeekday: (DayOfWeek) -> Unit = {},
    val onCycleQuota: () -> Unit = {},
    val onCycleInterval: () -> Unit = {},
    val onOpenPrompt: (WizardField) -> Unit = {},
    val onPromptChange: (String) -> Unit = {},
    val onPromptSubmit: () -> Unit = {},
    val onPromptCancel: () -> Unit = {},
    val onClearEmoji: () -> Unit = {},
    val onClearRemind: () -> Unit = {},
    val onMore: () -> Unit = {},
    val onDone: () -> Unit = {},
    val onAddAnother: () -> Unit = {},
    val onCancel: () -> Unit = {}
) {
    constructor(viewModel: WizardViewModel) : this(
        onName = viewModel::onName,
        onType = viewModel::onType,
        onScheme = viewModel::onScheme,
        onToggleWeekday = viewModel::onToggleWeekday,
        onCycleQuota = viewModel::onCycleQuota,
        onCycleInterval = viewModel::onCycleInterval,
        onOpenPrompt = viewModel::onOpenPrompt,
        onPromptChange = viewModel::onPromptChange,
        onPromptSubmit = viewModel::onPromptSubmit,
        onPromptCancel = viewModel::onPromptCancel,
        onClearEmoji = viewModel::onClearEmoji,
        onClearRemind = viewModel::onClearRemind,
        onMore = viewModel::onMore,
        onDone = viewModel::onDone,
        onAddAnother = viewModel::onAddAnother,
        onCancel = viewModel::onCancel
    )
}

@Composable
private fun transcriptLines(
    state: WizardUiState,
    actions: WizardActions,
    remindArmed: Boolean,
    onGrantNotifications: () -> Unit
): List<CanvasLine> {
    val syntax = ThabitTheme.syntax
    val draft = state.draft
    val lines = mutableListOf<CanvasLine>()

    // What was already added in this session stays on screen: the receipts of a
    // conversation, and the reason a second test costs one tap rather than a trip.
    state.added.forEach { name ->
        lines += CodeLine(
            text = AnnotatedString("✓ \"$name\" added to the suite", SpanStyle(color = syntax.diffAdd)),
            contentDescription = stringResource(R.string.cd_wizard_added, name)
        )
    }
    if (state.added.isNotEmpty()) lines += commentLine("", syntax)

    lines += commandLine(
        if (draft.isEditing) "$ thabit edit \"${draft.name}\"" else "$ thabit add",
        syntax
    )

    lines += nameLine(state, actions, syntax)
    if (!draft.expanded) {
        lines += commentLine("# what do you want to call it?", syntax, indent = 1)
    }

    if (draft.expanded) {
        lines += typeLines(draft, actions, syntax)
        if (state.isCounter) lines += assertLine(state, actions, syntax)
        lines += whenLines(draft, actions, syntax)
        lines += schemeDetailLines(draft, actions, syntax)
        lines += remindLines(state, actions, syntax, remindArmed, onGrantNotifications)
        lines += emojiLine(state, actions, syntax)
    }

    state.error?.let { lines += errorLine(it, syntax) }

    lines += commentLine("", syntax)
    // The confirm sits above the row and not inside it: the row is where every
    // way out of the confirm already is, because touching any other control
    // disarms it (Fase 4's lesson, and Fase 3's before that).
    if (state.discardConfirm) lines += commentLine("# $DISCARD_CONFIRM", syntax)
    lines += controlsLine(state, actions, syntax)
    return lines
}

/**
 * What the second `[esc]` will do, said before it does it.
 *
 * English, like every other comment: this is the file's own channel. The spoken
 * half travels on the `[esc]` token itself, which changes what it says when it
 * is armed.
 */
const val DISCARD_CONFIRM: String = "nothing is written yet — tap [esc] again to discard"

// ---- the rows ------------------------------------------------------------

@Composable
private fun nameLine(
    state: WizardUiState,
    actions: WizardActions,
    syntax: SyntaxColors
): CanvasLine {
    if (state.focus == WizardField.Name || state.draft.name.isEmpty()) {
        // Focused only when the name is the open question — which is true the
        // moment the transcript opens, and again after `[+ another]`. It is
        // deliberately *not* true on the line left behind by a `[done]`, where
        // the reader is looking at the receipt and not at a new answer.
        val focused = state.focus == WizardField.Name
        return WidgetLine(measureText = "> name: ${"_".repeat(24)}    ") {
            TerminalInput(
                value = state.draft.name,
                onValueChange = actions.onName,
                prompt = "> name:",
                placeholder = stringResource(R.string.cd_wizard_name),
                autoFocus = focused,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { actions.onPromptSubmit() })
            )
        }
    }
    return promptValueLine(
        key = "name",
        value = state.draft.name,
        syntax = syntax,
        description = stringResource(R.string.cd_wizard_name) + ": " + state.draft.name,
        onClick = { actions.onOpenPrompt(WizardField.Name) }
    )
}

@Composable
private fun typeLines(
    draft: WizardDraft,
    actions: WizardActions,
    syntax: SyntaxColors
): List<CanvasLine> = choiceLines(
    key = "type",
    prompt = "# what kind of test is this?",
    options = listOf(
        Choice(
            token = "boolean",
            spoken = stringResource(R.string.cd_type_boolean),
            hint = "did you do it, yes or no",
            active = draft.type == HabitType.BOOLEAN
        ) { actions.onType(HabitType.BOOLEAN) },
        Choice(
            token = "counter",
            spoken = stringResource(R.string.cd_type_counter),
            hint = "count up to a number",
            active = draft.type == HabitType.COUNTER
        ) { actions.onType(HabitType.COUNTER) },
        Choice(
            token = "avoid",
            spoken = stringResource(R.string.cd_type_avoid),
            hint = "something to stay away from",
            active = draft.type == HabitType.AVOID
        ) { actions.onType(HabitType.AVOID) }
    ),
    syntax = syntax
)

@Composable
private fun whenLines(
    draft: WizardDraft,
    actions: WizardActions,
    syntax: SyntaxColors
): List<CanvasLine> {
    val spoken = mapOf(
        ScheduleScheme.Daily to stringResource(R.string.cd_when_daily),
        ScheduleScheme.Weekdays to stringResource(R.string.cd_when_weekdays),
        ScheduleScheme.Quota to pluralStringResource(R.plurals.cd_when_quota, draft.quota, draft.quota),
        ScheduleScheme.Interval to pluralStringResource(R.plurals.cd_when_interval, draft.intervalDays, draft.intervalDays)
    )
    val hints = mapOf(
        ScheduleScheme.Daily to "every day",
        ScheduleScheme.Weekdays to "certain days of the week",
        ScheduleScheme.Quota to "a number of times each week",
        ScheduleScheme.Interval to "with days in between"
    )
    return choiceLines(
        key = "when",
        prompt = "# how often?",
        options = ScheduleScheme.entries.map { scheme ->
            Choice(
                token = draft.scheduleToken(scheme),
                spoken = spoken.getValue(scheme),
                hint = hints.getValue(scheme),
                active = scheme == draft.scheme
            ) { actions.onScheme(scheme) }
        },
        syntax = syntax,
        detailFor = draft.scheme.ordinal
    )
}

/**
 * The parameters of the chosen scheme, under the option they belong to.
 *
 * The week is two lines, weekdays then weekend: seven tappable tokens on one row
 * are wider than a phone, and a day nobody can see is a day nobody can pick.
 */
@Composable
private fun schemeDetailLines(
    draft: WizardDraft,
    actions: WizardActions,
    syntax: SyntaxColors
): List<CanvasLine> = when (draft.scheme) {
    ScheduleScheme.Daily -> emptyList()

    ScheduleScheme.Weekdays -> listOf(
        weekdayRow(
            days = listOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
            ),
            draft = draft,
            actions = actions,
            syntax = syntax
        ),
        weekdayRow(
            days = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            draft = draft,
            actions = actions,
            syntax = syntax
        )
    )

    ScheduleScheme.Quota -> listOf(
        WidgetLine(indent = 3, measureText = "[3] times a week     ") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextControl(
                    label = "[${draft.quota}]",
                    color = syntax.number,
                    description = pluralStringResource(R.plurals.cd_when_quota, draft.quota, draft.quota),
                    actionLabel = stringResource(R.string.cd_action_change),
                    onClick = actions.onCycleQuota
                )
                Text(
                    text = "times a week",
                    style = MaterialTheme.typography.bodySmall,
                    color = syntax.comment,
                    // The `[3]` beside it already says *three times a week* in
                    // the listener's own language.
                    modifier = Modifier.decorative()
                )
            }
        }
    )

    ScheduleScheme.Interval -> listOf(
        WidgetLine(indent = 3, measureText = "every [2] days     ") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "every",
                    style = MaterialTheme.typography.bodySmall,
                    color = syntax.comment,
                    modifier = Modifier.decorative()
                )
                TextControl(
                    label = "[${draft.intervalDays}]",
                    color = syntax.number,
                    description = pluralStringResource(R.plurals.cd_when_interval, draft.intervalDays, draft.intervalDays),
                    actionLabel = stringResource(R.string.cd_action_change),
                    onClick = actions.onCycleInterval
                )
                Text(
                    text = "days",
                    style = MaterialTheme.typography.bodySmall,
                    color = syntax.comment,
                    modifier = Modifier.decorative()
                )
            }
        }
    )
}

@Composable
private fun weekdayRow(
    days: List<DayOfWeek>,
    draft: WizardDraft,
    actions: WizardActions,
    syntax: SyntaxColors
): CanvasLine = WidgetLine(
    indent = 3,
    measureText = days.joinToString(" ") { "[${it.token()}]" } + "   "
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        days.forEach { day ->
            val on = day in draft.weekdays
            TextControl(
                label = if (on) "[${day.token()}]" else " ${day.token()} ",
                color = if (on) syntax.string else syntax.comment,
                description = stringResource(
                    if (on) R.string.cd_wizard_weekday_on else R.string.cd_wizard_weekday_off,
                    day.spoken()
                ),
                onClick = { actions.onToggleWeekday(day) }
            )
        }
    }
}

/** `mon` — the file's own spelling for a day, the same one the schedule uses. */
private fun DayOfWeek.token(): String = name.take(3).lowercase(Locale.ROOT)

@Composable
private fun assertLine(
    state: WizardUiState,
    actions: WizardActions,
    syntax: SyntaxColors
): CanvasLine {
    val draft = state.draft
    if (state.focus == WizardField.Unit || state.focus == WizardField.Target) {
        val unitPrompt = state.focus == WizardField.Unit
        return WidgetLine(indent = 1, measureText = "> assert: ${"_".repeat(20)}    ") {
            TerminalInput(
                value = state.pending,
                onValueChange = actions.onPromptChange,
                prompt = if (unitPrompt) "> counting:" else "> how much:",
                autoFocus = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (unitPrompt) KeyboardType.Text else KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { actions.onPromptSubmit() })
            )
        }
    }
    // `pages >= 20` reads without knowing the word `assert` (VISION §4.5), and
    // both halves are their own control.
    return WidgetLine(indent = 1, measureText = "> assert: [pages] >= [20]  # how much counts as done   ") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "> assert:",
                style = MaterialTheme.typography.bodySmall,
                color = syntax.comment,
                modifier = Modifier.decorative()
            )
            TextControl(
                label = "[${draft.unit}]",
                color = syntax.string,
                description = stringResource(R.string.cd_wizard_unit, draft.unit),
                onClick = { actions.onOpenPrompt(WizardField.Unit) }
            )
            Text(
                text = ">=",
                style = MaterialTheme.typography.bodySmall,
                color = syntax.comment,
                modifier = Modifier.decorative()
            )
            TextControl(
                label = "[${CodeFormat.number(draft.target)}]",
                color = syntax.number,
                description = stringResource(
                    R.string.cd_wizard_target,
                    CodeFormat.number(draft.target)
                ),
                onClick = { actions.onOpenPrompt(WizardField.Target) }
            )
            Text(
                text = "  # how much counts as done",
                style = MaterialTheme.typography.bodySmall,
                color = syntax.comment.copy(alpha = 0.6f),
                modifier = Modifier.decorative()
            )
        }
    }
}

/**
 * `> remind: [07:00] [off]  # approximate — a nudge, not an alarm clock`.
 *
 * The approximation is **declared here**, where the reminder is set, and not
 * only in the settings file: `setWindow` can be ten minutes late, and a user who
 * discovers that at 07:09 without having been told has been lied to by omission
 * (VISION §1.1, §6.7).
 *
 * It sits above `emoji:` on purpose. The emoji is the transcript's last, most
 * decorative question, and a reminder is the one remaining answer that changes
 * what the app will *do*.
 */
@Composable
private fun remindLines(
    state: WizardUiState,
    actions: WizardActions,
    syntax: SyntaxColors,
    armed: Boolean,
    onGrant: () -> Unit
): List<CanvasLine> {
    if (state.focus == WizardField.Remind) {
        return listOf(
            WidgetLine(indent = 1, measureText = "> remind: ______    ") {
                TerminalInput(
                    value = state.pending,
                    onValueChange = actions.onPromptChange,
                    prompt = "> remind:",
                    placeholder = "07:00",
                    autoFocus = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { actions.onPromptSubmit() })
                )
            },
            commentLine("# empty to turn it off", syntax, indent = 2)
        )
    }

    val remindAt = state.draft.remindAt
    val lines = mutableListOf<CanvasLine>(
        WidgetLine(indent = 1, measureText = "> remind: [07:00] [off]  # approximate   ") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "> remind:",
                    style = MaterialTheme.typography.bodySmall,
                    color = syntax.comment,
                    modifier = Modifier.decorative()
                )
                TextControl(
                    label = remindAt?.let { "[${CodeFormat.time(it)}]" } ?: "[off]",
                    color = if (remindAt != null) syntax.string else syntax.comment,
                    description = remindAt
                        ?.let { stringResource(R.string.cd_wizard_remind, it.spoken()) }
                        ?: stringResource(R.string.cd_wizard_remind_off),
                    onClick = { actions.onOpenPrompt(WizardField.Remind) }
                )
                if (remindAt != null) {
                    TextControl(
                        label = "[off]",
                        color = syntax.comment,
                        description = stringResource(R.string.cd_wizard_remind_clear),
                        onClick = actions.onClearRemind
                    )
                }
                Text(
                    text = if (remindAt != null) {
                        "  # approximate — a nudge, not an alarm"
                    } else {
                        "  # optional — a nudge at a time you pick"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = syntax.comment.copy(alpha = 0.6f),
                    modifier = Modifier.decorative()
                )
            }
        }
    )
    // The reminder is set and nothing can post it. Said in the transcript rather
    // than left to be discovered at seven the next morning.
    //
    // Two lines, and that is the point: the message and the way out shared a row
    // at first, and on a narrow screen `[grant]` sat past the right edge — an
    // affordance that exists only for whoever thinks to scroll sideways, which
    // is the exact defect Fase 5 fixed in the `when:` options and Fase 4 in the
    // restore confirm. **The way out gets its own line.**
    if (remindAt != null && !armed) {
        lines += CodeLine(
            text = AnnotatedString(
                "# not armed: notifications are off",
                SpanStyle(color = syntax.diffDel)
            ),
            indent = 2
        )
        lines += WidgetLine(indent = 2, measureText = "[grant]    ") {
            TextControl(
                label = "[grant]",
                color = syntax.diffDel,
                description = stringResource(R.string.cd_action_grant_notifications),
                onClick = onGrant
            )
        }
    }
    return lines
}

@Composable
private fun emojiLine(
    state: WizardUiState,
    actions: WizardActions,
    syntax: SyntaxColors
): CanvasLine {
    if (state.focus == WizardField.Emoji) {
        return WidgetLine(indent = 1, measureText = "> emoji: ____    ") {
            TerminalInput(
                value = state.pending,
                onValueChange = actions.onPromptChange,
                prompt = "> emoji:",
                autoFocus = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { actions.onPromptSubmit() })
            )
        }
    }
    val emoji = state.draft.emoji
    return WidgetLine(indent = 1, measureText = "> emoji: [none]  # optional  [skip]   ") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "> emoji:",
                style = MaterialTheme.typography.bodySmall,
                color = syntax.comment,
                modifier = Modifier.decorative()
            )
            TextControl(
                label = emoji ?: "[none]",
                color = if (emoji != null) syntax.string else syntax.comment,
                description = stringResource(
                    R.string.cd_wizard_emoji,
                    emoji ?: stringResource(R.string.cd_wizard_emoji_none)
                ),
                onClick = { actions.onOpenPrompt(WizardField.Emoji) }
            )
            if (emoji != null) {
                TextControl(
                    label = "[skip]",
                    color = syntax.comment,
                    description = stringResource(R.string.cd_wizard_emoji_none),
                    onClick = actions.onClearEmoji
                )
            }
            Text(
                text = "  # optional",
                style = MaterialTheme.typography.bodySmall,
                color = syntax.comment.copy(alpha = 0.6f),
                modifier = Modifier.decorative()
            )
        }
    }
}

@Composable
private fun controlsLine(
    state: WizardUiState,
    actions: WizardActions,
    syntax: SyntaxColors
): CanvasLine {
    val draft = state.draft
    val justAdded = state.added.isNotEmpty() && draft.name.isBlank()
    return WidgetLine(measureText = "[+ another]  [done]  [more]  [esc]      ") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (justAdded) {
                TextControl(
                    label = "[+ another]",
                    color = syntax.diffAdd,
                    description = stringResource(R.string.cd_action_add_another),
                    onClick = actions.onAddAnother
                )
            } else {
                // `[done]` appears the moment the test has a name — the promise
                // that one answer is enough (VISION §4.5).
                if (draft.name.isNotBlank()) {
                    TextControl(
                        label = if (draft.isEditing) "[save]" else "[done]",
                        color = syntax.diffAdd,
                        description = stringResource(
                            if (draft.isEditing) R.string.cd_action_save_test
                            else R.string.cd_action_add_test
                        ),
                        onClick = actions.onDone
                    )
                }
                if (!draft.expanded) {
                    TextControl(
                        label = "[more]",
                        color = syntax.key,
                        description = stringResource(R.string.cd_action_more),
                        onClick = actions.onMore
                    )
                }
            }
            TextControl(
                label = "[esc]",
                // Armed, it is the one token on screen that throws work away, so
                // it wears the colour the file gives to a deletion.
                color = if (state.discardConfirm) syntax.diffDel else syntax.comment,
                description = stringResource(
                    if (state.discardConfirm) R.string.cd_action_close_confirm
                    else R.string.cd_action_close
                ),
                onClick = actions.onCancel
            )
        }
    }
}

// ---- small pieces --------------------------------------------------------

private data class Choice(
    val token: String,
    val spoken: String,
    /** The plain meaning, in the file's own comment channel. */
    val hint: String,
    val active: Boolean,
    val onClick: () -> Unit
)

/**
 * A question and its answers, one per line.
 *
 * They started life on a single row — `> when: [daily] mon,wed,fri 3/week every
 * 2d` — the way VISION §4.5 sketches it, and a test caught the problem: on a
 * phone the row runs past the right edge, so the third and fourth options exist
 * only for whoever thinks to scroll sideways. In the app's first sixty seconds
 * that is not a layout detail, it is options the reader never learns about.
 *
 * One line each also buys what the sketch could not fit: **every** option gets
 * its plain meaning beside it, not just the chosen one (§3.3.7).
 */
@Composable
private fun choiceLines(
    key: String,
    prompt: String,
    options: List<Choice>,
    syntax: SyntaxColors,
    detailFor: Int? = null
): List<CanvasLine> {
    val lines = mutableListOf<CanvasLine>()
    lines += CodeLine(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = syntax.comment)) { append("> $key:") }
            withStyle(SpanStyle(color = syntax.comment.copy(alpha = 0.6f))) {
                append("  $prompt")
            }
        },
        indent = 1
    )
    options.forEach { option ->
        lines += WidgetLine(
            indent = 2,
            measureText = "[${option.token}]   # ${option.hint}   "
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextControl(
                    label = if (option.active) "[${option.token}]" else " ${option.token} ",
                    color = if (option.active) syntax.string else syntax.comment,
                    description = stringResource(
                        if (option.active) R.string.cd_wizard_chosen else R.string.cd_wizard_choose,
                        option.spoken
                    ),
                    onClick = option.onClick
                )
                Text(
                    text = "  # ${option.hint}",
                    style = MaterialTheme.typography.bodySmall,
                    color = syntax.comment.copy(alpha = 0.6f),
                    // The token carries the same hint, translated, in its own
                    // spoken name: *Scegli: conta fino a un numero*.
                    modifier = Modifier.decorative()
                )
            }
        }
    }
    return lines
}

private fun promptValueLine(
    key: String,
    value: String,
    syntax: SyntaxColors,
    description: String,
    onClick: () -> Unit
): CodeLine = CodeLine(
    text = buildAnnotatedString {
        withStyle(SpanStyle(color = syntax.comment)) { append("> $key: ") }
        withStyle(SpanStyle(color = syntax.string)) { append(value) }
    },
    indent = 1,
    onClick = onClick,
    contentDescription = description
)

private fun errorLine(message: String, syntax: SyntaxColors): CodeLine = CodeLine(
    text = AnnotatedString("# $message", SpanStyle(color = syntax.diffDel)),
    contentDescription = message.removePrefix("ERROR: ")
)

private fun commandLine(command: String, syntax: SyntaxColors): CodeLine =
    CodeLine(AnnotatedString(command, SpanStyle(color = syntax.key)))

@Composable
private fun DayOfWeek.spoken(): String =
    getDisplayName(TextStyle.FULL, LocalConfiguration.current.locales[0])

/** `07:00` is the file's spelling; *le 7:00* is what a screen reader should hear. */
@Composable
private fun LocalTime.spoken(): String {
    val locale = LocalConfiguration.current.locales[0]
    return format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A, heightDp = 420)
@Composable
private fun WizardPreview() {
    ThabitTheme {
        WizardScreen(
            state = WizardUiState(
                draft = WizardDraft(
                    name = "read 20 pages",
                    type = HabitType.COUNTER,
                    unit = "pages",
                    target = 20.0,
                    emoji = "📖",
                    expanded = true
                ),
                focus = null
            ),
            actions = WizardActions()
        )
    }
}
