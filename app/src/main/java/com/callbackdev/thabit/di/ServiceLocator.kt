package com.callbackdev.thabit.di

import android.content.Context
import com.callbackdev.thabit.data.HabitRepository
import com.callbackdev.thabit.data.NotificationStateStore
import com.callbackdev.thabit.data.SettingsStore
import com.callbackdev.thabit.data.WorkspaceStore
import com.callbackdev.thabit.data.db.ThabitDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    fun workspace(context: Context): WorkspaceStore = graph(context).workspace

    fun notificationState(context: Context): NotificationStateStore =
        graph(context).notificationState

    /** Swaps the graph in a test, or restores the real one with null. */
    fun overrideForTests(replacement: AppGraph?) {
        synchronized(this) { graph = replacement }
    }
}

interface AppGraph {
    val database: ThabitDatabase
    val settings: SettingsStore

    /** Session state — which file the editor tab has open (Fase 7). */
    val workspace: WorkspaceStore

    /** What the app has already announced, so it never announces it twice (Fase 9). */
    val notificationState: NotificationStateStore
    val repository: HabitRepository
    val clock: Clock

    /**
     * For writes that must outlive the screen that started them.
     *
     * A `viewModelScope` dies with its destination, so a write started by a tap
     * that also navigates away — adding a test and immediately leaving the
     * transcript — can be cancelled halfway and lose what the user just typed.
     * That is a one-star bug, and the fix is a scope that belongs to the app
     * rather than to a screen. Reads and screen state stay in `viewModelScope`,
     * where cancelling them on the way out is exactly right.
     */
    val appScope: CoroutineScope
}

private class DefaultAppGraph(private val context: Context) : AppGraph {
    override val clock: Clock = Clock.systemDefaultZone()
    override val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override val database: ThabitDatabase by lazy { ThabitDatabase.build(context) }
    override val settings: SettingsStore by lazy { SettingsStore(context) }
    override val workspace: WorkspaceStore by lazy { WorkspaceStore.create(context) }
    override val notificationState: NotificationStateStore by lazy {
        NotificationStateStore.create(context)
    }
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
