package com.callbackdev.thabit.work

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.callbackdev.thabit.data.HabitRepository
import com.callbackdev.thabit.data.SettingsStore
import com.callbackdev.thabit.data.db.ThabitDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * The contract that matters about the rollover: **it never writes.**
 *
 * A worker stamping presence or freezing a verdict would invent a user who was
 * not there and would corrupt the one number `no run` exists to protect. This
 * test runs the job against a real database and asserts the database did not
 * move.
 */
@RunWith(RobolectricTestRunner::class)
class RolloverWorkerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var db: ThabitDatabase
    private lateinit var repository: HabitRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = ThabitDatabase.inMemory(context)
        val settings = SettingsStore(
            PreferenceDataStoreFactory.create { folder.newFile("settings.preferences_pb") }
        )
        repository = HabitRepository(db.habitDao(), db.checkDao(), db.dayDao(), settings)
    }

    @After
    fun tearDown() {
        RolloverEffects.reset()
        db.close()
    }

    @Test
    fun `the rollover writes nothing at all`() = runTest {
        // The default effect posts the day's commit against the app's own graph;
        // this test is about the database in front of it, so the effect is
        // silenced and the assertion is only about rows.
        RolloverEffects.install(RolloverEffects.None)
        val id = repository.addHabit("meditate 10 min")
        repository.pass(id, LocalDate.now())
        val checksBefore = db.checkDao().all()
        val daysBefore = db.dayDao().all()

        val worker = TestListenableWorkerBuilder<RolloverWorker>(
            ApplicationProvider.getApplicationContext()
        ).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())

        assertEquals(checksBefore, db.checkDao().all())
        assertEquals(daysBefore, db.dayDao().all())
        assertTrue("the boundary must never stamp presence", db.dayDao().all().isEmpty())
    }

    @Test
    fun `the rollover calls its effects, which are repaints and nothing else`() = runTest {
        var called = 0
        RolloverEffects.install { called++ }
        val worker = TestListenableWorkerBuilder<RolloverWorker>(
            ApplicationProvider.getApplicationContext()
        ).build()
        worker.doWork()
        assertEquals(1, called)
    }
}
