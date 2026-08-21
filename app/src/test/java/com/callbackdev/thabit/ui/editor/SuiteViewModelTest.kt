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
    private val clock: Clock = Clock.fixed(Instant.parse("2026-08-21T09:00:00Z"), rome)

    private val dispatcher = StandardTestDispatcher()
    private val dataStoreScope = CoroutineScope(dispatcher + SupervisorJob())

    private lateinit var db: ThabitDatabase
    private lateinit var repository: HabitRepository
    private lateinit var settings: SettingsStore
    private lateinit var store: ViewModelStore
    private lateinit var viewModel: SuiteViewModel

    @Before
    fun setUp() {
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

        viewModel.onSkip(test)
        viewModel.onPromptChange("rest day")
        viewModel.onSubmitPrompt()

        assertEquals(TestState.SKIP, row().state)
        assertEquals("skip: rest day", row().comment)
    }

    @Test
    fun `a week away is one interaction, not one skip per day`() = runUi {
        val test = addHabit("run 5k")

        viewModel.onSkip(test)
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

    // ---- the expansion ---------------------------------------------------

    @Test
    fun `the name unfolds the spec and folds it again`() = runUi {
        val test = addHabit("meditate 10 min")

        viewModel.onDetails(test)
        assertEquals(test.habitId, interaction().expandedId)
        viewModel.onDetails(test)
        assertNull(interaction().expandedId)
    }

    @Test
    fun `archiving takes two taps and keeps the history`() = runUi {
        val test = addHabit("meditate 10 min")

        viewModel.onArchive(test)
        assertEquals(test.habitId, interaction().archiveConfirmId)
        assertEquals(1, file().suiteSize)

        viewModel.onArchive(test)
        assertEquals(0, file().suiteSize)
        assertNull(interaction().archiveConfirmId)
        // Archived, never deleted: the days it already ran belong to the user.
        assertEquals(1, db.habitDao().all().size)
    }

    @Test
    fun `the first tap of an archive can be taken back`() = runUi {
        val test = addHabit("meditate 10 min")

        viewModel.onArchive(test)
        assertEquals(test.habitId, interaction().archiveConfirmId)
        viewModel.onCancelArchive()
        assertNull(interaction().archiveConfirmId)
        assertEquals(1, file().suiteSize)
    }

    // ---- what is not built yet -------------------------------------------

    @Test
    fun `the FAB says where the wizard is instead of doing nothing`() = runUi {
        viewModel.onAddTest()
        assertEquals(SuiteViewModel.COMING_SOON_ADD, interaction().transient)
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
}
