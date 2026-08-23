package com.callbackdev.thabit.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.callbackdev.thabit.MainActivity
import com.callbackdev.thabit.data.NotificationSettings
import com.callbackdev.thabit.di.ServiceLocator
import com.callbackdev.thabit.domain.model.Habit
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.ZonedDateTime

/**
 * The alarms: one per test that carries a reminder, plus one for the evening
 * digest.
 *
 * **Inexact, by design** (VISION §6.7). `setWindow` asks the OS for a window
 * instead of a moment, which costs no permission at all — no
 * `SCHEDULE_EXACT_ALARM`, no user-visible "alarms & reminders" grant, no
 * exemption to ask for. A habit reminder is a nudge, and a nudge that arrives at
 * 07:04 has done its job; an app that demands the OS interrupt everything at
 * 07:00:00 for a note about reading is an app that has mistaken itself for an
 * alarm clock. The file says so where the reminder is set, so the approximation
 * is declared and not discovered.
 *
 * **One alarm per test, and not one chained "next reminder".** That single-alarm
 * design was evaluated and rejected, twice over: inexact alarms are batched by
 * the OS, so fifteen registrations wake the device exactly as often as one — the
 * battery argument for a chain is imaginary — while a chain that re-arms itself
 * after each fire stops entirely the first time a fire is missed (a reboot at
 * the wrong minute, a force-stop, a doze window that swallowed it). Independent
 * registrations degrade one test at a time, and every one of them is re-armed by
 * [rearmAll] on boot, on app open and after any edit to the suite.
 */
object Reminders {

    const val ACTION_REMIND = "com.callbackdev.thabit.action.REMIND"
    const val ACTION_DIGEST = "com.callbackdev.thabit.action.DIGEST"

    /** The digest's request code, out of reach of any row id (they start at 1). */
    private const val DIGEST_REQUEST = -1

    /** How much slack the OS is allowed. Ten minutes is also its own floor. */
    const val WINDOW_MILLIS: Long = 10 * 60 * 1000L

    /**
     * Re-registers every alarm from the stored suite and settings.
     *
     * It **cancels** as well as arms, and reads the whole table rather than the
     * live one on purpose: a test that was archived, or whose reminder was
     * cleared, has an alarm out there with its name on it, and the only way to
     * take it back is to build the same PendingIntent and cancel it.
     */
    suspend fun rearmAll(context: Context, clock: Clock = Clock.systemDefaultZone()) {
        val repository = ServiceLocator.repository(context)
        val settings = ServiceLocator.settings(context).settings.first()
        val now = ZonedDateTime.now(clock)
        armAll(context, repository.observeSuite().first(), now)
        armDigest(context, settings.notifications, now)
    }

    /**
     * The whole suite at once — **including the archived tests**, which is the
     * point: `arm` cancels whatever has no next fire, so passing everything is
     * how an alarm belonging to a test that left the suite is taken back.
     */
    fun armAll(
        context: Context,
        habits: List<Habit>,
        now: ZonedDateTime = ZonedDateTime.now()
    ) = habits.forEach { arm(context, it, now) }

    /** One test: armed when it has a reminder and a next occurrence, cancelled otherwise. */
    fun arm(context: Context, habit: Habit, now: ZonedDateTime = ZonedDateTime.now()) {
        val next = ReminderPlan.nextFire(habit, now)
        if (next == null) {
            cancel(context, habit.id)
            return
        }
        alarms(context).setWindow(
            AlarmManager.RTC_WAKEUP,
            next.toInstant().toEpochMilli(),
            WINDOW_MILLIS,
            reminderIntent(context, habit.id)
        )
    }

    fun cancel(context: Context, habitId: Long) {
        alarms(context).cancel(reminderIntent(context, habitId))
    }

    /** The evening digest, or nothing at all while it is off (it is opt-in). */
    fun armDigest(
        context: Context,
        settings: NotificationSettings,
        now: ZonedDateTime = ZonedDateTime.now()
    ) {
        val intent = digestIntent(context)
        if (!settings.pendingDigest) {
            alarms(context).cancel(intent)
            return
        }
        alarms(context).setWindow(
            AlarmManager.RTC_WAKEUP,
            ReminderPlan.nextDigest(settings.digestHour, now).toInstant().toEpochMilli(),
            WINDOW_MILLIS,
            intent
        )
    }

    private fun alarms(context: Context): AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Distinct request code **and** distinct data per test.
     *
     * `filterEquals` — which is what both `AlarmManager` and `PendingIntent`
     * compare with — ignores extras entirely, so two reminders differing only by
     * an id extra would be the same alarm: arming the second would silently
     * replace the first, and cancelling one would cancel both.
     */
    private fun reminderIntent(context: Context, habitId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            ThabitNotifier.reminderId(habitId),
            Intent(context, ReminderReceiver::class.java)
                .setAction(ACTION_REMIND)
                .putExtra(MainActivity.EXTRA_HABIT_ID, habitId)
                .setData(Uri.parse("thabit://remind/$habitId")),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun digestIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            DIGEST_REQUEST,
            Intent(context, ReminderReceiver::class.java)
                .setAction(ACTION_DIGEST)
                .setData(Uri.parse("thabit://digest")),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
