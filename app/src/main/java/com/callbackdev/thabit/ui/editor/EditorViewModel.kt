package com.callbackdev.thabit.ui.editor

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.callbackdev.thabit.data.EditorFile
import com.callbackdev.thabit.data.HabitRepository
import com.callbackdev.thabit.data.SettingsStore
import com.callbackdev.thabit.data.WorkspaceStore
import com.callbackdev.thabit.di.ServiceLocator
import com.callbackdev.thabit.domain.SuiteHistory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The editor tab itself: which of its two files is open, and what `README.md`
 * needs to say.
 *
 * `habits.test` keeps its own [SuiteViewModel] — it has a whole file's worth of
 * gestures and no business knowing about tabs. This holds the shell's state and
 * the README's facts, which are the raw history rather than a built document:
 * the README's words come from resources (it is the one fully localized file),
 * so the prose is composed on the Compose side where a `Resources` exists.
 */
class EditorViewModel(
    private val repository: HabitRepository,
    private val settings: SettingsStore,
    private val workspace: WorkspaceStore,
    private val clock: Clock
) : ViewModel() {

    private val redraw = MutableStateFlow(0)

    val state: StateFlow<EditorUiState> = combine(
        workspace.editorFile,
        settings.settings,
        repository.observeFullHistory(),
        redraw
    ) { file, config, history, _ ->
        EditorUiState(
            file = file,
            history = history,
            today = config.boundary.logicalDate(clock.instant(), clock.zone),
            weekStartsOn = config.weekStartsOn,
            loading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditorUiState())

    /** Same reason as the other screens': the passing of time is not a data change. */
    fun onResumed() = redraw.update { it + 1 }

    /**
     * Switching file is written down, so the tab survives a restart the way an
     * editor reopens yesterday's tab — session state, not a setting.
     */
    fun onSelectFile(file: EditorFile) {
        viewModelScope.launch { workspace.setEditorFile(file) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val graph = ServiceLocator.graph(this[APPLICATION_KEY] as Application)
                EditorViewModel(graph.repository, graph.settings, graph.workspace, graph.clock)
            }
        }

        /** For tests and previews. */
        fun factory(
            repository: HabitRepository,
            settings: SettingsStore,
            workspace: WorkspaceStore,
            clock: Clock
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                EditorViewModel(repository, settings, workspace, clock) as T
        }
    }
}

data class EditorUiState(
    val file: EditorFile = EditorFile.TEST,
    val history: SuiteHistory = SuiteHistory.Empty,
    /**
     * A placeholder that is never drawn: nothing renders while [loading].
     *
     * `LocalDate.MIN` and not `EPOCH`, which arrived in the Android SDK only at
     * API 34 — lint caught it against this app's minSdk 33, which is exactly why
     * the CI runs lint before it builds anything.
     */
    val today: LocalDate = LocalDate.MIN,
    val weekStartsOn: DayOfWeek = DayOfWeek.MONDAY,
    val loading: Boolean = true
)
