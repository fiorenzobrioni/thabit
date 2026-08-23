package com.callbackdev.thabit.notifications

import android.content.Context
import android.content.res.Resources
import com.callbackdev.thabit.data.HabitRepository
import com.callbackdev.thabit.domain.TestOutcome
import com.callbackdev.thabit.domain.TestState
import com.callbackdev.thabit.domain.Verdicts
import com.callbackdev.thabit.di.ServiceLocator
import java.time.ZonedDateTime

/**
 * One test's reminder, decided at the moment it rings.
 *
 * The alarm only knows a row id and a clock; everything else is re-read here,
 * and that is the design (see [ReminderPlan]). A test archived this morning, a
 * schedule edited yesterday, a quota whose week is already full, a box ticked an
 * hour ago — each of them turns the alarm into a silent wake-up instead of a
 * notification about something that is no longer true.
 *
 * The one state that surprises people is **avoid**: a holding avoid test *is*
 * reminded. Its reminder is not "you still owe this", it is the intention itself
 * arriving at the hour it matters (`no phone after 23:00`, at five to eleven),
 * and the body says in plain words that it is holding. Suppressing it would make
 * `remind:` on an avoid test a field that does nothing — a file lying about
 * itself.
 */
object ReminderNotifier {

    /** Fired by one test's alarm; re-arms that test for its next occurrence. */
    suspend fun run(context: Context, habitId: Long) {
        val app = context.applicationContext
        val repository = ServiceLocator.repository(app)
        evaluate(
            repository = repository,
            habitId = habitId,
            resources = app.resources,
            canPost = { ThabitNotifier.canPost(app) },
            post = { outcome, content ->
                ThabitNotifier.postReminder(app, habitId, outcome.habit.type, content)
            }
        )
        // Re-armed whatever the answer was: a reminder suppressed today must
        // still ring tomorrow, and this is also where an archived test's alarm
        // finally goes away (arm() cancels when there is no next fire).
        repository.habit(habitId)?.let { habit ->
            Reminders.arm(app, habit, ZonedDateTime.now(ServiceLocator.graph(app).clock))
        } ?: Reminders.cancel(app, habitId)
    }

    /** Dependency-explicit core, unit-tested without a NotificationManager. */
    suspend fun evaluate(
        repository: HabitRepository,
        habitId: Long,
        resources: Resources,
        canPost: () -> Boolean = { true },
        post: (TestOutcome, ThabitNotifications.Content) -> Unit
    ) {
        if (!canPost()) return
        val habit = repository.habit(habitId) ?: return
        val remindAt = habit.remindAt ?: return
        if (habit.archivedAt != null) return

        val today = repository.today()
        val outcome = Verdicts
            .outcomesOn(repository.fullHistory(), today, today)
            .firstOrNull { it.habit.id == habitId }
            ?: return
        // Settled already — passed, failed, skipped, or a counter the user has
        // finished. Nothing to nudge about, and nudging anyway is how an app
        // teaches people to swipe it away without reading.
        if (outcome.state != TestState.PENDING && outcome.state != TestState.HOLDING) return

        post(outcome, ThabitNotifications.reminder(outcome, remindAt, resources))
    }
}
