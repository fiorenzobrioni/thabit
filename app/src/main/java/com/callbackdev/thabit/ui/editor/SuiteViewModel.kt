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

    /**
     * Bumped to redraw the file against the clock as it is *now*.
     *
     * The other three sources only speak when data changes, and the passing of
     * time is not a data change: with the app left open across `day_ends`, or
     * asleep in the background, nothing would emit and the file would go on
     * showing a day that is over.
     */
    private val redraw = MutableStateFlow(0)

    val state: StateFlow<SuiteUiState> = combine(
        settings.settings,
        repository.observeFullHistory(),
        interaction,
        redraw
    ) { config, history, ui, _ ->
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

    /**
     * The screen came back to the front: read the clock again.
     *
     * A phone put down at half past eleven and picked up after midnight comes
     * back to a file that must already be the new day's — before any tap, and
     * without waiting for something in the database to move.
     */
    fun onResumed() = redraw.update { it + 1 }

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
            HabitType.AVOID -> write(row.habitId) { date ->
                if (row.state == TestState.FAIL) repository.clear(row.habitId, date)
                else repository.fail(row.habitId, date)
            }
            HabitType.BOOLEAN -> write(row.habitId) { date ->
                if (row.state == TestState.PASS) repository.clear(row.habitId, date)
                else repository.pass(row.habitId, date)
            }
        }
    }

    /** `[+1]`: the repository owns the step, so the row only has to say "one more". */
    fun onIncrement(row: TestRow) {
        if (row.incrementStep == null) return
        write(row.habitId) { date -> repository.increment(row.habitId, date) }
    }

    /**
     * Unfolds a test's spec, or folds it back.
     *
     * Takes an id and not a row on purpose: the same expansion serves the tests
     * due today **and** the ones commented out because today does not ask for
     * them, and those are two different shapes of the same suite.
     */
    fun onDetails(habitId: Long) = interaction.update {
        it.copy(expandedId = if (it.expandedId == habitId) null else habitId, archiveConfirmId = null)
    }

    fun onToggleNotDue() = interaction.update { it.copy(notDueExpanded = !it.notDueExpanded) }

    // ---- the expansion's text controls ------------------------------------

    fun onSkip(habitId: Long) = interaction.update {
        it.copy(prompt = SuitePrompt.Skip(habitId, "", SkipWindow.Today), archiveConfirmId = null)
    }

    /**
     * `[~ unskip]` — the skip taken back.
     *
     * It cancels **from today on** and never rewrites the days already covered:
     * a week away declared last Friday keeps every day it has already skipped,
     * and stops covering the days that have not happened yet. Coming back early
     * from a holiday is a decision about the future, not a correction of the
     * past (VISION §3.3.5).
     */
    fun onUnskip(habitId: Long) = write(habitId) { date -> repository.resumeSkip(habitId, date) }

    fun onNote(habitId: Long) = interaction.update {
        it.copy(prompt = SuitePrompt.Note(habitId, ""), archiveConfirmId = null)
    }

    /**
     * `[rm]` is two taps, and the second one is a `$` command spelled out in the
     * file — the series' shape for anything destructive. It archives; nothing in
     * this app deletes a test, because the days it already ran belong to the user.
     */
    fun onArchive(habitId: Long) {
        if (interaction.value.archiveConfirmId != habitId) {
            interaction.update { it.copy(archiveConfirmId = habitId, prompt = null) }
            return
        }
        interaction.update { it.copy(archiveConfirmId = null, expandedId = null) }
        viewModelScope.launch { repository.archiveHabit(habitId) }
    }

    fun onCancelArchive() = interaction.update { it.copy(archiveConfirmId = null) }

    // ---- prompts ----------------------------------------------------------

    private fun openValuePrompt(row: TestRow) {
        val current = (row.detail as? RowDetail.Counter)?.value?.takeIf { it > 0 }
        interaction.update {
            it.copy(
                prompt = SuitePrompt.Value(
                    habitId = row.habitId,
                    unit = row.unit.orEmpty(),
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
        interaction.update { it.copy(prompt = null) }
        when (prompt) {
            is SuitePrompt.Value -> {
                val value = prompt.text.trim().replace(',', '.').toDoubleOrNull()
                write(prompt.habitId) { date ->
                    // An empty (or unreadable) answer clears the row rather than
                    // storing a zero: "I did not enter a number" is not "I did none".
                    if (value == null || value <= 0.0) repository.clear(prompt.habitId, date)
                    else repository.record(prompt.habitId, date, value)
                }
            }
            is SuitePrompt.Skip -> write(prompt.habitId) { date ->
                repository.skip(
                    habitId = prompt.habitId,
                    date = date,
                    note = prompt.note.trim().ifBlank { null },
                    until = prompt.window.until(date)
                )
            }
            is SuitePrompt.Note -> write(prompt.habitId) { date ->
                repository.fail(prompt.habitId, date, prompt.text.trim().ifBlank { null })
            }
        }
    }

    // ---- plumbing ---------------------------------------------------------

    /**
     * Every write, against the day the **clock** says it is.
     *
     * The date used to come from the rendered document, which is only right for
     * as long as the document is fresh. An app left open across `day_ends` had a
     * stale one, so a tap landed on the day before — inside the amend window, so
     * it was written without complaint, on a day the user was not looking at.
     * [HabitRepository.today] reads the boundary and the clock, and is the only
     * answer allowed here.
     *
     * When the file on screen turns out to be showing another day, the tap does
     * **not** run: the row it came from describes a day that is over, so acting
     * on it would be acting on a state nobody can still see. The file redraws
     * and says what happened, and the second tap is an ordinary one.
     */
    private fun write(habitId: Long, block: suspend (LocalDate) -> WriteOutcome) {
        viewModelScope.launch {
            val date = repository.today()
            val shown = state.value.document?.logicalDate
            if (shown != null && shown != date) {
                redraw.update { it + 1 }
                say(rolledOver(date), habitId)
                return@launch
            }
            when (block(date)) {
                WriteOutcome.WRITTEN -> Unit
                WriteOutcome.READ_ONLY_DAY -> say(READ_ONLY, habitId)
                WriteOutcome.UNKNOWN_TEST -> say(UNKNOWN_TEST, habitId)
            }
        }
    }

    /**
     * A transient comment in the file — the series' answer to a toast.
     *
     * It is printed **under the row that produced it**, not at the foot of the
     * file, because that is where the reader is looking: a message is always the
     * answer to a tap, and the thumb that tapped is still on the line. At the
     * end of a suite longer than a screen the same words were technically there
     * and practically invisible. Only a message with no row to belong to — or
     * about a test today no longer asks for — falls back to the foot.
     */
    private fun say(message: String, habitId: Long? = null) {
        val token = ++transientToken
        interaction.update { it.copy(transient = SuiteMessage(message, habitId)) }
        viewModelScope.launch {
            delay(TRANSIENT_MILLIS)
            if (token == transientToken) interaction.update { it.copy(transient = null) }
        }
    }

    companion object {
        private const val TRANSIENT_MILLIS = 4_000L

        // Terminal output: English, like every other comment in the file.
        const val READ_ONLY = "ERROR: that day is history — only today and yesterday are writable"
        const val UNKNOWN_TEST = "ERROR: that test is not in today's suite"

        /**
         * Not an `ERROR:` — nothing went wrong, the day simply ended while the
         * file was open. It states the new date, because the whole problem was
         * a screen quietly showing the wrong one.
         */
        fun rolledOver(date: LocalDate): String =
            "the day rolled over — this file is ${CodeFormat.date(date)} now"

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

/**
 * One line of terminal output, and the row it is about.
 *
 * [habitId] is where it gets printed: under that test's line if the file still
 * has one for it, at the foot of the file otherwise.
 */
data class SuiteMessage(val text: String, val habitId: Long? = null)

/** What the reader has opened, typed or is about to confirm. */
data class SuiteInteraction(
    val expandedId: Long? = null,
    val notDueExpanded: Boolean = false,
    val archiveConfirmId: Long? = null,
    val prompt: SuitePrompt? = null,
    val transient: SuiteMessage? = null
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
