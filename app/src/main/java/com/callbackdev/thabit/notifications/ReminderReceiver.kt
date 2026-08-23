package com.callbackdev.thabit.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.callbackdev.thabit.MainActivity
import com.callbackdev.thabit.di.ServiceLocator
import kotlinx.coroutines.launch

/**
 * Where the alarms land: one test's reminder, or the evening digest.
 *
 * A receiver's `onReceive` runs on the main thread and its process may be killed
 * the moment it returns, so the work goes on [goAsync] and on the app's own
 * scope — the same scope the wizard writes with, and for the same reason: a job
 * that outlives the thing that started it needs a scope that does too.
 *
 * **Nothing here writes data.** Both branches read the suite, decide whether
 * there is anything worth saying, and re-arm their own alarm. Presence in
 * particular is untouched: a reminder the user never looked at must not make the
 * day count as a day they were there (VISION §7).
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val action = intent.action ?: return
        val habitId = intent.getLongExtra(MainActivity.EXTRA_HABIT_ID, NO_ID)
        val pending = goAsync()
        ServiceLocator.graph(app).appScope.launch {
            try {
                when (action) {
                    Reminders.ACTION_REMIND -> if (habitId != NO_ID) {
                        ReminderNotifier.run(app, habitId)
                    }
                    Reminders.ACTION_DIGEST -> PendingDigestNotifier.run(app)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val NO_ID = -1L
    }
}
