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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Fase 14: which installs get `$ thabit init` and which inherit an answer.
 *
 * The whole point is the second half — an app already carrying somebody's tests
 * must never be asked the question, whatever it would have answered.
 */
class FirstRunStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun store() = FirstRunStore(
        PreferenceDataStoreFactory.create(scope = scope) {
            folder.newFile("first-run-${System.nanoTime()}.preferences_pb")
        }
    )

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `the shell draws nothing until the check has landed`() = runBlocking {
        assertEquals(FirstRun.Unknown, store().state.first())
    }

    @Test
    fun `a fresh install is sent to init`() = runBlocking {
        val store = store()

        store.migrate(used = false)

        assertEquals(FirstRun.Pending, store.state.first())
    }

    @Test
    fun `an install that already holds tests inherits the answer`() = runBlocking {
        val store = store()

        store.migrate(used = true)

        assertEquals(FirstRun.Done, store.state.first())
    }

    /** Once decided, never revisited: writing the first test must not re-run it. */
    @Test
    fun `the check runs exactly once`() = runBlocking {
        val store = store()
        store.migrate(used = false)

        store.migrate(used = true)

        assertEquals(FirstRun.Pending, store.state.first())
    }

    @Test
    fun `skipping init still counts as answering it`() = runBlocking {
        val store = store()
        store.migrate(used = false)

        store.markInitDone()

        assertEquals(FirstRun.Done, store.state.first())
    }
}
