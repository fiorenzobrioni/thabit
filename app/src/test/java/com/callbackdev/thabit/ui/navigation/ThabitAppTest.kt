package com.callbackdev.thabit.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
            override val appScope = writeScope
        })
    }

    @After
    fun tearDown() {
        ServiceLocator.overrideForTests(null)
        writeScope.cancel()
        db.close()
    }

    private fun show() = compose.setContent { ThabitTheme { ThabitApp() } }

    /**
     * Waits for the file to catch up with a write.
     *
     * The suite arrives through a Room flow on its own dispatcher, so a line
     * added in the wizard is on screen a moment after the transcript closes —
     * asserting straight away is a race, and a race in a test is a lie waiting
     * to happen.
     */
    private fun awaitText(text: String) = compose.waitUntil(TIMEOUT) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun awaitDescription(description: String) = compose.waitUntil(TIMEOUT) {
        compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }

    @Test
    fun `the app opens on the suite`() {
        show()
        awaitText("# habits.test")
        compose.onNodeWithText("# habits.test").assertIsDisplayed()
    }

    @Test
    fun `each tab opens its own file`() {
        show()

        compose.onNodeWithText("Settings").performClick()
        awaitText("// settings.config")
        compose.onNodeWithText("// settings.config").assertIsDisplayed()

        compose.onNodeWithText("Log").performClick()
        awaitText("# habits_history.diff")
        compose.onNodeWithText("# habits_history.diff").assertIsDisplayed()

        compose.onNodeWithText("Stats").performClick()
        awaitText("# stats.md — not yet written")
        compose.onNodeWithText("# stats.md — not yet written").assertIsDisplayed()
    }

    @Test
    fun `coming back to a tab finds the file that was there`() {
        show()
        compose.onNodeWithText("Settings").performClick()
        awaitText("// settings.config")
        compose.onNodeWithText("// settings.config").assertIsDisplayed()

        compose.onNodeWithText("Editor").performClick()
        awaitText("# habits.test")
        compose.onNodeWithText("# habits.test").assertIsDisplayed()
        compose.onNodeWithText("// settings.config").assertDoesNotExist()
    }

    @Test
    fun `tapping the tab you are already on changes nothing`() {
        show()
        compose.onNodeWithText("Editor").performClick()
        compose.onNodeWithText("Editor").performClick()
        awaitText("# habits.test")
        compose.onNodeWithText("# habits.test").assertIsDisplayed()
    }

    @Test
    fun `the FAB opens the wizard inside the editor tab`() {
        show()
        compose.onNodeWithContentDescription("Add a test to the suite").performClick()
        awaitText("$ thabit add")
        compose.onNodeWithText("$ thabit add").assertIsDisplayed()
        // The wizard is a destination *of* the editor tab, so that tab stays lit.
        compose.onNodeWithText("Editor").assertIsDisplayed()
    }

    @Test
    fun `escaping the wizard comes back to the file`() {
        show()
        compose.onNodeWithContentDescription("Add a test to the suite").performClick()
        awaitText("$ thabit add")
        compose.onNodeWithText("$ thabit add").assertIsDisplayed()

        compose.onNodeWithText("[esc]").performClick()
        awaitText("# habits.test")
        compose.onNodeWithText("# habits.test").assertIsDisplayed()
        compose.onNodeWithText("$ thabit add").assertDoesNotExist()
    }

    @Test
    fun `a test added in the wizard is in the file when the session is left`() {
        show()
        compose.onNodeWithContentDescription("Add a test to the suite").performClick()
        compose.onNodeWithContentDescription("Name of the test").performTextInput("meditate 10 min")
        compose.onNodeWithText("[done]").performClick()
        awaitText("✓ \"meditate 10 min\" added to the suite")

        compose.onNodeWithText("[esc]").performClick()
        awaitText("meditate 10 min")
        compose.onNodeWithText("meditate 10 min").assertIsDisplayed()
    }

    @Test
    fun `edit reopens the same transcript, prefilled, and saves in place`() {
        show()
        compose.onNodeWithContentDescription("Add a test to the suite").performClick()
        compose.onNodeWithContentDescription("Name of the test").performTextInput("meditate")
        compose.onNodeWithText("[done]").performClick()
        compose.onNodeWithText("[esc]").performClick()
        awaitDescription("Details of meditate")

        compose.onNodeWithContentDescription("Details of meditate").performClick()
        compose.onNodeWithText("[edit]").performClick()

        compose.onNodeWithText("$ thabit edit \"meditate\"").assertIsDisplayed()
        compose.onNodeWithContentDescription("Name of the test: meditate").performClick()
        compose.onNodeWithContentDescription("Name of the test").performTextClearance()
        compose.onNodeWithContentDescription("Name of the test").performTextInput("meditate 10 min")
        compose.onNodeWithText("[save]").performClick()

        // An edit closes the session and hands the reader back to the file.
        awaitText("meditate 10 min")
        compose.onNodeWithText("meditate 10 min").assertIsDisplayed()
    }

    @Test
    fun `the placeholder file wears the comment marker of the file it will be`() {
        show()
        // A JSON-style file gets `//`, a YAML- or diff-style one gets `#`
        // (VISION §1.1: the comment wears the host file's syntax). Only
        // `stats.md` is still a placeholder: the log landed in Fase 6.
        compose.onNodeWithText("Stats").performClick()
        awaitText("# stats.md — not yet written")
        compose.onNodeWithText("# stats.md — not yet written").assertIsDisplayed()
    }
}
