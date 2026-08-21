package com.callbackdev.thabit.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callbackdev.thabit.R
import com.callbackdev.thabit.ui.components.CanvasLine
import com.callbackdev.thabit.ui.components.CodeCanvas
import com.callbackdev.thabit.ui.components.CodeLine
import com.callbackdev.thabit.ui.components.EditorTabs
import com.callbackdev.thabit.ui.components.StatusBarStart
import com.callbackdev.thabit.ui.components.StatusBarText
import com.callbackdev.thabit.ui.components.TerminalStatusBar
import com.callbackdev.thabit.ui.components.WidgetLine
import com.callbackdev.thabit.ui.components.commentLine
import com.callbackdev.thabit.ui.components.keyOpenLine
import com.callbackdev.thabit.ui.components.punctLine
import com.callbackdev.thabit.ui.components.rawValueLine
import com.callbackdev.thabit.ui.components.stringItemLine
import com.callbackdev.thabit.ui.components.stringValueLine
import com.callbackdev.thabit.ui.editor.TextControl
import com.callbackdev.thabit.ui.editor.decorative
import com.callbackdev.thabit.ui.theme.ThabitTheme
import com.callbackdev.thabit.ui.theme.ThemeProfile
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * `settings.config` — the series' JSON with comments, where the values are the
 * controls.
 *
 * Booleans flip on tap, cycles cycle, the theme is chosen by tapping the profile
 * you want in the list. No switches, no dialogs, no Material rows: the file *is*
 * the settings screen (VISION §1.1, §4.4).
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(state = state, actions = SettingsActions(viewModel), modifier = modifier)
}

/** The stateless half — what the previews and the UI tests drive. */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier
) {
    val document = state.document
    Column(modifier.fillMaxSize()) {
        // A one-element strip and not a plainer bar of its own: this was the
        // sibling's pre-v1 lesson — the single-file screens were the only places
        // where the open file had no indicator under it, and that read as a
        // different kind of chrome on every switch into them.
        EditorTabs(fileNames = listOf(SettingsDocument.FILE_NAME), activeIndex = 0, onSelect = {})
        Box(Modifier.weight(1f)) {
            CodeCanvas(
                lines = if (document == null) emptyList() else settingsLines(
                    document = document,
                    interaction = state.interaction,
                    actions = actions
                ),
                state = rememberLazyListState(),
                modifier = Modifier.fillMaxSize()
            )
        }
        TerminalStatusBar {
            StatusBarStart { StatusBarText("⎇ main") }
            // The name is in the strip above now, so the bar says what an
            // editor's bar says instead of repeating it: this is the one file
            // the reader can actually write to, and `ro` would be a lie.
            StatusBarText("rw")
        }
    }
}

/** The taps the settings file can produce. */
class SettingsActions(
    val onCycleDayEnds: () -> Unit = {},
    val onCycleWeekStart: () -> Unit = {},
    val onToggleLineNumbers: () -> Unit = {},
    val onToggleWordWrap: () -> Unit = {},
    val onSelectTheme: (ThemeProfile) -> Unit = {},
    val onExport: (String) -> Unit = {},
    val onRestore: () -> Unit = {},
    val onCancelRestore: () -> Unit = {}
) {
    constructor(viewModel: SettingsViewModel) : this(
        onCycleDayEnds = viewModel::onCycleDayEnds,
        onCycleWeekStart = viewModel::onCycleWeekStart,
        onToggleLineNumbers = viewModel::onToggleLineNumbers,
        onToggleWordWrap = viewModel::onToggleWordWrap,
        onSelectTheme = viewModel::onSelectTheme,
        onExport = viewModel::onExport,
        onRestore = viewModel::onRestore,
        onCancelRestore = viewModel::onCancelRestore
    )
}

