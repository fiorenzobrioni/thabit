package com.callbackdev.thabit.ui.log

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
import com.callbackdev.thabit.data.WriteOutcome
import com.callbackdev.thabit.di.ServiceLocator
import com.callbackdev.thabit.domain.TestState
import com.callbackdev.thabit.domain.model.HabitType
import com.callbackdev.thabit.ui.format.CodeFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/**
 * `habits_history.diff` on screen, and the one place the past can still be
 * touched.
 *
 * The log is a read of the same rows `habits.test` writes, so it holds no copy
 * of anything: a tap on yesterday writes a check and the whole file — that day's
 * verdict, its week's rate, the streak tags above it — comes back recomputed
 * (VISION §6.8). The amend window itself is not defended here but in the
 * repository, which refuses any date outside it whoever asks.
 */
class LogViewModel(
    private val repository: HabitRepository,
    private val settings: SettingsStore,
    private val clock: Clock
) : ViewModel() {

    private val interaction = MutableStateFlow(LogInteraction())
    private var transientToken = 0

    /** Same reason as the suite's: the passing of time is not a data change. */
    private val redraw = MutableStateFlow(0)

    val state: StateFlow<LogUiState> = combine(
        settings.settings,
        repository.observeFullHistory(),
        interaction,
        redraw
    ) { config, history, ui, _ ->
        LogUiState(
            document = LogDocument.of(
                history = history,
                logicalDate = config.boundary.logicalDate(clock.instant(), clock.zone),
                dayEnds = config.dayEnds
            ),
            interaction = ui,
            loading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogUiState())

    fun onResumed() = redraw.update { it + 1 }

    /** A commit collapses and expands; several can be open at once, like a diff. */
    fun onToggleCommit(date: LocalDate) = interaction.update { ui ->
        ui.copy(
            expanded = if (date in ui.expanded) ui.expanded - date else ui.expanded + date,
            prompt = ui.prompt?.takeIf { it.date != date }
        )
    }

    /**
     * `--amend`: the same tap as today, on the day before.
     *
     * The gesture is deliberately the row itself and not a control to recognize
     * (VISION §4.2): `--amend` is how this file *refers* to what happened, never
     * something the reader has to find.
     */
    fun onCheckbox(row: RowTest, date: LocalDate) {
        when (row.type) {
            HabitType.COUNTER -> interaction.update {
                it.copy(prompt = LogPrompt(row.habitId, date, row.unit.orEmpty(), ""))
            }
            HabitType.AVOID -> write(date) {
                if (row.state == TestState.FAIL) repository.clear(row.habitId, date)
                else repository.fail(row.habitId, date)
            }
            HabitType.BOOLEAN -> write(date) {
                if (row.state == TestState.PASS) repository.clear(row.habitId, date)
                else repository.pass(row.habitId, date)
            }
        }
    }

    fun onPromptChange(text: String) = interaction.update { ui ->
        ui.copy(prompt = ui.prompt?.copy(text = text))
    }

    fun onCancelPrompt() = interaction.update { it.copy(prompt = null) }

    fun onSubmitPrompt() {
        val prompt = interaction.value.prompt ?: return
        interaction.update { it.copy(prompt = null) }
        val value = prompt.text.trim().replace(',', '.').toDoubleOrNull()
        write(prompt.date) {
            // Same rule as the suite: an empty answer clears the row instead of
            // writing a zero, because "I did not enter a number" is not "none".
            if (value == null || value <= 0.0) repository.clear(prompt.habitId, prompt.date)
            else repository.record(prompt.habitId, prompt.date, value)
        }
    }

    /**
     * Every amend, against the day the **clock** says it is.
     *
     * The window can close while the file is open — it closes at `day_ends`, and
     * a log left on screen through it would go on offering yesterday's rows.
     * Rather than write into a day that is no longer amendable, the tap redraws
     * the file and says what happened; the repository would have refused anyway,
     * which is the belt to this braces.
     */
    private fun write(date: LocalDate, block: suspend () -> WriteOutcome) {
        viewModelScope.launch {
            val today = repository.today()
            if (state.value.document?.today != today) {
                redraw.update { it + 1 }
                say(rolledOver(today))
                return@launch
            }
            when (block()) {
                WriteOutcome.WRITTEN -> Unit
                WriteOutcome.READ_ONLY_DAY -> say(READ_ONLY)
                WriteOutcome.UNKNOWN_TEST -> say(UNKNOWN_TEST)
            }
        }
    }

    /** A transient comment printed under the commit it answers. */
    private fun say(message: String, date: LocalDate? = null) {
        val token = ++transientToken
        interaction.update { it.copy(transient = LogMessage(message, date)) }
        viewModelScope.launch {
            delay(TRANSIENT_MILLIS)
            if (token == transientToken) interaction.update { it.copy(transient = null) }
        }
    }

    companion object {
        private const val TRANSIENT_MILLIS = 4_000L

        // Terminal output: English, like every other comment in the file.
        const val READ_ONLY = "ERROR: that day is history — the amend window has closed"
        const val UNKNOWN_TEST = "ERROR: that test was not in the suite that day"

        fun rolledOver(date: LocalDate): String =
            "the day rolled over — this file is ${CodeFormat.date(date)} now"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val graph = ServiceLocator.graph(this[APPLICATION_KEY] as Application)
                LogViewModel(graph.repository, graph.settings, graph.clock)
            }
        }

        /** For tests and previews: the same view model with everything injected. */
        fun factory(
            repository: HabitRepository,
            settings: SettingsStore,
            clock: Clock
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                LogViewModel(repository, settings, clock) as T
        }
    }
}

data class LogUiState(
    val document: LogDocument? = null,
    val interaction: LogInteraction = LogInteraction(),
    val loading: Boolean = true
)

/** Which commits are open, what is being typed, and what the file last said. */
data class LogInteraction(
    val expanded: Set<LocalDate> = emptySet(),
    val prompt: LogPrompt? = null,
    val transient: LogMessage? = null
)

/** `> pages: _` inside a commit — a counter's value, amended. */
data class LogPrompt(
    val habitId: Long,
    val date: LocalDate,
    val unit: String,
    val text: String
)

/** One line of terminal output, and the commit it belongs under. */
data class LogMessage(val text: String, val date: LocalDate? = null)
