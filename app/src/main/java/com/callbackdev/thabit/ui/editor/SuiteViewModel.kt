package com.callbackdev.thabit.ui.editor

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.callbackdev.thabit.data.HabitRepository
import com.callbackdev.thabit.data.SettingsStore
import com.callbackdev.thabit.data.WriteOutcome
import com.callbackdev.thabit.di.ServiceLocator
import com.callbackdev.thabit.domain.TestState
import com.callbackdev.thabit.domain.model.HabitType
import com.callbackdev.thabit.ui.format.CodeFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/**
 * The state of `habits.test` on screen, and every gesture it answers to.
 *
 * The document itself is derived, never held: the repository's rows come in, the
 * pure [SuiteDocument] builder turns them into a file, and a tap writes a row
 * and lets the file come back different. There is no local copy of the suite to
 * drift out of sync with the database — the same reason the domain refuses to
 * persist a verdict (VISION §6.8), applied one layer up.
 */
class SuiteViewModel(
    private val repository: HabitRepository,
    private val settings: SettingsStore,
    private val clock: Clock
) : ViewModel() {

    private val interaction = MutableStateFlow(SuiteInteraction())
    private var transientToken = 0

    val state: StateFlow<SuiteUiState> = combine(
        settings.settings,
        repository.observeFullHistory(),
        interaction
    ) { config, history, ui ->
        val logical = config.boundary.logicalDate(clock.instant(), clock.zone)
        SuiteUiState(
            document = SuiteDocument.of(
                history = history,
                logicalDate = logical,
                wallDate = LocalDate.now(clock),
                dayEnds = config.dayEnds
            ),
            interaction = ui,
            loading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SuiteUiState())

    private fun today(): LocalDate = state.value.document?.logicalDate ?: LocalDate.now(clock)

    // ---- the checkbox: the day's one frequent gesture ---------------------

    /**
     * Runs the test, or takes it back.
     *
     * Today is the working tree and it is *supposed* to change (VISION §4.1), so
     * every one of these is reversible by the same tap that made it. A counter is
     * the exception and asks for its number: a value cannot be guessed from a tap.
     */
    fun onCheckbox(row: TestRow) {
        when (row.type) {
            HabitType.COUNTER -> openValuePrompt(row)
            HabitType.AVOID -> write {
                if (row.state == TestState.FAIL) repository.clear(row.habitId, today())
                else repository.fail(row.habitId, today())
            }
            HabitType.BOOLEAN -> write {
                if (row.state == TestState.PASS) repository.clear(row.habitId, today())
                else repository.pass(row.habitId, today())
            }
        }
    }

    /** `[+1]`: the repository owns the step, so the row only has to say "one more". */
    fun onIncrement(row: TestRow) {
        if (row.incrementStep == null) return
        write { repository.increment(row.habitId, today()) }
    }

    fun onDetails(row: TestRow) = interaction.update {
        it.copy(expandedId = if (it.expandedId == row.habitId) null else row.habitId, archiveConfirmId = null)
    }

    fun onToggleNotDue() = interaction.update { it.copy(notDueExpanded = !it.notDueExpanded) }

    // ---- the expansion's text controls ------------------------------------

    fun onSkip(row: TestRow) = interaction.update {
        it.copy(prompt = SuitePrompt.Skip(row.habitId, "", SkipWindow.Today), archiveConfirmId = null)
    }

    fun onNote(row: TestRow) = interaction.update {
        it.copy(prompt = SuitePrompt.Note(row.habitId, ""), archiveConfirmId = null)
    }

    /** The wizard is Fase 5; until then the file says so instead of doing nothing. */
    fun onEdit(@Suppress("UNUSED_PARAMETER") row: TestRow) = say(COMING_SOON_EDIT)

    fun onAddTest() = say(COMING_SOON_ADD)

    /**
     * `[rm]` is two taps, and the second one is a `$` command spelled out in the
     * file — the series' shape for anything destructive. It archives; nothing in
     * this app deletes a test, because the days it already ran belong to the user.
     */
    fun onArchive(row: TestRow) {
        if (interaction.value.archiveConfirmId != row.habitId) {
            interaction.update { it.copy(archiveConfirmId = row.habitId, prompt = null) }
            return
        }
        interaction.update { it.copy(archiveConfirmId = null, expandedId = null) }
        viewModelScope.launch { repository.archiveHabit(row.habitId) }
    }

    fun onCancelArchive() = interaction.update { it.copy(archiveConfirmId = null) }

    // ---- prompts ----------------------------------------------------------

    private fun openValuePrompt(row: TestRow) {
        val current = (row.detail as? RowDetail.Counter)?.value?.takeIf { it > 0 }
        interaction.update {
            it.copy(
                prompt = SuitePrompt.Value(
                    habitId = row.habitId,
                    unit = (row.detail as? RowDetail.Counter)?.unit.orEmpty(),
                    text = current?.let { value -> CodeFormat.number(value) }.orEmpty()
                ),
                archiveConfirmId = null
            )
        }
    }

    fun onPromptChange(text: String) = interaction.update { ui ->
        ui.copy(
            prompt = when (val prompt = ui.prompt) {
                is SuitePrompt.Value -> prompt.copy(text = text)
                is SuitePrompt.Skip -> prompt.copy(note = text)
                is SuitePrompt.Note -> prompt.copy(text = text)
                null -> null
            }
        )
    }

    fun onCycleSkipWindow() = interaction.update { ui ->
        val prompt = ui.prompt as? SuitePrompt.Skip ?: return@update ui
        ui.copy(prompt = prompt.copy(window = prompt.window.next()))
    }

    fun onCancelPrompt() = interaction.update { it.copy(prompt = null) }

    fun onSubmitPrompt() {
        val prompt = interaction.value.prompt ?: return
        val date = today()
        interaction.update { it.copy(prompt = null) }
        when (prompt) {
            is SuitePrompt.Value -> {
                val value = prompt.text.trim().replace(',', '.').toDoubleOrNull()
                write {
                    // An empty (or unreadable) answer clears the row rather than
                    // storing a zero: "I did not enter a number" is not "I did none".
                    if (value == null || value <= 0.0) repository.clear(prompt.habitId, date)
                    else repository.record(prompt.habitId, date, value)
                }
            }
            is SuitePrompt.Skip -> write {
                repository.skip(
                    habitId = prompt.habitId,
                    date = date,
                    note = prompt.note.trim().ifBlank { null },
                    until = prompt.window.until(date)
                )
            }
            is SuitePrompt.Note -> write {
                repository.fail(prompt.habitId, date, prompt.text.trim().ifBlank { null })
            }
        }
    }

    // ---- plumbing ---------------------------------------------------------

    private fun write(block: suspend () -> WriteOutcome) {
        viewModelScope.launch {
            when (block()) {
                WriteOutcome.WRITTEN -> Unit
                WriteOutcome.READ_ONLY_DAY -> say(READ_ONLY)
                WriteOutcome.UNKNOWN_TEST -> say(UNKNOWN_TEST)
            }
        }
    }

    /** A transient comment at the end of the file — the series' answer to a toast. */
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

        // Terminal output: English, like every other comment in the file.
        const val COMING_SOON_ADD = "$ thabit add — the wizard lands in the next phase"
        const val COMING_SOON_EDIT = "$ thabit edit — the wizard lands in the next phase"
        const val READ_ONLY = "ERROR: that day is history — only today and yesterday are writable"
        const val UNKNOWN_TEST = "ERROR: that test is not in today's suite"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                val graph = ServiceLocator.graph(app)
                SuiteViewModel(graph.repository, graph.settings, graph.clock)
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
                SuiteViewModel(repository, settings, clock) as T
        }
    }
}