@Composable
private fun settingsLines(
    document: SettingsDocument,
    interaction: SettingsInteraction,
    actions: SettingsActions
): List<CanvasLine> {
    val syntax = ThabitTheme.syntax
    val change = stringResource(R.string.cd_action_change)
    val lines = mutableListOf<CanvasLine>()

    document.lastModified?.let { millis ->
        val stamp = millis.spoken()
        lines += commentLine(
            text = "// Last modified: $stamp",
            syntax = syntax,
            contentDescription = stringResource(R.string.cd_last_modified, stamp)
        )
    }
    lines += punctLine("{", 0, syntax)

    // ---- suite ----------------------------------------------------------
    lines += keyOpenLine("suite", 1, syntax)
    lines += stringValueLine(
        key = "day_ends",
        value = document.dayEndsValue,
        comma = true,
        syntax = syntax,
        hint = SettingsDocument.DAY_ENDS_HINT,
        contentDescription = stringResource(
            R.string.cd_setting_day_ends,
            document.dayEnds.spoken()
        ),
        onClickLabel = change,
        onClick = actions.onCycleDayEnds
    )
    lines += stringValueLine(
        key = "week_starts",
        value = document.weekStartsValue,
        comma = false,
        syntax = syntax,
        hint = SettingsDocument.WEEK_STARTS_HINT,
        contentDescription = stringResource(
            R.string.cd_setting_week_starts,
            document.weekStartsOn.spoken()
        ),
        onClickLabel = change,
        onClick = actions.onCycleWeekStart
    )
    lines += punctLine("},", 1, syntax)

    // ---- editor ---------------------------------------------------------
    lines += keyOpenLine("editor", 1, syntax)
    lines += rawValueLine(
        key = "line_numbers",
        value = document.showLineNumbers.toString(),
        comma = true,
        syntax = syntax,
        contentDescription = stringResource(
            R.string.cd_setting_line_numbers,
            document.showLineNumbers.spoken()
        ),
        onClickLabel = change,
        onClick = actions.onToggleLineNumbers
    )
    lines += rawValueLine(
        key = "word_wrap",
        value = document.wordWrap.toString(),
        comma = false,
        syntax = syntax,
        contentDescription = stringResource(
            R.string.cd_setting_word_wrap,
            document.wordWrap.spoken()
        ),
        onClickLabel = change,
        onClick = actions.onToggleWordWrap
    )
    lines += punctLine("},", 1, syntax)

    // ---- theme ----------------------------------------------------------
    lines += keyOpenLine("theme", 1, syntax)
    lines += stringValueLine(
        key = "active_profile",
        value = document.activeProfileValue,
        comma = true,
        syntax = syntax,
        indent = 2
    )
    lines += keyOpenLine("available_profiles", 2, syntax, bracket = "[")
    val useTheme = stringResource(R.string.cd_action_use_theme)
    document.profiles.forEachIndexed { index, entry ->
        lines += stringItemLine(
            value = entry.value,
            comma = index < document.profiles.lastIndex,
            syntax = syntax,
            indent = 3,
            hint = if (entry.active) SettingsDocument.ACTIVE_HINT else null,
            contentDescription = stringResource(
                if (entry.active) R.string.cd_setting_theme_active else R.string.cd_setting_theme,
                entry.value
            ),
            onClickLabel = useTheme,
            onClick = { actions.onSelectTheme(entry.profile) }
        )
    }
    lines += punctLine("]", 2, syntax)
    lines += punctLine("},", 1, syntax)

    // ---- notifications --------------------------------------------------
    lines += keyOpenLine("notifications", 1, syntax)
    if (!document.notificationsWired) {
        // An honest empty section beats three switches that do nothing: a
        // config that shows a toggle is promising the toggle works.
        lines += commentLine(SettingsDocument.NOTIFICATIONS_PLACEHOLDER, syntax, indent = 2)
    }
    lines += punctLine("},", 1, syntax)

    // ---- about ----------------------------------------------------------
    lines += keyOpenLine("about", 1, syntax)
    lines += stringValueLine(
        key = "version",
        value = document.versionName,
        comma = true,
        syntax = syntax,
        contentDescription = stringResource(R.string.cd_setting_version, document.versionName)
    )
    lines += stringValueLine("storage", "this device only", comma = true, syntax = syntax)
    lines += stringValueLine("network", "none — no INTERNET permission", comma = true, syntax = syntax)
    lines += stringValueLine("license", "GPL-3.0", comma = false, syntax = syntax)
    lines += punctLine("}", 1, syntax)
    lines += punctLine("}", 0, syntax)

    // ---- the commands ---------------------------------------------------
    lines += commentLine("", syntax)
    lines += commandLine(
        command = "$ thabit export --json",
        color = syntax.key,
        description = stringResource(R.string.cd_action_export_json),
        onClick = { actions.onExport("json") }
    )
    lines += commandLine(
        command = "$ thabit export --csv",
        color = syntax.key,
        description = stringResource(R.string.cd_action_export_csv),
        onClick = { actions.onExport("csv") }
    )

    if (interaction.restoreConfirm) {
        lines += commandLine(
            command = "$ git restore ${SettingsDocument.FILE_NAME}",
            color = syntax.diffDel,
            description = stringResource(R.string.cd_action_restore_confirm),
            onClick = actions.onRestore
        )
        // The way out stays on its own line, always on screen — the lesson the
        // suite's archive confirm learned the hard way (Fase 3).
        lines += WidgetLine(measureText = "${SettingsDocument.RESTORE_CONFIRM}  [esc]   ") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = SettingsDocument.RESTORE_CONFIRM,
                    style = MaterialTheme.typography.bodySmall,
                    color = syntax.comment,
                    // The command above and the `[esc]` beside it already say
                    // this, localized, as what they do.
                    modifier = Modifier.decorative()
                )
                TextControl(
                    label = "[esc]",
                    color = syntax.comment,
                    description = stringResource(R.string.cd_action_cancel),
                    onClick = actions.onCancelRestore
                )
            }
        }
    } else {
        lines += commandLine(
            command = "$ git restore ${SettingsDocument.FILE_NAME}",
            color = syntax.diffDel,
            description = stringResource(R.string.cd_action_restore),
            onClick = actions.onRestore
        )
        lines += commentLine(SettingsDocument.RESTORE_HINT, syntax)
    }

    interaction.transient?.let { message ->
        lines += commentLine("", syntax)
        lines += commentLine(message, syntax)
    }
    return lines
}

