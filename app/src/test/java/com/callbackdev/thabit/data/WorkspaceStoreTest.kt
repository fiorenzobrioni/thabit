package com.callbackdev.thabit.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Editor session state: the open file, and the one-shot `HELP.md` pointer.
 *
 * Both live here rather than in `settings.config` for the same reason — neither
 * is a setting, and `$ git restore settings.config` must not close a tab or put
 * a first-run hint back in front of a reader who dealt with it a year ago.
 */
class WorkspaceStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun store() = WorkspaceStore(
        PreferenceDataStoreFactory.create(scope = scope) {
            folder.newFile("workspace-${System.nanoTime()}.preferences_pb")
        }
    )

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `the editor opens on the suite until told otherwise`() = runBlocking {
        assertEquals(EditorFile.TEST, store().editorFile.first())
    }

    @Test
    fun `the open file survives the session that chose it`() = runBlocking {
        val store = store()

        store.setEditorFile(EditorFile.README)

        assertEquals(EditorFile.README, store.editorFile.first())
    }

    @Test
    fun `the help hint is offered until it is dealt with`() = runBlocking {
        val store = store()
        assertFalse(store.helpHintDismissed.first())

        store.dismissHelpHint()

        assertTrue(store.helpHintDismissed.first())
    }

    /** Taking the hint is final: it is a pointer, not a preference. */
    @Test
    fun `a dismissed hint stays dismissed`() = runBlocking {
        val store = store()
        store.dismissHelpHint()

        store.setEditorFile(EditorFile.README)

        assertTrue(store.helpHintDismissed.first())
    }
}
