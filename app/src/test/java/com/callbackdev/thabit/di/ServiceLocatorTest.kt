package com.callbackdev.thabit.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.callbackdev.thabit.data.HabitRepository
import com.callbackdev.thabit.data.SettingsStore
import com.callbackdev.thabit.data.db.ThabitDatabase
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock

/**
 * Wiring by hand, with the one feature a framework would have been bought for.
 */
@RunWith(RobolectricTestRunner::class)
class ServiceLocatorTest {

    @get:Rule
    val folder = TemporaryFolder()

    @After
    fun tearDown() = ServiceLocator.overrideForTests(null)

    @Test
    fun `the graph is built once and shared`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertSame(ServiceLocator.graph(context), ServiceLocator.graph(context))
        assertSame(ServiceLocator.repository(context), ServiceLocator.repository(context))
    }

    @Test
    fun `a test can swap the whole graph and put it back`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val real = ServiceLocator.repository(context)

        val fake = FakeGraph(folder)
        ServiceLocator.overrideForTests(fake)
        assertSame(fake.repository, ServiceLocator.repository(context))
        assertNotSame(real, ServiceLocator.repository(context))

        ServiceLocator.overrideForTests(null)
        assertNotSame(fake.repository, ServiceLocator.repository(context))
    }

    private class FakeGraph(folder: TemporaryFolder) : AppGraph {
        override val clock: Clock = Clock.systemUTC()
        override val database: ThabitDatabase =
            ThabitDatabase.inMemory(ApplicationProvider.getApplicationContext())
        override val settings: SettingsStore = SettingsStore(
            PreferenceDataStoreFactory.create { folder.newFile("fake.preferences_pb") }
        )
        override val repository: HabitRepository = HabitRepository(
            database.habitDao(), database.checkDao(), database.dayDao(), settings, clock
        )
    }
}