/** A `$` command line: the series' shape for anything that runs or resets. */
private fun commandLine(
    command: String,
    color: Color,
    description: String,
    onClick: () -> Unit
): CodeLine = CodeLine(
    text = AnnotatedString(command, SpanStyle(color = color)),
    onClick = onClick,
    contentDescription = description
)

// ---- the localized half --------------------------------------------------

/** `03:00` said the way the reader's language says a time of day. */
@Composable
private fun LocalTime.spoken(): String {
    val locale = LocalConfiguration.current.locales[0]
    return format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
}

/** `monday` is source; *lunedì* is what a screen reader should hear. */
@Composable
private fun DayOfWeek.spoken(): String =
    getDisplayName(TextStyle.FULL, LocalConfiguration.current.locales[0])

@Composable
private fun Boolean.spoken(): String =
    stringResource(if (this) R.string.cd_value_on else R.string.cd_value_off)

/** The one localized value inside the file: a timestamp is data, not source. */
@Composable
private fun Long.spoken(): String {
    val locale = LocalConfiguration.current.locales[0]
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .format(
            DateTimeFormatter
                .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(locale)
        )
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A, heightDp = 720)
@Composable
private fun SettingsScreenPreview() {
    ThabitTheme {
        SettingsScreen(
            state = SettingsUiState(
                document = SettingsDocument(
                    dayEnds = LocalTime.of(3, 0),
                    weekStartsOn = DayOfWeek.MONDAY,
                    theme = ThemeProfile.Obsidian,
                    showLineNumbers = true,
                    wordWrap = false,
                    lastModified = 1_787_000_000_000L,
                    versionName = "0.1.0"
                )
            ),
            actions = SettingsActions()
        )
    }
}
