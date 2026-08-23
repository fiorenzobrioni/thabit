package com.callbackdev.thabit.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callbackdev.thabit.R
import com.callbackdev.thabit.notifications.ThabitNotifier
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
import com.callbackdev.thabit.ui.theme.SyntaxColors
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
    val context = LocalContext.current
    val activity = LocalActivity.current

    // POST_NOTIFICATIONS — the series' state machine, ported from tsteps.
    //
    // Re-read on every resume rather than remembered once: the grant lives in
    // the system settings, where it can be given or taken away while this screen
    // is paused, and a config file showing a stale answer would be lying about
    // the one thing this block is for.
    var permissionEpoch by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionEpoch++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val hasPermission = remember(permissionEpoch) { ThabitNotifier.canPost(context) }
    var deniedPermanently by remember { mutableStateOf(false) }
    // A switch flipped on before the grant, applied the moment it arrives.
    var pendingToggle by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionEpoch++
        if (granted) {
            deniedPermanently = false
            pendingToggle?.invoke()
        } else if (
            activity?.shouldShowRequestPermissionRationale(
                Manifest.permission.POST_NOTIFICATIONS
            ) == false
        ) {
            // Android stops showing the dialog after the second refusal, so from
            // here the only way back is the app's own page in the settings.
            deniedPermanently = true
        }
        pendingToggle = null
    }
    // The system-settings detour has no result callback: coming back IS the
    // callback. A grant applies the pending switch; any other return drops it,
    // so a switch flipped ten minutes ago cannot go off by surprise.
    LaunchedEffect(permissionEpoch) {
        if (hasPermission) pendingToggle?.invoke()
        pendingToggle = null
    }

    val document = state.document
    val notifState = when {
        document == null || !document.anyNotification -> NotifLineState.Disabled
        hasPermission -> NotifLineState.Armed
        deniedPermanently -> NotifLineState.DeniedPermanently
        else -> NotifLineState.MissingPermission
    }

    /** Switching a notification ON without the permission asks for it first. */
    fun gated(toggle: () -> Unit): () -> Unit = {
        if (!hasPermission) {
            pendingToggle = toggle
            if (deniedPermanently) {
                context.openAppSystemSettings()
            } else {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            toggle()
        }
    }

    SettingsScreen(
        state = state,
        actions = SettingsActions(viewModel).copy(
            // Only the switch going ON needs the grant; turning one off must
            // never open a permission dialog.
            onToggleDailyCommit = {
                if (document?.notifications?.dailyCommit == true) viewModel.onToggleDailyCommit()
                else gated(viewModel::onToggleDailyCommit)()
            },
            onTogglePendingDigest = {
                if (document?.notifications?.pendingDigest == true) viewModel.onTogglePendingDigest()
                else gated(viewModel::onTogglePendingDigest)()
            },
            onNotifLine = {
                when (notifState) {
                    NotifLineState.MissingPermission ->
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    NotifLineState.DeniedPermanently -> context.openAppSystemSettings()
                    else -> Unit
                }
            }
        ),
        notifState = notifState,
        modifier = modifier
    )
}

/**
 * What the `notifications` block's dynamic `//` line has to say (tweather's
 * states, tsteps' wording).
 *
 * The line exists because a permission the app never got is a fact the config
 * file must state: without it a reader sees `"daily_commit": true` and a phone
 * that never buzzes, with nothing on screen connecting the two.
 */
enum class NotifLineState {
    /** Nothing is on and no test carries a reminder: nothing will ever post. */
    Disabled,

    /** Something is on and the permission is granted. */
    Armed,

    /** Something is on but there is no permission; tapping asks for it. */
    MissingPermission,

    /** Refused twice: only the system settings can give it back. */
    DeniedPermanently
}

