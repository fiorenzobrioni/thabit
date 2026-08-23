package com.callbackdev.thabit.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.callbackdev.thabit.MainActivity
import com.callbackdev.thabit.R
import com.callbackdev.thabit.domain.model.HabitType

/**
 * Posts the three notifications, and nothing else decides whether they are
 * allowed to exist.
 *
 * Every post is gated on POST_NOTIFICATIONS at the last moment rather than
 * trusted to the caller: the grant can be taken away in the system settings
 * while an alarm is already in flight, and the honest answer to that is a
 * silent no-op, not a crash and not a stale permission cached at schedule time.
 *
 * Three channels, so the system settings can silence one without the others:
 *
 * - `reminders` — DEFAULT: the user asked for this one at a time they chose.
 * - `daily_commit` — LOW and silent: it posts at `day_ends`, often midnight, and
 *   a summary that wakes people up is precisely the noise VISION §3.3.4 forbids.
 * - `pending_digest` — LOW and silent: opt-in already, and never a nag.
 */
object ThabitNotifier {

    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_DAILY = "daily_commit"
    const val CHANNEL_DIGEST = "pending_digest"

    const val ID_DAILY = 1
    const val ID_DIGEST = 2

    /**
     * Reminder ids start well past the fixed two, so a test can never collide
     * with the daily commit however small its row id is.
     */
    const val ID_REMINDER_BASE = 1_000

    fun canPost(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun reminderId(habitId: Long): Int = (ID_REMINDER_BASE + habitId).toInt()

    fun postDailyCommit(context: Context, content: ThabitNotifications.Content) {
        post(context, ID_DAILY, CHANNEL_DAILY, R.string.notif_channel_daily, content, silent = true)
    }

    fun postDigest(context: Context, content: ThabitNotifications.Content) {
        post(context, ID_DIGEST, CHANNEL_DIGEST, R.string.notif_channel_digest, content, silent = true)
    }

    /**
     * One test's reminder, with the shade's own `[pass]` action when the test is
     * the kind a tap can settle.
     *
     * Boolean tests get the action: "did you do it" is answerable from the
     * shade, and the check-off writes the day's presence like any other
     * deliberate interaction (VISION §7). Counters do not — a value cannot be
     * guessed from a tap, so their reminder opens the file on that test's
     * prompt. Avoid tests do not either, and for a different reason: they are
     * already holding, so the only thing a shade action could offer is *"I broke
     * it"*, and a one-tap failure button on a lock screen is a trap, not a
     * feature.
     */
    fun postReminder(
        context: Context,
        habitId: Long,
        type: HabitType,
        content: ThabitNotifications.Content
    ) {
        if (!canPost(context)) return
        ensureChannel(context, CHANNEL_REMINDERS, R.string.notif_channel_reminders, NotificationManager.IMPORTANCE_DEFAULT)
        val builder = builder(context, CHANNEL_REMINDERS, content, silent = false, habitId = habitId)
        if (type == HabitType.BOOLEAN) {
            builder.addAction(
                NotificationCompat.Action.Builder(
                    0,
                    context.getString(R.string.notif_action_pass),
                    passIntent(context, habitId)
                ).build()
            )
        }
        manager(context).notify(reminderId(habitId), builder.build())
    }

    /** The reminder goes away once the test is settled from anywhere. */
    fun cancelReminder(context: Context, habitId: Long) {
        manager(context).cancel(reminderId(habitId))
    }

    private fun post(
        context: Context,
        id: Int,
        channel: String,
        channelName: Int,
        content: ThabitNotifications.Content,
        silent: Boolean
    ) {
        if (!canPost(context)) return
        ensureChannel(context, channel, channelName, NotificationManager.IMPORTANCE_LOW)
        manager(context).notify(id, builder(context, channel, content, silent, habitId = null).build())
    }

    private fun manager(context: Context): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun ensureChannel(context: Context, id: String, name: Int, importance: Int) {
        manager(context).createNotificationChannel(
            NotificationChannel(id, context.getString(name), importance)
        )
    }

    private fun builder(
        context: Context,
        channel: String,
        content: ThabitNotifications.Content,
        silent: Boolean,
        habitId: Long?
    ) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_stat_thabit)
        .setContentTitle(content.title)
        .setContentText(content.summary)
        .setStyle(NotificationCompat.BigTextStyle().bigText(content.expanded))
        .setContentIntent(openIntent(context, habitId))
        .setAutoCancel(true)
        .setSilent(silent)

    /**
     * Tapping the notification opens the app — and, for a reminder, opens it
     * **on that test**.
     *
     * The activity is started directly rather than through a receiver: since
     * Android 12 a notification may not trampoline through a broadcast to launch
     * a screen, and there is nothing to do on the way anyway.
     */
    private fun openIntent(context: Context, habitId: Long?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        habitId?.let {
            intent.putExtra(MainActivity.EXTRA_HABIT_ID, it)
            // PendingIntents compare with `filterEquals`, which ignores extras:
            // without a distinct data Uri per test, every reminder would reuse
            // the first one's intent and open the wrong row (the sibling's
            // widget lesson, learned there one row at a time).
            intent.data = Uri.parse("thabit://test/$it")
        }
        return PendingIntent.getActivity(
            context,
            habitId?.let { reminderId(it) } ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** `[pass]` from the shade: a broadcast, so nothing has to open to answer. */
    private fun passIntent(context: Context, habitId: Long): PendingIntent {
        val intent = Intent(context, CheckActionReceiver::class.java)
            .setAction(CheckActionReceiver.ACTION_PASS)
            .putExtra(MainActivity.EXTRA_HABIT_ID, habitId)
            .setData(Uri.parse("thabit://pass/$habitId"))
        return PendingIntent.getBroadcast(
            context,
            reminderId(habitId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
