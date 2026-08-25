package com.callbackdev.thabit.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.callbackdev.thabit.BuildConfig
import com.callbackdev.thabit.data.HabitRepository
import com.callbackdev.thabit.data.SettingsStore
import com.callbackdev.thabit.data.ThabitSettings
import com.callbackdev.thabit.data.WorkspaceStore
import com.callbackdev.thabit.di.ServiceLocator
import com.callbackdev.thabit.export.DataExporter
import com.callbackdev.thabit.export.ExportFormat
import com.callbackdev.thabit.export.ExportResult
import com.callbackdev.thabit.ui.theme.ThemeProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * `settings.config` on screen.
 *
 * Every control is a write to [SettingsStore] and a redraw of the file that
 * comes back — no local mirror of the config, the same discipline the suite
 * screen follows.
 */
class SettingsViewModel(
    private val settings: SettingsStore,
    /**
     * Only ever read, and only for one number: how many live tests carry a
     * reminder.
     *
     * `settings.config` does not edit a test — reminders belong to the test
     * (VISION §4.4). It reads the suite so the `notifications` block can stop
     * short of claiming that everything is off when a test still has an alarm
     * registered; a file that says "disabled" while the phone rings at seven is
     * the exact failure §1.1 exists to prevent.
     */
    private val repository: HabitRepository,
    /** Built lazily so a screen that never exports never touches MediaStore. */
    private val exporter: () -> DataExporter,
    /** Survives this destination, so `[esc]` mid-write cannot cancel the write. */
    private val exportScope: CoroutineScope,
    /** Session state, written to for one thing only: see [onHelpOpened]. */
    private val workspace: WorkspaceStore,
    private val versionName: String = BuildConfig.VERSION_NAME
) : ViewModel() {

    /**
     * `HELP.md` is on screen, so the editor stops pointing at it (Fase 14).
     *
     * Called however the file was reached — the hint, the tab strip, or the tab
     * the app happened to reopen on. A pointer to something the reader has now
     * read is the kind of chrome that turns into furniture.
     */
    fun onHelpOpened() {
        viewModelScope.launch { workspace.dismissHelpHint() }
    }

    private val interaction = MutableStateFlow(SettingsInteraction())
    private val _export = MutableStateFlow<ExportState>(ExportState.Idle)

    val state: StateFlow<SettingsUiState> = combine(
        settings.settings,
        repository.observeLiveSuite().map { suite -> suite.count { it.remindAt != null } },
        interaction,
        _export
    ) { config, reminders, ui, export ->
        SettingsUiState(
            document = SettingsDocument.of(
                dayEnds = config.dayEnds,
                weekStartsOn = config.weekStartsOn,
                theme = config.theme,
                showLineNumbers = config.showLineNumbers,
                wordWrap = config.wordWrap,
                lastModified = config.lastModified,
                versionName = versionName,
                notifications = config.notifications,
                reminderCount = reminders,
                widgetOpacityPct = config.widgetOpacityPct
            ),
            interaction = ui,
            export = export
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /**
     * Every control reads the **stored** value, not the drawn one.
     *
     * Reading the rendered document would make a tap depend on the file having
     * been composed first — true in the app, but a silent no-op the moment it is
     * not, and the sort of coupling that turns into a bug the first time a
     * control is reachable from anywhere else (a notification action, a widget).
     */
    fun onCycleDayEnds() = write {
        settings.setDayEnds(SettingsDocument.nextDayEnds(current().dayEnds))
    }

    fun onCycleWeekStart() = write {
        settings.setWeekStart(SettingsDocument.nextWeekStart(current().weekStartsOn))
    }

    fun onToggleLineNumbers() = write {
        settings.setShowLineNumbers(!current().showLineNumbers)
    }

    fun onToggleWordWrap() = write {
        settings.setWordWrap(!current().wordWrap)
    }

    fun onSelectTheme(profile: ThemeProfile) = write { settings.setTheme(profile) }

    fun onToggleDailyCommit() = write {
        settings.setDailyCommit(!current().notifications.dailyCommit)
    }

    /**
     * The one notification that is opt-in, and the one whose alarm has to move
     * with it: switching it on registers the evening wake-up, switching it off
     * takes it back. Doing that here rather than in the screen keeps it true for
     * whoever flips the setting next — a widget, a future shortcut, a test.
     */
    fun onTogglePendingDigest() = write {
        settings.setPendingDigest(!current().notifications.pendingDigest)
    }

    fun onCycleDigestHour() = write {
        settings.setDigestHour(SettingsDocument.nextDigestHour(current().notifications.digestHour))
    }

    fun onCycleWidgetOpacity() = write {
        settings.setWidgetOpacity(SettingsDocument.nextWidgetOpacity(current().widgetOpacityPct))
    }

    private suspend fun current(): ThabitSettings = settings.settings.first()

    /**
     * `$ thabit export --json|--csv`.
     *
     * One tap, one pass, and the answer comes back into the file as terminal
     * output — the names the store actually wrote, not the names that were
     * asked for. It runs on [appScope] and not on `viewModelScope`: leaving the
     * settings tab mid-write would otherwise cancel the export and leave a
     * pending row in MediaStore that nobody ever publishes.
     */
    fun onExport(format: ExportFormat) {
        if (_export.value == ExportState.Running) return
        _export.value = ExportState.Running
        exportScope.launch {
            _export.value = ExportState.Done(exporter().export(format))
        }
    }

    /**
     * `$ git restore settings.config`, two taps.
     *
     * The second tap resets the config and **only** the config: the suite and
     * every check row live in Room and are never in reach of this call. A user
     * who wanted their line numbers back must not lose a day of their history
     * for it (VISION §4.4).
     */
    fun onRestore() {
        if (!interaction.value.restoreConfirm) {
            interaction.update { it.copy(restoreConfirm = true) }
            return
        }
        interaction.update { it.copy(restoreConfirm = false) }
        viewModelScope.launch { settings.restoreDefaults() }
    }

    fun onCancelRestore() = interaction.update { it.copy(restoreConfirm = false) }

    private fun write(block: suspend () -> Unit) {
        interaction.update { it.copy(restoreConfirm = false) }
        viewModelScope.launch { block() }
    }

    companion object {

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                val graph = ServiceLocator.graph(app)
                SettingsViewModel(
                    settings = graph.settings,
                    repository = graph.repository,
                    exporter = { ServiceLocator.exporter(app) },
                    exportScope = graph.appScope,
                    workspace = graph.workspace
                )
            }
        }

        /** For tests and previews. */
        fun factory(
            settings: SettingsStore,
            repository: HabitRepository,
            exporter: () -> DataExporter,
            exportScope: CoroutineScope,
            workspace: WorkspaceStore,
            versionName: String
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                    SettingsViewModel(
                        settings, repository, exporter, exportScope, workspace, versionName
                    ) as T
            }
    }
}

data class SettingsUiState(
    val document: SettingsDocument? = null,
    val interaction: SettingsInteraction = SettingsInteraction(),
    val export: ExportState = ExportState.Idle
)

/**
 * What the export command is doing right now.
 *
 * Its own state and not a [SettingsInteraction.transient] line: an export
 * answers with several lines (one per file the store wrote, plus what went into
 * them), it can fail with a message worth colouring red, and unlike a transient
 * it must **not** time out — the filename it reports is the only place the user
 * will ever see where their data went.
 */
sealed interface ExportState {
    data object Idle : ExportState
    data object Running : ExportState
    data class Done(val result: ExportResult) : ExportState
}

/**
 * The file's own confirmations.
 *
 * There used to be a `transient` line here too — the series' answer to a toast,
 * with a four-second life. Fase 11 took it out with its only caller: an export
 * reports a **filename**, and a filename that disappears while you are reading
 * it is worse than no line at all, so [ExportState] answers instead and stays
 * put. A channel with no producer left is dead code, and dead code in this repo
 * is a defect, not furniture.
 */
data class SettingsInteraction(
    val restoreConfirm: Boolean = false
)