/** Everything the screen draws, in one value. */
data class SuiteUiState(
    val document: SuiteDocument? = null,
    val interaction: SuiteInteraction = SuiteInteraction(),
    val loading: Boolean = true
)

/** What the reader has opened, typed or is about to confirm. */
data class SuiteInteraction(
    val expandedId: Long? = null,
    val notDueExpanded: Boolean = false,
    val archiveConfirmId: Long? = null,
    val prompt: SuitePrompt? = null,
    val transient: String? = null
)

/** An in-place terminal prompt, opened inside the file rather than over it. */
sealed interface SuitePrompt {
    val habitId: Long

    /** `> pages: _` — a counter's value. */
    data class Value(override val habitId: Long, val unit: String, val text: String) : SuitePrompt

    /** `> skip: _` plus the window token. */
    data class Skip(
        override val habitId: Long,
        val note: String,
        val window: SkipWindow
    ) : SuitePrompt

    /** `> note: _` — why an avoid test broke. Optional, always. */
    data class Note(override val habitId: Long, val text: String) : SuitePrompt
}

/**
 * How long a skip lasts, as a cycling token.
 *
 * The week away is the category's most requested scenario after `day_ends`
 * (VISION §6.10), and this is the cheap half of the answer: one interaction
 * instead of one skip per test per day. The full answer is the `vacation`
 * branch, deferred.
 */
enum class SkipWindow(val token: String, private val extraDays: Long) {
    Today("[today]", 0),
    ThreeDays("[3d]", 2),
    OneWeek("[1w]", 6),
    TwoWeeks("[2w]", 13);

    fun next(): SkipWindow = entries[(ordinal + 1) % entries.size]

    /** The last logical day the skip covers, or null when it is only today. */
    fun until(from: LocalDate): LocalDate? =
        if (extraDays == 0L) null else from.plusDays(extraDays)
}