/** The stateless half — what the previews and the UI tests drive. */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    notifState: NotifLineState = NotifLineState.Disabled
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
                    notifState = notifState,
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
data class SettingsActions(
    val onCycleDayEnds: () -> Unit = {},
    val onCycleWeekStart: () -> Unit = {},
    val onToggleLineNumbers: () -> Unit = {},
    val onToggleWordWrap: () -> Unit = {},
    val onSelectTheme: (ThemeProfile) -> Unit = {},
    val onToggleDailyCommit: () -> Unit = {},
    val onTogglePendingDigest: () -> Unit = {},
    val onCycleDigestHour: () -> Unit = {},
    /** The dynamic `//` line, tappable only when it reports a missing grant. */
    val onNotifLine: () -> Unit = {},
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
        onToggleDailyCommit = viewModel::onToggleDailyCommit,
        onTogglePendingDigest = viewModel::onTogglePendingDigest,
        onCycleDigestHour = viewModel::onCycleDigestHour,
        onExport = viewModel::onExport,
        onRestore = viewModel::onRestore,
        onCancelRestore = viewModel::onCancelRestore
    )
}

@Composable
private fun settingsLines(
    document: SettingsDocument,
    interaction: SettingsInteraction,
    notifState: NotifLineState,
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
    lines += rawValueLine(
        key = "daily_commit",
        value = document.notifications.dailyCommit.toString(),
        comma = true,
        syntax = syntax,
        hint = SettingsDocument.DAILY_COMMIT_HINT,
        contentDescription = stringResource(
            R.string.cd_setting_daily_commit,
            document.notifications.dailyCommit.spoken()
        ),
        onClickLabel = change,
        onClick = actions.onToggleDailyCommit
    )
    lines += rawValueLine(
        key = "pending_digest",
        value = document.notifications.pendingDigest.toString(),
        comma = true,
        syntax = syntax,
        hint = SettingsDocument.PENDING_DIGEST_HINT,
        contentDescription = stringResource(
            R.string.cd_setting_pending_digest,
            document.notifications.pendingDigest.spoken()
        ),
        onClickLabel = change,
        onClick = actions.onTogglePendingDigest
    )
    lines += stringValueLine(
        key = "digest_hour",
        value = document.digestHourValue,
        comma = false,
        syntax = syntax,
        hint = SettingsDocument.DIGEST_HOUR_HINT,
        contentDescription = stringResource(
            R.string.cd_setting_digest_hour,
            document.notifications.digestHour.spoken()
        ),
        onClickLabel = change,
        onClick = actions.onCycleDigestHour
    )
    // Per-test reminders are not settings and are not edited here — but leaving
    // them unmentioned would let this block imply that two `false`s mean silence.
    lines += commentLine(document.remindersComment, syntax, indent = 2)
    lines += commentLine(SettingsDocument.REMINDERS_HINT, syntax, indent = 2)
    lines += notifStatusLine(
        state = notifState,
        syntax = syntax,
        onClickLabel = stringResource(R.string.cd_action_grant_notifications),
        onClick = actions.onNotifLine
    )
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

/**
 * The `notifications` block's dynamic `//` line.
 *
 * The two error states are **tappable**, and that is the whole point of the line:
 * a config that reports a missing permission without offering the way to grant
 * it has told the reader about a wall. English like every other comment; the
 * spoken half travels on the line's own click label.
 */
private fun notifStatusLine(
    state: NotifLineState,
    syntax: SyntaxColors,
    onClickLabel: String,
    onClick: () -> Unit
): CodeLine {
    val (text, color) = when (state) {
        NotifLineState.Disabled ->
            "// nothing will post — everything here is off" to syntax.comment.copy(alpha = 0.6f)
        NotifLineState.Armed ->
            "// armed — posts at the boundary and at the times you set" to
                syntax.comment.copy(alpha = 0.6f)
        NotifLineState.MissingPermission ->
            "// ERROR: notifications permission missing — tap to grant" to syntax.diffDel
        NotifLineState.DeniedPermanently ->
            "// ERROR: notifications blocked — tap to open the system settings" to syntax.diffDel
    }
    val clickable = state == NotifLineState.MissingPermission ||
        state == NotifLineState.DeniedPermanently
    return CodeLine(
        text = AnnotatedString(text, SpanStyle(color = color)),
        indent = 2,
        onClick = onClick.takeIf { clickable },
        onClickLabel = onClickLabel.takeIf { clickable }
    )
}

/** A permission refused twice can only be given back from the app's own page. */
private fun android.content.Context.openAppSystemSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

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
