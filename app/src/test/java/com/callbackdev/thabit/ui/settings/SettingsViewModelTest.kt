package com.callbackdev.thabit.ui.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.callbackdev.thabit.data.SettingsStore
import com.callbackdev.thabit.ui.theme.ThemeProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
import java.time.LocalTime
import java.time.ZoneId

/**
 * The controls of `settings.config`, driven against a real DataStore.
 *
 * **One scheduler drives everything.** The view model's coroutines, the state
 * flow and DataStore's own scope all run on the same test dispatcher, so a tap
 * followed by [runCurrent] is a complete, deterministic round trip. The earlier
 * shape of this suite waited on real time and failed roughly one run in five —
 * a test that is red at random is worse than no test, because it teaches the
 * team to ignore red.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val clock: Clock =
        Clock.fixed(Instant.parse("2026-08-21T09:00:00Z"), ZoneId.of("Europe/Rome"))

    private val dispatcher = StandardTestDispatcher()
    private val dataStoreScope = CoroutineScope(dispatcher + SupervisorJob())

    private lateinit var settings: SettingsStore
    private lateinit var store: ViewModelStore
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        settings = SettingsStore(
            PreferenceDataStoreFactory.create(scope = dataStoreScope) {
                folder.newFile("settings.preferences_pb")
            },
            clock
        )
        // A real ViewModelStore so the teardown can close `viewModelScope`
        // deterministically, before the main dispatcher is handed back.
        store = ViewModelStore()
        viewModel = ViewModelProvider(store, SettingsViewModel.factory(settings, "0.1.0"))
            .get(SettingsViewModel::class.java)
    }

    @After
    fun tearDown() {
        store.clear()
        dataStoreScope.cancel()
        Dispatchers.resetMain()
    }

    /**
     * Runs a test with the state flow collected throughout — the flow is shared
     * `WhileSubscribed`, and a screen that is not composed is not a state the app
     * can be in.
     */
    private fun runUi(body: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        backgroundScope.launch { viewModel.state.collect { } }
        runCurrent()
        body()
    }

    /** Everything queued, nothing waited for: the file as it is right now. */
    private fun TestScope.file(): SettingsDocument {
        runCurrent()
        return requireNotNull(viewModel.state.value.document) { "the config has not arrived" }
    }

    private fun TestScope.interaction(): SettingsInteraction {
        runCurrent()
        return viewModel.state.value.interaction
    }

    // ---- the controls ----------------------------------------------------

    @Test
    fun `a boolean flips on tap and flips back`() = runUi {
        assertFalse(file().showLineNumbers)
        viewModel.onToggleLineNumbers()
        assertTrue(file().showLineNumbers)
        viewModel.onToggleLineNumbers()
        assertFalse(file().showLineNumbers)
    }

    @Test
    fun `word wrap is its own switch`() = runUi {
        viewModel.onToggleWordWrap()
        assertTrue(file().wordWrap)
        assertFalse(file().showLineNumbers)
    }

    @Test
    fun `day_ends cycles, and the whole app moves with it`() = runUi {
        assertEquals(LocalTime.MIDNIGHT, file().dayEnds)
        repeat(3) { viewModel.onCycleDayEnds(); runCurrent() }
        assertEquals(LocalTime.of(3, 0), file().dayEnds)
        // The boundary the domain reads is the same value, not a copy of it.
        assertEquals(LocalTime.of(3, 0), settings.settings.first().boundary.dayEnds)
    }

    @Test
    fun `week_starts cycles`() = runUi {
        viewModel.onCycleWeekStart()
        assertEquals(DayOfWeek.SUNDAY, file().weekStartsOn)
    }

    @Test
    fun `a theme is chosen by tapping the profile you want`() = runUi {
        viewModel.onSelectTheme(ThemeProfile.Monokai)
        val document = file()
        assertEquals(ThemeProfile.Monokai, document.theme)
        assertEquals("monokai", document.activeProfileValue)
        assertEquals("monokai", document.profiles.single { it.active }.value)
    }

    // ---- the modified stamp ----------------------------------------------

    @Test
    fun `an untouched file carries no last-modified line, and the first change adds it`() = runUi {
        assertNull(file().lastModified)
        viewModel.onToggleWordWrap()
        assertEquals(clock.millis(), file().lastModified)
    }

    // ---- the reset --------------------------------------------------------

    @Test
    fun `restoring takes two taps and puts every value back`() = runUi {
        viewModel.onToggleLineNumbers()
        viewModel.onSelectTheme(ThemeProfile.Dracula)
        runCurrent()
        assertTrue(file().showLineNumbers)
        assertEquals(ThemeProfile.Dracula, file().theme)

        viewModel.onRestore()
        assertTrue(interaction().restoreConfirm)
        // Still nothing lost on the first tap.
        assertTrue(file().showLineNumbers)

        viewModel.onRestore()
        val restored = file()
        assertFalse(restored.showLineNumbers)
        assertEquals(ThemeProfile.Obsidian, restored.theme)
        assertEquals(LocalTime.MIDNIGHT, restored.dayEnds)
    }

    @Test
    fun `a restored file is not a modified file`() = runUi {
        viewModel.onToggleLineNumbers()
        assertNotNull(file().lastModified)
        viewModel.onRestore()
        viewModel.onRestore()
        assertNull(file().lastModified)
    }

    @Test
    fun `the first tap of a reset can be taken back`() = runUi {
        viewModel.onRestore()
        assertTrue(interaction().restoreConfirm)
        viewModel.onCancelRestore()
        assertFalse(interaction().restoreConfirm)
    }

    @Test
    fun `touching any other control drops a pending reset`() = runUi {
        // Otherwise a confirm armed minutes ago would fire on an unrelated tap.
        viewModel.onRestore()
        assertTrue(interaction().restoreConfirm)
        viewModel.onToggleWordWrap()
        assertFalse(interaction().restoreConfirm)
        assertTrue(file().wordWrap)
    }

    // ---- what is not built yet -------------------------------------------

    @Test
    fun `the export commands answer honestly instead of doing nothing`() = runUi {
        viewModel.onExport("json")
        val message = interaction().transient
        assertNotNull(message)
        assertTrue(message!!.startsWith("$ thabit export --json"))
        assertTrue(message.contains("nothing to export yet"))
    }

    @Test
    fun `a transient answer clears itself instead of staying forever`() = runUi {
        viewModel.onExport("csv")
        assertNotNull(interaction().transient)
        advanceTimeBy(5_000)
        assertNull(interaction().transient)
    }
}
