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
import com.callbackdev.thabit.data.WorkspaceStore
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
        val workspace = WorkspaceStore(
            PreferenceDataStoreFactory.create { folder.newFile("workspace.preferences_pb") }
        )
        ServiceLocator.overrideForTests(object : AppGraph {
            override val database = db
            override val settings = settings
            override val workspace = workspace
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
        awaitText("habits.test")
        compose.onNodeWithText("habits.test").assertIsDisplayed()
    }

    @Test
    fun `the editor tab has two files, and the strip switches between them`() {
        show()
        awaitText("habits.test")
        // Both names are in the strip; only one file is open under it.
        compose.onNodeWithText("README.md").assertIsDisplayed()

        compose.onNodeWithText("README.md").performClick()
        awaitText("## Today")
        compose.onNodeWithText("## Today").assertIsDisplayed()
        // The suite is not on screen any more, but its name still is.
        compose.onNodeWithText("# no tests in the suite yet").assertDoesNotExist()

        compose.onNodeWithText("habits.test").performClick()
        awaitText("# no tests in the suite yet")
        compose.onNodeWithText("# no tests in the suite yet").assertIsDisplayed()
    }

    @Test
    fun `the open file survives a trip to another tab`() {
        show()
        awaitText("README.md")
        compose.onNodeWithText("README.md").performClick()
        awaitText("## Today")

        compose.onNodeWithText("Settings").performClick()
        awaitText("settings.config")
        compose.onNodeWithText("Editor").performClick()

        // An editor reopens the tab you left open — it is session state, not a
        // setting, and it is why the workspace has a store of its own.
        awaitText("## Today")
        compose.onNodeWithText("## Today").assertIsDisplayed()
    }

    @Test
    fun `the FAB is on the README too, and opens the same transcript`() {
        show()
        awaitText("README.md")
        compose.onNodeWithText("README.md").performClick()
        awaitText("## Today")

        // The verb belongs to the tab, not to the suite: it does not go away
        // because the reader is on the other file.
        compose.onNodeWithContentDescription("Add a test to the suite").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add a test to the suite").performClick()
        awaitText("$ thabit add")
        compose.onNodeWithText("$ thabit add").assertIsDisplayed()
    }

    @Test
    fun `the wizard gives the README back, not the suite`() {
        show()
        awaitText("README.md")
        compose.onNodeWithText("README.md").performClick()
        awaitText("## Today")

        compose.onNodeWithContentDescription("Add a test to the suite").performClick()
        awaitText("$ thabit add")
        compose.onNodeWithText("[esc]").performClick()

        // Which file is open is the reader's decision, and adding a test is not
        // a reason for the app to overrule it.
        awaitText("## Today")
        compose.onNodeWithText("## Today").assertIsDisplayed()
        compose.onNodeWithText("$ thabit add").assertDoesNotExist()
    }

    @Test
    fun `each tab opens its own file`() {
        show()

        compose.onNodeWithText("Settings").performClick()
        awaitText("settings.config")
        compose.onNodeWithText("settings.config").assertIsDisplayed()

        compose.onNodeWithText("Log").performClick()
        awaitText("habits_history.diff")
        compose.onNodeWithText("habits_history.diff").assertIsDisplayed()

        compose.onNodeWithText("Stats").performClick()
        awaitText("stats.md")
        compose.onNodeWithText("stats.md").assertIsDisplayed()
        // An empty suite still draws its grid: the shape of what is coming.
        compose.onNodeWithText("## contributions (last 12 weeks)").assertIsDisplayed()
    }

    @Test
    fun `coming back to a tab finds the file that was there`() {
        show()
        compose.onNodeWithText("Settings").performClick()
        awaitText("settings.config")
        compose.onNodeWithText("settings.config").assertIsDisplayed()

        compose.onNodeWithText("Editor").performClick()
        awaitText("habits.test")
        compose.onNodeWithText("habits.test").assertIsDisplayed()
        compose.onNodeWithText("settings.config").assertDoesNotExist()
    }

    @Test
    fun `tapping the tab you are already on changes nothing`() {
        show()
        compose.onNodeWithText("Editor").performClick()
        compose.onNodeWithText("Editor").performClick()
        awaitText("habits.test")
        compose.onNodeWithText("habits.test").assertIsDisplayed()
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
        awaitText("habits.test")
        compose.onNodeWithText("habits.test").assertIsDisplayed()
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

}
