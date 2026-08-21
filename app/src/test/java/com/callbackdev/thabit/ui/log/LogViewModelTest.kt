package com.callbackdev.thabit.ui.log

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
import java.time.LocalDate
import java.time.ZoneId

/**
 * `--amend` against a real database.
 *
 * The interesting half is not that a tap writes — it is *which* day it writes
 * to, that the day says so afterwards, and that nothing older than the window
 * can be touched however the file is tapped.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LogViewModelTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val rome: ZoneId = ZoneId.of("Europe/Rome")
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
    private lateinit var viewModel: LogViewModel

    private val today: LocalDate = LocalDate.of(2026, 8, 21)
    private val yesterday: LocalDate = today.minusDays(1)

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
        viewModel = ViewModelProvider(store, LogViewModel.factory(repository, settings, clock))
            .get(LogViewModel::class.java)
    }

    @After
    fun tearDown() {
        store.clear()
        dataStoreScope.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    private fun runUi(body: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        backgroundScope.launch { viewModel.state.collect { } }
        runCurrent()
        body()
    }

    private fun TestScope.interaction(): LogInteraction {
        runCurrent()
        return viewModel.state.value.interaction
    }

    private fun TestScope.file(): LogDocument {
        runCurrent()
        return requireNotNull(viewModel.state.value.document) { "the log has not arrived" }
    }

    /** A test that has been in the suite for a week, and two days that ran. */
    private suspend fun TestScope.suiteWithHistory(
        type: HabitType = HabitType.BOOLEAN,
        assert: AssertSpec? = null
    ): Long {
        val here = now
        now = Instant.parse("2026-08-14T09:00:00Z")
        val id = repository.addHabit("meditate 10 min", type, assert)
        now = Instant.parse("2026-08-20T09:00:00Z")
        repository.markPresent()
        now = here
        repository.markPresent()
        return id
    }

    // ---- amend -------------------------------------------------------------

    @Test
    fun `yesterday answers the same tap as today, and says so afterwards`() = runUi {
        val id = suiteWithHistory()
        val commit = file().commitOn(yesterday)!!
        assertTrue(commit.amendable)
        assertFalse(commit.amended)
        assertEquals(TestState.FAIL, commit.rows.single().test!!.state)

        viewModel.onCheckbox(commit.rows.single().test!!, yesterday)

        val amended = file().commitOn(yesterday)!!
        assertEquals(TestState.PASS, amended.rows.single().test!!.state)
        assertEquals("✓ build passed (1/1)", amended.verdict?.text)
        // The marker is the point: the history admits it was edited.
        assertTrue(amended.amended)
        assertEquals("# amended", amended.headline.substringAfter("  "))
        assertEquals(id, amended.rows.single().test!!.habitId)
    }

    @Test
    fun `taking an amendment back leaves the marker where it is`() = runUi {
        suiteWithHistory()
        val row = file().commitOn(yesterday)!!.rows.single().test!!

        viewModel.onCheckbox(row, yesterday)
        val passed = file().commitOn(yesterday)!!
        viewModel.onCheckbox(passed.rows.single().test!!, yesterday)

        val back = file().commitOn(yesterday)!!
        assertEquals(TestState.FAIL, back.rows.single().test!!.state)
        // The row is back to what it was; the fact that the day was edited is not.
        assertTrue(back.amended)
    }

    @Test
    fun `a counter is amended through its own prompt`() = runUi {
        suiteWithHistory(HabitType.COUNTER, AssertSpec(20.0, "pages"))
        val row = file().commitOn(yesterday)!!.rows.single().test!!

        viewModel.onCheckbox(row, yesterday)
        runCurrent()
        assertEquals("pages", interaction().prompt?.unit)
        assertEquals(yesterday, interaction().prompt?.date)

        viewModel.onPromptChange("31")
        viewModel.onSubmitPrompt()

        val amended = file().commitOn(yesterday)!!
        assertEquals("+ [x] meditate 10 min  # 31 pages", amended.rows.single().text)
        assertTrue(amended.amended)
    }

    @Test
    fun `an empty answer clears the amended row instead of writing a zero`() = runUi {
        suiteWithHistory(HabitType.COUNTER, AssertSpec(20.0, "pages"))
        val row = file().commitOn(yesterday)!!.rows.single().test!!

        viewModel.onCheckbox(row, yesterday)
        viewModel.onPromptChange("  ")
        viewModel.onSubmitPrompt()
        runCurrent()

        assertEquals(0, db.checkDao().all().size)
    }

    @Test
    fun `the window closing under an open file stops the tap`() = runUi {
        suiteWithHistory()
        val row = file().commitOn(yesterday)!!.rows.single().test!!

        // Midnight passes with the log still on screen: what was yesterday is
        // now two days back, and two days back is history.
        now = Instant.parse("2026-08-22T00:30:00Z")
        viewModel.onCheckbox(row, yesterday)
        runCurrent()

        assertEquals(0, db.checkDao().all().size)
        assertEquals(LocalDate.of(2026, 8, 22), file().today)
        assertEquals(
            LogViewModel.rolledOver(LocalDate.of(2026, 8, 22)),
            interaction().transient?.text
        )
    }

    // ---- reading -----------------------------------------------------------

    @Test
    fun `a commit unfolds and folds again, and several can be open at once`() = runUi {
        suiteWithHistory()

        viewModel.onToggleCommit(yesterday)
        assertTrue(yesterday in interaction().expanded)

        viewModel.onToggleCommit(today.minusDays(7))
        assertEquals(2, interaction().expanded.size)

        viewModel.onToggleCommit(yesterday)
        assertFalse(yesterday in interaction().expanded)
    }

    @Test
    fun `the working tree is not a commit, and the log says what is uncommitted`() = runUi {
        suiteWithHistory()
        assertNull(file().commitOn(today))
        assertEquals("0/1 passed · 1 pending", file().todaySummary)
    }
}
