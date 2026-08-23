package com.callbackdev.thabit.ui.wizard

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.callbackdev.thabit.data.HabitRepository
import com.callbackdev.thabit.di.AppGraph
import com.callbackdev.thabit.di.ServiceLocator
import com.callbackdev.thabit.domain.model.HabitType
import com.callbackdev.thabit.ui.format.CodeFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek

/**
 * `$ thabit add` — the transcript, and the one place a test is born.
 *
 * The conversation is a value ([WizardDraft]); this holds it, answers taps, and
 * writes exactly once, when the reader says the test is finished.
 */
class WizardViewModel(
    private val repository: HabitRepository,
    /** The test to reopen prefilled, or null for a new one. */
    private val editingId: Long? = null,
    /**
     * Where the two writes run.
     *
     * Not `viewModelScope`: `[done]` and `[esc]` can be milliseconds apart, and
     * a scope that dies with the destination would cancel the insert on the way
     * out and lose the test the reader just described (see [AppGraph.appScope]).
     */
    private val writeScope: CoroutineScope
) : ViewModel() {

    private val _state = MutableStateFlow(WizardUiState(loading = editingId != null))
    val state: StateFlow<WizardUiState> = _state.asStateFlow()

    init {
        if (editingId != null) {
            viewModelScope.launch {
                val habit = repository.habit(editingId)
                _state.update {
                    if (habit == null) {
                        // The test was archived from another surface while the
                        // transcript was opening: say so rather than opening an
                        // editor onto nothing.
                        it.copy(loading = false, error = MISSING_TEST, closeRequested = true)
                    } else {
                        val loaded = WizardDraft.of(habit)
                        // The baseline is what `[esc]` compares against: leaving
                        // an edit nobody changed asks nothing.
                        it.copy(
                            draft = loaded,
                            baseline = loaded,
                            loading = false,
                            focus = null
                        )
                    }
                }
            }
        }
    }

    // ---- the transcript's answers ----------------------------------------

    /**
     * The name is bound live rather than buffered: `[done]` appears the moment
     * there is something to call the test, which is the whole point of a
     * transcript that can be finished after one answer.
     */
    fun onName(value: String) = _state.update {
        it.copy(draft = it.draft.withName(value), pending = value, error = null, discardConfirm = false)
    }

    fun onType(type: HabitType) = edit { it.withType(type) }

    fun onScheme(scheme: ScheduleScheme) = edit { it.withScheme(scheme) }

    fun onToggleWeekday(day: DayOfWeek) = edit { it.toggleWeekday(day) }

    fun onCycleQuota() = edit { it.cycleQuota() }

    fun onCycleInterval() = edit { it.cycleInterval() }

    fun onClearEmoji() = edit { it.withEmoji(null) }

    /** `[off]`: the reminder taken back, without going through the keyboard. */
    fun onClearRemind() = edit { it.withRemind(null) }

    /** `[more]`: the remaining prompts, for whoever wants them. */
    fun onMore() = _state.update { ui ->
        commit(ui).let { it.copy(draft = it.draft.expand(), focus = null, error = null) }
    }

    // ---- the prompts that need a keyboard --------------------------------

    fun onOpenPrompt(field: WizardField) = _state.update { ui ->
        ui.copy(
            focus = field,
            pending = when (field) {
                WizardField.Name -> ui.draft.name
                WizardField.Unit -> ui.draft.unit
                WizardField.Target -> CodeFormat.number(ui.draft.target)
                WizardField.Emoji -> ui.draft.emoji.orEmpty()
                WizardField.Remind -> ui.draft.remindAt?.let { CodeFormat.time(it) }.orEmpty()
            },
            error = null
        )
    }

    fun onPromptChange(text: String) =
        _state.update { it.copy(pending = text, discardConfirm = false) }

    fun onPromptSubmit() = _state.update { commit(it) }

    /**
     * Folds whatever is in the open prompt into the draft.
     *
     * Every other transition goes through this first, so typing a name and then
     * tapping `[more]`, `[boolean]` or `[done]` keeps the name. Losing typed
     * text to a focus rule nobody was told about is the kind of small betrayal
     * that makes an app feel untrustworthy for reasons the user cannot name.
     */
    private fun commit(ui: WizardUiState): WizardUiState {
        // Touching any other control disarms a pending `[esc]`, the same rule
        // the settings file's restore follows: a confirm armed a minute ago must
        // not go off under an innocent tap.
        val focus = ui.focus ?: return ui.copy(error = null, discardConfirm = false)
        val closed = ui.copy(focus = null, pending = "", error = null, discardConfirm = false)
        val draft = when (focus) {
            WizardField.Name -> ui.draft.withName(ui.pending)
            WizardField.Unit -> ui.draft.withUnit(ui.pending)
            WizardField.Target -> ui.draft.withTarget(ui.pending)
            WizardField.Emoji -> ui.draft.withEmoji(ui.pending)
            WizardField.Remind -> {
                val text = ui.pending.trim()
                when {
                    // An empty answer is the way out of a prompt this app opens
                    // on values that are allowed not to exist — the same rule the
                    // emoji prompt follows.
                    text.isEmpty() -> ui.draft.withRemind(null)
                    else -> WizardDraft.parseTime(text)
                        ?.let { ui.draft.withRemind(it) }
                        // Unreadable: the draft is left exactly as it was and the
                        // transcript says why. Storing a guess would put a time in
                        // the file that nobody typed.
                        ?: return closed.copy(error = BAD_TIME)
                }
            }
        }
        return closed.copy(draft = draft)
    }

    fun onPromptCancel() =
        _state.update { it.copy(focus = null, pending = "", error = null, discardConfirm = false) }

    // ---- closing the session ---------------------------------------------

    /**
     * `[done]`: writes the test, once.
     *
     * A pending prompt is committed first, so a reader who typed a name and went
     * straight for `[done]` does not lose it to a focus rule they never saw.
     */
    fun onDone() {
        _state.update { commit(it) }
        val draft = _state.value.draft
        draft.validationError()?.let { message ->
            _state.update { it.copy(error = message) }
            return
        }
        writeScope.launch {
            if (draft.editing != null) {
                val existing = repository.habit(draft.editing)
                if (existing == null) {
                    _state.update { it.copy(error = MISSING_TEST, closeRequested = true) }
                    return@launch
                }
                repository.updateHabit(draft.applyTo(existing))
                _state.update { it.copy(closeRequested = true) }
            } else {
                repository.insertHabit(
                    draft.toHabit(repository.today(), repository.nextPosition())
                )
                // The session stays open on purpose: "three habits in under a
                // minute" (VISION §9) is a promise about the second and third
                // one, and closing the transcript would make each of them a
                // fresh trip through the file and the FAB.
                _state.update {
                    WizardUiState(added = it.added + draft.name.trim(), focus = null)
                }
            }
        }
    }

    /** `[+ another]`: a clean transcript, with the confirmations kept above it. */
    fun onAddAnother() = _state.update {
        WizardUiState(added = it.added, focus = WizardField.Name)
    }

    /**
     * `[esc]` — and the one place this app asks twice for something that is not
     * destroying data.
     *
     * A draft is a conversation, not a record, so confirming the abandonment of
     * every unfinished sentence would be pure friction: an empty transcript, or
     * an edit nobody changed, closes on the first tap. What makes the second tap
     * worth asking for is **where the token sits** — `[esc]` is eight points
     * from `[done]` on the same row, and a thumb that misses loses everything
     * that was typed with nothing to undo it.
     *
     * So the question is not "are you sure" but "is there anything to lose":
     * the draft is compared with the point it started from.
     */
    fun onCancel() = _state.update { ui ->
        val armed = ui.discardConfirm
        val committed = commit(ui)
        when {
            committed.draft == committed.baseline -> committed.copy(closeRequested = true)
            armed -> committed.copy(closeRequested = true)
            else -> committed.copy(discardConfirm = true)
        }
    }

    private fun edit(block: (WizardDraft) -> WizardDraft) =
        _state.update { ui -> commit(ui).let { it.copy(draft = block(it.draft)) } }

    companion object {
        const val MISSING_TEST = "ERROR: that test is no longer in the suite"

        const val BAD_TIME = "ERROR: a reminder is a time of day — 07:00"

        fun factory(
            repository: HabitRepository,
            editingId: Long?,
            writeScope: CoroutineScope
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                WizardViewModel(repository, editingId, writeScope) as T
        }

        fun appFactory(editingId: Long?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val graph = ServiceLocator.graph(this[APPLICATION_KEY] as Application)
                WizardViewModel(graph.repository, editingId, graph.appScope)
            }
        }
    }
}

/** Which prompt is open — the ones that need a keyboard rather than a tap. */
enum class WizardField { Name, Unit, Target, Emoji, Remind }

data class WizardUiState(
    val draft: WizardDraft = WizardDraft(),
    /**
     * The draft as the session found it — empty for a new test, the stored test
     * for an `[edit]`. Only there to answer one question: has anything been said
     * that leaving would throw away?
     */
    val baseline: WizardDraft = WizardDraft(),
    /** True once `[esc]` has asked, and only while nothing else has been tapped. */
    val discardConfirm: Boolean = false,
    /** The open prompt, or null when the transcript is waiting for a tap. */
    val focus: WizardField? = WizardField.Name,
    val pending: String = "",
    /** Compiler-style refusal, in the file's own English. */
    val error: String? = null,
    /** Names added in this session, oldest first — the transcript's receipts. */
    val added: List<String> = emptyList(),
    val closeRequested: Boolean = false,
    val loading: Boolean = false
) {
    val isCounter: Boolean get() = draft.type == HabitType.COUNTER
}
