package com.callbackdev.thabit.di

import android.content.Context
import com.callbackdev.thabit.data.HabitRepository
import com.callbackdev.thabit.data.SettingsStore
import com.callbackdev.thabit.data.db.ThabitDatabase
import java.time.Clock

/**
 * Dependency wiring by hand — the series' choice, and it keeps paying.
 *
 * thabit has one database, one settings store and one repository. A DI framework
 * would add a build step, an annotation processor and a graph nobody can read at
 * a glance in exchange for wiring three objects. [overrideForTests] is the whole
 * feature set the app actually needed from one.
 */
object ServiceLocator {

    @Volatile
    private var graph: AppGraph? = null

    /** The app's object graph, built on first use. */
    fun graph(context: Context): AppGraph =
        graph ?: synchronized(this) {
            graph ?: DefaultAppGraph(context.applicationContext).also { graph = it }
        }

    fun repository(context: Context): HabitRepository = graph(context).repository

    fun settings(context: Context): SettingsStore = graph(context).settings

    /** Swaps the graph in a test, or restores the real one with null. */
    fun overrideForTests(replacement: AppGraph?) {
        synchronized(this) { graph = replacement }
    }
}

interface AppGraph {
    val database: ThabitDatabase
    val settings: SettingsStore
    val repository: HabitRepository
    val clock: Clock
}

private class DefaultAppGraph(private val context: Context) : AppGraph {
    override val clock: Clock = Clock.systemDefaultZone()
    override val database: ThabitDatabase by lazy { ThabitDatabase.build(context) }
    override val settings: SettingsStore by lazy { SettingsStore(context) }
    override val repository: HabitRepository by lazy {
        HabitRepository(
            habitDao = database.habitDao(),
            checkDao = database.checkDao(),
            dayDao = database.dayDao(),
            settings = settings,
            clock = clock
        )
    }
}
