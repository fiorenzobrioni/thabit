package com.callbackdev.thabit.ui.stats

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.callbackdev.thabit.data.HabitRepository
import com.callbackdev.thabit.data.SettingsStore
import com.callbackdev.thabit.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock

/**
 * `stats.md` on screen.
 *
 * A read and nothing else: this file has no gestures except the tag rows, which
 * navigate rather than write. Every number is derived on read from the same
 * check rows the suite writes (VISION §6.8), so there is nothing here to keep in
 * sync and nothing to invalidate.
 */
class StatsViewModel(
    private val repository: HabitRepository,
    private val settings: SettingsStore,
    private val clock: Clock
) : ViewModel() {

    private val redraw = MutableStateFlow(0)

    val state: StateFlow<StatsUiState> = combine(
        settings.settings,
        repository.observeFullHistory(),
        redraw
    ) { config, history, _ ->
        StatsUiState(
            document = StatsDocument.of(
                history = history,
                today = config.boundary.logicalDate(clock.instant(), clock.zone),
                weekStartsOn = config.weekStartsOn
            ),
            loading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    /** Same reason as every other screen's: the passing of time is not a data change. */
    fun onResumed() = redraw.update { it + 1 }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val graph = ServiceLocator.graph(this[APPLICATION_KEY] as Application)
                StatsViewModel(graph.repository, graph.settings, graph.clock)
            }
        }

        /** For tests and previews. */
        fun factory(
            repository: HabitRepository,
            settings: SettingsStore,
            clock: Clock
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                StatsViewModel(repository, settings, clock) as T
        }
    }
}

data class StatsUiState(
    val document: StatsDocument? = null,
    val loading: Boolean = true
)
