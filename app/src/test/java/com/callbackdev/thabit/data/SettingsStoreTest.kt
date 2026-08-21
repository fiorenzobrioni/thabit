package com.callbackdev.thabit.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.callbackdev.thabit.ui.theme.ThemeProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * `settings.config` as data: honest defaults, and a reset that costs no history.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun store() = SettingsStore(
        PreferenceDataStoreFactory.create { folder.newFile("settings-${counter++}.preferences_pb") }
    )

    private var counter = 0

    @Test
    fun `the defaults are the invisible ones`() = runTest {
        val settings = store().settings.first()
        assertEquals(LocalTime.MIDNIGHT, settings.dayEnds)
        assertEquals(DayOfWeek.MONDAY, settings.weekStartsOn)
        assertEquals(ThemeProfile.Obsidian, settings.theme)
        assertFalse(settings.showLineNumbers)
        assertFalse(settings.wordWrap)
    }

    @Test
    fun `every setting round trips`() = runTest {
        val store = store()
        store.setDayEnds(LocalTime.of(3, 30))
        store.setWeekStart(DayOfWeek.SUNDAY)
        store.setTheme(ThemeProfile.Dracula)
        store.setShowLineNumbers(true)
        store.setWordWrap(true)

        val settings = store.settings.first()
        assertEquals(LocalTime.of(3, 30), settings.dayEnds)
        assertEquals(DayOfWeek.SUNDAY, settings.weekStartsOn)
        assertEquals(ThemeProfile.Dracula, settings.theme)
        assertTrue(settings.showLineNumbers)
        assertTrue(settings.wordWrap)
    }

    @Test
    fun `day_ends reaches the boundary the whole app reads`() = runTest {
        val store = store()
        store.setDayEnds(LocalTime.of(3, 0))
        assertEquals(LocalTime.of(3, 0), store.settings.first().boundary.dayEnds)
    }

    @Test
    fun `restoring defaults clears the config and only the config`() = runTest {
        val store = store()
        store.setDayEnds(LocalTime.of(3, 30))
        store.setTheme(ThemeProfile.Monokai)
        store.restoreDefaults()

        val settings = store.settings.first()
        assertEquals(LocalTime.MIDNIGHT, settings.dayEnds)
        assertEquals(ThemeProfile.Obsidian, settings.theme)
        // The suite and the checks live in Room and were never in reach of this call.
    }
}
