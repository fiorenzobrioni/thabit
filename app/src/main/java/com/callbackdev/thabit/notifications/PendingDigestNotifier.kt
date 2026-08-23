package com.callbackdev.thabit.notifications

import android.content.Context
import android.content.res.Resources
import com.callbackdev.thabit.data.HabitRepository
import com.callbackdev.thabit.data.NotificationStateStore
import com.callbackdev.thabit.data.SettingsStore
import com.callbackdev.thabit.di.ServiceLocator
import com.callbackdev.thabit.domain.TestState
import com.callbackdev.thabit.domain.Verdicts
import kotlinx.coroutines.flow.first
import java.time.ZonedDateTime

/**
 * The `pending_digest`: **one** evening summary, opt-in, never a nag per test
 * (VISION §3.3.4).
 *
 * It is the only notification in the app that could read as pressure, which is
 * why it is off by default, why it is a single message however many tests are
 * open, and why it says nothing at all when there is nothing open. It also never
 * mentions a verdict: at eight in the evening the day is still the working tree,
 * and a pending test is not a failed one.
 *
 * Holding avoid tests are left out on purpose. A test that passes unless you
 * break it is not something to be reminded of — listing it would turn "you are
 * doing fine" into an item on a to-do list.
 */
object PendingDigestNotifier {

    /** Fired by the evening alarm; re-arms itself for tomorrow. */
    suspend fun run(context: Context) {
        val app = context.applicationContext
        evaluate(
            repository = ServiceLocator.repository(app),
            settingsStore = ServiceLocator.settings(app),
            stateStore = ServiceLocator.notificationState(app),
            resources = app.resources,
            canPost = { ThabitNotifier.canPost(app) },
            post = { content -> ThabitNotifier.postDigest(app, content) }
        )
        Reminders.armDigest(
            app,
            ServiceLocator.settings(app).settings.first().notifications,
            ZonedDateTime.now(ServiceLocator.graph(app).clock)
        )
    }

    /** Dependency-explicit core, unit-tested without a NotificationManager. */
    suspend fun evaluate(
        repository: HabitRepository,
        settingsStore: SettingsStore,
        stateStore: NotificationStateStore,
        resources: Resources,
        canPost: () -> Boolean = { true },
        post: (ThabitNotifications.Content) -> Unit
    ) {
        val settings = settingsStore.settings.first()
        if (!settings.notifications.pendingDigest || !canPost()) return

        val today = repository.today()
        if (stateStore.digestedDay() == today) return

        val pending = Verdicts
            .outcomesOn(repository.fullHistory(), today, today)
            .filter { it.state == TestState.PENDING }
        // Nothing open: silence, and no mark either — the day is not "digested",
        // it simply had nothing to say at this hour.
        if (pending.isEmpty()) return

        stateStore.markDigested(today)
        post(ThabitNotifications.pendingDigest(pending, settings.dayEnds, resources))
    }
}
