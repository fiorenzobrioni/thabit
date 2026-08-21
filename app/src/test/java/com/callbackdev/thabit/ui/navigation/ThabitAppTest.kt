package com.callbackdev.thabit.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.thabit.data.HabitRepository
import com.callbackdev.thabit.data.SettingsStore
import com.callbackdev.thabit.data.db.ThabitDatabase
import com.callbackdev.thabit.di.AppGraph
import com.callbackdev.thabit.di.ServiceLocator
import com.callbackdev.thabit.ui.theme.ThabitTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock

/**
 * The shell: four files, one bottom bar, and a tab that remembers where it was.
 */
@RunWith(RobolectricTestRunner::class)
class ThabitAppTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var db: ThabitDatabase

    @Before
    fun setUp() {
        // No setMain here on purpose: the screens collect through
        // collectAsStateWithLifecycle, and a lifecycle observer may only be
        // removed on the real main thread — which the Compose rule already
        // drives. Swapping the main dispatcher out from under it fails the
        // teardown of every test in this class.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = ThabitDatabase.inMemory(context)
        val settings = SettingsStore(
            PreferenceDataStoreFactory.create { folder.newFile("settings.preferences_pb") }
        )
        val repository = HabitRepository(db.habitDao(), db.checkDao(), db.dayDao(), settings)
        ServiceLocator.overrideForTests(object : AppGraph {
            override val database = db
            override val settings = settings
            override val repository = repository
            override val clock: Clock = Clock.systemDefaultZone()
        })
    }

    @After
    fun tearDown() {
        ServiceLocator.overrideForTests(null)
        db.close()
    }

    private fun show() = compose.setContent { ThabitTheme { ThabitApp() } }

    @Test
    fun `the app opens on the suite`() {
        show()
        compose.onNodeWithText("# habits.test").assertIsDisplayed()
    }

    @Test
    fun `each tab opens its own file`() {
        show()

        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("// settings.config").assertIsDisplayed()

        compose.onNodeWithText("Log").performClick()
        compose.onNodeWithText("# habits_history.diff — not yet written").assertIsDisplayed()

        compose.onNodeWithText("Stats").performClick()
        compose.onNodeWithText("# stats.md — not yet written").assertIsDisplayed()
    }

    @Test
    fun `coming back to a tab finds the file that was there`() {
        show()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("// settings.config").assertIsDisplayed()

        compose.onNodeWithText("Editor").performClick()
        compose.onNodeWithText("# habits.test").assertIsDisplayed()
        compose.onNodeWithText("// settings.config").assertDoesNotExist()
    }

    @Test
    fun `tapping the tab you are already on changes nothing`() {
        show()
        compose.onNodeWithText("Editor").performClick()
        compose.onNodeWithText("Editor").performClick()
        compose.onNodeWithText("# habits.test").assertIsDisplayed()
    }

    @Test
    fun `the placeholder files wear the comment marker of the file they will be`() {
        show()
        // A JSON-style file gets `//`, a YAML- or diff-style one gets `#`
        // (VISION §1.1: the comment wears the host file's syntax).
        compose.onNodeWithText("Log").performClick()
        compose.onNodeWithText("# habits_history.diff — not yet written").assertIsDisplayed()
    }
}
