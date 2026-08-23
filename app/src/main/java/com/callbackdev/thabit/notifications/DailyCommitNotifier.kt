package com.callbackdev.thabit.notifications

import android.content.Context
import android.content.res.Resources
import com.callbackdev.thabit.data.HabitRepository
import com.callbackdev.thabit.data.NotificationStateStore
import com.callbackdev.thabit.data.SettingsStore
import com.callbackdev.thabit.di.ServiceLocator
import com.callbackdev.thabit.domain.Verdicts
import com.callbackdev.thabit.ui.log.LogDocument
import kotlinx.coroutines.flow.first

/**
 * The `daily_commit` notification: the day that just closed, said once.
 *
 * This is the whole of what the rollover does with the data, and it is worth
 * being explicit about what it is **not**: it reads, it never writes (VISION §7).
 * The verdict it announces is derived from the check rows on the spot, the same
 * way the log derives it a second later when the user opens the app — there is
 * no frozen result anywhere, so an `--amend` tomorrow simply makes both of them
 * say something else.
 *
 * The one thing it does store is that it *spoke*, in [NotificationStateStore].
 * That is a fact about the notification, not about the day.
 */
object DailyCommitNotifier {

    /** Wired into the rollover worker. */
    suspend fun run(context: Context) {
        val app = context.applicationContext
        evaluate(
            repository = ServiceLocator.repository(app),
            settingsStore = ServiceLocator.settings(app),
            stateStore = ServiceLocator.notificationState(app),
            resources = app.resources,
            canPost = { ThabitNotifier.canPost(app) },
            post = { content -> ThabitNotifier.postDailyCommit(app, content) }
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
        if (!settings.notifications.dailyCommit || !canPost()) return

        val today = repository.today()
        val closed = today.minusDays(1)
        // Already announced — a periodic job that ran twice, an alarm the OS
        // batched into the next day, a reboot in between. `>=` and not `==`: a
        // clock moved backwards must not make yesterday announceable again.
        stateStore.committedDay()?.let { if (it >= closed) return }

        val history = repository.fullHistory()
        val run = Verdicts.dayRun(history, closed, today)
        // A day the app never saw has no commit at all, and a day whose tests
        // were all skipped has a commit with no verdict. Neither is worth a
        // buzz: the first would be a red build nobody earned (VISION §3.3.8),
        // the second a notification with nothing in it.
        if (!run.hasCommit || !run.result.hasBadge) return
        val commit = LogDocument.of(history, today, settings.dayEnds).commitOn(closed) ?: return

        // Marked before posting: a crash between the two costs one notification,
        // never a repeat of one (the sibling's rule).
        stateStore.markCommitted(closed)
        post(ThabitNotifications.dailyCommit(run, commit, resources))
    }
}
