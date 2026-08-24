package com.callbackdev.thabit.ui.editor

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.thabit.data.HabitRepository
import com.callbackdev.thabit.data.SettingsStore
import com.callbackdev.thabit.data.db.ThabitDatabase
import com.callbackdev.thabit.domain.TestState
import com.callbackdev.thabit.domain.model.AssertSpec
import com.callbackdev.thabit.domain.model.HabitType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The gestures of `habits.test`, driven against a real database.
 *
 * Every assertion reads the **document** afterwards rather than the database:
 * what matters is that the file came back saying the truth, because the file is
 * what the user actually reads.
 *
 * **One scheduler drives everything** — the view model's coroutines, the state
 * flow, Room's query executor and invalidation tracker, and DataStore's scope —
 * so a tap followed by [runCurrent] is a complete, deterministic round trip and
 * nothing here waits on a wall clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SuiteViewModelTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val rome: ZoneId = ZoneId.of("Europe/Rome")
    /**
     * Settable, because the day ending under an open file is one of the things
     * worth asserting — `Clock.fixed` cannot express "and then it was tomorrow".
     */
    private var now: Instant = Instant.parse("2026-08-21T09:00:00Z")

    private val clock: Clock = object : Clock() {
        override fun getZone(): ZoneId = rome
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }

    private val dispatcher = StandardTestDispatcher()
    private val dataStoreScope = CoroutineScope(dispatcher + SupervisorJob())

    private lateinit var db: ThabitDatabase
    private lateinit var repository: HabitRepository
    private lateinit var settings: SettingsStore
    private lateinit var store: ViewModelStore
    private lateinit var viewModel: SuiteViewModel

    @Before
    fun setUp() {
        SuiteFocus.consume()
        Dispatchers.setMain(dispatcher)
        db = ThabitDatabase.inMemory(
            ApplicationProvider.getApplicationContext(),
            executor = dispatcher.asExecutor()
        )
        settings = SettingsStore(
            PreferenceDataStoreFactory.create(scope = dataStoreScope) {
                folder.newFile("settings.preferences_pb")
            }
        )
        repository = HabitRepository(db.habitDao(), db.checkDao(), db.dayDao(), settings, clock)
        store = ViewModelStore()
        viewModel = ViewModelProvider(store, SuiteViewModel.factory(repository, settings, clock))
            .get(SuiteViewModel::class.java)
    }

    @After
    fun tearDown() {
        // `SuiteFocus` is a process-wide channel: a request left behind by one
        // test is a request the next test's view model swallows.
        SuiteFocus.consume()
        store.clear()
        dataStoreScope.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    /** Runs a test with the file on screen — that is, with the state collected. */
    private fun runUi(body: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        backgroundScope.launch { viewModel.state.collect { } }
        runCurrent()
        body()
    }

    private fun TestScope.file(): SuiteDocument {
        runCurrent()
        return requireNotNull(viewModel.state.value.document) { "the document has not arrived" }
    }

    private fun TestScope.row(index: Int = 0): TestRow = file().due[index]

    private fun TestScope.interaction(): SuiteInteraction {
        runCurrent()
        return viewModel.state.value.interaction
    }

    private suspend fun TestScope.addHabit(
        name: String,
        type: HabitType = HabitType.BOOLEAN,
        assert: AssertSpec? = null
    ): TestRow {
        repository.addHabit(name, type, assert)
        return row()
    }

    // ---- the checkbox ----------------------------------------------------

    @Test
    fun `one tap runs a boolean test, and the same tap takes it back`() = runUi {
        val test = addHabit("meditate 10 min")

        viewModel.onCheckbox(test)
        assertEquals(TestState.PASS, row().state)
        assertEquals("11:00", row().comment) // 09:00 UTC is 11:00 in Rome
        assertEquals(1, file().passed)

        viewModel.onCheckbox(row())
        assertEquals(TestState.PENDING, row().state)
        assertNull(row().comment)
        assertEquals(0, file().passed)
    }

    @Test
    fun `an avoid test is broken by a tap and holds again by the next one`() = runUi {
        val test = addHabit("no sugar", HabitType.AVOID)
        assertEquals(TestState.HOLDING, test.state)

        viewModel.onCheckbox(test)
        assertEquals(TestState.FAIL, row().state)
        assertTrue(row().comment!!.startsWith("failed"))

        viewModel.onCheckbox(row())
        assertEquals(TestState.HOLDING, row().state)
    }

    // ---- counters --------------------------------------------------------

    @Test
    fun `a counter asks for its number instead of guessing one`() = runUi {
        val test = addHabit("read", HabitType.COUNTER, AssertSpec(20.0, "pages", 1.0))

        viewModel.onCheckbox(test)
        val prompt = interaction().prompt as SuitePrompt.Value
        assertEquals("pages", prompt.unit)
        assertEquals("", prompt.text)

        viewModel.onPromptChange("12")
        viewModel.onSubmitPrompt()
        assertEquals("12/20 pages", row().comment)
        assertEquals(TestState.PENDING, row().state)
        assertNull(interaction().prompt)
    }

    @Test
    fun `a counter passes when the assert holds`() = runUi {
        val test = addHabit("read", HabitType.COUNTER, AssertSpec(20.0, "pages", 1.0))

        viewModel.onCheckbox(test)
        viewModel.onPromptChange("23")
        viewModel.onSubmitPrompt()
        assertEquals(TestState.PASS, row().state)
        assertEquals("23 pages", row().comment)
    }

    @Test
    fun `an empty answer clears the row instead of writing a zero`() = runUi {
        val test = addHabit("read", HabitType.COUNTER, AssertSpec(20.0, "pages", 1.0))

        viewModel.onCheckbox(test)
        viewModel.onPromptChange("12")
        viewModel.onSubmitPrompt()
        assertEquals("12/20 pages", row().comment)

        // Reopening prefills what is already there: a correction, not a retype.
        viewModel.onCheckbox(row())
        assertEquals("12", (interaction().prompt as SuitePrompt.Value).text)

        viewModel.onPromptChange("")
        viewModel.onSubmitPrompt()
        assertEquals("0/20 pages", row().comment)
        assertEquals(TestState.PENDING, row().state)
        assertTrue(db.checkDao().all().isEmpty())
    }

    @Test
    fun `a counter takes its number back in one gesture`() = runUi {
        val test = addHabit("read", HabitType.COUNTER, AssertSpec(20.0, "pages", 1.0))

        viewModel.onCheckbox(test)
        viewModel.onPromptChange("12")
        viewModel.onSubmitPrompt()
        assertEquals("12/20 pages", row().comment)

        // A boolean is undone by tapping its box again. `[clear]` is the same
        // one gesture for a counter, instead of open-empty-confirm (Fase 12).
        viewModel.onCheckbox(row())
        assertTrue((interaction().prompt as SuitePrompt.Value).written)
        viewModel.onClearPrompt()

        assertEquals("0/20 pages", row().comment)
        assertEquals(TestState.PENDING, row().state)
        assertTrue(db.checkDao().all().isEmpty())
        assertNull(interaction().prompt)
    }

    @Test
    fun `a counter with nothing written has nothing to clear`() = runUi {
        val test = addHabit("read", HabitType.COUNTER, AssertSpec(20.0, "pages", 1.0))

        viewModel.onCheckbox(test)
        // The control is not offered at all, and the flag is what decides it:
        // an undo for a number nobody wrote would be the file making a promise
        // it cannot keep.
        assertFalse((interaction().prompt as SuitePrompt.Value).written)
    }

    @Test
    fun `a decimal comma is read the way the user typed it`() = runUi {
        val test = addHabit("water", HabitType.COUNTER, AssertSpec(2.0, "l", 0.5))

        viewModel.onCheckbox(test)
        viewModel.onPromptChange("1,5")
        viewModel.onSubmitPrompt()
        assertEquals("1.5/2 l", row().comment)
    }

    @Test
    fun `plus one adds a step and flips the box when the target is reached`() = runUi {
        val test = addHabit("water", HabitType.COUNTER, AssertSpec(3.0, "glasses", 1.0))
        assertEquals(1.0, test.incrementStep!!, 0.0)

        viewModel.onIncrement(row())
        assertEquals("1/3 glasses", row().comment)
        viewModel.onIncrement(row())
        assertEquals("2/3 glasses", row().comment)
        viewModel.onIncrement(row())
        assertEquals(TestState.PASS, row().state)
    }

    @Test
    fun `a prompt can be escaped without writing anything`() = runUi {
        val test = addHabit("read", HabitType.COUNTER, AssertSpec(20.0, "pages", 1.0))

        viewModel.onCheckbox(test)
        viewModel.onPromptChange("12")
        viewModel.onCancelPrompt()

        assertNull(interaction().prompt)
        assertTrue(db.checkDao().all().isEmpty())
    }

    // ---- skip ------------------------------------------------------------

    @Test
    fun `a skip carries its note and only today by default`() = runUi {
        val test = addHabit("run 5k")

        viewModel.onSkip(test.habitId)
        viewModel.onPromptChange("rest day")
        viewModel.onSubmitPrompt()

        assertEquals(TestState.SKIP, row().state)
        assertEquals("skip: rest day", row().comment)
    }

    @Test
    fun `a week away is one interaction, not one skip per day`() = runUi {
        val test = addHabit("run 5k")

        viewModel.onSkip(test.habitId)
        repeat(2) { viewModel.onCycleSkipWindow() } // today -> 3d -> 1w
        assertEquals(SkipWindow.OneWeek, (interaction().prompt as SuitePrompt.Skip).window)

        viewModel.onPromptChange("away")
        viewModel.onSubmitPrompt()
        assertEquals("skip: away until 2026-08-27", row().comment)
        // One row written; the six days it covers are expanded on read.
        assertEquals(1, db.checkDao().all().size)
    }

    @Test
    fun `the skip window cycles back round`() {
        assertEquals(SkipWindow.ThreeDays, SkipWindow.Today.next())
        assertEquals(SkipWindow.Today, SkipWindow.TwoWeeks.next())
        assertNull(SkipWindow.Today.until(clock.instant().atZone(rome).toLocalDate()))
    }

    @Test
    fun `unskip gives back the days the window had not reached yet`() = runUi {
        val test = addHabit("run 5k")

        viewModel.onSkip(test.habitId)
        repeat(2) { viewModel.onCycleSkipWindow() } // today -> 3d -> 1w
        viewModel.onPromptChange("away")
        viewModel.onSubmitPrompt()
        assertEquals(TestState.SKIP, row().state)

        viewModel.onUnskip(test.habitId)
        assertEquals(TestState.PENDING, row().state)
        // Tapped and taken back on the same day: nothing of it is left behind.
        assertEquals(0, db.checkDao().all().size)
    }

    @Test
    fun `a skipped counter still knows what it counts`() = runUi {
        val test = addHabit("read 20 pages", HabitType.COUNTER, AssertSpec(20.0, "pages"))

        viewModel.onSkip(test.habitId)
        viewModel.onSubmitPrompt()
        assertEquals(TestState.SKIP, row().state)

        // The unit belongs to the test, not to the day: the prompt of a skipped
        // counter used to open as the anonymous .
        viewModel.onCheckbox(row())
        assertEquals("pages", (interaction().prompt as SuitePrompt.Value).unit)
    }

    // ---- the day ending under an open file --------------------------------

    @Test
    fun `a tap after the day rolled over does not land on the day that ended`() = runUi {
        val test = addHabit("meditate 10 min")
        assertEquals(LocalDate.of(2026, 8, 21), file().logicalDate)

        // The file stays on screen; in Rome it is now half past two at night.
        now = Instant.parse("2026-08-22T00:30:00Z")
        viewModel.onCheckbox(test)

        // Nothing was written into the day that ended behind the reader's back,
        // and the file caught up by itself instead of waiting for the database.
        assertEquals(0, db.checkDao().all().size)
        assertEquals(LocalDate.of(2026, 8, 22), file().logicalDate)
        val message = interaction().transient!!
        assertEquals(SuiteNote.RolledOver(LocalDate.of(2026, 8, 22)), message.note)
        // Printed under the row that was tapped, where the thumb still is.
        assertEquals(test.habitId, message.habitId)

        // And the second tap is an ordinary one, on the day that is now open.
        viewModel.onCheckbox(row())
        assertEquals(TestState.PASS, row().state)
        assertEquals("2026-08-22", db.checkDao().all().single().date)
    }

    @Test
    fun `coming back to the front reads the clock again`() = runUi {
        addHabit("meditate 10 min")
        assertEquals(LocalDate.of(2026, 8, 21), file().logicalDate)

        now = Instant.parse("2026-08-22T00:30:00Z")
        viewModel.onResumed()

        assertEquals(LocalDate.of(2026, 8, 22), file().logicalDate)
    }

    // ---- the expansion ---------------------------------------------------

    @Test
    fun `the name unfolds the spec and folds it again`() = runUi {
        val test = addHabit("meditate 10 min")

        viewModel.onDetails(test.habitId)
        assertEquals(test.habitId, interaction().expandedId)
        viewModel.onDetails(test.habitId)
        assertNull(interaction().expandedId)
    }

    @Test
    fun `archiving takes two taps and keeps the history`() = runUi {
        val test = addHabit("meditate 10 min")

        viewModel.onArchive(test.habitId)
        assertEquals(test.habitId, interaction().archiveConfirmId)
        assertEquals(1, file().suiteSize)

        viewModel.onArchive(test.habitId)
        assertEquals(0, file().suiteSize)
        assertNull(interaction().archiveConfirmId)
        // Archived, never deleted: the days it already ran belong to the user.
        assertEquals(1, db.habitDao().all().size)
    }

    @Test
    fun `the first tap of an archive can be taken back`() = runUi {
        val test = addHabit("meditate 10 min")

        viewModel.onArchive(test.habitId)
        assertEquals(test.habitId, interaction().archiveConfirmId)
        viewModel.onCancelArchive()
        assertNull(interaction().archiveConfirmId)
        assertEquals(1, file().suiteSize)
    }

    @Test
    fun `the not-due block opens and closes`() = runUi {
        viewModel.onToggleNotDue()
        assertTrue(interaction().notDueExpanded)
        viewModel.onToggleNotDue()
        assertFalse(interaction().notDueExpanded)
    }

    @Test
    fun `the document arrives even on an empty suite`() = runUi {
        val document = file()
        assertTrue(document.isEmpty)
        assertTrue(document.due.isEmpty())
    }

    // ---- the jump a reminder asks for (Fase 9) ----------------------------

    @Test
    fun `a reminder tapped from the shade unfolds the test it was about`() = runUi {
        val id = repository.addHabit("meditate 10 min")
        file()

        SuiteFocus.request(id)
        runCurrent()
        assertEquals(id, interaction().expandedId)
        // Consumed, so the file does not reopen it on every redraw.
        assertNull(SuiteFocus.request.value)
        // And it never ticks the box for the reader: they were being asked, not
        // answered for.
        assertNull(db.checkDao().find(id, "2026-08-21"))
    }

    @Test
    fun `a counter opens its prompt, because that is why the shade could not settle it`() =
        runUi {
            val id = repository.addHabit(
                name = "read 20 pages",
                type = HabitType.COUNTER,
                assert = AssertSpec(20.0, "pages")
            )
            file()

            SuiteFocus.request(id)
            runCurrent()
            assertEquals("pages", (interaction().prompt as SuitePrompt.Value).unit)
        }

    /**
     * The crash this test exists for: opening the app **from** the widget.
     *
     * The request is already set when the view model is constructed — the
     * activity reads the intent before any view model exists — and the app's
     * real main dispatcher is `Dispatchers.Main.immediate`, which runs a
     * `launch` issued from a constructor already on the main thread
     * *synchronously*. With the `init` block declared above `state`, that
     * dereferenced a property Kotlin had not initialised yet and threw: the app
     * opened from a widget row and bounced straight back out.
     *
     * It runs on an **unconfined** dispatcher on purpose. The rest of this suite
     * uses `StandardTestDispatcher`, which *queues* the body instead of running
     * it — by the time it runs, the constructor has finished and the field is
     * there, so the bug is invisible. Reproducing a timing bug needs the timing.
     */
    @Test
    fun `a request already pending when the view model is built does not kill it`() =
        runTest(dispatcher) {
            // `Main.immediate`'s timing — and on *this* test's scheduler, so the
            // uncaught exception the bug produces fails this test instead of
            // surfacing in whichever one happens to run next.
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val freshStore = ViewModelStore()
            try {
                SuiteFocus.request(1L)
                // Constructing it is the whole test: this used to throw.
                ViewModelProvider(
                    freshStore,
                    SuiteViewModel.factory(repository, settings, clock)
                ).get(SuiteViewModel::class.java)
                runCurrent()
            } finally {
                freshStore.clear()
                Dispatchers.setMain(dispatcher)
            }
        }

    @Test
    fun `a request for a test the file does not know is dropped, not acted on`() = runUi {
        file()
        SuiteFocus.request(404L)
        runCurrent()
        assertNull(interaction().expandedId)
        assertNull(interaction().prompt)
        assertNull(SuiteFocus.request.value)
    }
}
