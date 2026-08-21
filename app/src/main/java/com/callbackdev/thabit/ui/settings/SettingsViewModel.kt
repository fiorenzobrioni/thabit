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
import com.callbackdev.thabit.data.SettingsStore
import com.callbackdev.thabit.data.ThabitSettings
import com.callbackdev.thabit.di.ServiceLocator
import com.callbackdev.thabit.ui.theme.ThemeProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    private val versionName: String = BuildConfig.VERSION_NAME
) : ViewModel() {

    private val interaction = MutableStateFlow(SettingsInteraction())
    private var transientToken = 0

    val state: StateFlow<SettingsUiState> = combine(
        settings.settings,
        interaction
    ) { config, ui ->
        SettingsUiState(
            document = SettingsDocument.of(
                dayEnds = config.dayEnds,
                weekStartsOn = config.weekStartsOn,
                theme = config.theme,
                showLineNumbers = config.showLineNumbers,
                wordWrap = config.wordWrap,
                lastModified = config.lastModified,
                versionName = versionName
            ),
            interaction = ui
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

    private suspend fun current(): ThabitSettings = settings.settings.first()

    /** Export lands in Fase 11; until then the command answers instead of lying. */
    fun onExport(format: String) = say("$ thabit export --$format  ${SettingsDocument.EXPORT_PENDING}")

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

    /** A transient comment at the foot of the file — the series' answer to a toast. */
    private fun say(message: String) {
        val token = ++transientToken
        interaction.update { it.copy(transient = message) }
        viewModelScope.launch {
            delay(TRANSIENT_MILLIS)
            if (token == transientToken) interaction.update { it.copy(transient = null) }
        }
    }

    companion object {
        private const val TRANSIENT_MILLIS = 4_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                SettingsViewModel(ServiceLocator.settings(app))
            }
        }

        /** For tests and previews. */
        fun factory(settings: SettingsStore, versionName: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                    SettingsViewModel(settings, versionName) as T
            }
    }
}

data class SettingsUiState(
    val document: SettingsDocument? = null,
    val interaction: SettingsInteraction = SettingsInteraction()
)

data class SettingsInteraction(
    val restoreConfirm: Boolean = false,
    val transient: String? = null
)
