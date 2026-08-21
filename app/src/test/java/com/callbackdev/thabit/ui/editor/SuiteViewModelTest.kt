package com.callbackdev.thabit.ui.editor

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.thabit.data.HabitRepository
import com.callbackdev.thabit.data.SettingsStore
import com.callbackdev.thabit.data.db.ThabitDatabase
import com.callbackdev.thabit.domain.TestState
import com.callbackdev.thabit.domain.model.AssertSpec
import com.callbackdev.thabit.domain.model.HabitType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
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
 * The waiting is deliberately real rather than virtual. The state flow is fed by
 * Room and DataStore, both of which do genuine work on their own threads, and a
 * virtual clock would happily skip past them and assert on a document that had
 * not been rebuilt yet — a test that passes for the wrong reason today and lies
 * about a regression tomorrow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SuiteViewModelTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val rome: ZoneId = ZoneId.of("Europe/Rome")
    private val clock: Clock = Clock.fixed(Instant.parse("2026-08-21T09:00:00Z"), rome)

    private lateinit var db: ThabitDatabase
    private lateinit var repository: HabitRepository
    private lateinit var settings: SettingsStore
    private lateinit var viewModel: SuiteViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = ThabitDatabase.inMemory(ApplicationProvider.getApplicationContext())
        settings = SettingsStore(
            PreferenceDataStoreFactory.create { folder.newFile("settings.preferences_pb") }
        )
        repository = HabitRepository(db.habitDao(), db.checkDao(), db.dayDao(), settings, clock)
        viewModel = SuiteViewModel(repository, settings, clock)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    // ---- waiting on the file ---------------------------------------------

    private fun awaitState(predicate: (SuiteUiState) -> Boolean): SuiteUiState = runBlocking {
        withTimeout(TIMEOUT) { viewModel.state.first(predicate) }
    }

    private fun awaitDocument(predicate: (SuiteDocument) -> Boolean = { true }): SuiteDocument =
        awaitState { it.document?.let(predicate) == true }.document!!

    private fun awaitRow(predicate: (TestRow) -> Boolean = { true }): TestRow =
        awaitDocument { it.due.firstOrNull()?.let(predicate) == true }.due.first()

    private fun addHabit(
        name: String,
        type: HabitType = HabitType.BOOLEAN,
        assert: AssertSpec? = null
    ): TestRow {
        runBlocking { repository.addHabit(name, type, assert) }
        return awaitRow { it.name == name }
    }

    // ---- the checkbox ----------------------------------------------------

    @Test
    fun `one tap runs a boolean test, and the same tap takes it back`() {
        val row = addHabit("meditate 10 min")

        viewModel.onCheckbox(row)
        val passed = awaitRow { it.state == TestState.PASS }
        assertEquals("11:00", passed.comment) // 09:00 UTC is 11:00 in Rome
        assertEquals(1, awaitDocument { it.passed == 1 }.passed)

        viewModel.onCheckbox(passed)
        val undone = awaitRow { it.state == TestState.PENDING }
        assertNull(undone.comment)
    }

    @Test
    fun `an avoid test is broken by a tap and holds again by the next one`() {
        val row = addHabit("no sugar", HabitType.AVOID)
        assertEquals(TestState.HOLDING, row.state)

        viewModel.onCheckbox(row)
        val broken = awaitRow { it.state == TestState.FAIL }
        assertTrue(broken.comment!!.startsWith("failed"))

        viewModel.onCheckbox(broken)
        awaitRow { it.state == TestState.HOLDING }
    }

    // ---- counters --------------------------------------------------------

    @Test
    fun `a counter asks for its number instead of guessing one`() {
        val row = addHabit("read", HabitType.COUNTER, AssertSpec(20.0, "pages", 1.0))

        viewModel.onCheckbox(row)
        val prompt = awaitState { it.interaction.prompt is SuitePrompt.Value }
            .interaction.prompt as SuitePrompt.Value
        assertEquals("pages", prompt.unit)
        assertEquals("", prompt.text)

        viewModel.onPromptChange("12")
        viewModel.onSubmitPrompt()
        assertEquals("12/20 pages", awaitRow { it.comment == "12/20 pages" }.comment)
        assertNull(awaitState { it.interaction.prompt == null }.interaction.prompt)
    }

    @Test
    fun `a counter passes when the assert holds`() {
        val row = addHabit("read", HabitType.COUNTER, AssertSpec(20.0, "pages", 1.0))

        viewModel.onCheckbox(row)
        awaitState { it.interaction.prompt != null }
        viewModel.onPromptChange("23")
        viewModel.onSubmitPrompt()

        val passed = awaitRow { it.state == TestState.PASS }
        assertEquals("23 pages", passed.comment)
    }

    @Test
    fun `an empty answer clears the row instead of writing a zero`() {
        val row = addHabit("read", HabitType.COUNTER, AssertSpec(20.0, "pages", 1.0))

        viewModel.onCheckbox(row)
        awaitState { it.interaction.prompt != null }
        viewModel.onPromptChange("12")
        viewModel.onSubmitPrompt()
        val partial = awaitRow { it.comment == "12/20 pages" }

        // Reopening prefills what is already there: a correction, not a retype.
        viewModel.onCheckbox(partial)
        val reopened = awaitState { (it.interaction.prompt as? SuitePrompt.Value)?.text == "12" }
        assertEquals("12", (reopened.interaction.prompt as SuitePrompt.Value).text)

        viewModel.onPromptChange("")
        viewModel.onSubmitPrompt()
        val cleared = awaitRow { it.comment == "0/20 pages" }
        assertEquals(TestState.PENDING, cleared.state)
        assertTrue(runBlocking { db.checkDao().all() }.isEmpty())
    }

    @Test
    fun `a decimal comma is read the way the user typed it`() {
        val row = addHabit("water", HabitType.COUNTER, AssertSpec(2.0, "l", 0.5))

        viewModel.onCheckbox(row)
        awaitState { it.interaction.prompt != null }
        viewModel.onPromptChange("1,5")
        viewModel.onSubmitPrompt()
        assertEquals("1.5/2 l", awaitRow { it.comment == "1.5/2 l" }.comment)
    }

    @Test
    fun `plus one adds a step and flips the box when the target is reached`() {
        var row = addHabit("water", HabitType.COUNTER, AssertSpec(3.0, "glasses", 1.0))
        assertEquals(1.0, row.incrementStep!!, 0.0)

        viewModel.onIncrement(row)
        row = awaitRow { it.comment == "1/3 glasses" }
        viewModel.onIncrement(row)
        row = awaitRow { it.comment == "2/3 glasses" }
        viewModel.onIncrement(row)
        awaitRow { it.state == TestState.PASS }
    }

    @Test
    fun `a prompt can be escaped without writing anything`() {
        val row = addHabit("read", HabitType.COUNTER, AssertSpec(20.0, "pages", 1.0))

        viewModel.onCheckbox(row)
        awaitState { it.interaction.prompt != null }
        viewModel.onPromptChange("12")
        viewModel.onCancelPrompt()

        assertNull(awaitState { it.interaction.prompt == null }.interaction.prompt)
        assertTrue(runBlocking { db.checkDao().all() }.isEmpty())
    }

    // ---- skip ------------------------------------------------------------

    @Test
    fun `a skip carries its note and only today by default`() {
        val row = addHabit("run 5k")

        viewModel.onSkip(row)
        awaitState { it.interaction.prompt is SuitePrompt.Skip }
        viewModel.onPromptChange("rest day")
        viewModel.onSubmitPrompt()

        val skipped = awaitRow { it.state == TestState.SKIP }
        assertEquals("skip: rest day", skipped.comment)
    }

    @Test
    fun `a week away is one interaction, not one skip per day`() {
        val row = addHabit("run 5k")

        viewModel.onSkip(row)
        awaitState { it.interaction.prompt is SuitePrompt.Skip }
        repeat(2) { viewModel.onCycleSkipWindow() } // today -> 3d -> 1w
        assertEquals(
            SkipWindow.OneWeek,
            (awaitState {
                (it.interaction.prompt as? SuitePrompt.Skip)?.window == SkipWindow.OneWeek
            }.interaction.prompt as SuitePrompt.Skip).window
        )
        viewModel.onPromptChange("away")
        viewModel.onSubmitPrompt()

        assertEquals(
            "skip: away until 2026-08-27",
            awaitRow { it.state == TestState.SKIP }.comment
        )
        // One row written; the six days it covers are expanded on read.
        assertEquals(1, runBlocking { db.checkDao().all() }.size)
    }

    @Test
    fun `the skip window cycles back round`() {
        assertEquals(SkipWindow.ThreeDays, SkipWindow.Today.next())
        assertEquals(SkipWindow.Today, SkipWindow.TwoWeeks.next())
        assertNull(SkipWindow.Today.until(clock.instant().atZone(rome).toLocalDate()))
    }

    // ---- the expansion ---------------------------------------------------

    @Test
    fun `the name unfolds the spec and folds it again`() {
        val row = addHabit("meditate 10 min")

        viewModel.onDetails(row)
        assertEquals(
            row.habitId,
            awaitState { it.interaction.expandedId == row.habitId }.interaction.expandedId
        )
        viewModel.onDetails(row)
        assertNull(awaitState { it.interaction.expandedId == null }.interaction.expandedId)
    }

    @Test
    fun `archiving takes two taps and keeps the history`() {
        val row = addHabit("meditate 10 min")

        viewModel.onArchive(row)
        assertEquals(
            row.habitId,
            awaitState { it.interaction.archiveConfirmId == row.habitId }.interaction.archiveConfirmId
        )
        assertEquals(1, awaitDocument().suiteSize)

        viewModel.onArchive(row)
        assertEquals(0, awaitDocument { it.suiteSize == 0 }.suiteSize)
        // Archived, never deleted: the days it already ran belong to the user.
        assertEquals(1, runBlocking { db.habitDao().all() }.size)
    }

    @Test
    fun `the first tap of an archive can be taken back`() {
        val row = addHabit("meditate 10 min")

        viewModel.onArchive(row)
        awaitState { it.interaction.archiveConfirmId != null }
        viewModel.onCancelArchive()

        assertNull(awaitState { it.interaction.archiveConfirmId == null }.interaction.archiveConfirmId)
        assertEquals(1, awaitDocument().suiteSize)
    }

    // ---- what is not built yet -------------------------------------------

    @Test
    fun `the FAB says where the wizard is instead of doing nothing`() {
        awaitDocument()
        viewModel.onAddTest()
        assertEquals(
            SuiteViewModel.COMING_SOON_ADD,
            awaitState { it.interaction.transient != null }.interaction.transient
        )
    }

    @Test
    fun `the not-due block opens and closes`() {
        awaitDocument()
        viewModel.onToggleNotDue()
        assertTrue(awaitState { it.interaction.notDueExpanded }.interaction.notDueExpanded)
        viewModel.onToggleNotDue()
        assertFalse(awaitState { !it.interaction.notDueExpanded }.interaction.notDueExpanded)
    }

    @Test
    fun `the document arrives even on an empty suite`() {
        val document = awaitDocument()
        assertTrue(document.isEmpty)
        assertTrue(document.due.isEmpty())
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
