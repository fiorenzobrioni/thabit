package com.callbackdev.thabit.ui.wizard

import com.callbackdev.thabit.R
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.thabit.data.HabitRepository
import com.callbackdev.thabit.data.SettingsStore
import com.callbackdev.thabit.data.db.ThabitDatabase
import com.callbackdev.thabit.domain.model.HabitType
import com.callbackdev.thabit.domain.model.Schedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * `$ thabit add`, end to end, against a real database.
 *
 * One scheduler drives the view model, Room and DataStore, so every assertion
 * follows its tap deterministically (the pattern the Fase 4 flake hunt settled).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WizardViewModelTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val clock: Clock =
        Clock.fixed(Instant.parse("2026-08-21T09:00:00Z"), ZoneId.of("Europe/Rome"))
    private val today = LocalDate.of(2026, 8, 21)

    private val dispatcher = StandardTestDispatcher()
    private val dataStoreScope = CoroutineScope(dispatcher + SupervisorJob())

    /** Stands in for [com.callbackdev.thabit.di.AppGraph.appScope]. */
    private val writeScope = CoroutineScope(dispatcher + SupervisorJob())

    private lateinit var db: ThabitDatabase
    private lateinit var repository: HabitRepository
    private lateinit var store: ViewModelStore

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = ThabitDatabase.inMemory(
            ApplicationProvider.getApplicationContext(),
            executor = dispatcher.asExecutor()
        )
        val settings = SettingsStore(
            PreferenceDataStoreFactory.create(scope = dataStoreScope) {
                folder.newFile("settings.preferences_pb")
            }
        )
        repository = HabitRepository(db.habitDao(), db.checkDao(), db.dayDao(), settings, clock)
        store = ViewModelStore()
    }

    @After
    fun tearDown() {
        store.clear()
        writeScope.cancel()
        dataStoreScope.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    private fun wizard(editingId: Long? = null): WizardViewModel =
        ViewModelProvider(store, WizardViewModel.factory(repository, editingId, writeScope))
            .get(WizardViewModel::class.java)

    private fun runUi(body: suspend TestScope.(WizardViewModel) -> Unit) = runTest(dispatcher) {
        body(wizard())
    }

    private fun TestScope.state(viewModel: WizardViewModel): WizardUiState {
        runCurrent()
        return viewModel.state.value
    }

    private suspend fun suite() = repository.fullHistory().habits

    // ---- the one-answer path ---------------------------------------------

    @Test
    fun `a name and one tap is a whole test`() = runUi { wizard ->
        wizard.onName("meditate 10 min")
        wizard.onDone()
        runCurrent()

        val habit = suite().single()
        assertEquals("meditate 10 min", habit.name)
        assertEquals(HabitType.BOOLEAN, habit.type)
        assertEquals(Schedule.Daily, habit.schedule)
        assertEquals(today, habit.createdAt)
    }

    @Test
    fun `a test with no name is refused, in the terminal's own words`() = runUi { wizard ->
        wizard.onDone()
        assertEquals(R.string.wiz_err_name, state(wizard).error)
        assertTrue(suite().isEmpty())
    }

    @Test
    fun `the session stays open so the second test costs one answer too`() = runUi { wizard ->
        wizard.onName("meditate 10 min")
        wizard.onDone()
        runCurrent()

        // "Three habits in under a minute" is a promise about the second and
        // third one (VISION §9): the transcript keeps its receipts and waits.
        val after = state(wizard)
        assertEquals(listOf("meditate 10 min"), after.added)
        assertFalse(after.closeRequested)
        assertEquals("", after.draft.name)

        wizard.onAddAnother()
        wizard.onName("journal")
        wizard.onDone()
        runCurrent()
        assertEquals(listOf("meditate 10 min", "journal"), suite().map { it.name })
        assertEquals(listOf(0, 1), suite().map { it.position }) // new tests go last
    }

    // ---- the remaining prompts -------------------------------------------

    @Test
    fun `typing a name and reaching for another control never loses the name`() = runUi { wizard ->
        wizard.onName("read")
        wizard.onMore()
        assertEquals("read", state(wizard).draft.name)
        assertTrue(state(wizard).draft.expanded)
    }

    @Test
    fun `a counter is three taps and a number`() = runUi { wizard ->
        wizard.onName("read")
        wizard.onMore()
        wizard.onType(HabitType.COUNTER)
        wizard.onOpenPrompt(WizardField.Unit)
        wizard.onPromptChange("pages")
        wizard.onPromptSubmit()
        wizard.onOpenPrompt(WizardField.Target)
        wizard.onPromptChange("20")
        wizard.onPromptSubmit()
        wizard.onDone()
        runCurrent()

        val habit = suite().single()
        assertEquals(HabitType.COUNTER, habit.type)
        assertEquals("pages", habit.assert!!.unit)
        assertEquals(20.0, habit.assert!!.target, 0.0)
    }

    @Test
    fun `an avoid test asserts an absence and counts nothing`() = runUi { wizard ->
        wizard.onName("no sugar")
        wizard.onMore()
        wizard.onType(HabitType.AVOID)
        wizard.onDone()
        runCurrent()

        val habit = suite().single()
        assertEquals(HabitType.AVOID, habit.type)
        assertNull(habit.assert)
    }

    @Test
    fun `a weekday schedule is built by tapping days`() = runUi { wizard ->
        wizard.onName("deep work")
        wizard.onMore()
        wizard.onScheme(ScheduleScheme.Weekdays)
        wizard.onToggleWeekday(DayOfWeek.TUESDAY)
        wizard.onToggleWeekday(DayOfWeek.THURSDAY)
        wizard.onDone()
        runCurrent()

        assertEquals(
            Schedule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
            suite().single().schedule
        )
    }

    @Test
    fun `a quota is chosen and counted`() = runUi { wizard ->
        wizard.onName("run 5k")
        wizard.onMore()
        wizard.onScheme(ScheduleScheme.Quota)
        wizard.onCycleQuota() // 3 -> 4
        wizard.onDone()
        runCurrent()
        assertEquals(Schedule.Quota(4), suite().single().schedule)
    }

    @Test
    fun `an interval is chosen and counted`() = runUi { wizard ->
        wizard.onName("water the plants")
        wizard.onMore()
        wizard.onScheme(ScheduleScheme.Interval)
        wizard.onCycleInterval() // 2 -> 3
        wizard.onDone()
        runCurrent()
        assertEquals(Schedule.Interval(3), suite().single().schedule)
    }

    @Test
    fun `an emoji is optional both ways`() = runUi { wizard ->
        wizard.onName("read")
        wizard.onMore()
        wizard.onOpenPrompt(WizardField.Emoji)
        wizard.onPromptChange("📖")
        wizard.onPromptSubmit()
        assertEquals("📖", state(wizard).draft.emoji)
        wizard.onClearEmoji()
        wizard.onDone()
        runCurrent()
        assertNull(suite().single().emoji)
    }

    @Test
    fun `a prompt can be escaped without changing anything`() = runUi { wizard ->
        wizard.onName("read")
        wizard.onMore()
        wizard.onType(HabitType.COUNTER)
        wizard.onOpenPrompt(WizardField.Unit)
        wizard.onPromptChange("chapters")
        wizard.onPromptCancel()
        assertEquals(WizardDraft.DEFAULT_UNIT, state(wizard).draft.unit)
    }

    // ---- editing ----------------------------------------------------------

    @Test
    fun `an edit opens prefilled and saves in place`() = runTest(dispatcher) {
        val id = repository.addHabit("read", HabitType.COUNTER, com.callbackdev.thabit.domain.model.AssertSpec(20.0, "pages"))
        runCurrent()

        val wizard = wizard(editingId = id)
        val opened = state(wizard)
        assertEquals("read", opened.draft.name)
        assertEquals(HabitType.COUNTER, opened.draft.type)
        assertTrue(opened.draft.isEditing)

        wizard.onName("read 20 pages")
        wizard.onDone()
        runCurrent()

        val habit = suite().single()
        assertEquals("read 20 pages", habit.name)
        assertEquals(id, habit.id)
        assertEquals(today, habit.createdAt)
        assertTrue(state(wizard).closeRequested) // an edit closes, it does not loop
    }

    @Test
    fun `an edit of a test that is gone says so instead of opening onto nothing`() =
        runTest(dispatcher) {
            val wizard = wizard(editingId = 404L)
            val opened = state(wizard)
            assertEquals(WizardViewModel.MISSING_TEST, opened.error)
            assertTrue(opened.closeRequested)
        }

    // ---- the reminder ------------------------------------------------------

    @Test
    fun `a reminder is typed, kept, and written onto the test`() = runUi { wizard ->
        wizard.onName("meditate 10 min")
        wizard.onOpenPrompt(WizardField.Remind)
        wizard.onPromptChange("7")
        wizard.onPromptSubmit()
        assertEquals(LocalTime.of(7, 0), state(wizard).draft.remindAt)

        wizard.onDone()
        runCurrent()
        assertEquals(LocalTime.of(7, 0), suite().single().remindAt)
    }

    @Test
    fun `an empty answer is how a reminder is turned off`() = runUi { wizard ->
        wizard.onName("meditate 10 min")
        wizard.onOpenPrompt(WizardField.Remind)
        wizard.onPromptChange("07:00")
        wizard.onPromptSubmit()

        wizard.onOpenPrompt(WizardField.Remind)
        wizard.onPromptChange("")
        wizard.onPromptSubmit()
        assertNull(state(wizard).draft.remindAt)
    }

    @Test
    fun `an unreadable time is refused in the terminal's own voice, and changes nothing`() =
        runUi { wizard ->
            wizard.onName("meditate 10 min")
            wizard.onOpenPrompt(WizardField.Remind)
            wizard.onPromptChange("07:00")
            wizard.onPromptSubmit()

            wizard.onOpenPrompt(WizardField.Remind)
            wizard.onPromptChange("half seven")
            wizard.onPromptSubmit()

            val after = state(wizard)
            assertEquals(WizardViewModel.BAD_TIME, after.error)
            // The draft is untouched: a guess would put a time in the file that
            // nobody typed.
            assertEquals(LocalTime.of(7, 0), after.draft.remindAt)
        }

    @Test
    fun `the reminder prompt opens on the time that is already set`() = runUi { wizard ->
        wizard.onName("meditate 10 min")
        wizard.onOpenPrompt(WizardField.Remind)
        wizard.onPromptChange("21:30")
        wizard.onPromptSubmit()

        wizard.onOpenPrompt(WizardField.Remind)
        assertEquals("21:30", state(wizard).pending)
    }

    // ---- leaving -----------------------------------------------------------

    @Test
    fun `escaping writes nothing`() = runUi { wizard ->
        wizard.onName("meditate")
        wizard.onCancel()
        wizard.onCancel()
        runCurrent()
        assertTrue(state(wizard).closeRequested)
        assertTrue(suite().isEmpty())
    }

    @Test
    fun `an empty transcript closes on the first tap`() = runUi { wizard ->
        // Nothing has been said yet: asking would be friction, not care.
        wizard.onCancel()
        runCurrent()
        assertTrue(state(wizard).closeRequested)
    }

    @Test
    fun `escaping asks once when there is something to lose`() = runUi { wizard ->
        wizard.onName("meditate")

        wizard.onCancel()
        assertTrue(state(wizard).discardConfirm)
        assertFalse(state(wizard).closeRequested)

        wizard.onCancel()
        assertTrue(state(wizard).closeRequested)
    }

    @Test
    fun `touching anything else disarms a pending escape`() = runUi { wizard ->
        wizard.onName("meditate")
        wizard.onCancel()
        assertTrue(state(wizard).discardConfirm)

        // A confirm armed a minute ago must not go off under an innocent tap.
        wizard.onType(HabitType.COUNTER)
        assertFalse(state(wizard).discardConfirm)

        wizard.onCancel()
        assertFalse(state(wizard).closeRequested)
    }
}
